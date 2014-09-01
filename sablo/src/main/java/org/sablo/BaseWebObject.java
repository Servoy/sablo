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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.ISmartPropertyValue;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a base web object that hold properties and records changes for the specific {@link WebComponentSpecProvider}
 *
 * @author jcompagner
 */
public abstract class BaseWebObject
{
	private static final Logger log = LoggerFactory.getLogger(BaseWebObject.class.getCanonicalName());


	protected final WebComponentSpecification specification;

	/**
	 * model properties to interact with webcomponent values, maps name to value
	 */
	protected final Map<String, Object> properties = new HashMap<>();

	/**
	 * the changed properties
	 */
	private final Set<String> changedProperties = new HashSet<>(3);

	protected final String name;

	public BaseWebObject(String name, WebComponentSpecification specification)
	{
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
	 * Execute incoming event
	 *
	 * @param eventType
	 * @param args
	 * @return
	 */
	public Object executeEvent(String eventType, Object[] args)
	{
		return null;
	}

	/**
	 * Get a property
	 *
	 * @param propertyName
	 * @return the value or null
	 */
	public Object getProperty(String propertyName)
	{
		Object object = properties.get(propertyName);
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
		return !changedProperties.isEmpty();
	}

	public TypedData<Map<String, Object>> getChanges()
	{
		if (changedProperties.size() > 0)
		{
			Map<String, Object> changes = new HashMap<>();
			PropertyDescription changeTypes = AggregatedPropertyType.newAggregatedProperty();
			for (String propertyName : changedProperties)
			{
				changes.put(propertyName, properties.get(propertyName));
				PropertyDescription t = specification.getProperty(propertyName);
				if (t != null) changeTypes.putProperty(propertyName, t);
			}
			if (!changeTypes.hasChildProperties()) changeTypes = null;
			changedProperties.clear();
			return new TypedData<Map<String, Object>>(changes, changeTypes);
		}
		Map<String, Object> em = Collections.emptyMap();
		return new TypedData<>(em, null);
	}

	public Map<String, Object> getRawProperties()
	{
		return properties;
	}

	public TypedData<Map<String, Object>> getProperties()
	{
		PropertyDescription propertyTypes = AggregatedPropertyType.newAggregatedProperty();
		for (Entry<String, Object> p : properties.entrySet())
		{
			PropertyDescription t = specification.getProperty(p.getKey());
			if (t != null) propertyTypes.putProperty(p.getKey(), t);
		}
		if (!propertyTypes.hasChildProperties()) propertyTypes = null;

		return new TypedData<Map<String, Object>>(properties, propertyTypes);
	}

	public void clearChanges()
	{
		changedProperties.clear();
	}

	/**
	 * Setting new data and recording this as change.
	 *
	 * @param propertyName
	 * @param propertyValue
	 * @return true is was change
	 */
	public boolean setProperty(String propertyName, Object propertyValue)
	{
		Map<String, Object> map = properties;
		try
		{
			// TODO can the propertyName can contain dots? Or should this be
			// handled by the type??
			propertyValue = wrapPropertyValue(propertyName, map.get(propertyName), propertyValue);
		}
		catch (Exception e)
		{
			// TODO change this as part of SVY-6337
			throw new RuntimeException(e);
		}

		// TODO can the propertyName can contain dots? Or should this be handled
		// by the type?? Remove this code below.
		String firstPropertyPart = propertyName;
		String lastPropertyPart = propertyName;
		String[] parts = propertyName.split("\\.");
		if (parts.length > 1)
		{
			firstPropertyPart = parts[0];
			for (int i = 0; i < parts.length - 1; i++)
			{
				Map<String, Object> propertyMap = (Map<String, Object>)map.get(parts[i]);
				if (propertyMap == null)
				{
					propertyMap = new HashMap<>();
					map.put(parts[i], propertyMap);
				}
				map = propertyMap;
			}
			lastPropertyPart = parts[parts.length - 1];
		}

		if (map.containsKey(lastPropertyPart))
		{
			// existing property
			Object oldValue = map.put(lastPropertyPart, propertyValue);
			onPropertyChange(firstPropertyPart, oldValue, propertyValue);

			if ((oldValue != null && !oldValue.equals(propertyValue)) || (propertyValue != null && !propertyValue.equals(oldValue)))
			{
				changedProperties.add(firstPropertyPart);
				return true;
			}
		}
		else
		{
			// new property
			map.put(lastPropertyPart, propertyValue);
			onPropertyChange(firstPropertyPart, null, propertyValue);

			changedProperties.add(firstPropertyPart);
			return true;
		}
		return false;
	}

	/**
	 * put property from the outside world, not recording changes. converting to
	 * the right type.
	 *
	 * @param propertyName
	 * @param propertyValue
	 *            can be a JSONObject or array or primitive.
	 */
	public void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		// currently we keep Java objects in here; we could switch to having
		// only json objects in here is it make things quicker
		// (then whenever a server-side value is put in the map, convert it via
		// JSONUtils.toJSONValue())
		// TODO remove this when hierarchical tree structure comes into play
		// (only needed for )
		// if (propertyValue instanceof JSONObject)
		// {
		// Iterator<String> it = ((JSONObject)propertyValue).keys();
		// while (it.hasNext())
		// {
		// String key = it.next();
		// properties.put(propertyName + '.' + key,
		// ((JSONObject)propertyValue).get(key));
		// }
		// }// end TODO REMOVE
		Object oldValue = properties.get(propertyName);
		properties.put(propertyName, convertValueFromJSON(propertyName, oldValue, propertyValue));
	}

	/**
	 * Allow for subclasses to act on property changes
	 *
	 * @param propertyName
	 *            the property name
	 * @param oldValue
	 *            the old val
	 * @param newValue
	 *            the new val
	 */
	protected void onPropertyChange(String propertyName, Object oldValue, Object newValue)
	{
		if (newValue instanceof ISmartPropertyValue && newValue != oldValue)
		{
			final String complexPropertyRoot = propertyName;

			// NOTE here newValue and oldValue are the wrapped values in case of wrapper types (so what is actually stored in this base web object's map)

			if (oldValue instanceof ISmartPropertyValue)
			{
				((ISmartPropertyValue)oldValue).detach();
			}

			// a new complex property is linked to this component; initialize it
			((ISmartPropertyValue)newValue).attachToBaseObject(new IChangeListener()
			{
				@Override
				public void valueChanged()
				{
					flagPropertyChanged(complexPropertyRoot);
					// this must have happened on the event thread, in which case, after each event is fired, a check for changes happen
					// if it didn't happen on the event thread something is really wrong, cause then properties might change while
					// they are being read at the same time by the event thread
				}
			}, this);
		}
	}

	/**
	 * @param key
	 */
	public void flagPropertyChanged(String key)
	{
		changedProperties.add(key);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object wrapPropertyValue(String propertyName, Object oldValue, Object newValue) throws JSONException
	{
		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		IPropertyType<Object> type = propertyDesc != null ? (IPropertyType<Object>)propertyDesc.getType() : null;
		Object object = (type instanceof IWrapperType) ? ((IWrapperType)type).wrap(newValue, oldValue, new DataConverterContext(propertyDesc, this)) : newValue;
		if (type instanceof IClassPropertyType && object != null)
		{
			if (!((IClassPropertyType< ? >)type).getTypeClass().isAssignableFrom(object.getClass()))
			{
				log.info("property: " + propertyName + " of component " + getName() + " set with value: " + newValue + " which is not of type: " +
					((IClassPropertyType< ? >)type).getTypeClass());
				return null;
			}
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
	private Object convertValueFromJSON(String propertyName, Object previousComponentValue, Object newJSONValue) throws JSONException
	{
		if (newJSONValue == null || newJSONValue == JSONObject.NULL) return null;

		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		Object value = propertyDesc != null ? JSONUtils.fromJSON(previousComponentValue, newJSONValue, propertyDesc, new DataConverterContext(propertyDesc,
			this)) : null;
		return value != null && value != newJSONValue ? value : convertPropertyValue(propertyName, previousComponentValue, newJSONValue);
	}

	/**
	 * Allow for subclasses to do conversions, by default it just ask for the
	 * type to do the conversion to Java
	 *
	 * @param propertyName
	 *            the property name
	 * @param oldValue
	 *            the old val
	 * @param newValue
	 *            the new val
	 * @param sourceOfValue
	 * @return the converted value
	 * @throws JSONException
	 */
	protected Object convertPropertyValue(String propertyName, Object oldValue, Object newValue) throws JSONException
	{
		return newValue;
	}

	/**
	 * Called when this object will not longer be used - to release any held resources/remove listeners.
	 */
	public void dispose()
	{
		for (Object p : getRawProperties().values())
		{
			if (p instanceof ISmartPropertyValue) ((ISmartPropertyValue)p).detach(); // clear any listeners/held resources
		}
	}

}