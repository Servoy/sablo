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

package org.sablo.eventthread;

import java.io.IOException;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.websocket.IWebsocketEndpoint;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.IWindow;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * A {@link IWindow} implementation that redirects all the calls on it to the current registered,
 * {@link IWebsocketSession#getWindows()} windows.
 *
 * @author jcompagner, rgansevles
 *
 */
public class WebsocketSessionWindows implements IWindow
{

	// private static final Logger log = LoggerFactory.getLogger(WebsocketSessionWindows.class.getCanonicalName());


	private final IWebsocketSession session;

	/**
	 * @param session
	 */
	public WebsocketSessionWindows(IWebsocketSession session)
	{
		this.session = session;
	}

	@Override
	public Container getForm(String formName)
	{
		return null;
	}

	@Override
	public IWebsocketSession getSession()
	{
		return session;
	}

	@Override
	public void setSession(IWebsocketSession session)
	{
		// should not be called for WebsocketSessionWindows, this window just maps calls to all windows for the session
		throw new IllegalStateException("setSession on WebsocketSessionWindows");
	}

	@Override
	public void setEndpoint(IWebsocketEndpoint endpoint)
	{
		// should not be called for WebsocketSessionWindows, this window just maps calls to all windows for the session
		throw new IllegalStateException("setEndpoint on WebsocketSessionWindows");
	}

	@Override
	public String getCurrentFormUrl()
	{
		return null;
	}

	@Override
	public void setCurrentFormUrl(String currentFormUrl)
	{
		// ignore
	}

	@Override
	public String getUuid()
	{
		return null;
	}

	@Override
	public void setUuid(String uuid)
	{
		// ignore
	}

	@Override
	public String getName()
	{
		return null;
	}

	@Override
	public void registerContainer(Container container)
	{
		// ignore
	}

	@Override
	public void onOpen()
	{
		// ignore
	}

	@Override
	public void flush() throws IOException
	{
		for (IWindow window : session.getWindows())
		{
			window.flush();
		}
	}

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		for (IWindow window : session.getWindows())
		{
			window.executeAsyncServiceCall(serviceName, functionName, arguments, argumentTypes);
		}
	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes, Map<String, ? > changes,
		PropertyDescription changesTypes) throws IOException
	{
		for (IWindow window : session.getWindows())
		{
			window.executeServiceCall(serviceName, functionName, arguments, argumentTypes, changes, changesTypes);
		}
		return null;
	}

	@Override
	public Object invokeApi(WebComponent receiver, WebComponentApiDefinition apiFunction, Object[] arguments, PropertyDescription argumentTypes)
	{
		return null;
	}

	@Override
	public boolean hasEndpoint()
	{
		return true;
	}

	@Override
	public boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		return false;
	}

	@Override
	public void closeSession()
	{
		for (IWindow window : session.getWindows())
		{
			window.closeSession();
		}
	}

	@Override
	public void cancelSession(String reason)
	{
		for (IWindow window : session.getWindows())
		{
			window.cancelSession(reason);
		}
	}

	@Override
	public void destroy()
	{
		for (IWindow window : session.getWindows())
		{
			window.destroy();
		}
	}

	@Override
	public void sendChanges() throws IOException
	{
		for (IWindow window : session.getWindows())
		{
			window.sendChanges();
		}
	}
}