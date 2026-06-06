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

import java.util.HashMap;

import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;

/**
 * Class that can send needed client side type and pushToServer information to browser.
 *
 * @author acostescu
 */
public class ClientSideSpecState
{

	private final static Object DUMMY_O = new Object(); // just a dummy value for componentsWhosNeededClientSpecsWereAlreadySentToClient in cases where we are only interested in keys - so that we don't have lots of Object instances

	// component handlers/return/param types of api calls or model property types that need conversions in browser to/from websocket need to
	// be sent to browser so that it knows what to do; but they are sent only when needed (when a component is used in this window the first time, it sends all it's types over)
	// it only keeps the full component names that were already sent (as keys in the map); the value is just an object that we don't care about (it could be a set of form names which are shown in the window if we'd want to also remove type info from client if containers having certain components are no longer there, but for now we avoid doing that in case those components will be reused in containers that will be showing in the future; the number of custom components available in the system is limited anyway)
	private final HashMap<String, Object> componentsWhosClientSpecsWereAlreadySentToClient = new HashMap<>(3);

	protected IWindow window;

	public ClientSideSpecState(IWindow window)
	{
		this.window = window;
	}

	/**
	 * Sends all types with client-side conversions from all services to the browser window.
	 */
	public void sendAllServiceClientSideSpecs()
	{
		EmbeddableJSONWriter clSideSpecs = WebServiceSpecProvider.getInstance().getClientSideSpecs();
		if (clSideSpecs != null)
		{
			if (window.getSession() != null)
			{
				window.getSession().getTypesRegistryService().setServiceClientSideSpecs(clSideSpecs);
			}
		}
	}

	/**
	 * Sends all types with client-side conversions from all component types found on this form - but only if they were not sent before.
	 */
	public void handleNewContainerToBeSentToClient(Container container)
	{
		// as a note - container.getComponents() in case of Servoy will also already contain all simple form component component child components (not the ones in list form component component)
		boolean hasClientSideSpecs = false;
		EmbeddableJSONWriter toBeSent = new EmbeddableJSONWriter();
		toBeSent.object(); // keys are spec names, values are objects so: { compNameFromSpec: { /* see comment from ClientSideTypeCache.getClientSideTypesFor() */ } , ... }
		for (WebComponent component : container.getComponents())
		{
			WebObjectSpecification componentSpec = component.getSpecification();
			// TODO is .getSpecification().getName() enough or should we also include .getSpecification().getPackageName() here?
			// usually (or always?) components contain in their name packageName-... anyway
			String componentType = componentSpec.getName();
			if (componentsWhosClientSpecsWereAlreadySentToClient.put(componentType, DUMMY_O) == null)
			{
				// we didn't yet send client-side property types for this component to the browser window; do so now; it is added to the map now
				EmbeddableJSONWriter clSideSpecForThisComponent = WebComponentSpecProvider.getInstance().getClientSideTypeCache().getClientSideSpecFor(
					componentSpec);
				if (clSideSpecForThisComponent != null)
				{
					toBeSent.key(componentType).value(clSideSpecForThisComponent);
					hasClientSideSpecs = true;
				}
			}

			if (component instanceof Container) handleNewContainerToBeSentToClient((Container)component); // for components that have child components through component prop. type
		}
		toBeSent.endObject();

		if (hasClientSideSpecs)
		{
			if (window.getSession() != null)
			{
				window.getSession().getTypesRegistryService().addComponentClientSideSpecs(toBeSent);
			}
		}

	}

	public void dispose()
	{
		componentsWhosClientSpecsWereAlreadySentToClient.clear();
	}

	public void handleFreshBrowserWindowConnected()
	{
		// clear all form and component client types; these will be loaded (again if it's a refresh) anyway later - and sent as needed
		componentsWhosClientSpecsWereAlreadySentToClient.clear();
		sendAllServiceClientSideSpecs(); // we send all service types with client side conversions because a service could get called client side at any time, even if it was not yet used before on the server
		// TODO if globally defined custom object types become useful for clients in the future - see org.sablo.specification.WebSpecReader.readGloballyDefinedTypes(List<Package>)
		// then we should send here to the client also all such globally defined custom object types
	}

}
