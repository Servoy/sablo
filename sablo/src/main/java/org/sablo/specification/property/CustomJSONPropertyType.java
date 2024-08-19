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

package org.sablo.specification.property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.PropertyDescriptionBuilder;
import org.sablo.specification.WebObjectApiFunctionDefinition;
import org.sablo.specification.property.types.DefaultPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Property types that are defined in JSON spec files.
 *
 * @author acostescu
 */
public abstract class CustomJSONPropertyType<T> extends DefaultPropertyType<T> implements ICustomType<T>
{

	protected static final Logger log = LoggerFactory.getLogger(CustomJSONPropertyType.class.getCanonicalName());
	private PropertyDescription definition;
	private final String name;
	private String documentation;
	private Map<String, WebObjectApiFunctionDefinition> apiFunctions;
	private ICustomType< ? > parent;

	/**
	 * Creates a new property types that is defined in JSON spec files.
	 * @param typeName the name of this type as used in spec files.
	 * @param definition the parsed JSON definition of this type. If null, it must be set later via {@link #setCustomJSONDefinition(PropertyDescription)}.
	 */
	public CustomJSONPropertyType(String typeName, PropertyDescription definition)
	{
		this.name = typeName;
		setCustomJSONDefinition(definition);
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String getDocumentation()
	{
		return documentation;
	}

	@Override
	public void setDocumentation(String documentation)
	{
		this.documentation = documentation;
	}

	public void setCustomJSONDefinition(PropertyDescription definition)
	{
		this.definition = definition;
	}

	/**
	 * Returns the parsed JSON definition of this type.
	 * @return the parsed JSON definition of this type.
	 */
	@Override
	public PropertyDescription getCustomJSONTypeDefinition()
	{
		if (parent != null)
		{
			return new PropertyDescriptionBuilder().copyFrom(definition).withProperties(parent.getCustomJSONTypeDefinition().getProperties()).build();
		}
		return definition;
	}

	@Override
	public void addApiFunction(WebObjectApiFunctionDefinition def)
	{
		if (apiFunctions == null) apiFunctions = new java.util.HashMap<>();
		apiFunctions.put(def.getName(), def);
	}

	@Override
	public Collection<WebObjectApiFunctionDefinition> getApiFunctions()
	{
		Collection<WebObjectApiFunctionDefinition> apis = apiFunctions != null ? Collections.unmodifiableCollection(apiFunctions.values())
			: Collections.emptyList();
		if (parent != null)
		{
			ArrayList<WebObjectApiFunctionDefinition> allApis = new ArrayList<>(parent.getApiFunctions());
			allApis.addAll(apis);
			apis = allApis;
		}
		return apis;
	}

	@Override
	public WebObjectApiFunctionDefinition getApiFunction(String apiName)
	{
		if (apiFunctions == null) return parent != null ? parent.getApiFunction(apiName) : null;
		WebObjectApiFunctionDefinition api = apiFunctions.get(apiName);
		if (api == null && parent != null) api = parent.getApiFunction(apiName);
		return api;
	}

	/**
	 * @param parent the parent to set
	 */
	public void setParent(ICustomType< ? > parent)
	{
		this.parent = parent;
	}

	/**
	 * @return the parent
	 */
	public ICustomType< ? > getParent()
	{
		return parent;
	}

	@Override
	public String toString()
	{
		return super.toString() + "\nDefinition JSON:\n  " + (definition == null ? "null" : definition.toStringWholeTree()); //$NON-NLS-1$//$NON-NLS-2$
	}

	@Override
	public Object parseConfig(JSONObject config)
	{
		return config;
	}

}
