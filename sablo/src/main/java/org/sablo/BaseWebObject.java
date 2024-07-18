/*
 * Copyright (C) 2014 Servoy BV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sablo;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.PropertyDescription.PDAndComputedPushToServer;
import org.sablo.specification.PropertyDescriptionBuilder;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectFunctionDefinition;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IGranularProtectionChecker;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.IPushToServerSpecialType;
import org.sablo.specification.property.ISmartPropertyValue;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.WrappingContext;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.specification.property.types.EnabledPropertyType;
import org.sablo.specification.property.types.ProtectedConfig;
import org.sablo.specification.property.types.VisiblePropertyType;
import org.sablo.util.ValueReference;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.TypedDataWithChangeInfo;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a base web object that hold properties and records changes for the specific {@link WebComponentSpecProvider}
 *
 * @author jcompagner
 */
public abstract class BaseWebObject implements IWebObjectContext
{

	private static final TypedData<Map<String, Object>> EMPTY_PROPERTIES = new TypedData<Map<String, Object>>(Collections.<String, Object> emptyMap(), null);
	private static final TypedDataWithChangeInfo EMPTY_PROPERTIES_WITH_CHANGE_INFO = new TypedDataWithChangeInfo(Collections.<String, Object> emptyMap(), null,
		null);

	protected static final Logger log = LoggerFactory.getLogger(BaseWebObject.class.getCanonicalName());


	protected final WebObjectSpecification specification;

	/**
	 * model properties to interact with webcomponent values, maps name to value
	 */
	protected final Map<String, Object> properties = new HashMap<>();

	/**
	 * default model properties that are not send to the browser or template (design) values that were sent to browser as template values but have not (yet) changed at runtime;
	 * the common thing for these is that they either don't have to be sent initially to client
	 * or they have already been sent to client as template-to-json; they will not be written afterwards when a fullToJSON happens or when a changesToJSON happens
	 */
	protected final Map<String, Object> defaultAndTemplatePropertiesUnwrapped = new HashMap<>();

	/**
	 * the event handlers
	 */
	private final ConcurrentMap<String, IEventHandler> eventHandlers = new ConcurrentHashMap<String, IEventHandler>();

	private final Changes changes;
	private PropertyChangeSupport propertyChangeSupport;
	private IDirtyPropertyListener dirtyPropertyListener;

	protected final String name;

	private boolean propertiesInitialized; // we want to be able to convert all initial property values from sablo to web component before 'attaching' them if they are instances of ISmartPropertyValue; so we wait for all to be set and then trigger onPropertyChanged on all of them

	public BaseWebObject(String name, WebObjectSpecification specification)
	{
		this(name, specification, false);
	}

	/**
	 * @param waitForPropertyInitBeforeAttach if true it will block property change propagation and smart property values attaching initially - until
	 * {@link #propertiesInitialized()} is called. Do not forget to call {@link #propertiesInitialized()} after setting initial props. if you set this to true.
	 */
	public BaseWebObject(String name, WebObjectSpecification specification, boolean waitForPropertyInitBeforeAttach)
	{
		propertiesInitialized = !waitForPropertyInitBeforeAttach;

		this.name = name;
		this.specification = specification;
		changes = new Changes();

		if (specification == null) throw new IllegalStateException("Cannot work without a specification for: " + name);
	}

	/**
	 * Returns the component name
	 *
	 * @return the name
	 */
	public final String getName()
	{
		return name;
	}

	/**
	 * @return the specification
	 */
	public WebObjectSpecification getSpecification()
	{
		return specification;
	}

	/**
	 * @param dirtyPropertyListener set the listeners that is called when {@link WebFormComponent#flagPropertyAsDirty(String) is called
	 */
	public void setDirtyPropertyListener(IDirtyPropertyListener dirtyPropertyListener)
	{
		this.dirtyPropertyListener = dirtyPropertyListener;
	}

	public class Changes
	{
		/**
		 * Keeps track of properties that have changed content.
		 */
		private final Set<String> propertiesWithChangedContent = new HashSet<>(3);

		/**
		 * Keeps track of properties that have changed by reference (a different (!=) value was assigned to them).
		 */
		private final Set<String> propertiesChangedByRef = new HashSet<>(3);

		private boolean shouldNotReceiveChanges = false;

		private void setShouldNotReceiveChanges(boolean shouldNotReceiveChanges)
		{
			this.shouldNotReceiveChanges = shouldNotReceiveChanges;
		}

		private void clearChanges()
		{
			propertiesChangedByRef.clear();
			propertiesWithChangedContent.clear();
		}

		public boolean clearChangedStatusForProperty(String key)
		{
			boolean somethingHappened = propertiesChangedByRef.remove(key); // whether or not we will add or remove it from a changes map (if it is already there or can't be removed, it will be false)
			// remove it from any of the 2 changes map (it can't be in both)
			somethingHappened = propertiesWithChangedContent.remove(key) || somethingHappened;

			if (somethingHappened && dirtyPropertyListener != null) dirtyPropertyListener.propertyFlaggedAsDirty(key, false, false);

			return somethingHappened;
		}

		private void checkIfChangeCameInWhenItShouldnt()
		{
			if (shouldNotReceiveChanges)
			{
				if (log.isDebugEnabled()) log.debug(
					"A new change was registered while previous changes are being handled; probably one property's toJSON ends up marking another property as dirty. This should be avoided. See associated stack trace", //$NON-NLS-1$
					new RuntimeException("Stack trace")); //$NON-NLS-1$
			}
		}

		private boolean markElementContentsUpdated(String key)
		{
			checkIfChangeCameInWhenItShouldnt(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean somethingHappened = false;
			if (!changes.propertiesWithChangedContent.contains(key) && !changes.propertiesChangedByRef.contains(key))
			{
				somethingHappened = changes.propertiesWithChangedContent.add(key);
			} // else don't add it in the changes map as it's already in the ref. changed map; it will be fully sent to client

			if (somethingHappened) makeSureItIsInPropertiesMapAndNotInDefaultAndTemplateMap(key);

			if (somethingHappened && dirtyPropertyListener != null) dirtyPropertyListener.propertyFlaggedAsDirty(key, true, true);
			return somethingHappened;
		}

		public boolean markElementChangedByRef(String key)
		{
			checkIfChangeCameInWhenItShouldnt(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean somethingHappened = changes.propertiesChangedByRef.add(key); // whether or not we will add it to a changes map (if it is already there or can't be removed, it will be false)
			if (somethingHappened)
			{
				// we just added it to be sent fully; remove it from contents changed so that it will not be sent twice
				changes.propertiesWithChangedContent.remove(key);
				makeSureItIsInPropertiesMapAndNotInDefaultAndTemplateMap(key);
			}

			if (somethingHappened && dirtyPropertyListener != null) dirtyPropertyListener.propertyFlaggedAsDirty(key, true, false);

			return somethingHappened;
		}

		private void makeSureItIsInPropertiesMapAndNotInDefaultAndTemplateMap(String key)
		{
			// it is changed and needs to be re-sent to client - so make sure it's in 'properties' not in 'defaultAndTemplatePropertiesUnwrapped'
			// because full/changes toJSON does not send stuff from 'defaultAndTemplatePropertiesUnwrapped'
			if (!properties.containsKey(key) && defaultAndTemplatePropertiesUnwrapped.containsKey(key))
			{
				properties.put(key, wrapPropertyValue(key, null,
					defaultAndTemplatePropertiesUnwrapped.remove(key)));
			}
		}

		public boolean isChanged()
		{
			for (String propertyName : propertiesWithChangedContent)
			{
				if (isVisible(propertyName) || isVisibilityProperty(propertyName))
				{
					return true;
				}
			}
			for (String propertyName : propertiesChangedByRef)
			{
				if (isVisible(propertyName) || isVisibilityProperty(propertyName))
				{
					return true;
				}
			}

			return false;
		}

		/**
		 * Get the changes of this component, clear changes. It does not write them to JSON just returns the java content + prop. types + change types.
		 * When the component is not visible, only the visibility-properties are returned and cleared from the changes.
		 */
		public TypedDataWithChangeInfo getAndClearChanges()
		{
			if (propertiesChangedByRef.isEmpty() && propertiesWithChangedContent.isEmpty())
			{
				return EMPTY_PROPERTIES_WITH_CHANGE_INFO;
			}

			Map<String, Object> changesMap = null;
			PropertyDescriptionBuilder changeTypes = null;
			Set<String> propertiesWithContentUpdateOnly = null;

			// add all changes to an array to iterate on all
			int idxStartChangedByRef = propertiesWithChangedContent.size();
			ArrayList<String> allChangedPropertyNames = new ArrayList<String>(idxStartChangedByRef + propertiesChangedByRef.size());
			allChangedPropertyNames.addAll(propertiesWithChangedContent);
			allChangedPropertyNames.addAll(propertiesChangedByRef);

			for (int i = 0; i < allChangedPropertyNames.size(); i++)
			{
				String propertyName = allChangedPropertyNames.get(i);
				if (isVisible(propertyName) || isVisibilityProperty(propertyName))
				{
					clearChangedStatusForProperty(propertyName);
					if (changesMap == null)
					{
						changesMap = new HashMap<>();
					}
					changesMap.put(propertyName, properties.get(propertyName));
					PropertyDescription t = specification.getProperty(propertyName);
					if (t != null)
					{
						if (changeTypes == null)
						{
							changeTypes = AggregatedPropertyType.newAggregatedPropertyBuilder();
						}
						changeTypes.withProperty(propertyName, t);
					}
					if (i < idxStartChangedByRef)
					{
						// this is a property with content updates only
						if (propertiesWithContentUpdateOnly == null) propertiesWithContentUpdateOnly = new HashSet<>();
						propertiesWithContentUpdateOnly.add(propertyName);
					}
				}
			}

			if (changesMap == null)
			{
				return EMPTY_PROPERTIES_WITH_CHANGE_INFO;
			}

			return new TypedDataWithChangeInfo(changesMap, changeTypes != null ? changeTypes.build() : null, propertiesWithContentUpdateOnly);
		}

	}

	/**
	 * Execute incoming event
	 *
	 * @param eventType
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public final JSONString executeEvent(String eventType, Object[] args) throws Exception
	{
		WebObjectFunctionDefinition handler = getSpecification().getHandler(eventType);
		try
		{
			checkForProtectedPropertiesThatMightBlockUpdatesOn(eventType);
		}
		catch (IllegalChangeFromClientException e)
		{
			checkIfFunctionAccessIsAllowedDisregardingProtection(handler, e);
		}

		// test if this is a private handler, should not be callable from a client
		if (handler != null && handler.isPrivate()) throw new IllegalAccessException(
			"Event " + eventType + " is called from the client, but it is a private event of " + this + " with spec " + specification);

		EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true);

		FullValueToJSONConverter.INSTANCE.toJSONValue(ejw, null, doExecuteEvent(eventType, args), handler != null ? handler.getReturnType() : null,
			new BrowserConverterContext(this, PushToServerEnum.reject));
		return ejw;
	}

	protected void checkIfFunctionAccessIsAllowedDisregardingProtection(WebObjectFunctionDefinition functionDef, IllegalChangeFromClientException e)
	{
		boolean rethrow = true;
		if (functionDef != null)
		{
			String allowAccess = functionDef.getAllowAccess();
			if (allowAccess != null)
			{
				List<String> allowAccessProperties = Arrays.asList(allowAccess.split(","));
				for (String element : allowAccessProperties)
				{
					if (element.equals(e.getBlockedByProperty()))
					{
						rethrow = false;
						break;
					}
				}
			}
		}
		if (rethrow)
		{
			throw e;
		}
	}

	protected Object doExecuteEvent(String eventType, Object[] args) throws Exception
	{
		IEventHandler handler = getEventHandler(eventType);
		if (handler == null)
		{
			log.warn("Unknown event '" + eventType + "' for component " + this);
			return null;
		}
		return handler.executeEvent(args); // FIXME here we know it's comming from client json?/sablo/java and returning to client json; see SVY-18096
	}

	public final boolean isVisible()
	{
		return isVisible(null);
	}

	/**
	 * Determine visibility on properties of type VisiblePropertyType.INSTANCE.
	 *
	 * @param property check properties that have for defined for this  when null, check for component-level visibility.
	 */
	public boolean isVisible(String property)
	{
		for (PropertyDescription prop : specification.getProperties(VisiblePropertyType.INSTANCE))
		{
			if (Boolean.FALSE.equals(getProperty(prop.getName())))
			{
				Object config = prop.getConfig();
				if (config instanceof ProtectedConfig && ((ProtectedConfig)config).getForEntries() != null)
				{
					Collection<String> forEntries = (((ProtectedConfig)config).getForEntries()).getEntries();
					if (forEntries != null && forEntries.size() > 0 && (property == null || !forEntries.contains(property)))
					{
						// specific visibility-property, not for this property
						continue;
					}
				}

				// general visibility-property or specific for this property
				return false;
			}
		}

		return true;
	}

	public final void setVisible(boolean visible)
	{
		boolean set = false;
		for (PropertyDescription prop : specification.getProperties(VisiblePropertyType.INSTANCE))
		{
			Object config = prop.getConfig();
			if (config instanceof ProtectedConfig && ((ProtectedConfig)config).getForEntries() != null)
			{
				Collection<String> forEntries = (((ProtectedConfig)config).getForEntries()).getEntries();
				if (forEntries != null && forEntries.size() > 0)
				{
					// specific enable-property, skip
					continue;
				}
			}

			setProperty(prop.getName(), Boolean.valueOf(visible));
			set = true;
		}

		if (!set)
		{
			log.warn("Could not set component '" + getName() + "' visibility to " + visible + ", no visibility property found");
		}
	}

	public boolean isVisibilityProperty(String propertyName)
	{
		PropertyDescription description = specification.getProperty(propertyName);
		return description != null && description.getType() == VisiblePropertyType.INSTANCE;
	}

	/**
	 * Check protection of property.
	 * Validate if component or not visible or protected by another property.
	 *
	 * @throws IllegalChangeFromClientException when property is protected
	 */
	protected void checkForProtectedPropertiesThatMightBlockUpdatesOn(String property)
	{
		for (PropertyDescription prop : specification.getProperties().values())
		{
			if (prop.getType().isProtecting())
			{
				Object config = prop.getConfig();

				// visible default true, so block on false by default
				// protected default false, so block on true by default
				boolean blockingOn = Boolean.FALSE.equals(prop.getType().defaultValue(prop));
				if (config instanceof ProtectedConfig)
				{
					blockingOn = ((ProtectedConfig)config).getBlockingOn();
				}

				if (Boolean.valueOf(blockingOn).equals(getProperty(prop.getName())))
				{
					if (config instanceof ProtectedConfig && ((ProtectedConfig)config).getForEntries() != null)
					{
						Collection<String> forEntries = (((ProtectedConfig)config).getForEntries()).getEntries();
						if (forEntries != null && forEntries.size() > 0 && (property == null || !forEntries.contains(property)))
						{
							// specific enable-property, not for this property
							continue;
						}
					}
					// general protected property or specific for this property
					throw new IllegalChangeFromClientException(prop.getName(), "Changes from client for property '" + property +
						"' are not allowed when the value of property '" + prop.getName() + "' is " + blockingOn, getName(), property);
				}
			}
		}

		// ok
	}

	/**
	 * Check if the property is protected from browser updates by push-to-server or by the fact that it is a "protecting" prop. itself, i.e. it cannot be set from the client.
	 *
	 * @param propName can be a simple property name string or a nested property name with '.' and '[]'.
	 * @throws IllegalChangeFromClientException when property is protected
	 */
	protected void checkThatPushToServerAllowsUpdatesOn(String propName)
	{
		List<PropertyDescription> propertyPath = specification.getPropertyPath(propName);
		PushToServerEnum computedPushToServer = null;
		for (int i = 0; i < propertyPath.size(); i++)
		{
			PropertyDescription property = propertyPath.get(i);

			if (i == 0) computedPushToServer = property.getPushToServer();
			else computedPushToServer = computedPushToServer.combineWithChild(property.getPushToServerAsDeclaredInSpecFile());

			if (property.getType().isProtecting())
			{
				// property is protected, i.e. it cannot be set from the client
				throw new IllegalChangeFromClientException(null,
					"Property '" + propertyPath.stream().limit(i + 1).map(pd -> pd.getName()).reduce(specification.getName(), (a, b) -> a + '.' + b) +
						"' is a 'protecting' property itself so it can never be changed from client.",
					getName(), propName);
			}

			if (PushToServerEnum.allow.compareTo(computedPushToServer) > 0 && (!(property.getType() instanceof IPushToServerSpecialType) ||
				!((IPushToServerSpecialType)property.getType()).shouldAlwaysAllowIncommingJSON()))
			{
				// computed pushToServer not at least 'allow' on the prop. itself and parents; it should not be changed from the client
				throw new IllegalChangeFromClientException(null,
					"Property '" + propertyPath.stream().limit(i + 1).map(pd -> pd.getName()).reduce(specification.getName(), (a, b) -> a + '.' + b) +
						"' has computed 'pushToServer' set to 'reject' so it cannot be changed from client.",
					getName(), propName);
			}
		}
		// if propertyPath.size() == 0, so properties that are sent from browser but are not in spec will be converted to null later anyway in BaseWebObject.convertValueFromJSON() because PD is null
	}

	/**
	 * Get a property
	 *
	 * @param propertyName
	 * @return the value or null
	 */
	public Object getProperty(String propertyName)
	{
		return unwrapValue(propertyName, getRawPropertyValue(propertyName));
	}

	protected Object unwrapValue(String propertyName, Object object)
	{
		if (object != null)
		{
			PropertyDescription propDesc = specification.getProperty(propertyName);
			if (propDesc != null)
			{
				IPropertyType< ? > type = propDesc.getType();
				if (type instanceof IWrapperType)
				{
					return ((IWrapperType)type).unwrap(object);
				}
			}
		}
		return object;
	}

	/**
	 * For testing only.
	 *
	 * DO NOT USE THIS METHOD; when possible please use {@link #getProperty(String)}, {@link #getProperties()} or {@link #getAllPropertyNames(boolean)} instead.
	 */
	Map<String, Object> getRawPropertiesWithoutDefaults()
	{
		return properties;
	}

	public TypedData<Map<String, Object>> getProperties()
	{
		if (properties.isEmpty())
		{
			return EMPTY_PROPERTIES;
		}

		PropertyDescriptionBuilder propertyTypes = AggregatedPropertyType.newAggregatedPropertyBuilder();
		for (Entry<String, Object> p : properties.entrySet())
		{
			PropertyDescription t = specification.getProperty(p.getKey());
			if (t != null) propertyTypes.withProperty(p.getKey(), t);
		}
		PropertyDescription pd = propertyTypes.build();
		return new TypedData<Map<String, Object>>(Collections.unmodifiableMap(properties), pd.hasChildProperties() ? pd : null);
	}

	/**
	 * Set the defaults property value that is not sent to the browser.
	 * This should/can reflect the values that are set in the template as default values.
	 */
	public void setDefaultProperty(String propertyName, Object propertyValue)
	{
		Object oldWrappedValue = getRawPropertyValue(propertyName);

		defaultAndTemplatePropertiesUnwrapped.put(propertyName, propertyValue);

		Object newWrappedValue = getRawPropertyValue(propertyName);
		Object newUnwrappedV = unwrapValue(propertyName, newWrappedValue); // a default value wrap/unwrap might result in a different value
		if (newUnwrappedV != propertyValue) defaultAndTemplatePropertiesUnwrapped.put(propertyName, newUnwrappedV);

		if (oldWrappedValue != newWrappedValue) onPropertyChange(propertyName, oldWrappedValue, newWrappedValue);
	}

	/**
	 * Setting new data and recording this as change.
	 *
	 * @return true if it was changed.
	 */
	@SuppressWarnings("nls")
	public boolean setProperty(String propertyName, Object propertyValue)
	{
		boolean dirty = false;

		Object oldWrappedValue = getRawPropertyValue(propertyName);
		Object wrappedValue = wrapPropertyValue(propertyName, oldWrappedValue, propertyValue);

		Map<String, Object> map = properties;
		String firstPropertyPart = propertyName;
		String lastPropertyPart = propertyName;
		String[] parts = propertyName.split("\\.");
		// TODO this doesn't seem to work properly with arrays while getter does treat that as well
		if (parts.length > 1)
		{
			firstPropertyPart = parts[0];
			String path = "";
			for (int i = 0; i < parts.length - 1; i++)
			{
				path += parts[i];

				// instanceof check below because spec. default values for custom object types cannot be java maps directly but JOSNObjects so they would have wrong type; we expect the default value to be already converted and set into "defaultPropertiesUnwrapped" in this case...
				Object rawPV = getRawPropertyValue(path);
				Map<String, Object> propertyMap = (rawPV instanceof Map< ? , ? > ? (Map<String, Object>)rawPV : null);
				if (propertyMap == null)
				{
					propertyMap = new HashMap<>();
					map.put(parts[i], wrapPropertyValue(path, null, propertyMap));
				}
				path += ".";
				map = propertyMap;
			}
			lastPropertyPart = parts[parts.length - 1];
		}

		if (map.containsKey(lastPropertyPart))
		{
			// existing property
			Object oldValue = getProperty(propertyName); // this unwraps it
			map.put(lastPropertyPart, wrappedValue);
			propertyValue = getProperty(propertyName); // this is required as a wrap + unwrap might result in a different object then the initial one

			if ((oldValue != null && !oldValue.equals(propertyValue)) || (propertyValue != null && !propertyValue.equals(oldValue)))
			{
				markPropertyAsChangedByRef(firstPropertyPart);
				dirty = true;
			}
		}
		else
		{
			// new property
			map.put(lastPropertyPart, wrappedValue);
			propertyValue = getProperty(propertyName); // this is required as a wrap + unwrap might result in a different object then the initial one

			markPropertyAsChangedByRef(firstPropertyPart);
			dirty = true;
		}

		// FIXME if this is a sub property then we fire here the onproperty change for the top level property with the values of a subproperty...
		if (oldWrappedValue != wrappedValue) onPropertyChange(propertyName, oldWrappedValue, wrappedValue);

		return dirty;
	}

	/**
	 * Gets the current value from the properties, if not set then it could fall-back to default properties value from spec - if possible.
	 * DO NOT USE THIS METHOD; when possible please use {@link #getProperty(String)}, {@link #getProperties()} or {@link #getAllPropertyNames(boolean)} instead.
	 */
	@SuppressWarnings("nls")
	public Object getRawPropertyValue(String propertyName)
	{
		String[] parts = propertyName.split("\\.");
		String firstProperty = parts[0];
		int arrayIndex = -1;
		if (firstProperty.indexOf('[') > 0)
		{
			if (firstProperty.endsWith("]"))
			{
				arrayIndex = Integer.parseInt((firstProperty.substring(firstProperty.lastIndexOf('[') + 1, firstProperty.length() - 1)));
			}
			firstProperty = firstProperty.substring(0, firstProperty.indexOf('['));
		}
		Object oldValue = properties.get(firstProperty);
		if (arrayIndex >= 0)
		{
			if (oldValue instanceof List) oldValue = ((List)oldValue).get(arrayIndex);
			else if (oldValue != null)
			{
				log.error("Trying to get an array element while the value is not an array: property=" + propertyName + ", component=" + getName() + ", spec=" +
					getSpecification(), new RuntimeException());
				return null;
			}
		}
		if (oldValue == null && !properties.containsKey(firstProperty))
		{
			Object defaultProperty = defaultAndTemplatePropertiesUnwrapped.get(firstProperty);
			if (defaultProperty == null && !defaultAndTemplatePropertiesUnwrapped.containsKey(firstProperty))
			{
				// default value based on spec
				PropertyDescription propertyDesc = specification.getProperty(firstProperty);
				if (propertyDesc != null)
				{
					defaultProperty = getDefaultFromPD(propertyDesc);
				}
			}

			if (defaultProperty != null)
			{
				// quickly wrap this value so that it can be used as the oldValue later on.
				oldValue = wrapPropertyValue(firstProperty, null, defaultProperty);
			}
		}
		if (parts.length > 1)
		{
			// here we rely on the fact that .spec does not allow nesting arrays directly into other arrays directly so you can't currently have prop: int[][],
			// you have to have something like prop: {a: int[]}[] so you access it like prop[4].a[3] not prop[4][3]
			for (int i = 1; i < parts.length; i++)
			{
				String pathProperty = parts[i];
				arrayIndex = -1;
				if (pathProperty.indexOf('[') > 0)
				{
					if (pathProperty.endsWith("]"))
					{
						arrayIndex = Integer.parseInt((pathProperty.substring(pathProperty.lastIndexOf('[') + 1, pathProperty.length() - 1)));
					}
					pathProperty = pathProperty.substring(0, pathProperty.indexOf('['));
				}
				if (oldValue instanceof Map)
				{
					oldValue = ((Map)oldValue).get(pathProperty);
					if (arrayIndex >= 0)
					{
						if (oldValue instanceof List) oldValue = ((List)oldValue).get(arrayIndex);
						else if (oldValue != null)
						{
							log.error("Trying to get a nested array element while the nested value is not an array: property=" + propertyName + ", component=" +
								getName() + ", spec=" + getSpecification() + ", part=" + parts[i], new RuntimeException());
							return null;
						}
					}
				}
			}
			// this value comes from internal maps, should be wrapped again (current value should always return a wrapped value)
			oldValue = wrapPropertyValue(propertyName, null, oldValue);
		}
		return oldValue;
	}

	/**
	 * If the value of a property is not set, try to see if the type or property description wants to return a value anyway.<br/>
	 * Can be overridden by extending classes in case the PD and type default values are treated earlier or in some other way...
	 */
	public Object getDefaultFromPD(PropertyDescription propertyDesc)
	{
		Object defaultPropertyValue = null;
		defaultPropertyValue = propertyDesc.getDefaultValue(); // can be a null or non-null
		if (!propertyDesc.hasDefault() && propertyDesc.getType() != null)
		{
			// if spec has no default value specified, take it from property type
			defaultPropertyValue = propertyDesc.getType().defaultValue(propertyDesc);
		}

		return defaultPropertyValue;
	}

	/** Check if a property can be modified from the outside (browser client).
	 *
	 * @param propertyName
	 *
	 * @throws IllegalChangeFromClientException when modification is denied.
	 */
	public final void checkThatPropertyAllowsUpdateFromClient(String propertyName)
	{
		checkForProtectedPropertiesThatMightBlockUpdatesOn(propertyName);

		checkThatPushToServerAllowsUpdatesOn(propertyName);
	}

	/**
	 * put property from the outside world, not recording changes. converting to
	 * the right type.
	 *
	 * @param propertyName
	 * @param propertyValue
	 *            can be a JSONObject or array or primitive.
	 */
	public final void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		try
		{
			checkThatPropertyAllowsUpdateFromClient(propertyName);
		}
		catch (IllegalChangeFromClientException e)
		{
			checkIfAccessIsAllowedDisregardingProtection(propertyName, e, propertyValue);
		}

		doPutBrowserProperty(propertyName, propertyValue);
	}

	@SuppressWarnings("unchecked")
	private void checkIfAccessIsAllowedDisregardingProtection(String propertyName, IllegalChangeFromClientException e, Object propertyValue)
	{
		boolean rethrow = true;
		PropertyDescription pd = getPropertyDescription(propertyName);
		if (pd != null)
		{
			if (propertyValue != null && pd.getType() instanceof IGranularProtectionChecker)
			{
				rethrow = !((IGranularProtectionChecker<Object>)pd.getType()).allowPush(propertyValue, getProperty(propertyName), e);
			}
			else
			{
				Object allowEditTag = pd.getTag(WebObjectSpecification.ALLOW_ACCESS);
				// allowEditTag is either a String or an array of Strings representing 'blocked by' property name(s) that should not block the given property (the spec makes specific exceptions in the property itself for the other props. that should not block it)
				if (allowEditTag instanceof JSONArray)
				{
					for (Object element : ((JSONArray)allowEditTag))
					{
						if (element.equals(e.getBlockedByProperty()))
						{
							rethrow = false;
							break;
						}
					}
				}
				else if (allowEditTag instanceof String && allowEditTag.equals(e.getBlockedByProperty()))
				{
					rethrow = false;
				}
			}
		}

		if (rethrow)
		{
			e.setData(propertyValue);
			throw e;
		}
	}

	protected void doPutBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		Object oldWrappedValue = getRawPropertyValue(propertyName);
		ValueReference<Boolean> returnValueAdjustedIncommingValue = new ValueReference<Boolean>(Boolean.FALSE);
		Object newWrappedValue = convertValueFromJSON(propertyName, oldWrappedValue, propertyValue, returnValueAdjustedIncommingValue);
		if (propertyName.indexOf('.') < 0 && propertyName.indexOf('[') < 0)
		{
			// if nested above function should already update the value
			properties.put(propertyName, newWrappedValue);
		}

		if (oldWrappedValue != newWrappedValue)
		{
			onPropertyChange(propertyName, oldWrappedValue, newWrappedValue);
		}

		if (returnValueAdjustedIncommingValue.value.booleanValue()) markPropertyAsChangedByRef(propertyName);
	}

	/**
	 * Allow for subclasses to act on property changes
	 *
	 * @param propertyName
	 *            the property name
	 * @param oldWrappedValue
	 *            the old  wrapped val
	 * @param newWrappedValue
	 *            the new wrapped val
	 */
	protected void onPropertyChange(String propertyName, final Object oldWrappedValue, final Object newWrappedValue)
	{
		if (!propertiesInitialized) return; // wait for all properties to be set before triggering changes and calling "attach" on ISmartPropertyValues

		if ((newWrappedValue instanceof ISmartPropertyValue || oldWrappedValue instanceof ISmartPropertyValue) && newWrappedValue != oldWrappedValue)
		{
			final String complexPropertyRoot = propertyName;

			// NOTE here newValue and oldValue are the unwrapped values in case of wrapper types; TODO maybe we should use wrapped values here

			if (oldWrappedValue instanceof ISmartPropertyValue)
			{
				((ISmartPropertyValue)oldWrappedValue).detach();
			}

			// in case the 'smart' value completely changed by ref. - which is the case here, no use keeping
			// something for it in default values - as it is too smart and it might want to notify changes later, although it wouldn't make sense cause the value is different now
			Object defaultSmartValue = defaultAndTemplatePropertiesUnwrapped.get(complexPropertyRoot);
			if (defaultSmartValue instanceof ISmartPropertyValue && defaultSmartValue != newWrappedValue)
			{
				defaultAndTemplatePropertiesUnwrapped.remove(complexPropertyRoot);
				((ISmartPropertyValue)defaultSmartValue).detach();
			}

			if (newWrappedValue instanceof ISmartPropertyValue)
			{
				// a new complex property is linked to this component; initialize it
				((ISmartPropertyValue)newWrappedValue).attachToBaseObject(new IChangeListener()
				{
					@Override
					public void valueChanged()
					{
						markPropertyContentsUpdated(complexPropertyRoot);

						if (defaultAndTemplatePropertiesUnwrapped.containsKey(complexPropertyRoot))
						{
							// TODO I think this can now be removed as markPropertyContentsUpdated(...) above
							// will call makeSureItIsInPropertiesMapAndNotInDefaultAndTemplateMap(...) and do what is also done here

							// something changed in this 'smart' property - so it no longer represents the default value; remove
							// it from default values (as the value reference is the same but the content changed) and put it in properties map
							properties.put(complexPropertyRoot, newWrappedValue);
							defaultAndTemplatePropertiesUnwrapped.remove(complexPropertyRoot);
						}
					}
				}, this);
			}
		}
		if (propertyChangeSupport != null) propertyChangeSupport.firePropertyChange(propertyName, oldWrappedValue, newWrappedValue);
	}

	public boolean markPropertyAsChangedByRef(String key)
	{
		return changes.markElementChangedByRef(key);
	}

	protected boolean markPropertyContentsUpdated(String key)
	{
		return changes.markElementContentsUpdated(key);
	}

	public boolean clearChangedStatusForProperty(String key)
	{
		return changes.clearChangedStatusForProperty(key);
	}

	public boolean hasChanges()
	{
		return changes.isChanged();
	}

	/**
	 * Get the changes of this component, clear changes. It does not write them to JSON just returns the java content + prop. types + change types.
	 * When the component is not visible, only the visibility-properties are returned and cleared from the changes.
	 */
	public TypedDataWithChangeInfo getAndClearChanges()
	{
		return changes.getAndClearChanges();
	}

	/**
	 * Don't call this method unless all changes are already sent to client!
	 */
	public void clearChanges()
	{
		changes.clearChanges();
	}

	protected boolean writeComponentProperties(JSONWriter w, IToJSONConverter<IBrowserConverterContext> converter, String nodeName) throws JSONException
	{
		TypedData<Map<String, Object>> typedProperties = getProperties();
		if (typedProperties.content.isEmpty())
		{
			return false;
		}

		w.key(nodeName).object();

		// only write properties that are visible, always write visibility properties
		Map<String, Object> data = new HashMap<>();
		for (Entry<String, Object> entry : typedProperties.content.entrySet())
		{
			String propertyName = entry.getKey();
			if (isVisibilityProperty(propertyName) || isVisible(propertyName))
			{
				data.put(propertyName, entry.getValue());
				clearChangedStatusForProperty(propertyName);
			}
			else
			{
				// will be sent as changed when component becomes visible
				markPropertyAsChangedByRef(propertyName);
			}
		}

		// here converter is always a full-value-to-json type (full value or initial value, so not changes); so don't give a second parameter as this should be used in any case (we don't want to send changes)
		writeProperties(converter, null, w, new TypedData<Map<String, Object>>(data, typedProperties.contentType));
		w.endObject();

		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object wrapPropertyValue(String propertyName, Object oldValue, Object newValue)
	{
		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		IPropertyType<Object> type = propertyDesc != null ? (IPropertyType<Object>)propertyDesc.getType() : null;
		Object object = (type instanceof IWrapperType) ? ((IWrapperType)type).wrap(newValue, oldValue, propertyDesc, new WrappingContext(this, propertyName))
			: newValue;
		if (type instanceof IClassPropertyType && object != null && !((IClassPropertyType< ? >)type).getTypeClass().isAssignableFrom(object.getClass()))
		{
			log.info("property: " + propertyName + " of component " + getName() + " set with value: " + newValue + " which is not of type: " +
				((IClassPropertyType< ? >)type).getTypeClass());
			return null;
		}
		return object;

	}

	/**
	 * Allow for subclasses to do conversions, by default it just ask for the
	 * type to do the conversion to Java
	 *
	 * @param propertyName
	 *            the property name
	 * @param previousComponentValue
	 *            the old val
	 * @param newJSONValue
	 *            the new val
	 * @param sourceOfValue
	 * @return the converted value
	 * @throws JSONException
	 */
	private Object convertValueFromJSON(String propertyName, Object previousComponentValue, Object newJSONValue,
		ValueReference<Boolean> returnValueAdjustedIncommingValue) throws JSONException
	{
		if (newJSONValue == JSONObject.NULL) newJSONValue = null;

		PDAndComputedPushToServer propertyDescAndPushToServer = specification.computePushToServerForPropertyPathAndGetPD(propertyName);
		Object value = (propertyDescAndPushToServer.pd != null) ? JSONUtils.fromJSON(previousComponentValue, newJSONValue, propertyDescAndPushToServer.pd,
			new BrowserConverterContext(this, propertyDescAndPushToServer.pushToServer), returnValueAdjustedIncommingValue) : null;
		return value;
	}

	/**
	 * Use the returned set only for reading, not modifying.
	 */
	public Set<String> getAllPropertyNames(boolean includeDefaultAndTemplateValueKeys)
	{
		Set<String> allValKeys;
		if (includeDefaultAndTemplateValueKeys)
		{
			allValKeys = new HashSet<String>();
			allValKeys.addAll(properties.keySet());
			allValKeys.addAll(defaultAndTemplatePropertiesUnwrapped.keySet());
		}
		else allValKeys = properties.keySet();

		return allValKeys;
	}

	public void addEventHandler(String handlerName, IEventHandler handler)
	{
		if (specification.getHandler(handlerName) == null)
		{
			throw new IllegalArgumentException("Handler for component '" + getName() + "' not found in component specification '" + specification.getName() +
				"' : handler '" + handlerName + "'");
		}
		eventHandlers.put(handlerName, handler);
	}

	public IEventHandler getEventHandler(String event)
	{
		return eventHandlers.get(event);
	}

	public IEventHandler removeEventHandler(String event)
	{
		return eventHandlers.remove(event);
	}

	public boolean hasEvent(String event)
	{
		return eventHandlers.containsKey(event);
	}

	/**
	 * Called when this object will not longer be used - to release any held resources/remove listeners.
	 */
	public void dispose()
	{
		propertyChangeSupport = null;
		dirtyPropertyListener = null;
		for (String pN : getAllPropertyNames(true))
		{
			Object pUnwrapped = getProperty(pN);
			if (pUnwrapped instanceof ISmartPropertyValue) ((ISmartPropertyValue)pUnwrapped).detach(); // clear any listeners/held resources
		}
	}

	public boolean writeOwnChanges(JSONWriter w, String keyInParent, String nodeName, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
	{
		TypedDataWithChangeInfo changesToWrite = changes.getAndClearChanges();
		if (changesToWrite.content.isEmpty())
		{
			return false;
		}

		if (keyInParent != null)
		{
			w.key(keyInParent).object();
		}

		w.key(nodeName).object();
		// converter here is always ChangesToJSONConverter except for some unit tests
		writeProperties(converter, FullValueToJSONConverter.INSTANCE, w, changesToWrite);
		w.endObject();

		return true;
	}

	/**
	 * @param converterForSendingFullValue can be null, if only the main converter should be used.
	 */
	public void writeProperties(IToJSONConverter<IBrowserConverterContext> mainConverter,
		IToJSONConverter<IBrowserConverterContext> converterForSendingFullValue, JSONWriter w, TypedData<Map<String, Object>> propertiesToWrite)
		throws IllegalArgumentException, JSONException
	{
		changes.setShouldNotReceiveChanges(true);
		TypedDataWithChangeInfo propertiesToWriteWithChangeInfo = (propertiesToWrite instanceof TypedDataWithChangeInfo)
			? (TypedDataWithChangeInfo)propertiesToWrite : null;

		for (Entry<String, Object> entry : propertiesToWrite.content.entrySet())
		{
			PropertyDescription pd = propertiesToWrite.contentType != null ? propertiesToWrite.contentType.getProperty(entry.getKey()) : null;

			BrowserConverterContext context;
			if (pd != null) context = new BrowserConverterContext(this, pd.getPushToServer());
			else context = new BrowserConverterContext(this, PushToServerEnum.reject); // should this ever happen? should we use allow here instead?

			if (propertiesToWriteWithChangeInfo != null && converterForSendingFullValue != null &&
				propertiesToWriteWithChangeInfo.hasFullyChanged(entry.getKey()))
			{
				// the property val needs to be sent whole - use the right converter for that
				converterForSendingFullValue.toJSONValue(w, entry.getKey(), entry.getValue(), pd, context);
			}
			else
			{
				// use 'main' converter (which can be a full value converter or only changes converter depending on caller wants)
				mainConverter.toJSONValue(w, entry.getKey(), entry.getValue(), pd, context);
			}

		}
		changes.setShouldNotReceiveChanges(false);
	}

	public boolean isEnabled()
	{
		for (PropertyDescription prop : specification.getProperties(EnabledPropertyType.INSTANCE))
		{
			return (boolean)getProperty(prop.getName());
		}
		return true;
	}

	public void setEnabled(boolean enabled)
	{
		for (PropertyDescription prop : specification.getProperties(EnabledPropertyType.INSTANCE))
		{
			setProperty(prop.getName(), enabled);
		}
	}

	public PropertyDescription getPropertyDescription(String propertyName)
	{
		return specification.getProperty(propertyName);
	}

	@Override
	public BaseWebObject getUnderlyingWebObject()
	{
		return this;
	}

	@Override
	public IWebObjectContext getParentContext()
	{
		return this;
	}

	@Override
	public Collection<PropertyDescription> getProperties(IPropertyType< ? > type)
	{
		return specification.getProperties(type);
	}

	/**
	 * These listeners will be triggered when the property changes by reference.
	 */
	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener)
	{
		if (propertyChangeSupport == null) propertyChangeSupport = new PropertyChangeSupport(this);
		if (propertyName == null) propertyChangeSupport.addPropertyChangeListener(listener);
		else propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
	}

	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener)
	{
		if (propertyChangeSupport != null)
		{
			if (propertyName == null) propertyChangeSupport.removePropertyChangeListener(listener);
			else propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
		}
	}

	/**
	 * Call this after all initial (form element - to - sablo) properties have been set (either in defaults map or in properties map)
	 * if you instantiated this object with 'waitForPropertyInitBeforeAttach' = true
	 */
	public void propertiesInitialized()
	{
		// so after all of them are converted from form element to sablo and set, attach them to the webComponent (so that when attach is called on any ISmartPropertyValue at least all the other properties are converted
		// this could help initialize smart properties that depend on each other faster then if we would convert and then attach right away each value)
		propertiesInitialized = true;

		SortedSet<String> availableInitialKeys = new TreeSet<>();
		availableInitialKeys.addAll(defaultAndTemplatePropertiesUnwrapped.keySet());
		availableInitialKeys.addAll(properties.keySet());

		// notify and attach initial values
		for (String propName : availableInitialKeys)
		{
			onPropertyChange(propName, null, getRawPropertyValue(propName));
		}
	}

	@Override
	public String toString()
	{
		return getName() + "(" + (getSpecification() != null ? getSpecification().getDisplayName() : null) + " - " + getClass().getCanonicalName() + ")";
	}

}
