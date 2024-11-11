/*
 * Copyright (C) 2024 Servoy BV
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
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;

/**
 * Calls from client to server side service handler can return this.<br/>
 * If this is returned then the service handler call resolve will not be sent to client right away, but later.<br/><br/>
 *
 * A new event is then added to the event thread with {@link #getEventLevelForPostponedReturn()} event level. When that executes, the {@link #getValueToReturn()} is sent to client to be resolved there.
 *
 * @author acostescu
 */
public interface IDelayedReturnValue
{

	int getEventLevelForPostponedReturn();

	/**
	 * Gives the value to returned to client.<br/><br/>
	 *
	 * IMPORTANT: the return value should be a ready-to-send-to-client (JSON) value or a value that only needs to go through {@link JSONUtils#defaultToJSONValue(org.sablo.websocket.utils.JSONUtils.IToJSONConverter, org.json.JSONWriter, String, Object, org.sablo.specification.PropertyDescription, org.sablo.websocket.utils.DataConversion, Object)}
	 * via the {@link FullValueToJSONConverter}. So it has to be some primitive, or something that only needs to undergo default conversion to client,  or, if a
	 * specific conversion to client is needed, then this method needs to apply that conversion to the returned value and return a {@link JSONString} / {@link EmbeddableJSONWriter} (with true given to it's constructor) of the result of the conversion instead.
	 */
	Object getValueToReturn();

}
