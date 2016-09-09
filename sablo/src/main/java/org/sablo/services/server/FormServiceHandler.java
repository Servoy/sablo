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

package org.sablo.services.server;

import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IEventDispatchAwareServerService;
import org.sablo.websocket.IWebsocketEndpoint;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * formService implementation to handle methods at form level.
 *
 * @author rgansevles
 *
 */
public class FormServiceHandler implements IEventDispatchAwareServerService
{
	public static final Logger log = LoggerFactory.getLogger(FormServiceHandler.class.getCanonicalName());

	public static final FormServiceHandler INSTANCE = new FormServiceHandler();

	/**
	 * In order not to generate a deadlock between potential sync service call to client (that waits on client for form to get it's initial data
	 * and occupies the event dispatch thread on the server) and a initial form data request (service call to server) that wants to execute on the
	 * event dispatch thread, these initial form data requests will have a higher execution level then {@link IWebsocketEndpoint#EVENT_LEVEL_SYNC_API_CALL}.
	 */
	public static final int EVENT_LEVEL_INITIAL_FORM_DATA_REQUEST = 1000;

	protected FormServiceHandler()
	{
	}

	@Override
	public Object executeMethod(String methodName, JSONObject args) throws Exception
	{
		switch (methodName)
		{
			case "requestData" :
			{
				return requestData(args.optString("formname"));
			}

			case "dataPush" :
			{
				dataPush(args);
				break;
			}

			case "executeEvent" :
			{
				dataPush(args);
				return executeEvent(args);
			}

			case "getCurrentFormUrl" :
			{
				return CurrentWindow.get().getCurrentFormUrl();
			}

			case "setCurrentFormUrl" :
			{
				CurrentWindow.get().setCurrentFormUrl(args.optString("url"));
				break;
			}

			default :
				log.warn("Method not implemented: '" + methodName + "'");
		}

		return null;
	}


	/**
	 * @param args
	 * @throws Exception
	 */
	protected Object executeEvent(JSONObject obj) throws Exception
	{
		String formName = obj.getString("formname");

		Container form = CurrentWindow.get().getForm(formName);
		if (form == null)
		{
			log.warn("executeEvent for unknown form '" + formName + "'");
			return null;
		}

		String beanName = obj.optString("beanname");
		WebComponent webComponent = form.getComponent(beanName);
		if (webComponent == null)
		{
			log.warn("executeEvent for unknown bean '" + beanName + "' on form '" + formName + "'");
			return null;
		}

		JSONArray jsargs = obj.getJSONArray("args");
		String eventType = obj.getString("event");
		Object[] args = new Object[jsargs == null ? 0 : jsargs.length()];
		for (int i = 0; jsargs != null && i < jsargs.length(); i++)
		{
			args[i] = jsargs.get(i);
		}

		return webComponent.executeEvent(eventType, args);
	}

	protected JSONString requestData(String formName) throws JSONException
	{
		Container form = CurrentWindow.get().getForm(formName);
		if (form == null)
		{
			log.warn("Data requested from unknown form '" + formName + "'");
			return null;
		}

		EmbeddableJSONWriter initialFormDataWriter = new EmbeddableJSONWriter();

		initialFormDataWriter.array().object();
		boolean dataWasWritten = form.writeAllComponentsProperties(initialFormDataWriter, getInitialRequestDataConverter());
		initialFormDataWriter.endObject().endArray();

		if (!dataWasWritten) initialFormDataWriter = null;

		return initialFormDataWriter;
	}

	protected IToJSONConverter<IBrowserConverterContext> getInitialRequestDataConverter()
	{
		return FullValueToJSONConverter.INSTANCE;
	}

	protected void dataPush(JSONObject obj) throws JSONException
	{
		JSONObject changes = obj.getJSONObject("changes");
		if (changes.length() > 0)
		{
			String formName = obj.getString("formname");

			Container form = CurrentWindow.get().getForm(formName);
			if (form == null)
			{
				log.warn("dataPush for unknown form '" + formName + "'");
				return;
			}

			String beanName = obj.optString("beanname");

			JSONObject oldvalues = obj.optJSONObject("oldvalues");

			WebComponent webComponent = beanName.length() > 0 ? form.getComponent(beanName) : (WebComponent)form;
			Iterator<String> keys = changes.keys();
			while (keys.hasNext())
			{
				String key = keys.next();
				if (oldvalues != null)
				{
					Object oldValue = oldvalues.opt(key);
					Object currentValue = webComponent.getProperty(key);
					PropertyDescription propertyDesc = webComponent.getSpecification().getProperty(key);
					oldValue = propertyDesc != null
						? JSONUtils.fromJSON(null, oldValue, propertyDesc, new BrowserConverterContext(webComponent, PushToServerEnum.allow), null) : null; // Is allow here ok?
					if (oldValue != null && currentValue != null && !oldValue.equals(currentValue))
					{
						if (!(oldValue instanceof Number && currentValue instanceof Number &&
							((Number)oldValue).doubleValue() == ((Number)currentValue).doubleValue()))
						{
							log.trace("skipping setting key " + key + " to the the value " + changes.get(key) +
								" because the the current value in the component " + currentValue + " is not equals to old value the browser has " + oldValue);
							continue;
						}
					}
				}
				webComponent.putBrowserProperty(key, changes.get(key));
			}
		}
	}

	@Override
	public int getMethodEventThreadLevel(String methodName, JSONObject arguments, int dontCareLevel)
	{
		return "requestData".equals(methodName) ? EVENT_LEVEL_INITIAL_FORM_DATA_REQUEST : dontCareLevel;
	}

}
