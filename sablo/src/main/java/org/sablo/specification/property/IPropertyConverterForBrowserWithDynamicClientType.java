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

import org.json.JSONException;
import org.json.JSONString;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.ClientSideSpecState;
import org.sablo.websocket.utils.JSONUtils;

/**
 * A property type that implements this interface needs to/from json conversions server-side when communicating with the browser
 * but it also can require random client-side conversions (for incomming data from server). For example a type sending random data types
 * might want to sometimes send no client side conversion (for numbers, booleans, strings) but send a 'date' client side conversions when the data it contains is a Date. <br/><br/>
 *
 * So sometimes client-side needs to perform some conversions but not always the same type (so this conversions types are not known initially are not available client side via the {@link ClientSideSpecState} mechanism).<br/><br/>
 *
 * IMPORTANT:
 * <ul>
 * <li>if for this type {@link #toJSON(JSONWriter, String, Object, PropertyDescription, Object)} is called it will have to write both the type and the value using {@link JSONUtils#writeConvertedValueWithClientType(JSONWriter, String, String, java.util.concurrent.Callable)}. That means that the caller does not handle client side types separately in a special way.
 * <li>if for this type {@link #toJSONWithDynamicClientSideType(JSONWriter, String, Object, PropertyDescription, IBrowserConverterContext)} is called it will just write the value and return the client side type to be used (can be null). This means that the caller has special logic for sending the client side type for this property's value. This can be used to optimize for example large foundset viewports with the same type.</li>
 * </ul>
 *
 * @author acostescu
 * @param JT java class type to and from which JSON conversions take place.
 */
public interface IPropertyConverterForBrowserWithDynamicClientType<JT> extends IPropertyConverterForBrowser<JT>
{

	/**
	 * See interface javadoc above for an explanation of how this method should be/is used.
	 * Also see {@link #toJSON(JSONWriter, String, Object, PropertyDescription, Object)} which does almost the same thing with the differences mentioned in the interface description above.
	 *
	 * @param writer same as for {@link #toJSON(JSONWriter, String, Object, PropertyDescription, Object)}
	 * @param sabloValue same as for {@link #toJSON(JSONWriter, String, Object, PropertyDescription, Object)}
	 * @param propertyDescription same as for {@link #toJSON(JSONWriter, String, Object, PropertyDescription, Object)}
	 * @param dataConverterContext same as for {@link #toJSON(JSONWriter, String, Object, PropertyDescription, Object)}
	 * @return the client side type (usually it would be just the type name, but it could be other constructs that a {@link IPropertyWithClientSideConversions#writeClientSideTypeName(JSONWriter, String, PropertyDescription)} can return) to be used when converting the value once it reached the browser. It's a JSONString (a string representing actual JSON syntax) not a string because we want to be able to include it in JSON easily even if it not actually a String value.
	 * @throws JSONException
	 */
	JSONString toJSONWithDynamicClientSideType(JSONWriter writer, JT sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException;


}