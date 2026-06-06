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

package org.sablo.websocket;

import org.json.JSONStringer;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * An {@link IToJSONWriter} that does not need to implement the {@link #checkForAndWriteAnyUnexpectedRemainingChanges(org.json.JSONStringer, String, org.sablo.websocket.utils.JSONUtils.IToJSONConverter, org.sablo.websocket.utils.DataConversion)}.<br/>
 * That means that it will never have unexpected remaining changes after {@link #writeJSONContent(org.json.JSONWriter, String, org.sablo.websocket.utils.JSONUtils.IToJSONConverter, org.sablo.websocket.utils.DataConversion)} was called.
 *
 * @author acostescu
 */
public abstract class SimpleToJSONWriter<ContextT> implements IToJSONWriter<ContextT>
{

	@Override
	public boolean checkForAndWriteAnyUnexpectedRemainingChanges(JSONStringer w, String string, IToJSONConverter<IBrowserConverterContext> converter)
	{
		return false;
	}

}
