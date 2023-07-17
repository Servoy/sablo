/*
 * Copyright (C) 2022 Servoy BV
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

import org.json.JSONString;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * @author acostescu
 */
public class ClientToServerCallReturnValue
{

	public final Object retValOrErrorMessage;
	public final boolean success;
	public Object cmsgid;

	/**
	 * @param retValOrErrorMessage the value to return to client in case of success == true; or the error string if success = false; return value can be any
	 *        value that can be written directly via {@link JSONUtils#defaultToJSONValue(IToJSONConverter, JSONWriter, String, Object, PropertyDescription, DataConversion, Object)},
	 *        so if it needs any special toJSON conversion, that should have already be done (and result turned into a {@link JSONString}).
	 */
	public ClientToServerCallReturnValue(Object retValOrErrorMessage, boolean success, Object cmsgid)
	{
		this.retValOrErrorMessage = retValOrErrorMessage;
		this.success = success;
		this.cmsgid = cmsgid;
	}

}
