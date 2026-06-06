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
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;

/**
 * A property type that is able to send back an forth granular updates.
 * It would probably have a large content that can only slightly change, in which case there's no point in sending the whole content via toJSON() all the time.<br/><br/>
 *
 * These property types will probably define a JSON protocol of their own and use it in all 3 conversion methods: {@link #fromJSON(Object, Object, IBrowserConverterContext)}, {@link #toJSON(JSONWriter, String, Object, DataConversion)} and {@link #changesToJSON(JSONWriter, String, Object, DataConversion, boolean)}.
 *
 * @author acostescu
 * @param JT java class type to and from which JSON conversions take place.
 */
public interface ISupportsGranularUpdates<JT> extends IPropertyConverterForBrowser<JT>
{

	/**
	 * It writes property value changes as JSON that will be sent to the browser for existing property value.<br/><br/>
	 *
	 * For advanced/complex types this method can write custom JSON based protocol data - for example granular updates to be interpreted client side to update
	 * the value that was already previously set there. The inverse method for granular updates to be used is {@link #fromJSON(Object, Object, IBrowserConverterContext)}.
	 *
	 * @param writer the JSON writer to write JSON converted data to.
	 * @param key if this value will be part of a JSONObject then key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 * @param object the value to convert to JSON.
	 * @param propertyDescription the description of this property.
	 * @return the writer for cascaded usage.
	 * @throws JSONException if a JSON exception happens.
	 */
	JSONWriter changesToJSON(JSONWriter writer, String key, JT sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException;

}