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

/**
 * Calls from client to server side service handler can return this.<br/>
 * If this is returned then the service handler call resolve will not be sent back to client right away, but later.<br/><br/>
 *
 * A new event is then added to the event thread with {@link #getEventLevelForPostponedReturn()} event level. When that executes, the {@link #getValueToReturn()} is sent to client to be resolved there.
 *
 * @author acostescu
 */
public class DelayedReturnValue implements IDelayedReturnValue
{

	private final Object returnValue;
	private final int eventLevelForPostponedReturn;

	public DelayedReturnValue(Object returnValue, int eventLevelForPostponedReturn)
	{
		this.returnValue = returnValue;
		this.eventLevelForPostponedReturn = eventLevelForPostponedReturn;
	}

	public int getEventLevelForPostponedReturn()
	{
		return eventLevelForPostponedReturn;
	}

	public Object getValueToReturn()
	{
		return returnValue;
	}

}