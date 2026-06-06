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

package org.sablo.websocket;

import org.json.JSONException;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Interface that helps with writing to a JSON writer in a nested way.
 *
 * @author acostescu
 */
public interface IToJSONWriter<ContextT>
{

	/**
	 * Writes some JSON content.
	 * @param keyInParent a key (can be null in which case it should be ignored) that must be appended to 'w' initially if this method call writes content to it. If the method returns false, nothing should be written to the writer...
	 */
	boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<ContextT> converter) throws JSONException;

	/**
	 * This method is called after {@link #writeJSONContent(JSONWriter, String, IToJSONConverter, DataConversion)} above to check if this to-json-writer still
	 * would like to write changes. Most of the times this would just do noting. It is useful though for situations where code in {@link #writeJSONContent(JSONWriter, String, IToJSONConverter, DataConversion)}
	 * ends up generating more content/changes that need to be written.<br/><br/>
	 *
	 * For example when this writer writes component or service changes to json, normally we already have written changes in {@link #writeJSONContent(JSONWriter, String, IToJSONConverter, DataConversion)} called before
	 * so there should be none anymore; if we still have changes after that it means that the toJSON of some property triggered new changes somewhere
	 * and we want to write those again as well.
	 * @param clientDataConversions see {@link #writeJSONContent(JSONWriter, String, IToJSONConverter, DataConversion)}
	 * @param converter see {@link #writeJSONContent(JSONWriter, String, IToJSONConverter, DataConversion)}
	 * @param keyInParent see {@link #writeJSONContent(JSONWriter, String, IToJSONConverter, DataConversion)}
	 * @param w see {@link #writeJSONContent(JSONWriter, String, IToJSONConverter, DataConversion)}
	 *
	 * @return true if there were unexpected changes (and they were written); false if nothing more needed to be written.
	 */
	boolean checkForAndWriteAnyUnexpectedRemainingChanges(JSONStringer w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter);

}
