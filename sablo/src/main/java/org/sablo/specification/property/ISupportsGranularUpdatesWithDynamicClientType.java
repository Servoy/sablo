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

/**
 * Similar to {@link IPropertyConverterForBrowserWithDynamicClientType} but it is able to also give changes with dynamic types.
 * There is currently no class that needs this (is both ISupportsGranularUpdates and needs dynamic types) but the interface is there to keep the code clean. Don't know
 * if in the future classes will need to implement this...
 *
 * @author acostescu
 * @param JT java class type to and from which JSON conversions take place.
 */
public interface ISupportsGranularUpdatesWithDynamicClientType<JT> extends ISupportsGranularUpdates<JT>, IPropertyConverterForBrowserWithDynamicClientType<JT>
{

	/**
	 * Similar to {@link #toJSONWithDynamicClientSideType(JSONWriter, String, Object, PropertyDescription, IBrowserConverterContext)} but for changes, not full value.
	 * Please check the javadoc of that method.
	 */
	JSONString changesToJSONWithDynamicClientSideType(JSONWriter writer, JT sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException;


}