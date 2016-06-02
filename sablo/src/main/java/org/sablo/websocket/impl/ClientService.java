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
package org.sablo.websocket.impl;

import java.io.IOException;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectFunctionDefinition;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.IToJSONWriter;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * implementation of {@link IClientService}
 *
 * @author jcompagner
 */
public class ClientService extends BaseWebObject implements IClientService
{

	private static final Logger log = LoggerFactory.getLogger(ClientService.class.getCanonicalName());
	private String jsName = null;

	public ClientService(String serviceName, WebObjectSpecification spec)
	{
		super(serviceName, spec);
		jsName = convertToJSName(serviceName);
	}


	@Override
	public Object executeServiceCall(String functionName, Object[] arguments) throws IOException
	{
		WebObjectSpecification spec = WebServiceSpecProvider.getInstance().getWebServiceSpecification(name);
		WebObjectFunctionDefinition apiFunction = null;
		if (spec != null)
		{
			apiFunction = spec.getApiFunction(functionName);
		}

		Object retValue = CurrentWindow.get().executeServiceCall(jsName, functionName, arguments, apiFunction, new IToJSONWriter<IBrowserConverterContext>()
		{

			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
				DataConversion clientDataConversions) throws JSONException
			{
				TypedData<Map<String, Object>> serviceChanges = getAndClearChanges();
				if (serviceChanges.content != null && serviceChanges.content.size() > 0)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);

					w.object().key("services").object().key(getName()).object();
					clientDataConversions.pushNode("services").pushNode(getName());

					writeProperties(converter, w, serviceChanges.content, serviceChanges.contentType, clientDataConversions);

					clientDataConversions.popNode().popNode();
					w.endObject().endObject().endObject();

					return true;
				}

				return false;
			}
		}, apiFunction != null ? apiFunction.getBlockEventProcessing() : true);

		if (retValue != null)
		{
			if (spec != null)
			{
				if (apiFunction != null && apiFunction.getReturnType() != null)
				{
					try
					{
						return JSONUtils.fromJSONUnwrapped(null, retValue, apiFunction.getReturnType(),
							new BrowserConverterContext(this, PushToServerEnum.allow), null);
					}
					catch (JSONException e)
					{
						log.error("Error interpreting return value (wrong type ?):", e); //$NON-NLS-1$
						return null;
					}
				}
			}
		}
		return retValue;
	}

	public void executeAsyncServiceCall(String functionName, Object[] arguments)
	{
		CurrentWindow.get().executeAsyncServiceCall(jsName, functionName, arguments, getParameterTypes(functionName));
	}

	protected PropertyDescription getParameterTypes(String functionName)
	{
		// we have a list of properties available; create a suitable wrapper PropertyDescription that has the
		// param indexes as child PropertyDescriptions
		PropertyDescription parameterTypes = null;
		WebObjectFunctionDefinition apiFunc = specification.getApiFunction(functionName);
		if (apiFunc != null)
		{
			parameterTypes = BaseWebObject.getParameterTypes(apiFunc);
		}
		return parameterTypes;
	}

	public static String convertToJSName(String name)
	{
		int index = name.indexOf('-');
		while (index != -1 && name.length() > index + 2)
		{
			name = name.substring(0, index) + Character.toUpperCase(name.charAt(index + 1)) + name.substring(index + 2);
			index = name.indexOf('-');
		}
		return name;
	}

}
