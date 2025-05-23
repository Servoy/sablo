/*
 * Copyright (C) 2019 Servoy BV
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.IPropertyWithClientSideConversions;

/**
 * @author lvostinar
 *
 */
abstract class AbstractPropertyDescriptionBuilder<B extends AbstractPropertyDescriptionBuilder<B, P>, P extends PropertyDescription>
{
	protected String name = "";
	protected IPropertyType< ? > type;
	protected Object config;
	protected boolean optional;
	protected Object defaultValue;
	protected Object initialValue;
	protected List<Object> values;
	protected PushToServerEnum pushToServer;
	protected JSONObject tags;
	protected Map<String, PropertyDescription> properties;
	protected boolean hasDefault;
	protected String deprecated;
	protected boolean internal;

	public B withProperty(String propertyName, PropertyDescription pd)
	{
		if (properties == null)
		{
			properties = new HashMap<>();
		}
		properties.put(propertyName, pd);
		return getThis();
	}

	/**
	 * The resulting PropertyDescription will have the given properties as sub-properties.<br/><br/>
	 *
	 * <b><i>Note</i></b>: PropertyDescriptions with child properties (created with this method, so probably with irrelevant type on themselves) should NOT be written toJSON using the main/parent/this property value - as that will
	 * probably mean the default object conversion will be used even for sub-properties (which could be in fact typed); that will write them correctly (as far as default object conversion goes) but the subproperty
	 * types, if they are also {@link IPropertyWithClientSideConversions}, will be included in the sent value, even if the client might know them already. (if child properties are the properties of a web component or service for example then
	 * they are already available client-side). Still if you do choose to write toJSON such a parent PD's value (using default conversion) please make sure to call the fromServerToClient conversion on that value on the client,
	 * otherwise you will probably encounter unexpected structure (with types as default object conversion does it) inside that value on the client.
	 *
	 * @param subPropertiesList
	 * @return
	 */
	public B withProperties(Map<String, PropertyDescription> subPropertiesList)
	{
		if (properties == null)
		{
			properties = new HashMap<>();
		}
		properties.putAll(subPropertiesList);
		return getThis();
	}

	public B withName(String name)
	{
		this.name = name;
		return getThis();
	}

	public B withPushToServer(PushToServerEnum pushToServer)
	{
		this.pushToServer = pushToServer;
		return getThis();
	}

	public B withType(IPropertyType< ? > type)
	{
		this.type = type;
		return getThis();
	}

	public B withDefaultValue(Object defaultValue)
	{
		this.defaultValue = defaultValue;
		return getThis();
	}

	public B withInitialValue(Object initialValue)
	{
		this.initialValue = initialValue;
		return getThis();
	}

	public B withConfig(Object config)
	{
		this.config = config;
		return getThis();
	}

	public B withOptional(boolean optional)
	{
		this.optional = optional;
		return getThis();
	}

	public B withHasDefault(boolean hasDefault)
	{
		this.hasDefault = hasDefault;
		return getThis();
	}

	public B withValues(List<Object> values)
	{
		this.values = values;
		return getThis();
	}

	public B withTags(JSONObject tags)
	{
		this.tags = tags;
		return getThis();
	}

	public B withTagsCopiedFrom(PropertyDescription otherPD)
	{
		this.tags = otherPD.copyOfTags();
		return getThis();
	}

	public B withDeprecated(String deprecated)
	{
		this.deprecated = deprecated;
		return getThis();
	}


	/**
	 * internal means this is a property added by servoy internally, but should not be outputted on the tag  because its not a component property.
	 *
	 * @param b
	 * @return
	 */
	public B internal(boolean b)
	{
		this.internal = b;
		return getThis();
	}

	public B copyFrom(PropertyDescription pd)
	{
		this.name = pd.getName();
		this.type = pd.getType();
		this.config = pd.getConfig();
		this.optional = pd.isOptional();
		this.defaultValue = pd.getDefaultValue();
		this.initialValue = pd.getInitialValue();
		this.values = pd.getValues();
		this.pushToServer = pd.getPushToServer();
		this.tags = pd.copyOfTags();
		this.properties = new HashMap<>(pd.getProperties());
		this.hasDefault = pd.hasDefault();
		this.deprecated = pd.getDeprecated();
		this.internal = pd.isInternal();
		return getThis();
	}

	protected final B getThis()
	{
		return (B)this;
	}

	abstract public P build();
}
