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
import org.sablo.specification.property.CustomJSONPropertyType;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.websocket.utils.PropertyUtils;

/**
 * Property description as parsed from web component spec file.
 * @author rgansevles
 */
public class PropertyDescription
{

	private final String name;
	private final IPropertyType< ? > type;
	private final Object config;
	private final boolean optional;
	private final Object defaultValue;
	private final List<Object> values;
	private final PushToServerEnum pushToServer;
	private final JSONObject tags;

	//case of nested type
	// TODO: make properties final and remove put* calls so PropertyDescription is immutable
	private Map<String, PropertyDescription> properties = null;
	private final boolean hasDefault;


	public PropertyDescription(String name, IPropertyType< ? > type)
	{
		this(name, type, null, null, false, null, null, null, false);
	}

	public PropertyDescription(String name, IPropertyType< ? > type, PushToServerEnum pushToServer)
	{
		this(name, type, null, null, false, null, pushToServer, null, false);
	}

	public PropertyDescription(String name, IPropertyType< ? > type, Object config)
	{
		this(name, type, config, null, false, null, null, null, false);
	}

	public PropertyDescription(String name, IPropertyType< ? > type, Object config, Object defaultValue, boolean hasDefault, List<Object> values,
		PushToServerEnum pushToServer, JSONObject tags, boolean optional)
	{
		this.name = name;
		this.hasDefault = hasDefault;
		if (type instanceof IYieldingType)
		{
			YieldDescriptionArguments params = new YieldDescriptionArguments(config, defaultValue, values, pushToServer, tags, optional);
			this.type = ((IYieldingType< ? , ? >)type).yieldToOtherIfNeeded(name, params);

			if (this.type != type)
			{
				// it yielded; use new argument values in case yielding required it
				this.config = params.getConfig();
				this.defaultValue = params.defaultValue;
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
			this.values = values;
			this.pushToServer = pushToServer;
			this.tags = tags;
			this.optional = optional;
		}
	}

	public PropertyDescription(PropertyDescription pd, String name, IPropertyType< ? > type)
	{
		this.config = pd.config;
		this.defaultValue = pd.defaultValue;
		this.hasDefault = pd.hasDefault;
		this.name = name == null ? pd.name : name;
		this.optional = pd.optional;
		this.properties = pd.properties == null ? null : new HashMap<>(pd.properties);
		this.pushToServer = PushToServerEnum.allow;
		this.tags = pd.tags;
		this.type = type == null ? pd.type : type;
		this.values = pd.values == null ? null : new ArrayList<>(pd.values);
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
		if (properties == null)
		{
			return Collections.emptyList();
		}

		List<PropertyDescription> filtered = new ArrayList<>(4);
		for (PropertyDescription pd : properties.values())
		{
			if (pd.hasTag(tag))
			{
				filtered.add(pd);
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

	public PropertyDescription getProperty(String propname)
	{
		if (properties != null)
		{
			PropertyDescription propertyDescription = properties.get(propname);
			if (propertyDescription != null)
			{
				return propertyDescription;
			}
			int indexOfDot = propname.indexOf('.');
			if (indexOfDot >= 0)
			{
				// this must be a custom type then; also handle arrays
				String customType = propname.substring(0, indexOfDot);
				if (customType.indexOf('[') > 0)
				{
					customType = customType.substring(0, customType.indexOf('['));
				}
				String propertyName = propname.substring(indexOfDot + 1);
				if (propertyName.indexOf('[') > 0)
				{
					propertyName = propertyName.substring(0, propertyName.indexOf('['));
				}
				propertyDescription = properties.get(customType);
				if (propertyDescription instanceof WebObjectSpecification)
				{
					return propertyDescription.getProperty(propertyName);
				}
				else if (propertyDescription != null)
				{
					PropertyDescription typeSpec = ((ICustomType< ? >)propertyDescription.getType()).getCustomJSONTypeDefinition();
					return typeSpec.getProperty(propertyName);
				}
			}
		}
		else if (type instanceof CustomJSONPropertyType)
		{
			return ((CustomJSONPropertyType< ? >)type).getCustomJSONTypeDefinition().getProperty(propname);
		}
		return null;
	}

	// TODO: move to constructor so PropertyDescription is immutable
	public Map<String, PropertyDescription> getProperties()
	{
		if (properties != null) return Collections.unmodifiableMap(properties);
		else if (type instanceof CustomJSONPropertyType)
		{
			return ((CustomJSONPropertyType< ? >)type).getCustomJSONTypeDefinition().getProperties();
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
