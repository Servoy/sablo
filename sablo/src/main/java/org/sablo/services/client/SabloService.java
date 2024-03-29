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

}
