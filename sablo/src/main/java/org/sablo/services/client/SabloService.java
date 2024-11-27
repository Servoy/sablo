/*
 * Copyright (C) 2015 Servoy BV
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

package org.sablo.services.client;

import java.io.IOException;

import org.sablo.specification.FunctionParameters;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.PropertyDescriptionBuilder;
import org.sablo.specification.property.types.BooleanPropertyType;
import org.sablo.specification.property.types.IntPropertyType;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IClientService;

/**
 * Class to access sablo builtin server-service methods.
 *
 * @author rgansevles
 *
 */
public class SabloService
{
	public static final String SABLO_SERVICE = "$sabloService";

	private static final PropertyDescription defidPD = new PropertyDescriptionBuilder().withName("defid").withType(IntPropertyType.INSTANCE).build();
	private static final PropertyDescription successPD = new PropertyDescriptionBuilder().withName("success").withType(BooleanPropertyType.INSTANCE).build();

	private final IClientService clientService;

	public SabloService(IClientService clientService)
	{
		this.clientService = clientService;
	}

	public void setCurrentFormUrl(String currentFormUrl)
	{
		clientService.executeAsyncServiceCall("setCurrentFormUrl", new Object[] { currentFormUrl });
	}

	public void openWindowInClient(String url, String winname, String specs, String replace) throws IOException
	{
		clientService.executeServiceCall("windowOpen", new Object[] { url, winname, specs, replace });
	}

	public void resolveDeferedEvent(int defid, boolean success, Object argument, PropertyDescription argumentPD)
	{
		FunctionParameters paramTypes = null;
		if (argumentPD != null)
		{
			paramTypes = new FunctionParameters(3);
			paramTypes.add(defidPD);
			paramTypes.add(argumentPD);
			paramTypes.add(successPD);
		}
		CurrentWindow.get().executeAsyncServiceCall(clientService, "resolveDeferedEvent",
			new Object[] { Integer.valueOf(defid), argument, Boolean.valueOf(success) }, paramTypes);
	}

	/**
	 * Useful for sync calls to client when done from onShow of the form. It helps client-side code quickly decide if it
	 * should wait for the needed form + component to get shown or there is no point in waiting and slowing down execution.<br/><br/>
	 *
	 * As we try to show the client form only after everything is ready from server (even form's onShow handler - if any - got executed),
	 * that means that sync calls from the onShow can be sent before the command from server to show a form on client (that can execute to to
	 * higher event prio even if a sync call suspends the event thread). So in this situation we want the client sync call to wait for the
	 * form to show client-side and the execute the sync call; but if we are not expecting the form to show and the form is not shown on client
	 * already, then client-side code can decide right away to error out (TiNG) or load the needed form in a hidden div (NG1) directly.<br/><br/>
	 *
	 * Make sure to ALWAYS call it first with true (when a show/switch form is confirmed to go ahead to client after the onShow will execute) and
	 * afterwards, when the form show command is sent to the client, call it with with false; no exception!
	 */
	@SuppressWarnings("nls")
	public void setExpectFormToShowOnClient(boolean expectAFormToShow)
	{
		CurrentWindow.get().executeAsyncServiceCall(clientService, "expectFormToShowOnClient", new Object[] { Boolean.valueOf(expectAFormToShow) }, null);
	}

}
