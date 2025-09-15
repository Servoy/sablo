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
import org.json.JSONString;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;

/**
 * @author jcompagner
 */
public interface IServerService
{

	/**
	 * Execute a method requested from the browser client.
	 *
	 * @return It can return two things:
	 *         <ul>
	 *             <li>an actual value to be sent back to client. IMPORTANT: the return value should be a ready-to-send-to-client (JSON) value or a value that only needs to go through {@link JSONUtils#defaultToJSONValue(org.sablo.websocket.utils.JSONUtils.IToJSONConverter, org.json.JSONWriter, String, Object, org.sablo.specification.PropertyDescription, org.sablo.websocket.utils.DataConversion, Object)}
	 *                 via the {@link FullValueToJSONConverter}. So it has to be some primitive, or something that only needs to undergo default conversion to client,  or, if a
	 *                 specific conversion to client is needed, then this method needs to apply that conversion to the returned value and return a {@link JSONString} / {@link EmbeddableJSONWriter} (with true given to it's constructor) of the result of the conversion instead.</li>
	 *             <li>a {@link IDelayedReturnValue} that will result in the response to the client getting postponed. That means that the system will post another event to the event thread with
	 *                 an eventLevel given by {@link IDelayedReturnValue#getEventLevelForPostponedReturn()}; when that executes, the system will send the return value {@link IDelayedReturnValue#getValueToReturn()} and resolve the call on client (see description above for what the return value should be!)</li>
	 *         </ul>
	 */
	public Object executeMethod(String methodName, JSONObject args) throws Exception;

}
