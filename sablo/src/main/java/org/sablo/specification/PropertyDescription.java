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

	/**
	 * The tag name that can be used in .spec files of components/services to document what properties do. Can be used for documenting handlers as well - as a key in their JSON.
	 */
	public static final String DOCUMENTATION_TAG_FOR_PROP_OR_KEY_FOR_HANDLERS = "doc"; //$NON-NLS-1$

	public static final String VALUE_TYPES_TAG_FOR_PROP = "value_types"; //$NON-NLS-1$

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
	private String deprecated = null;
	private String documentation;

	// case of nested type
	private final Map<String, PropertyDescription> properties;
	private final boolean hasDefault;

	// only call from builder or child classes
	PropertyDescription(String name, IPropertyType< ? > type, Object config, Map<String, PropertyDescription> properties, Object defaultValue,
		Object initialValue, boolean hasDefault, List<Object> values, PushToServerEnum pushToServer, JSONObject tags, boolean optional, String deprecated)
	{
		this.name = name;
		this.hasDefault = hasDefault;
		this.properties = properties;
		if (type instanceof IYieldingType)
		{
			YieldDescriptionArguments params = new YieldDescriptionArguments(config, defaultValue, initialValue, values, pushToServer, tags, optional,
				deprecated);
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
				this.deprecated = params.deprecated;
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
				this.deprecated = deprecated;
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
			this.deprecated = deprecated;
		}
		setDocumentation((String)getTag(DOCUMENTATION_TAG_FOR_PROP_OR_KEY_FOR_HANDLERS));
	}

	/**
	 * Returns all properties in this property description that are of given type.<br/>
	 * Includes all yielding types that can yield to given type.
	 * @see #getProperties(IPropertyType, boolean)
	 */
	public Collection<PropertyDescription> getProperties(IPropertyType< ? > pt)
	{
		return getProperties(pt, true);
	}

	/**
	 * Returns all properties in this property description that are of given type or, if includingYieldingTypes is true, that can yield to given type.<br/>
	 *
	 * @param includingYieldingTypes if you have for example a DataproviderPropertyType that is configured with forFoundset -> it will actually be of type FoundsetLinkedPropertyType which is a yielding
	 * type that yields to DataproviderPropertyType; if this arg is true then types that yield to the given type will also be included
	 */
	public Collection<PropertyDescription> getProperties(IPropertyType< ? > typeOfProperty, boolean includingYieldingTypes)
	{
		if (properties == null)
		{
			return Collections.emptyList();
		}

		List<PropertyDescription> filtered = new ArrayList<>(4);
		for (PropertyDescription pd : properties.values())
		{
			IPropertyType< ? > propType = pd.getType();

			if (typeOfProperty.getClass().isAssignableFrom(propType.getClass()) || (includingYieldingTypes && propType instanceof IYieldingType &&
				typeOfProperty.getClass().isAssignableFrom(((IYieldingType< ? , ? >)propType).getPossibleYieldType().getClass())))
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

	/**
	 * If .spec file declared a pushToServer value then it will return that value; otherwise it will return/default to PushToServerEnum.reject.
	 * Reject is a default for root level properties that do not specify a push to server; all others levels - when they need to compute the pushToServerLevel
	 * can use {@link #getPushToServerAsDeclaredInSpecFile()} so that they can differentiate between reject being declared in .spec file and nothing being declared
	 * in .spec file.
	 *
	 * @see PushToServerEnum#combineWithChild(PushToServerEnum)
	 */
	public PushToServerEnum getPushToServer()
	{
		return pushToServer != null ? pushToServer : PushToServerEnum.reject;
	}

	/**
	 * If .spec file declared a pushToServer value then it will return that value; otherwise it will return null.
	 *
	 * @see PushToServerEnum#combineWithChild(PushToServerEnum)
	 */
	public PushToServerEnum getPushToServerAsDeclaredInSpecFile()
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

	/**
	 * Generates a list of all PropertyDescriptions from root to the inner most element in case of a nested propertyName like "myArray[3].data" for example.
	 * For simple property names the list will contain only that property's PD.
	 *
	 * It will return a 0-length array if the property was not found. All elements in the array are non-null.
	 *
	 * @param propertyName can be a simple property name like "myProperty" or a nested property name like "myArray[3].data".
	 * @return see description above.
	 */
	public List<PropertyDescription> getPropertyPath(String propertyName)
	{
		ArrayList<PropertyDescription> propertyPath = new ArrayList<PropertyDescription>();
		getPropertyOrPropertyPathInternal(propertyName, propertyPath);

		return propertyPath;
	}

	/**
	 * The reason why this method has both a return value and a param that stuff is added to is to reuse code but not allocate arrays if caller only needs last segment.
	 * So only one or the other will be used, not both! ArrayList will only be used if provided by caller.
	 *
	 * @param propertyPathToPopulateIfRequested if this is null, only the inner most PropertyDescription will be returned by this method (in case of nesting with . and []);
	 * 									if it is != null, all PDs encountered in the way in case of nesting will be added to propertyPathToPopulate and return value will be null
	 * @return see propertyPathToPopulate description above
	 */
	@SuppressWarnings("nls")
	private PropertyDescription getPropertyOrPropertyPathInternal(String propName, ArrayList<PropertyDescription> propertyPathToPopulateIfRequested)
	{
		// TODO maybe it would be better to make code not need this method at all - and let the property
		// types themselves handle nested prop code aspects that need this method, just like we do for toJSON, fromJSON etc. in nested custom array/obj types...

		PropertyDescription innerMostPDifRequested = null; // return value in case propertyPathToPopulate == null (so caller only wants last PD, not an array of all PDs in case of nesting)
		if (properties != null)
		{
			// so it's not an Array type PD (those don't have stuff in PD properties map);
			// it must be either a custom object PD's type custom definition or a web object specification
			if (propName.startsWith(".")) propName = propName.substring(1); // in case of multiple nesting levels for example a[3].b, the ".b" part will end up here so we must ignore the first dot if present

			PropertyDescription firstProp = properties.get(propName);
			if (firstProp == null)
			{
				// see if a nested property was requested
				int indexOfFirstDot = propName.indexOf('.');
				int indexOfFirstOpenBracket = propName.indexOf('[');
				if (indexOfFirstDot >= 0 || indexOfFirstOpenBracket >= 0)
				{
					int firstSeparatorIndex = Math.min(indexOfFirstDot >= 0 ? indexOfFirstDot : indexOfFirstOpenBracket,
						indexOfFirstOpenBracket >= 0 ? indexOfFirstOpenBracket : indexOfFirstDot);

					// this must be a custom type then;
					String firstChildPropName = propName.substring(0, firstSeparatorIndex);
					firstProp = properties.get(firstChildPropName);

					if (firstProp != null) // so it wants to get a deeper nested PD; forward it to child PD to deal with it further
					{
						if (propertyPathToPopulateIfRequested != null) propertyPathToPopulateIfRequested.add(firstProp);

						// here it should (according to check above) always be that firstSeparatorIndex < propname.length()
						innerMostPDifRequested = firstProp.getPropertyOrPropertyPathInternal(propName.substring(firstSeparatorIndex),
							propertyPathToPopulateIfRequested);
					}
					else if (propertyPathToPopulateIfRequested != null) propertyPathToPopulateIfRequested.clear(); // not found
				}
				else if (propertyPathToPopulateIfRequested != null) propertyPathToPopulateIfRequested.clear(); // not found
			}
			else
			{
				// simple property found - non nested
				if (propertyPathToPopulateIfRequested != null) propertyPathToPopulateIfRequested.add(firstProp);
				else innerMostPDifRequested = firstProp;
			}
		}
		else if (type instanceof CustomJSONObjectType)
		{
			innerMostPDifRequested = ((CustomJSONObjectType< ? , ? >)type).getCustomJSONTypeDefinition().getPropertyOrPropertyPathInternal(propName,
				propertyPathToPopulateIfRequested);
		}
		else if (type instanceof CustomJSONArrayType)
		{
			// check that propname starts with an index
			boolean ok = false;

			int idxOfFirstOpenBracket = propName.indexOf("[");
			int idxOfFirstCloseBracket = propName.indexOf("]");

			if (idxOfFirstOpenBracket == 0)
			{
				if (idxOfFirstCloseBracket > 1)
				{
					// if it's an array then use the element prop. def; propname should be an index in this case
					try
					{
						// just check that the index is an int
						Integer.parseInt(propName.substring(idxOfFirstOpenBracket + 1, idxOfFirstCloseBracket));
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
				PropertyDescription elemPD = ((CustomJSONPropertyType< ? >)type).getCustomJSONTypeDefinition();

				if (propertyPathToPopulateIfRequested != null) propertyPathToPopulateIfRequested.add(elemPD);
				else innerMostPDifRequested = elemPD;

				// if "ok" is true that means idxOfFirstCloseBracket >= 1, see code above;
				// continue looking inside the array's element if needed
				if (idxOfFirstCloseBracket < propName.length() - 1)
					innerMostPDifRequested = elemPD.getPropertyOrPropertyPathInternal(propName.substring(idxOfFirstCloseBracket + 1),
						propertyPathToPopulateIfRequested);
			}
			else
			{
				if (propertyPathToPopulateIfRequested != null) propertyPathToPopulateIfRequested.clear(); // not found
				log.debug("Property description get was called on an array type with propName that's not similar to [index] or []: " + propName + ". Path: " +
					propertyPathToPopulateIfRequested);
			}
		}
		else if (propertyPathToPopulateIfRequested != null) propertyPathToPopulateIfRequested.clear(); // not found

		return innerMostPDifRequested;
	}

	public PropertyDescription getProperty(String propname)
	{
		return getPropertyOrPropertyPathInternal(propname, null);
	}

	public static class PDAndComputedPushToServer
	{
		public final PropertyDescription pd;
		public final PushToServerEnum pushToServer;

		public PDAndComputedPushToServer(PropertyDescription pd, PushToServerEnum pushToServer)
		{
			this.pd = pd;
			this.pushToServer = pushToServer;
		}
	}

	public PDAndComputedPushToServer computePushToServerForPropertyPathAndGetPD(String propname)
	{
		List<PropertyDescription> propertyPath = getPropertyPath(propname);
		PushToServerEnum computedPTS = (propertyPath.size() > 0 ? propertyPath.get(0).getPushToServer() : PushToServerEnum.reject); // default for root properties is reject; the rest of the path is computed from parent computed and child declared pushToServer values

		for (int i = 1; i < propertyPath.size(); i++)
		{
			computedPTS = computedPTS.combineWithChild(propertyPath.get(i).getPushToServerAsDeclaredInSpecFile()); // note that here we use getPushToServerAsDeclaredInSpecFile() which can return null as well; for root property we used getPushToServer()
		}

		return new PDAndComputedPushToServer(propertyPath.size() > 0 ? propertyPath.get(propertyPath.size() - 1) : null, computedPTS);
	}

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

	public boolean isDeprecated()
	{
		return deprecated != null && !"false".equalsIgnoreCase(deprecated.trim());
	}

	public String getDeprecated()
	{
		return deprecated;
	}

	public String getDeprecatedMessage()
	{
		if (deprecated != null && !"false".equalsIgnoreCase(deprecated.trim()) && !"true".equalsIgnoreCase(deprecated.trim()))
		{
			return deprecated;
		}
		return "";
	}

	protected JSONObject copyOfTags()
	{
		return tags != null ? new JSONObject(tags.toString()) : null;
	}

	public String getDocumentation()
	{
		return getDocumentation(true);
	}

	public String getDocumentation(boolean addDeprecationInfoIfAvailable)
	{
		if (addDeprecationInfoIfAvailable) return (isDeprecated()
			? "<b>@deprecated</b>: " + getDeprecatedMessage() + (documentation != null ? "<br/><br/>" + documentation : "") : documentation); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		else return documentation;
	}

	public void setDocumentation(String documentation)
	{
		this.documentation = documentation;
	}

}
