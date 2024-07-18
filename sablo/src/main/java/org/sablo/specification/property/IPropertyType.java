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

import org.json.JSONObject;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;

/**
 * This class represents property types - which can have normal usage as declared in their type definition JSON,
 * can be default types or can have special handling (in case of wrapped/class types/complex properties, keeping different structures of data at design time/server side/client side).
 *
 * @param <T> the type that {@link WebComponent#setProperty(String, Object, org.sablo.websocket.ConversionLocation)} and {@link WebComponent#getProperty(String)} end up storing in the WebComponent properties map.
 *
 * @author acostescu
 */
public interface IPropertyType<T>
{

	/**
	 * The type name as used in .spec files.
	 * @return the type name as used in .spec files.
	 */
	String getName();

	/**
	 * Parse the JSON property configuration object into something that is easily usable later on (through {@link PropertyDescription#getConfig()}) by the property type implementation.<BR>
	 * Example of JSON: "myComponentProperty: { type: 'myCustomType', myCustomTypeConfig1: true, myCustomTypeConfig2: [2, 4 ,6] }"<BR><BR>
	 *
	 * If this is null but the property declaration contains configuration information, {@link PropertyDescription#getConfig()} will contain the actual JSON object.
	 *
	 * it should return the config object itself if it doesn't do anything.
	 *
	 * @return a custom object generated from the json or the json object itself.
	 */
	public Object parseConfig(JSONObject config);

	public T defaultValue(PropertyDescription pd);

	/**
	 * Defines whether this property can only be changed/set from the server; updates sent from client to it will be ignored.
	 * Used for protected, enabled and visible properties.
	 */
	boolean isProtecting();

	/**
	 * True for primitive types like int, string, etc.
	 */
	boolean isPrimitive();

}
