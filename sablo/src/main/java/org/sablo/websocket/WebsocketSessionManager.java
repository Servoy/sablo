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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Websocket user session management
 * @author jblok, rgansevles
 */
public class WebsocketSessionManager
{
	private static final Logger log = LoggerFactory.getLogger(WebsocketSessionManager.class.getCanonicalName());

	private final static Map<String, IWebsocketSessionFactory> websocketSessionFactories = new HashMap<>();

	//maps form uuid to session
	private final static Map<String, IWebsocketSession> wsSessions = new HashMap<>();

	public static void addSession(IWebsocketSession wsSession)
	{
		synchronized (wsSessions)
		{
			wsSessions.put(wsSession.getUuid(), wsSession);
		}
	}

	public static IWebsocketSession removeSession(String uuid)
	{
		synchronized (wsSessions)
		{
			return wsSessions.remove(uuid);
		}
	}

	public static IWebsocketSession getSession(String endpointType, String prevUuid)
	{
		try
		{
			return getOrCreateSession(endpointType, prevUuid, false);
		}
		catch (Exception e)
		{
			log.error("exception calling getSession (not create) should not happen", e);
		}
		return null;
	}

	/**
	 * This method only throws an exception if the creation of the client fails (so the create boolean is true)
	 *
	 * @param endpointType
	 * @param prevUuid
	 * @param create
	 * @return
	 * @throws Exception
	 */
	static IWebsocketSession getOrCreateSession(String endpointType, String prevUuid, boolean create) throws Exception
	{
		String uuid = prevUuid;
		IWebsocketSession wsSession = null;
		synchronized (wsSessions)
		{
			if (uuid != null && uuid.length() > 0)
			{
				wsSession = wsSessions.get(uuid);
			}
			else
			{
				uuid = UUID.randomUUID().toString();
			}
			if (wsSession == null || !wsSession.isValid())
			{
				wsSessions.remove(uuid);
				wsSession = null;
				if (create && websocketSessionFactories.containsKey(endpointType))
				{
					wsSession = websocketSessionFactories.get(endpointType).createSession(uuid);
				}
				if (wsSession != null)
				{
					wsSessions.put(uuid, wsSession);
				}
			}
		}
		return wsSession;
	}

	/**
	 * @param endpointType
	 * @param uuid
	 */
	static void closeInactiveSessions()
	{
		synchronized (wsSessions)
		{
			Iterator<IWebsocketSession> sessions = wsSessions.values().iterator();
			while (sessions.hasNext())
			{
				IWebsocketSession session = sessions.next();
				if (session.checkForWindowActivity())
				{
					sessions.remove();
					session.closeSession();
				}
			}
		}
	}

	public static void setWebsocketSessionFactory(String endpointType, IWebsocketSessionFactory factory)
	{
		websocketSessionFactories.put(endpointType, factory);
	}

	public static IWebsocketSessionFactory getWebsocketSessionFactory(String endpointType)
	{
		return websocketSessionFactories.get(endpointType);
	}

}
