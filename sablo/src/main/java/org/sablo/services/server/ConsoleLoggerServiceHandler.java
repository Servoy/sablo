/*
 * Copyright (C) 2018 Servoy BV
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

package org.sablo.services.server;

import org.json.JSONObject;
import org.sablo.websocket.IServerService;
import org.sablo.websocket.IWebsocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lvostinar
 *
 */
public class ConsoleLoggerServiceHandler implements IServerService
{
	public static final Logger log = LoggerFactory.getLogger("org.sablo.BrowserConsole");

	private final IWebsocketSession session;

	private String msg = null;

	public ConsoleLoggerServiceHandler(IWebsocketSession session)
	{
		this.session = session;
	}

	@Override
	public Object executeMethod(String methodName, JSONObject args) throws Exception
	{
		String message = getInformationMessage(args.optString("message"));
		switch (methodName)
		{
			case "error" :
			{
				log.error(message);
				break;
			}

			case "warn" :
			{
				if (msg == null || !msg.equals(message))
				{
					msg = message;
					log.warn(message);
				}
				break;
			}

			case "info" :
			{
				log.info(message);
				break;
			}
			case "debug" :
			{
				log.debug(message);
				break;
			}
			default :
				log.warn("Method not implemented: '" + methodName + "'");
		}
		return null;
	}

	protected String getInformationMessage(String message)
	{
		return message;
	}

	protected IWebsocketSession getSession()
	{
		return session;
	}
}
