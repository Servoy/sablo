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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectFunctionDefinition;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IClassPropertyType;
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
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
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

	private static final Logger log = LoggerFactory.getLogger(BaseWebObject.class.getCanonicalName());


	protected final WebObjectSpecification specification;

	/**
	 * model properties to interact with webcomponent values, maps name to value
	 */
	protected final Map<String, Object> properties = new HashMap<>();

	/**
	 * default model properties that are not send to the browser.
	 */
	protected final Map<String, Object> defaultPropertiesUnwrapped = new HashMap<>();

	/**
	 * Keeps track of properties that have changed content.
	 */
	private final Set<String> propertiesWithChangedContent = new HashSet<>(3);

	/**
	 * Keeps track of properties that have changed by reference (a different (!=) value was assigned to them).
	 */
	private final Set<String> propertiesChangedByRef = new HashSet<>(3);

	/**
	 * the event handlers
	 */
	private final ConcurrentMap<String, IEventHandler> eventHandlers = new ConcurrentHashMap<String, IEventHandler>();

	private PropertyChangeSupport propertyChangeSupport;

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
		if (specification == null) throw new IllegalStateException("Cannot work without specification");
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
	 * Execute incoming event
	 *
	 * @param eventType
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public final Object executeEvent(String eventType, Object[] args) throws Exception
	{
		checkProtection(eventType);

		// test if this is a private handler, should not be callable from a client
		WebObjectFunctionDefinition handler = getSpecification().getHandler(eventType);
		if (handler != null && handler.isPrivate()) throw new IllegalAccessException(
			"Event " + eventType + " is called from the client, but it is a private event of " + this + " with spec " + specification);

		return doExecuteEvent(eventType, args);
	}

	protected Object doExecuteEvent(String eventType, Object[] args) throws Exception
	{
		IEventHandler handler = getEventHandler(eventType);
		if (handler == null)
		{
			log.warn("Unknown event '" + eventType + "' for component " + this);
			return null;
		}
		return handler.executeEvent(args);
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
	public final boolean isVisible(String property)
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
	protected void checkProtection(String property)
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
	 * Check if the property is protected, i.e. it cannot be set from the client.
	 *
	 * @param propName
	 * @throws IllegalChangeFromClientException when property is protected
	 */
	protected void checkForProtectedProperty(String propName)
	{
		List<PropertyDescription> propertyPath = specification.getPropertyPath(propName);
		for (int i = 0; i < propertyPath.size(); i++)
		{
			PropertyDescription property = propertyPath.get(i);
			boolean isLastInPath = i == propertyPath.size() - 1;
			if (property != null)
			{
				if (property.getType().isProtecting())
				{
					// property is protected, i.e. it cannot be set from the client
					throw new IllegalChangeFromClientException(null,
						"Property '" + property + "' is a 'protecting' property itself so it can never be changed from client.", getName(), propName);
				}

				if (isLastInPath)
				{
					if (PushToServerEnum.allow.compareTo(property.getPushToServer()) > 0 && (!(property.getType() instanceof IPushToServerSpecialType) ||
						!((IPushToServerSpecialType)property.getType()).shouldAlwaysAllowIncommingJSON()))
					{
						// pushToServer not set to allowed on the prop. itself (isLastInPath), it should not be changed from the client
						throw new IllegalChangeFromClientException(null,
							"Property '" + propName + "' has 'pushToServer' set to 'reject' so it cannot be changed from client.", getName(), propName);
					}
				}
				else
				{
					// TODO hmm isn't elementConfig already checked with code above? (as it should affect pushToServer of the PropertyDescription of elements when we really will do pushToServer at more then root property level)
					if (property.getConfig() instanceof JSONObject)
					{
						JSONObject config = (JSONObject)property.getConfig();
						if (config.has(CustomJSONArrayType.ELEMENT_CONFIG_KEY))
						{
							config = config.getJSONObject(CustomJSONArrayType.ELEMENT_CONFIG_KEY);
						}
						if (config.has(WebObjectSpecification.PUSH_TO_SERVER_KEY))
						{
							PushToServerEnum configPushToServer = PushToServerEnum.fromString(config.optString(WebObjectSpecification.PUSH_TO_SERVER_KEY));
							if (PushToServerEnum.allow.compareTo(configPushToServer) > 0)
							{
								throw new IllegalChangeFromClientException(null, "Property '" + propName +
									"' has 'pushToServer' set to 'reject' (on an array element config in it's property path) so it cannot be changed from client.",
									getName(), propName);
							}
						}
					}
				}
			}

			// ok
		}
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

	public boolean hasChanges()
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
	 * Get the changes of this component, clear changes.
	 * When the component is not visible, only the visibility-properties are returned and cleared from the changes.
	 *
	 */
	public TypedDataWithChangeInfo getAndClearChanges()
	{
		if (propertiesChangedByRef.isEmpty() && propertiesWithChangedContent.isEmpty())
		{
			return EMPTY_PROPERTIES_WITH_CHANGE_INFO;
		}

		Map<String, Object> changes = null;
		PropertyDescription changeTypes = null;
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
				if (changes == null)
				{
					changes = new HashMap<>();
				}
				changes.put(propertyName, properties.get(propertyName));
				PropertyDescription t = specification.getProperty(propertyName);
				if (t != null)
				{
					if (changeTypes == null)
					{
						changeTypes = AggregatedPropertyType.newAggregatedProperty();
					}
					changeTypes.putProperty(propertyName, t);
				}
				if (i < idxStartChangedByRef)
				{
					// this is a property with content updates only
					if (propertiesWithContentUpdateOnly == null) propertiesWithContentUpdateOnly = new HashSet<>();
					propertiesWithContentUpdateOnly.add(propertyName);
				}
			}
		}

		if (changes == null)
		{
			return EMPTY_PROPERTIES_WITH_CHANGE_INFO;
		}

		return new TypedDataWithChangeInfo(changes, changeTypes, propertiesWithContentUpdateOnly);
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

		PropertyDescription propertyTypes = AggregatedPropertyType.newAggregatedProperty();
		for (Entry<String, Object> p : properties.entrySet())
		{
			PropertyDescription t = specification.getProperty(p.getKey());
			if (t != null) propertyTypes.putProperty(p.getKey(), t);
		}

		return new TypedData<Map<String, Object>>(Collections.unmodifiableMap(properties), propertyTypes.hasChildProperties() ? propertyTypes : null);
	}

	/**
	 * Don't call this method unless all changes are already sent to client!
	 */
	public void clearChanges()
	{
		propertiesChangedByRef.clear();
		propertiesWithChangedContent.clear();
	}

	/**
	 * Set the defaults property value that is not sent to the browser.
	 * This should/can reflect the values that are set in the template as default values.
	 */
	public void setDefaultProperty(String propertyName, Object propertyValue)
	{
		Object oldWrappedValue = getRawPropertyValue(propertyName);

		defaultPropertiesUnwrapped.put(propertyName, propertyValue);

		Object newWrappedValue = getRawPropertyValue(propertyName);
		Object newUnwrappedV = unwrapValue(propertyName, newWrappedValue); // a default value wrap/unwrap might result in a different value
		if (newUnwrappedV != propertyValue) defaultPropertiesUnwrapped.put(propertyName, newUnwrappedV);

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
			Object defaultProperty = defaultPropertiesUnwrapped.get(firstProperty);
			if (defaultProperty == null && !defaultPropertiesUnwrapped.containsKey(firstProperty))
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
	public final void checkPropertyProtection(String propertyName)
	{
		checkProtection(propertyName);

		checkForProtectedProperty(propertyName);
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
			checkPropertyProtection(propertyName);
		}
		catch (IllegalChangeFromClientException e)
		{
			checkIfAccessIsAllowedDisregardingProtection(propertyName, e);
		}

		doPutBrowserProperty(propertyName, propertyValue);
	}

	private void checkIfAccessIsAllowedDisregardingProtection(String propertyName, IllegalChangeFromClientException e)
	{
		boolean rethrow = true;
		Object allowEditTag = getPropertyDescription(propertyName).getTag(WebObjectSpecification.ALLOW_ACCESS);
		// allowEditTag is either a String or an array of Strings representing 'blocked by' property name(s) that should not block the given property (the spec makes specific exceptions in the property itself for the other props. that should not block it)
		if (allowEditTag instanceof JSONArray)
		{
			Iterator<Object> iterator = ((JSONArray)allowEditTag).iterator();
			while (iterator.hasNext())
			{
				if (iterator.next().equals(e.getBlockedByProperty()))
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
		if (rethrow)
		{
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

			// in case the 'smart' value completely changed by ref., no use keeping it in default values as it is too smart and it might want to notify changes later, although it wouldn't make sense cause the value is different now
			Object defaultSmartValue = defaultPropertiesUnwrapped.get(complexPropertyRoot);
			if (defaultSmartValue instanceof ISmartPropertyValue && defaultSmartValue != newWrappedValue)
			{
				defaultPropertiesUnwrapped.remove(complexPropertyRoot);
				((ISmartPropertyValue)defaultSmartValue).detach();
			}

			// a new complex property is linked to this component; initialize it
			if (newWrappedValue instanceof ISmartPropertyValue)
			{
				((ISmartPropertyValue)newWrappedValue).attachToBaseObject(new IChangeListener()
				{
					@Override
					public void valueChanged()
					{
						markPropertyContentsUpdated(complexPropertyRoot);

						if (defaultPropertiesUnwrapped.containsKey(complexPropertyRoot))
						{
							// something changed in this 'smart' property - so it no longer represents the default value; remove
							// it from default values (as the value reference is the same but the content changed) and put it in properties map
							properties.put(complexPropertyRoot, newWrappedValue);
							defaultPropertiesUnwrapped.remove(complexPropertyRoot);
						}
					}
				}, this);
			}
		}
		if (propertyChangeSupport != null) propertyChangeSupport.firePropertyChange(propertyName, oldWrappedValue, newWrappedValue);
	}

	public boolean markPropertyAsChangedByRef(String key)
	{
		boolean somethingHappened = propertiesChangedByRef.add(key); // whether or not we will add it to a changes map (if it is already there or can't be removed, it will be false)
		if (somethingHappened) propertiesWithChangedContent.remove(key); // we just added it to be sent fully; remove it from contents changed so that it will not be sent twice

		return somethingHappened;
	}

	public boolean clearChangedStatusForProperty(String key)
	{
		boolean somethingHappened = propertiesChangedByRef.remove(key); // whether or not we will add or remove it from a changes map (if it is already there or can't be removed, it will be false)
		// remove it from any of the 2 changes map (it can't be in both)
		somethingHappened = propertiesWithChangedContent.remove(key) || somethingHappened;

		return somethingHappened;
	}

	public boolean markPropertyContentsUpdated(String key)
	{
		boolean somethingHappened = false;
		if (!propertiesWithChangedContent.contains(key) && !propertiesChangedByRef.contains(key))
		{
			somethingHappened = propertiesWithChangedContent.add(key);
		} // else don't add it in the changes map as it's already in the ref. changed map; it will be fully sent to client

		return somethingHappened;
	}

	protected boolean writeComponentProperties(JSONWriter w, IToJSONConverter<IBrowserConverterContext> converter, String nodeName,
		DataConversion clientDataConversions) throws JSONException
	{
		TypedData<Map<String, Object>> typedProperties = getProperties();
		if (typedProperties.content.isEmpty())
		{
			return false;
		}

		w.key(nodeName).object();
		clientDataConversions.pushNode(nodeName);

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
		writeProperties(converter, null, w, new TypedData<Map<String, Object>>(data, typedProperties.contentType), clientDataConversions);
		clientDataConversions.popNode();
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

		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		Object value = propertyDesc != null ? JSONUtils.fromJSON(previousComponentValue, newJSONValue, propertyDesc,
			new BrowserConverterContext(this, propertyDesc.getPushToServer()), returnValueAdjustedIncommingValue) : null;
		return value;
	}

	/**
	 * Use the returned set only for reading, not modifying.
	 */
	public Set<String> getAllPropertyNames(boolean includeDefaultValueKeys)
	{
		Set<String> allValKeys;
		if (includeDefaultValueKeys)
		{
			allValKeys = new HashSet<String>();
			allValKeys.addAll(properties.keySet());
			allValKeys.addAll(defaultPropertiesUnwrapped.keySet());
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
		for (String pN : getAllPropertyNames(true))
		{
			Object pUnwrapped = getProperty(pN);
			if (pUnwrapped instanceof ISmartPropertyValue) ((ISmartPropertyValue)pUnwrapped).detach(); // clear any listeners/held resources
		}
		propertyChangeSupport = null;
	}

	public boolean writeOwnComponentChanges(JSONWriter w, String keyInParent, String nodeName, IToJSONConverter<IBrowserConverterContext> converter,
		DataConversion clientDataConversions) throws JSONException
	{
		TypedDataWithChangeInfo changes = getAndClearChanges();
		if (changes.content.isEmpty())
		{
			return false;
		}

		if (keyInParent != null)
		{
			w.key(keyInParent).object();
		}

		w.key(nodeName).object();
		clientDataConversions.pushNode(nodeName);
		// converter here is always ChangesToJSONConverter except for some unit tests
		writeProperties(converter, FullValueToJSONConverter.INSTANCE, w, changes, clientDataConversions);
		clientDataConversions.popNode();
		w.endObject();

		return true;
	}

	/**
	 * @param converterForSendingFullValue can be null, if only the main converter should be used.
	 */
	public void writeProperties(IToJSONConverter<IBrowserConverterContext> mainConverter,
		IToJSONConverter<IBrowserConverterContext> converterForSendingFullValue, JSONWriter w, TypedData<Map<String, Object>> propertiesToWrite,
		DataConversion clientDataConversions) throws IllegalArgumentException, JSONException
	{
		TypedDataWithChangeInfo propertiesToWriteWithChangeInfo = (propertiesToWrite instanceof TypedDataWithChangeInfo)
			? (TypedDataWithChangeInfo)propertiesToWrite : null;

		for (Entry<String, Object> entry : propertiesToWrite.content.entrySet())
		{
			clientDataConversions.pushNode(entry.getKey());
			PropertyDescription pd = propertiesToWrite.contentType != null ? propertiesToWrite.contentType.getProperty(entry.getKey()) : null;

			BrowserConverterContext context;
			if (pd != null) context = new BrowserConverterContext(this, pd.getPushToServer());
			else context = new BrowserConverterContext(this, PushToServerEnum.reject); // should this ever happen? should we use allow here instead?

			if (propertiesToWriteWithChangeInfo != null && converterForSendingFullValue != null &&
				propertiesToWriteWithChangeInfo.hasFullyChanged(entry.getKey()))
			{
				// the property val needs to be sent whole - use the right converter for that
				converterForSendingFullValue.toJSONValue(w, entry.getKey(), entry.getValue(), pd, clientDataConversions, context);
			}
			else
			{
				// use 'main' converter (which can be a full value converter or only changes converter depending on caller wants)
				mainConverter.toJSONValue(w, entry.getKey(), entry.getValue(), pd, clientDataConversions, context);
			}

			clientDataConversions.popNode();
		}
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

	public static PropertyDescription getParameterTypes(WebObjectFunctionDefinition apiFunc)
	{
		PropertyDescription parameterTypes = null;
		final List<PropertyDescription> types = apiFunc.getParameters();
		if (types.size() > 0)
		{
			parameterTypes = new PropertyDescription("", AggregatedPropertyType.INSTANCE)
			{
				@Override
				public Map<String, PropertyDescription> getProperties()
				{
					Map<String, PropertyDescription> map = new HashMap<String, PropertyDescription>();
					for (int i = 0; i < types.size(); i++)
					{
						map.put(String.valueOf(i), types.get(i));
					}
					return map;
				}

				@Override
				public PropertyDescription getProperty(String name)
				{
					try
					{
						int index = Integer.parseInt(name);
						if (index < types.size())
						{
							return types.get(index);
						}
						return null;
					}
					catch (NumberFormatException e)
					{
						return super.getProperty(name);
					}
				}

				@Override
				public Collection<String> getAllPropertiesNames()
				{
					Set<String> s = new HashSet<String>();
					for (int i = 0; i < types.size(); i++)
					{
						s.add(String.valueOf(i));
					}
					s.addAll(super.getAllPropertiesNames());
					return s;
				}
			};
		}
		return parameterTypes;
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
		return null;
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
		availableInitialKeys.addAll(defaultPropertiesUnwrapped.keySet());
		availableInitialKeys.addAll(properties.keySet());

		// notify and attach initial values
		for (String propName : availableInitialKeys)
		{
			onPropertyChange(propName, null, getRawPropertyValue(propName));
		}
	}

}
