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

import org.sablo.websocket.IClientService;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;

/**
 * Class to access client side types registry service (to give the client needed types/pushToServe/... info).
 *
 * @author acostescu
 */
public class TypesRegistryService
{
	public static final String TYPES_REGISTRY_SERVICE = "$typesRegistry";

	private final IClientService clientService;

	public TypesRegistryService(IClientService clientService)
	{
		this.clientService = clientService;
	}

	public void addComponentClientSideSpecs(EmbeddableJSONWriter toBeSent)
	{
		clientService.executeAsyncServiceCall("addComponentClientSideSpecs", new Object[] { toBeSent }, false);
	}

	public void setServiceClientSideSpecs(EmbeddableJSONWriter toBeSent)
	{
		clientService.executeAsyncServiceCall("setServiceClientSideSpecs", new Object[] { toBeSent });
	}

}
