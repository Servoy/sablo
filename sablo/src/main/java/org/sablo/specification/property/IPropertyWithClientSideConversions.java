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

import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.utils.JSONUtils;

/**
 * Some property types need to implement from-server and to-server conversions client-side. (so js conversions that happen in the browser).<br/>
 * For example dates are not supported in json so they will probably be converted to milliseconds or strings before being sent from browser to server over the websocket.<br/><br/>
 *
 * This is a tagging interface (but not only that) that says "this property type also has js conversions browser-side". It is needed because, for example, if a service or component declare this
 * type as a sync API call return value type or a handler argument type (components only) or if their model declares such a type in a property - but no value is sent for it from
 * the server, the browser-side client-to-server conversion still need to be called on them anyway before being sent to server when a new value appears; that means that server-side sablo needs
 * to provide this information to the browser (when a service or component that uses such properties is sent to a browser window/tab, the type information for types that implement
 * IPropertyWithClientSideConversions needs to be sent along with it).<br/><br/>
 *
 * Simple types that need client side conversions will just write their name as a string when {@link IPropertyWithClientSideConversions#writeClientSideTypeName(PropertyDescription)} gets called.<br/><br/>
 *
 * More complex types (arrays, objects of different keys etc., ...) that can have different types of sub-elements (usually the ones that also register as {@link TypesRegistry#addTypeFactory(String, org.sablo.specification.property.types.IPropertyTypeFactory)}
 * can choose to write an array of two elements when {@link IPropertyWithClientSideConversions#writeClientSideTypeName(PropertyDescription)} is called where the first element will be
 * the factory name and the second element will be the factory's param. For example for a custom object you might return something like ["o","tab"] - where 'o' means it's a custom object
 * and "tab" says the name it has in .spec.<br/><br/>
 *
 * Note that an IPropertyWithClientSideConversions can decide if a particular property of that type actually needs to use client side conversions or not by returning true or false (and writing
 * or not in 'w') in {@link IPropertyWithClientSideConversions#writeClientSideTypeName(JSONWriter, String, PropertyDescription)}.
 *
 * @author acostescu
 */
public interface IPropertyWithClientSideConversions<T> extends IPropertyType<T>
{

	/**
	 * Write the type (that browser needs for client side conversions) to be sent client-side.<br/>
	 *
	 * It can write the type name for simple types like "date" that needs client side-conversions.
	 * For types that use type factories (see this class' description) it should write an array of two elements - the factory name and the param.
	 *
	 * @param keyToAddTo as some property types might decide in some situations to not send anything at all to client (based on .spec configuration) and in some to do so, the key is not automatically
	 * written to the writer before this method gets called; so it is mandatory to write this given key to "w" before writing the actual value using {@link JSONUtils#addKeyIfPresent(JSONWriter, String)}; properties
	 * that decide to write nothing at all can then just return false leaving "w" untouched.
	 * @param pd the PropertyDescription for this property type.
	 *
	 * @return true if a client side type name is needed for this property (and has been written to w), false otherwise.
	 */
	boolean writeClientSideTypeName(JSONWriter w, String keyToAddTo, PropertyDescription pd);

}