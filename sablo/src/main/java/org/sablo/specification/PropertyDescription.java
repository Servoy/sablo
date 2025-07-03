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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.sablo.specification.IYieldingType.YieldDescriptionArguments;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.ChangeAwareList;
import org.sablo.specification.property.ChangeAwareMap;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.CustomJSONObjectType;
import org.sablo.specification.property.CustomJSONPropertyType;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyCanDependsOn;
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
	private String description;

	// case of nested type
	private final Map<String, PropertyDescription> properties;
	private final boolean hasDefault;

	// only call from builder or child classes
	PropertyDescription(String name, IPropertyType< ? > type, Object config, Map<String, PropertyDescription> properties, Object defaultValue,
		Object initialValue, boolean hasDefault, List<Object> values, PushToServerEnum pushToServer, JSONObject tags, boolean optional, String deprecated)
	{
		this.name = name;
		this.hasDefault = hasDefault;

		if (properties != null)
		{
			this.properties = dependencySort(properties);
		}
		else
		{
			this.properties = properties;
		}

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
		setDescription((String)getTag(DOCUMENTATION_TAG_FOR_PROP_OR_KEY_FOR_HANDLERS));
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

	/**
	 * Get the raw description.
	 */
	public String getDescriptionRaw()
	{
		return description;
	}

	/**
	 * Adds deprecation info to the description - if that is requested - and uses the given descriptionProcessor for pre-processing existing descriptions.
	 * @param descriptionProcessor can be null
	 */
	public String getDescriptionProcessed(boolean addDeprecationInfoIfAvailable, Function<String, String> descriptionProcessor)
	{
		if (addDeprecationInfoIfAvailable && isDeprecated())
			return "<b>@deprecated</b>: " + (descriptionProcessor != null ? descriptionProcessor.apply(getDeprecatedMessage()) : getDeprecatedMessage()) + //$NON-NLS-1$
				(description != null ? "<br/><br/>" + (descriptionProcessor != null ? descriptionProcessor.apply(description) : description) : ""); //$NON-NLS-1$//$NON-NLS-2$
		else return descriptionProcessor != null ? descriptionProcessor.apply(description) : description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}

	/**
	 * Performs dependency sorting and returns a LinkedHashMap with properties in sorted order
	 * @param unsortedProperties Map of property names to their descriptions
	 * @return LinkedHashMap of properties sorted by dependency order
	 */
	private LinkedHashMap<String, PropertyDescription> dependencySort(Map<String, PropertyDescription> unsortedProperties)
	{
//		boolean debug = false;
//		if (getName().equals("aggrid-groupingtable.column"))
//		{
//			debug = true;
//		}
//		else
//		{
//			debug = false;
//		}
		if (unsortedProperties != null)
		{
			// Phase 1: filter dependencies by category: independent / may depends on / custom
			LinkedHashMap<String, PropertyDescription> independentProps = new LinkedHashMap<>();
			List<String> dependentProps = new ArrayList<>();
			LinkedHashMap<String, PropertyDescription> customProps = new LinkedHashMap<>();

			for (Map.Entry<String, PropertyDescription> entry : unsortedProperties.entrySet())
			{
				String key = entry.getKey();
				PropertyDescription pd = entry.getValue();
				if (!(pd.getType() instanceof IPropertyCanDependsOn))
				{
					independentProps.put(key, pd);
				}
				else //can depends on something
				{
					String[] deps = ((IPropertyCanDependsOn)pd.getType()).getDependencies();
					if (deps != null)
					{
						if (IPropertyCanDependsOn.DEPENDS_ON_ALL[0].equals(deps[0]))
						{//custom type
							customProps.put(key, pd);
						}
						else
						{
							dependentProps.add(key);
						}
					}
					else
					{// no dependencies
						independentProps.put(key, pd);
					}
				}
			}

			// Phase 2: topological sort of explicit dependencies
			Map<String, List<String>> depMap = new HashMap<>();
			for (String key : dependentProps)
			{
				String[] deps = ((IPropertyCanDependsOn)unsortedProperties.get(key).getType()).getDependencies();
				List<String> list = deps == null
					? Collections.emptyList()
					: Arrays.stream(deps).filter(unsortedProperties::containsKey).collect(Collectors.toList());
				depMap.put(key, list);
			}

			List<String> sortedDependencies = alphaSortDependenciesByCategory(depMap, dependentProps);

			// Phase 3: assemble final map
			LinkedHashMap<String, PropertyDescription> result = new LinkedHashMap<>();
			result.putAll(independentProps);
			for (String key : sortedDependencies)
			{
				result.put(key, unsortedProperties.get(key));
			}
			result.putAll(customProps);

			return result;
		}
		return new LinkedHashMap<>();
	}

	@SuppressWarnings("boxing")
	private List<String> alphaSortDependenciesByCategory(Map<String, List<String>> dependenciesMap, List<String> dependentProperties)
	{
		// Step 1: Perform initial sort - just make sure dependet properties are always follow their dependencies (not alpha sorting for now)
		List<String> initialSort = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		Set<String> visiting = new HashSet<>();
		for (String key : dependentProperties)
		{
			if (!visited.contains(key))
			{
				visit(key, dependenciesMap, visited, visiting, initialSort);
			}
		}

		// Step 2: Compute dependency levels for each property
		Map<String, Integer> levels = computeDependencyLevels(dependenciesMap, initialSort);

		// Step 3: Group properties by their dependency level
		Map<Integer, List<String>> levelGroups = new HashMap<>();
		for (String prop : initialSort)
		{
			int level = levels.get(prop);
			if (!levelGroups.containsKey(level))
			{
				levelGroups.put(level, new ArrayList<>());
			}
			levelGroups.get(level).add(prop);
		}

		// Step 4: Sort each level alphabetically
		for (List<String> group : levelGroups.values())
		{
			Collections.sort(group);
		}

		// Step 5: Reassemble in dependency order
		List<String> sortedDependencies = new ArrayList<>();
		if (!levelGroups.isEmpty())
		{
			// Find max level safely (in case of circular dependencies)
			int maxLevel = 0;
			for (Integer level : levelGroups.keySet())
			{
				if (level != null && level > maxLevel)
				{
					maxLevel = level;
				}
			}

			// Add properties in level order
			for (int level = 0; level <= maxLevel; level++)
			{
				if (levelGroups.containsKey(level))
				{
					sortedDependencies.addAll(levelGroups.get(level));
				}
			}
		}

		return sortedDependencies;
	}

	/**
	 * Computes the dependency level for each property.
	 * Level 0: Properties with no dependencies
	 * Level N: Properties that depend on properties of max level N-1
	 *
	 * @param depMap Map of property names to their dependencies
	 * @param sortedProps List of properties in topological order
	 * @return Map of property names to their dependency levels
	 */
	private Map<String, Integer> computeDependencyLevels(Map<String, List<String>> depMap, List<String> sortedProps)
	{
		Map<String, Integer> levels = new HashMap<>();

		// First pass: assign level 0 to properties with no dependencies
		for (String prop : sortedProps)
		{
			List<String> deps = depMap.getOrDefault(prop, Collections.emptyList());
			if (deps.isEmpty())
			{
				levels.put(prop, 0);
			}
		}

		// Second pass: compute levels for properties with dependencies
		for (String prop : sortedProps)
		{
			if (!levels.containsKey(prop))
			{
				// Use a new visiting set for each property to track cycles
				Set<String> visiting = new HashSet<>();
				computeLevel(prop, depMap, levels, visiting);
			}
		}

		return levels;
	}

	/**
	 * Recursively computes the dependency level for a property.
	 * Handles circular dependencies by assigning a default level.
	 *
	 * @param prop Property name
	 * @param depMap Map of property names to their dependencies
	 * @param levels Map of property names to their computed levels
	 * @param visiting Set of properties currently being processed (for cycle detection)
	 * @return The computed level for the property
	 */
	private int computeLevel(String prop, Map<String, List<String>> depMap, Map<String, Integer> levels, Set<String> visiting)
	{
		// If level is already computed, return it
		if (levels.containsKey(prop))
		{
			return levels.get(prop);
		}

		// Check for circular dependency
		if (visiting.contains(prop))
		{
			// Circular dependency detected, assign a default level
			// We'll use 0 as the default level for circular dependencies
			levels.put(prop, 0);
			return 0;
		}

		// Mark property as being visited
		visiting.add(prop);

		// Get dependencies
		List<String> deps = depMap.getOrDefault(prop, Collections.emptyList());
		if (deps.isEmpty())
		{
			// No dependencies, level is 0
			levels.put(prop, 0);
			visiting.remove(prop); // Done processing this property
			return 0;
		}

		// Compute max level of dependencies
		int maxLevel = -1;
		for (String dep : deps)
		{
			int depLevel = computeLevel(dep, depMap, levels, visiting);
			maxLevel = Math.max(maxLevel, depLevel);
		}

		// Property level is max dependency level + 1
		int level = maxLevel + 1;
		levels.put(prop, level);

		// Done processing this property
		visiting.remove(prop);
		return level;
	}

	private void visit(String key, Map<String, List<String>> depMap,
		Set<String> visited, Set<String> visiting, List<String> sorted)
	{
		if (visited.contains(key)) return;
		if (visiting.contains(key))
		{
			// already been here; just ensure this node still appears once in the sorted list
			visited.add(key);
			sorted.add(key);
			return;
		}
		visiting.add(key);
		for (String dep : depMap.getOrDefault(key, Collections.emptyList()))
		{
			visit(dep, depMap, visited, visiting, sorted);
		}
		visiting.remove(key);
		visited.add(key);
		sorted.add(key);
	}

	/**
	 * Sort the received map according to dependency order stored in the previous calls
	 * @param unsortedProperties Map of property names to their descriptions
	 */
	public void sortProperties(Map<String, Object> unsortedProperties)
	{
		if (unsortedProperties == null || unsortedProperties.isEmpty() || this.properties == null || this.properties.isEmpty())
		{
			return;
		}

		//sort model'sproperties
		if (this.properties != null && !this.properties.isEmpty())
		{
			LinkedHashMap<String, Object> temp = new LinkedHashMap<>();
			// First, add entries not in design-time properties
			for (String key : unsortedProperties.keySet())
			{
				if (!this.properties.containsKey(key))
				{
					temp.put(key, unsortedProperties.get(key));
				}
			}
			// Then, add design-time properties in order
			for (String key : this.properties.keySet())
			{
				if (unsortedProperties.containsKey(key))
				{
					temp.put(key, unsortedProperties.get(key));
				}
			}
			unsortedProperties.clear();
			unsortedProperties.putAll(temp);
		}

		//sort custom types properties
		for (String key : unsortedProperties.keySet())
		{
			// ChangeAwareList contains an array of the same property type instances (for example a list of columns)
			if (this.properties.get(key) != null && unsortedProperties.get(key) instanceof ChangeAwareList awareList)
			{
				PropertyDescription pd = this.properties.get(key);
				for (Object obj : awareList)
				{
					//if the above property type have multiple properties - that is provided in a map

					if (pd.getType() instanceof ICustomType)
					{
						pd = ((ICustomType< ? >)pd.getType()).getCustomJSONTypeDefinition();
						Map<String, PropertyDescription> sortedProps = pd.getProperties();

						while (sortedProps.isEmpty() && pd != null && pd.getType() instanceof ICustomType)
						{
							pd = ((ICustomType< ? >)pd.getType()).getCustomJSONTypeDefinition();
							sortedProps = pd.getProperties();
						}
						if (!(sortedProps.isEmpty()) && (obj instanceof ChangeAwareMap< ? , ? > awareMap)) //we have something to sort
						{
							@SuppressWarnings("unchecked")
							Map<String, Object> baseMap = (Map<String, Object>)awareMap.getBaseMap();
							LinkedHashSet<String> sortedSet = new LinkedHashSet<>();
							for (String sortedKey : sortedProps.keySet())
							{
								if (baseMap.containsKey(sortedKey))
								{
									sortedSet.add(sortedKey);
								}
							}
							if (sortedSet.size() != baseMap.size())
							{
								log.warn("Can't sort properties; size mismatch"); //$NON-NLS-1$
							}
							else
							{
								awareMap.setSortedKeys(sortedSet);
							}
						}
					}

				}
			}
		}
	}

	/**
	 * Test dependency sorting with a clear input/output format.
	 * This method takes a map of property names to their dependencies and tests the sorting algorithm.
	 *
	 * @param dependencyMap Map where keys are property names and values are arrays of property names they depend on
	 * @param expectedOrder Expected order of properties after sorting
	 * @param verbose If true, print detailed output
	 * @param timeout Timeout in milliseconds for circular dependency tests (0 for no timeout)
	 * @return true if the test passed, false otherwise
	 */
	public static boolean testDependencySortingWithMap(Map<String, String[]> dependencyMap, String[] expectedOrder, boolean verbose, long timeout)
	{
		try
		{
			// Create a minimal PropertyDescription instance for testing
			PropertyDescription pd = new PropertyDescription("test", new MockPropertyType(), null, null, null, null, false, null, null, null, false, null);

			// Create unsorted properties map
			Map<String, PropertyDescription> unsortedProps = new HashMap<>();

			// Display input dependencies
			if (verbose)
			{
				System.out.println("\nTest Input Dependencies:");
				for (Map.Entry<String, String[]> entry : dependencyMap.entrySet())
				{
					String propName = entry.getKey();
					String[] deps = entry.getValue();

					if (deps == null || deps.length == 0)
					{
						System.out.println(propName + " -> [independent]");
					}
					else
					{
						System.out.println(propName + " -> depends on: " + String.join(", ", deps));
					}
				}
			}

			// Create property descriptions from the dependency map
			for (Map.Entry<String, String[]> entry : dependencyMap.entrySet())
			{
				String propName = entry.getKey();
				String[] deps = entry.getValue();

				PropertyDescription propDesc = createMockPropertyWithDeps(propName, deps);
				unsortedProps.put(propName, propDesc);
			}

			// If timeout is specified, run in a separate thread with timeout
			if (timeout > 0)
			{
				final Map<String, PropertyDescription>[] result = new Map[1];
				final boolean[] testCompleted = new boolean[1];
				final boolean[] testStarted = new boolean[1];
				final Map<String, PropertyDescription> finalUnsortedProps = unsortedProps;

				Thread testThread = new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							if (verbose)
							{
								System.out.println("Starting dependency sort with timeout...");
							}
							testStarted[0] = true;

							// Sort the properties
							result[0] = pd.dependencySort(finalUnsortedProps);
							testCompleted[0] = true;
						}
						catch (Throwable e)
						{
							if (verbose)
							{
								System.out.println("Exception in dependency sort: " + e.getClass().getName() + ": " + e.getMessage());
								e.printStackTrace();
							}
						}
					}
				});
				testThread.setName("DependencySortTestThread");
				testThread.start();

				// Wait for the specified timeout
				try
				{
					if (verbose)
					{
						System.out.println("Waiting for test thread to complete (timeout: " + timeout + "ms)...");
					}
					testThread.join(timeout);

					if (testCompleted[0])
					{
						// Test completed successfully
						Map<String, PropertyDescription> sortedProps = result[0];
						String[] actualOrder = sortedProps.keySet().toArray(new String[0]);

						if (verbose)
						{
							System.out.println("Expected order: " + String.join(", ", expectedOrder));
							System.out.println("Actual order: " + String.join(", ", actualOrder));
						}

						boolean testPassed = Arrays.equals(expectedOrder, actualOrder);
						if (verbose)
						{
							System.out.println("Test result: " + (testPassed ? "PASSED" : "FAILED"));
						}
						return testPassed;
					}
					else
					{
						// Test timed out
						if (verbose)
						{
							System.out.println("Test result: FAILED - Test timed out after " + timeout + "ms");
							if (testStarted[0])
							{
								System.out.println("Test started but did not complete. Likely stuck in dependencySort method.");
							}
							else
							{
								System.out.println("Test thread never started execution.");
							}
						}

						// Interrupt the test thread to prevent it from running indefinitely
						testThread.interrupt();
						return false;
					}
				}
				catch (InterruptedException e)
				{
					if (verbose)
					{
						System.out.println("Test was interrupted: " + e.getMessage());
					}
					return false;
				}
			}
			else
			{
				// No timeout, run directly
				LinkedHashMap<String, PropertyDescription> sortedProps = pd.dependencySort(unsortedProps);
				String[] actualOrder = sortedProps.keySet().toArray(new String[0]);

				if (verbose)
				{
					System.out.println("Expected order: " + String.join(", ", expectedOrder));
					System.out.println("Actual order: " + String.join(", ", actualOrder));
				}

				boolean testPassed = Arrays.equals(expectedOrder, actualOrder);
				if (verbose)
				{
					System.out.println("Test result: " + (testPassed ? "PASSED" : "FAILED"));
				}
				return testPassed;
			}
		}
		catch (Exception e)
		{
			if (verbose)
			{
				System.err.println("Exception during dependency sorting test: " + e.getMessage());
				e.printStackTrace();
			}
			return false;
		}
	}


	/**
	 * A simple mock property type for testing
	 */
	private static class MockPropertyType implements IPropertyType
	{
		@Override
		public String getName()
		{
			return "mocktype";
		}

		@Override
		public Object defaultValue(PropertyDescription pd)
		{
			return null;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.property.IPropertyType#parseConfig(org.json.JSONObject)
		 */
		@Override
		public Object parseConfig(JSONObject config)
		{
			// TODO Auto-generated method stub
			return null;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.property.IPropertyType#isProtecting()
		 */
		@Override
		public boolean isProtecting()
		{
			// TODO Auto-generated method stub
			return false;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.property.IPropertyType#isBuiltinType()
		 */
		@Override
		public boolean isBuiltinType()
		{
			// TODO Auto-generated method stub
			return false;
		}
	}

	/**
	 * A mock property type that implements IPropertyCanDependsOn for testing
	 */
	private static class MockPropertyTypeWithDependencies implements IPropertyType, IPropertyCanDependsOn
	{
		private final IPropertyType delegate;
		private final String[] dependencies;

		public MockPropertyTypeWithDependencies(IPropertyType delegate, String[] dependencies)
		{
			this.delegate = delegate;
			this.dependencies = dependencies;
		}

		@Override
		public String getName()
		{
			return delegate.getName();
		}

		@Override
		public Object defaultValue(PropertyDescription pd)
		{
			return delegate.defaultValue(pd);
		}

		@Override
		public String[] getDependencies()
		{
			return dependencies;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.property.IPropertyType#parseConfig(org.json.JSONObject)
		 */
		@Override
		public Object parseConfig(JSONObject config)
		{
			// TODO Auto-generated method stub
			return null;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.property.IPropertyType#isProtecting()
		 */
		@Override
		public boolean isProtecting()
		{
			// TODO Auto-generated method stub
			return false;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.property.IPropertyType#isBuiltinType()
		 */
		@Override
		public boolean isBuiltinType()
		{
			// TODO Auto-generated method stub
			return false;
		}
	}

	/**
	 * Helper method to create a mock PropertyDescription with dependencies for testing
	 */
	private static PropertyDescription createMockPropertyWithDeps(String name, String[] dependencies)
	{
		// Create a minimal PropertyDescription instance
		PropertyDescription pd = new PropertyDescription(name, new MockPropertyType(), null, null, null, null, false, null, null, null, false, null);

		// If dependencies are specified, create a type that implements both IPropertyType and IPropertyCanDependsOn
		if (dependencies != null)
		{
			// Create a mock property type that also implements IPropertyCanDependsOn
			pd = new PropertyDescription(name, new MockPropertyTypeWithDependencies(new MockPropertyType(), dependencies),
				null, null, null, null, false, null, null, null, false, null);
		}

		return pd;
	}

}