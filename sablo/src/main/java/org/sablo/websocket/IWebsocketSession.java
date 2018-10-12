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

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.services.client.SabloService;
import org.sablo.websocket.impl.ClientService;

/**
 * Interface for classes handling a websocket user session.
 * @author rgansevles
 */
public interface IWebsocketSession
{

	/**
	 * call done right after creating the session, so that it can fully initialize it.
	 *
	 * @throws Exception
	 */
	public abstract void init() throws Exception;

	/**
	 * Returns the event dispatcher, that should be a separate thread that processes all the events.
	 *
	 * @return
	 */
	IEventDispatcher getEventDispatcher();

	/**
	 * Can it still be used?
	 */
	boolean isValid();

	/**
	 * Called when a new connection is started (also on reconnect)
	 * @param argument
	 */
	public void onOpen(Map<String, List<String>> requestParams);

	String getUuid();

	void startHandlingEvent();

	void stopHandlingEvent();

	/**
	 * Called when all windows are expired, session is removed from WebsocketSessionManager
	 */

	void sessionExpired();

	/**
	 * Cleanup, close all windows.
	 */
	void dispose();

	/**
	 * Handle an incoming message.
	 * @param obj
	 */
	// TODO: remove this, all when messages are done via service calls
	void handleMessage(JSONObject obj);

	/**
	 * Register server side service
	 * @param name
	 * @param service handler
	 */
	void registerServerService(String name, IServerService service);

	/**
	 * Returns a server side service for that name.
	 * @param name
	 * @return
	 */
	IServerService getServerService(String name);

	IClientService getClientService(String name);

	/**
	 * @see ClientService#convertToJSName(String)
	 */
	IClientService getClientServiceByScriptingName(String scriptingName);

	Collection<IClientService> getServices();

	/**
	 * Get the window with give idm, when it does not exist, create a new window based on the widow name.
	 *
	 * @param windowId
	 * @param windowName
	 * @return
	 */
	IWindow getOrCreateWindow(String windowId, String windowName);

	IWindow getActiveWindow(String windowName);

	Collection< ? extends IWindow> getWindows();

	void updateLastAccessed(IWindow window);

	boolean checkForWindowActivity();

	SabloService getSabloService();

	Locale getLocale();

	/**
	 * Get the window timeout for this sessions in sec.
	 */
	long getWindowTimeout();

	/**
	 * Override the window timeout for this sessions in sec.
	 */
	void setSessionWindowTimeout(Long sessionWindowTimeout);

	/**
	 * gets the last ping time of all active windows.
	 * @return
	 */
	public long getLastPingTime();

	/**
	 * should return false if the session manager should test by sending Pongs to the client
	 * and test the last ping time to close a client if not get a ping time
	 *
	 * @return boolean To test the session or not
	 */
	public boolean shouldTest();

	/**
	 * @param window
	 * @return
	 */
	public abstract IMessageLogger getMessageLogger(IWindow window);
}
