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

import org.json.JSONObject;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;

/**
 * @author jcompagner
 */
public interface IServerService
{

	/**
	 * Execute a method requested from the browser client.
	 *
	 * @return IMPORTANT: the return value should be a ready-to-send-to-client (JSON) value, a value that only needs to go through {@link JSONUtils#defaultToJSONValue(org.sablo.websocket.utils.JSONUtils.IToJSONConverter, org.json.JSONWriter, String, Object, org.sablo.specification.PropertyDescription, org.sablo.websocket.utils.DataConversion, Object)}
	 * via the {@link FullValueToJSONConverter} or an instance of {@link ClientToServerCallReturnValue} if specific toJSON conversions are needed.
	 */
	public Object executeMethod(String methodName, JSONObject args) throws Exception;

}
