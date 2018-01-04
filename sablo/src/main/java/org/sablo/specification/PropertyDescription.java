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

package org.sablo.specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;
import org.sablo.specification.IYieldingType.YieldDescriptionArguments;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.CustomJSONObjectType;
import org.sablo.specification.property.CustomJSONPropertyType;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.websocket.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Property description as parsed from web component spec file.
 * @author rgansevles
 */
public class PropertyDescription
{

	private static final Logger log = LoggerFactory.getLogger(WebObjectSpecification.class.getCanonicalName());

	private final String name;
	private final IPropertyType< ? > type;
	private final Object config;
	private final boolean optional;
	private final Object defaultValue;
	private final Object initialValue;
	private final List<Object> values;
	private final PushToServerEnum pushToServer;
	private final JSONObject tags;

	//case of nested type
	// TODO: make properties final and remove put* calls so PropertyDescription is immutable
	private Map<String, PropertyDescription> properties = null;
	private final boolean hasDefault;


	public PropertyDescription(String name, IPropertyType< ? > type)
	{
		this(name, type, null, null, null, false, null, null, null, false);
	}

	public PropertyDescription(String name, IPropertyType< ? > type, PushToServerEnum pushToServer)
	{
		this(name, type, null, null, null, false, null, pushToServer, null, false);
	}

	public PropertyDescription(String name, IPropertyType< ? > type, Object config)
	{
		this(name, type, config, null, null, false, null, null, null, false);
	}

	public PropertyDescription(String name, IPropertyType< ? > type, Object config, Object defaultValue, Object initialValue, boolean hasDefault,
		List<Object> values, PushToServerEnum pushToServer, JSONObject tags, boolean optional)
	{
		this.name = name;
		this.hasDefault = hasDefault;
		if (type instanceof IYieldingType)
		{
			YieldDescriptionArguments params = new YieldDescriptionArguments(config, defaultValue, initialValue, values, pushToServer, tags, optional);
			this.type = ((IYieldingType< ? , ? >)type).yieldToOtherIfNeeded(name, params);

			if (this.type != type)
			{
				// it yielded; use new argument values in case yielding required it
				this.config = params.getConfig();
				this.defaultValue = params.defaultValue;
				this.initialValue = params.initialValue;
				this.values = params.values;
				this.pushToServer = params.pushToServer;
				this.tags = params.tags;
				this.optional = params.optional;
			}
			else
			{
				// didn't yield to another type; just use same args
				this.config = config;
				this.defaultValue = defaultValue;
				this.initialValue = initialValue;
				this.values = values;
				this.pushToServer = pushToServer;
				this.tags = tags;
				this.optional = optional;
			}
		}
		else
		{
			this.type = type;

			this.config = config;
			this.defaultValue = defaultValue;
			this.initialValue = initialValue;
			this.values = values;
			this.pushToServer = pushToServer;
			this.tags = tags;
			this.optional = optional;
		}
	}

	public Collection<PropertyDescription> getProperties(IPropertyType< ? > pt)
	{
		if (properties == null)
		{
			return Collections.emptyList();
		}

		List<PropertyDescription> filtered = new ArrayList<>(4);
		for (PropertyDescription pd : properties.values())
		{
			if (pt.getClass().isAssignableFrom(pd.getType().getClass()))
			{
				filtered.add(pd);
			}
		}
		return filtered;
	}

	public Collection<PropertyDescription> getTaggedProperties(String tag)
	{
		return getTaggedProperties(tag, null);
	}

	public Collection<PropertyDescription> getTaggedProperties(String tag, IPropertyType< ? > pt)
	{
		if (properties == null)
		{
			return Collections.emptyList();
		}

		List<PropertyDescription> filtered = new ArrayList<>(4);
		for (PropertyDescription pd : properties.values())
		{
			if (pd.hasTag(tag))
			{
				if (pt == null || pt.getClass().isAssignableFrom(pd.getType().getClass()))
				{
					filtered.add(pd);
				}
			}
		}
		return filtered;
	}

	public boolean hasChildProperties()
	{
		return properties != null && !properties.isEmpty();
	}

	public Collection<String> getAllPropertiesNames()
	{
		if (properties != null)
		{
			return Collections.unmodifiableCollection(properties.keySet());
		}
		return Collections.emptySet();
	}


	public String getName()
	{
		return name;
	}

	public IPropertyType< ? > getType()
	{
		return type;
	}

	public Object getConfig()
	{
		return config;
	}

	/**
	 * @return the hasDefault
	 */
	public boolean hasDefault()
	{
		return hasDefault;
	}

	public Object getDefaultValue()
	{
		return defaultValue;
	}

	public Object getInitialValue()
	{
		return initialValue;
	}

	public List<Object> getValues()
	{
		return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
	}

	public PushToServerEnum getPushToServer()
	{
		return pushToServer;
	}

	public Object getTag(String tag)
	{
		return tags == null ? null : tags.opt(tag);
	}

	public boolean hasTag(String tag)
	{
		return tags != null && tags.has(tag);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((config == null) ? 0 : config.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		PropertyDescription other = (PropertyDescription)obj;
		if (config == null)
		{
			if (other.config != null) return false;
		}
		else if (!config.equals(other.config)) return false;
		if (name == null)
		{
			if (other.name != null) return false;
		}
		else if (!name.equals(other.name)) return false;
		if (type != other.type) return false;

		if (defaultValue == null)
		{
			if (other.defaultValue != null) return false;
		}
		else if (!defaultValue.equals(other.defaultValue)) return false;

		return true;
	}

	public boolean isOptional()
	{
		return optional;
	}

	public PropertyDescription putProperty(String propname, PropertyDescription proptype)
	{
		if (properties == null) properties = new HashMap<>();
		if (proptype == null) properties.remove(propname);
		else properties.put(propname, proptype);
		return this;
	}

	public List<PropertyDescription> getPropertyPath(String propname)
	{
		ArrayList<PropertyDescription> propertyPath = new ArrayList<PropertyDescription>();

		PropertyDescription childProp;
		if (properties != null)
		{
			// so it's not an Array type PD (those don't have stuff in properties map); it must be either a custom object description or a web object specification
			if (propname.startsWith(".")) propname = propname.substring(1); // in case of multiple nesting levels for example a[3].b, the ".b" part will end up here so we must ignore the first dot if present

			childProp = properties.get(propname);
			if (childProp == null)
			{
				// see if a nested property was requested
				int indexOfFirstDot = propname.indexOf('.');
				int indexOfFirstOpenBracket = propname.indexOf('[');
				if (indexOfFirstDot >= 0 || indexOfFirstOpenBracket >= 0)
				{
					int firstSeparatorIndex = Math.min(indexOfFirstDot >= 0 ? indexOfFirstDot : indexOfFirstOpenBracket,
						indexOfFirstOpenBracket >= 0 ? indexOfFirstOpenBracket : indexOfFirstDot);

					// this must be a custom type then;
					String firstChildPropName = propname.substring(0, firstSeparatorIndex);
					childProp = properties.get(firstChildPropName);
					if (childProp != null) // so it wants to get a deeper nested PD; forward it to child PD to deal with it further
					{
						// here it should (according to check above) always be that firstSeparatorIndex < propname.length()
						propertyPath.add(childProp);
						List<PropertyDescription> childPropertyPath = childProp.getPropertyPath(propname.substring(firstSeparatorIndex));
						propertyPath.addAll(childPropertyPath.subList(0, childPropertyPath.size() - 1));
						childProp = childPropertyPath.get(childPropertyPath.size() - 1);
					}
				}
			}
		}
		else if (type instanceof CustomJSONObjectType)
		{
			// if it's a custom object type use that to get child prop. def
			childProp = ((CustomJSONPropertyType< ? >)type).getCustomJSONTypeDefinition().getProperty(propname);
		}
		else if (type instanceof CustomJSONArrayType)
		{
			// check that propname starts with an index
			boolean ok = false;

			int idxOfFirstOpenBracket = propname.indexOf("[");
			int idxOfFirstCloseBracket = propname.indexOf("]");

			if (idxOfFirstOpenBracket == 0)
			{
				if (idxOfFirstCloseBracket > 1)
				{
					// if it's an array then use the element prop. def; propname should be an index in this case
					try
					{
						Integer.parseInt(propname.substring(idxOfFirstOpenBracket + 1, idxOfFirstCloseBracket));
						ok = true;
					}
					catch (NumberFormatException e)
					{
					}
				}
				else if (idxOfFirstCloseBracket == 1) ok = true; // allow []
			}
			if (ok)
			{
				childProp = ((CustomJSONPropertyType< ? >)type).getCustomJSONTypeDefinition();
				if (idxOfFirstCloseBracket < propname.length() - 1) childProp = childProp.getProperty(propname.substring(idxOfFirstCloseBracket + 1)); // so it wants to get a deeper nested PD; forward it to element PD to deal with it further
			}
			else
			{
				log.debug("Property description get was called on an array type with propName that's not similar to [index] or []: " + propname);
				childProp = null;
			}
		}
		else childProp = null;

		propertyPath.add(childProp);
		return propertyPath;
	}

	public PropertyDescription getProperty(String propname)
	{
		List<PropertyDescription> propertyPath = getPropertyPath(propname);
		return propertyPath.get(propertyPath.size() - 1);
	}

	// TODO: move to constructor so PropertyDescription is immutable
	public Map<String, PropertyDescription> getProperties()
	{
		if (properties != null) return Collections.unmodifiableMap(properties);
		else if (type instanceof ICustomType)
		{
			return ((ICustomType< ? >)type).getCustomJSONTypeDefinition().getProperties();
		}

		return Collections.emptyMap();
	}

	public Map<String, PropertyDescription> getCustomJSONProperties()
	{
		HashMap<String, PropertyDescription> retVal = new HashMap<String, PropertyDescription>();
		if (properties != null)
		{
			for (Entry<String, PropertyDescription> e : properties.entrySet())
			{
				if (PropertyUtils.isCustomJSONProperty(e.getValue().getType())) retVal.put(e.getKey(), e.getValue());
			}
		}
		return retVal;
	}

	public void putAll(Map<String, PropertyDescription> map)
	{
		properties = new HashMap<>(map);
	}


	@Override
	public String toString()
	{
		return toString(true);
	}

	public String toString(boolean showFullType)
	{
		return "PropertyDescription[name: " + name + ", type: " + (showFullType ? type : "'" + type.getName() + "' type") + ", config: " + config +
			", default value: " + defaultValue + "]";
	}

	public String toStringWholeTree()
	{
		return toStringWholeTree(new StringBuilder(100), 2).toString();
	}

	public StringBuilder toStringWholeTree(StringBuilder b, int level)
	{
		b.append(toString(false));
		if (properties != null)
		{
			for (Entry<String, PropertyDescription> p : properties.entrySet())
			{
				b.append('\n');
				addSpaces(b, level + 1);
				b.append(p.getKey()).append(": ");
				p.getValue().toStringWholeTree(b, level + 1);
			}
		}
		else
		{
			b.append(" (no nested child properties)");
		}
		return b;
	}

	private static void addSpaces(StringBuilder b, int level)
	{
		for (int i = 0; i < level * 2; i++)
		{
			b.append(' ');
		}
	}

	public boolean isArrayReturnType(String dropTargetFieldName)
	{
		if (getProperty(dropTargetFieldName) != null) return PropertyUtils.isCustomJSONArrayPropertyType(getProperty(dropTargetFieldName).getType());
		return false;
	}

}
