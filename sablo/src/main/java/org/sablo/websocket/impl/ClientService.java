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

	public ClientService(String serviceName, WebObjectSpecification spec)
	{
		super(serviceName, spec);
	}

	@Override
	public String getScriptingName()
	{
		return getSpecification() != null ? getSpecification().getScriptingName() : getName();
	}

	@Override
	public Object executeServiceCall(String functionName, Object[] arguments) throws IOException
	{
		WebObjectFunctionDefinition apiFunction = specification.getApiFunction(functionName);

		Object retValue = CurrentWindow.get().executeServiceCall(this, functionName, arguments, apiFunction, new IToJSONWriter<IBrowserConverterContext>()
		{

			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
				DataConversion clientDataConversions) throws JSONException
			{
				TypedData<Map<String, Object>> serviceChanges = getAndClearChanges();
				if (serviceChanges.content != null && serviceChanges.content.size() > 0)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);

					w.object().key("services").object().key(getScriptingName()).object();
					clientDataConversions.pushNode("services").pushNode(getScriptingName());

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
			if (specification != null)
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
		CurrentWindow.get().executeAsyncServiceCall(this, functionName, arguments, getParameterTypes(functionName));
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

	/**
	 * Transform service names like testpackage-myTestService into testPackageMyTestService - as latter is how getServiceScope gets called (generated code) from service client js,
	 * and former is how auto-add-watches code knows the name (from the WebObjectSpecification)...
	 */
	public static String convertToJSName(String name)
	{
		// this should do the same as websocket.ts #scriptifyServiceNameIfNeeded()
		int index = name.indexOf('-');
		while (index != -1 && name.length() > index + 1)
		{
			name = name.substring(0, index) + Character.toUpperCase(name.charAt(index + 1)) + name.substring(index + 2);
			index = name.indexOf('-');
		}
		return name;
	}

//	/**
//	 * Guess-transforms service script names like testPackageMyTestService into testpackage-myTestService or testpackage-MyTestService. (example inspired from what new service action would generate)
//	 * This reverse transformation is ambiguous, so it can be used for guessing, not to be depended on. It's not a full reverse of {@link #convertToJSName(String)}.
//	 *
//	 * It can't know if the char after the dash was upper or lower when converting to script name; it cannot also know how many dashes there were...
//	 *
//	 * @return an array of possible initial service names based on the given scripting name. It has one or more items in it.
//	 */
//	public static String[] convertFromJSNameByGuessing(String jsName)
//	{
//		List<String> possibleServiceNames = new ArrayList<>();
//		possibleServiceNames.add(jsName);
//
//		if (jsName != null)
//		{
//			for (int i = 0; i < jsName.length(); i++)
//			{
//				if (Character.isUpperCase(jsName.charAt(i)))
//				{
//					possibleServiceNames.add(jsName.substring(0, i) + "-" + jsName.substring(i));
//					possibleServiceNames.add(jsName.substring(0, i) + "-" + Character.toLowerCase(jsName.charAt(i)) + jsName.substring(i + 1));
//					break;
//				}
//			}
//		}
//
//		return possibleServiceNames.toArray(new String[possibleServiceNames.size()]);
//	}

	public static WebObjectSpecification getServiceDefinitionFromScriptingName(String scriptingName)
	{
		WebObjectSpecification serviceDefinition = WebServiceSpecProvider.getSpecProviderState().getWebComponentSpecification(scriptingName);
		if (serviceDefinition == null)
		{
			// just search in all available services - which one has that scripting name
			WebObjectSpecification[] allServiceSpecs = WebServiceSpecProvider.getSpecProviderState().getAllWebComponentSpecifications();
			if (allServiceSpecs != null)
			{
				for (WebObjectSpecification ss : allServiceSpecs)
				{
					if (scriptingName.equals(ss.getScriptingName()))
					{
						serviceDefinition = ss;
						break;
					}
				}
			}
		}
		return serviceDefinition;
	}

}
