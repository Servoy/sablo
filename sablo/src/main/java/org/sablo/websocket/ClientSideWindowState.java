/*
 * Copyright (C) 2019 Servoy BV
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

import java.util.Set;
import java.util.WeakHashMap;

import org.sablo.Container;

/**
 * This class holds/manages a certain type of info about what is present in the browser tab associated with a {@link BaseWindow}. We can assume that:<br/>
 * <ul>
 * 		<li>when a browser tab reconnects after loosing connection for a while and finds the server side window instance is still there we can assume that this information is still present in the browser;</li>
 * 		<li>when a browser tab gets refreshed (F5) all this information can be considered as lost on client (as it's a fresh tab basically).</li>
 * </ul>
 *
 * This info can fall into 2 categories:
 * <ul>
 * 		<li>when a browser tab gets refreshed (F5) some things need to be re-sent right away (for example main form url, so that the browser can start reloading everything, types with client side conversions for all services that might get accessed client side any-time);</li>
 * 		<li>when a browser tab gets refreshed (F5) the information is gone and we can clear it server-side as well as the refreshed tab will reload all that anyway. (for example loaded forms, form/component client side conversions, used containers)</li>
 * </ul>
 *
 * @author acostescu
 */
public class ClientSideWindowState
{

	private String currentFormUrl;
	private final WeakHashMap<Container, Object> usedContainers = new WeakHashMap<>(3); // set of used container browser-side - in order to collect all changes and send them when needed
	private final ClientSideSpecState clientSideTypesWithConversionsState;
	protected final IWindow window;

	public ClientSideWindowState(IWindow window)
	{
		this(window, new ClientSideSpecState(window));
	}

	public ClientSideWindowState(IWindow window, ClientSideSpecState clientSideTypesState)
	{
		this.window = window;
		clientSideTypesWithConversionsState = clientSideTypesState;
	}

	protected void setCurrentFormUrl(String newFormUrl)
	{
		this.currentFormUrl = newFormUrl;
		sendCurrentFormUrl();
	}

	public void sendCurrentFormUrl()
	{
		if (currentFormUrl != null && window.getSession() != null)
		{
			window.getSession().getSabloService().setCurrentFormUrl(currentFormUrl);
		}
	}

	public String getCurrentFormUrl()
	{
		return currentFormUrl;
	}


	public Set<Container> getUsedContainers()
	{
		return usedContainers.keySet();
	}


	public void putUsedContainer(Container container)
	{
		usedContainers.put(container, new Object());
		clientSideTypesWithConversionsState.handleNewContainerToBeSentToClient(container);
	}

	public void removeUsedContainer(Container container)
	{
		usedContainers.remove(container);
		// clientSideTypesWithConversionsState.containerRemoved(container); // we could do this and remove types that are no longer used... but custom components are a limited number and future forms might want to reuse them
		// so it's not a memory leak; we just keep them there on client to not need to re-send them later if they are needed again; a browser refresh will clear them anyway
	}

	public void handleBrowserReconnected()
	{
		// on reconnect, browser form url, usedContainers as well as clientSideTypesState remain the same as before
		// so nothing to do here I suppose
	}


	public void handleFreshBrowserWindowConnected()
	{
		// browser refresh or new browser window
		// so we need to send back anything that is needed in the browser for this window state
		sendCurrentFormUrl();

		// if this is due to a refresh we need to clear the used containers as they will be loaded again anyway; a fresh window will have none anyway
		usedContainers.forEach((c, o) -> {
			c.clearRegisteredToWindow();
		});
		usedContainers.clear();

		clientSideTypesWithConversionsState.handleFreshBrowserWindowConnected();
	}

	public void dispose()
	{
		currentFormUrl = null;
		usedContainers.clear();
		clientSideTypesWithConversionsState.dispose();
	}

}
