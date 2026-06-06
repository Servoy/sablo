/*
 * Copyright (C) 2015 Servoy BV
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

import java.util.List;

import org.json.JSONObject;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.IPropertyType;

/**
 * Such a type is able to yield implementation to another type depending on the spec configuration.</br>
 * When the {@link PropertyDescription} instance for such a type is created, depending on it's configuration this type can decide
 * to yield to another type. So the new PropertyDescription instance will use instead the other type.<br/><br/>
 *
 * This is useful when you want to create and register types that wrap other types and can contribute to their wrapped
 * type's behavior only when some config options are specified in the .spec file.
 *
 * For example in Servoy a dataprovider type defined in .spec will actually be a foundset linked type that can yield to dataprovider
 * when parsing .spec if "forFoundset" is not present in it's configuration. If it is present it will remain a foundset linked type.
 *
 * @author acostescu
 */
public interface IYieldingType<T, YT> extends IPropertyType<T>
{

	/**
	 * See class javadoc.
	 * @param parameters when called it contains the arguments of the property description. If this type decides to yield to another type implementation
	 * for a property it can also alter the given parameters.
	 *
	 * @return either "this" if it doesn't need to yield, or the yielded to type.
	 */
	IPropertyType< ? > yieldToOtherIfNeeded(String propertyName, YieldDescriptionArguments parameters);

	/**
	 * This is the different type that yieldToOtherIfNeeded is able to return.
	 */
	IPropertyType<YT> getPossibleYieldType();

	/**
	 * Class that can be used to read current IYieldingType property description params.<br>
	 * These params can be changed by the #yieldToOtherIfNeeded method in case the type being yielded to needs modified ones.
	 */
	public class YieldDescriptionArguments
	{

		public YieldDescriptionArguments(Object config, Object defaultValue, Object initialValue, List<Object> values, PushToServerEnum pushToServer,
			JSONObject tags, boolean optional, String deprecated)
		{
			this.config = config;
			this.defaultValue = defaultValue;
			this.initialValue = initialValue;
			this.values = values;
			this.pushToServer = pushToServer;
			this.tags = tags;
			this.optional = optional;
			this.deprecated = deprecated;
		}

		public final Object defaultValue;
		public final Object initialValue;
		public final List<Object> values;
		public final PushToServerEnum pushToServer;
		public final JSONObject tags;
		public final boolean optional;
		public final String deprecated;

		private Object config;

		public Object getConfig()
		{
			return config;
		}

		public void setConfig(Object config)
		{
			this.config = config;
		}
	}

}
