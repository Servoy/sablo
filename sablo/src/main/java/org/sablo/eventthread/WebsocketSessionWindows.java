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
import java.util.List;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.IFunctionParameters;
import org.sablo.specification.WebObjectApiFunctionDefinition;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.ClientToServerCallReturnValue;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.IToJSONWriter;
import org.sablo.websocket.IWebsocketEndpoint;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.IWindow;
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
	public void setEndpoint(IWebsocketEndpoint endpoint)
	{
		// should not be called for WebsocketSessionWindows, this window just maps calls to all windows for the session
		throw new IllegalStateException("setEndpoint on WebsocketSessionWindows");
	}

	@Override
	public IWebsocketEndpoint getEndpoint()
	{
		// just return the fist windows endpoint
		for (IWindow window : session.getWindows())
		{
			return window.getEndpoint();
		}
		throw new IllegalStateException("no endpoint found for the WebsocketSessionWindows, session has no windows");
	}

	@Override
	public long getLastPingTime()
	{
		long lastPingTime = 0;
		for (IWindow window : session.getWindows())
		{
			lastPingTime = Math.max(lastPingTime, window.getLastPingTime());
		}
		return lastPingTime;
	}

	@Override
	public int getNextMessageNumber()
	{
		// should not be called for WebsocketSessionWindows, this window just maps calls to all windows for the session
		throw new IllegalStateException("getNextMessageNumber in WebsocketSessionWindows");
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
	public int getNr()
	{
		return -1;
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
	public void unregisterContainer(Container container)
	{
		for (IWindow window : session.getWindows())
		{
			window.unregisterContainer(container);
		}
	}

	@Override
	public void onOpen(java.util.Map<String, List<String>> requestParams)
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
	public void executeAsyncServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes)
	{
		for (IWindow window : session.getWindows())
		{
			window.executeAsyncServiceCall(clientService, functionName, arguments, argumentTypes);
		}
	}

	@Override
	public void executeAsyncNowServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes,
		boolean sendOtherPendingAsyncCallsAsWell)
	{
		for (IWindow window : session.getWindows())
		{
			window.executeAsyncNowServiceCall(clientService, functionName, arguments, argumentTypes, sendOtherPendingAsyncCallsAsWell);
		}
	}

	@Override
	public Object executeServiceCall(IClientService clientService, String functionName, Object[] arguments, WebObjectApiFunctionDefinition apiFunction,
		IToJSONWriter<IBrowserConverterContext> pendingChangesWriter, boolean blockEventProcessing) throws IOException
	{
		// always just return the first none null value.
		Object retValue = null;
		for (IWindow window : session.getWindows())
		{
			Object val = window.executeServiceCall(clientService, functionName, arguments, apiFunction, pendingChangesWriter, blockEventProcessing);
			retValue = retValue != null ? retValue : val;
		}
		return retValue;
	}

	@Override
	public Object invokeApi(WebComponent receiver, WebObjectApiFunctionDefinition apiFunction, Object[] arguments)
	{
		Object retValue = null;
		for (IWindow window : session.getWindows())
		{
			if (retValue == null) retValue = window.invokeApi(receiver, apiFunction, arguments);
			else window.invokeApi(receiver, apiFunction, arguments);
		}
		return retValue;
	}

	@Override
	public boolean hasEndpoint()
	{
		return true;
	}

	@Override
	public boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
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
	public void dispose()
	{
		for (IWindow window : session.getWindows())
		{
			window.dispose();
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

	@Override
	public void setClientToServerCallReturnValueForChanges(ClientToServerCallReturnValue clientToServerCallReturnValue)
	{
		// ignore
	}
}
