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

package org.sablo.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.CloseReason;

import org.json.JSONException;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebComponentSpecification.PushToServerEnum;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.CustomVariableArgsType;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.impl.ClientService;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.ChangesToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A window is created for a websocket endpoint to communicate with the websocket session.
 * When the websocket connection a dropped and recrated (browser refresh), the window will be reused.
 *
 * @author jcompagner, rgansevles
 */

public class BaseWindow implements IWindow
{
	private static final Logger log = LoggerFactory.getLogger(BaseWindow.class.getCanonicalName());

	private IWebsocketEndpoint endpoint;
	private boolean endpointWasOverwriten = false;
	private IWebsocketSession session;
	private String uuid;
	private final String name;

	private final AtomicInteger nextMessageId = new AtomicInteger(0);

	private final List<Map<String, ? >> serviceCalls = new ArrayList<>();
	private final List<Map<String, Object>> delayedApiCalls = new ArrayList<>();
	private final PropertyDescription serviceCallTypes = AggregatedPropertyType.newAggregatedProperty();

	private final WeakHashMap<Container, Object> usedContainers = new WeakHashMap<>(3); // set of used container in order to collect all changes

	private String currentFormUrl;

	public BaseWindow(String name)
	{
		this.name = name;
	}

	@Override
	public void setUuid(String uuid)
	{
		this.uuid = uuid;
	}

	@Override
	public String getUuid()
	{
		return uuid;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public IWebsocketSession getSession()
	{
		return session;
	}

	/**
	 * @param session the session to set
	 */
	@Override
	public void setSession(IWebsocketSession session)
	{
		this.session = session;
	}

	@Override
	public void setEndpoint(IWebsocketEndpoint endpoint)
	{
		if (endpoint == null)
		{
			// endpoint was closed
			if (endpointWasOverwriten)
			{
				// endpoint was replaced before previous one was closed
				endpointWasOverwriten = false;
			}
			else
			{
				this.endpoint = null;
			}
			session.invalidateWindow(this); // decrements refcount
		}
		else
		{
			endpointWasOverwriten = this.endpoint != null;
			this.endpoint = endpoint;
			session.activateWindow(this); // increments refcount
		}
	}

	@Override
	public long getLastPingTime()
	{
		if (endpoint != null) return endpoint.getLastPingTime();
		return 0;
	}

	@Override
	public void onOpen()
	{
		// window was connected to new endpoint
		try
		{
			sendServices();
			sendWindowId();
			sendCurrentFormUrl();
		}
		catch (IOException e)
		{
			log.error("Error sending services/windowid to new endpoint", e);
		}
	}

	protected void sendServices() throws IOException
	{
		// send all the service data to the browser.
		final Map<String, Map<String, Object>> serviceData = new HashMap<>();
		final PropertyDescription serviceDataTypes = AggregatedPropertyType.newAggregatedProperty();

		final Collection<IClientService> services = getSession().getServices();
		for (IClientService service : services)
		{
			TypedData<Map<String, Object>> sd = service.getProperties();
			if (!sd.content.isEmpty())
			{
				serviceData.put(service.getName(), sd.content);
			}
			if (sd.contentType != null) serviceDataTypes.putProperty(service.getName(), sd.contentType);
		}

		if (serviceData.size() > 0)
		{
			// we send each service changes independently so that the IBrowserConverterContext instance is correct (it needs the property type of each service property to be correct)
			sendAsyncMessage(new IToJSONWriter<IBrowserConverterContext>()
			{

				@Override
				public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
					DataConversion clientDataConversions) throws JSONException
				{
					if (serviceData != null && serviceData.size() > 0)
					{
						JSONUtils.addKeyIfPresent(w, keyInParent);
						w.object().key("services").object();
						clientDataConversions.pushNode("services");

						for (IClientService service : services)
						{
							Map<String, Object> dataForThisService = serviceData.get(service.getName());
							String serviceName = service.getName();
							if (dataForThisService != null && dataForThisService.size() > 0)
							{
								clientDataConversions.pushNode(serviceName);
								w.key(serviceName);
								w.object();
								service.writeProperties(converter, w, dataForThisService, serviceDataTypes.getProperty(serviceName), clientDataConversions);
								w.endObject();
								clientDataConversions.popNode();
							}
						}

						w.endObject().endObject();
						clientDataConversions.popNode();

						return true;
					}
					return false;
				}
			}, FullValueToJSONConverter.INSTANCE);
		}
	}

	protected void sendWindowId() throws IOException
	{
		if (uuid == null)
		{
			throw new IllegalStateException("window uuid not set");
		}

		Map<String, String> msg = new HashMap<>();
		msg.put("sessionid", getSession().getUuid());
		msg.put("windowid", uuid);
		sendAsyncMessage(msg, null, FullValueToJSONConverter.INSTANCE);
	}

	@Override
	public void dispose()
	{
		onDispose();
		if (endpoint != null)
		{
			endpoint.closeSession(new CloseReason(CloseReason.CloseCodes.SERVICE_RESTART, "Window disposed because of ping timeout"));
		}
	}

	protected void onDispose()
	{
	}

	@Override
	public Container getForm(String formName)
	{
		return null;
	}

	/**
	 * @return the currentFormUrl
	 */
	public String getCurrentFormUrl()
	{
		return currentFormUrl;
	}

	/**
	 * @param currentFormUrl the currentFormUrl to set
	 */
	public void setCurrentFormUrl(String currentFormUrl)
	{
		this.currentFormUrl = currentFormUrl;
		sendCurrentFormUrl();
	}

	public void sendCurrentFormUrl()
	{
		if (currentFormUrl != null && getSession() != null)
		{
			getSession().getSabloService().setCurrentFormUrl(currentFormUrl);
		}
	}

	@Override
	public void cancelSession(String reason)
	{
		if (endpoint != null)
		{
			endpoint.cancelSession(reason);
		}
	}

	@Override
	public void closeSession()
	{
		if (endpoint != null)
		{
			endpoint.closeSession();
		}
	}

	@Override
	public void registerContainer(Container container)
	{
		usedContainers.put(container, new Object());
	}

	/**
	 * Sends a message to the client/browser, containing the given data (transformed into JSON based on give dataTypes).
	 *
	 * If there are any pending service calls those will be sent to the client/attached to the message as well.
	 *
	 * @param data the data to be sent to the client (converted to JSON format where needed).
	 * @param dataTypes description of the data structure; each key in "data" might have a corresponding child "dataTypes.getProperty(key)" who's type can be used for "to JSON" conversion.
	 * @param converter converter for values to json.
	 * @throws IOException when such an exception occurs.
	 */
	protected void sendAsyncMessage(final Map<String, ? > data, final PropertyDescription dataTypes, IToJSONConverter<IBrowserConverterContext> converter)
		throws IOException
	{
		sendAsyncMessage((data == null || data.size() == 0) ? null : new IToJSONWriter<IBrowserConverterContext>()
		{
			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converterParam,
				DataConversion clientDataConversions) throws JSONException
			{
				if (data != null && data.size() > 0)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					converterParam.toJSONValue(w, null, data, dataTypes, clientDataConversions, BrowserConverterContext.NULL_WEB_OBJECT_WITH_NO_PUSH_TO_SERVER);
					return true;
				}
				return false;
			}

		}, converter);
	}

	/**
	 * Sends a message to the client/browser, containing the given data (transformed into JSON based on give dataTypes).
	 *
	 * If there are any pending service calls those will be sent to the client/attached to the message as well.
	 *
	 * @param data the data to be sent to the client (converted to JSON format where needed).
	 * @param dataTypes description of the data structure; each key in "data" might have a corresponding child "dataTypes.getProperty(key)" who's type can be used for "to JSON" conversion.
	 * @param converter converter for values to json.
	 * @param blockEventProcessing if true then the event processing will be blocked until we get the expected response from the browser/client (or until a timeout expires).
	 * @return it will return whatever the client sends back as a response to this message.
	 * @throws IOException when such an exception occurs.
	 * @throws CancellationException if cancelled for some reason while waiting for response
	 * @throws TimeoutException if it timed out while waiting for a response value. This can happen if blockEventProcessing == true.
	 */
	protected Object sendSyncMessage(final Map<String, ? > data, final PropertyDescription dataTypes, IToJSONConverter<IBrowserConverterContext> converter,
		boolean blockEventProcessing, final ClientService service) throws IOException, CancellationException, TimeoutException
	{
		return sendSyncMessage((data == null || data.size() == 0) ? null : new IToJSONWriter<IBrowserConverterContext>()
		{
			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converterParam,
				DataConversion clientDataConversions) throws JSONException
			{
				if (data != null && data.size() > 0)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					converterParam.toJSONValue(w, null, data, dataTypes, clientDataConversions, new BrowserConverterContext(service, PushToServerEnum.allow));
					return true;
				}
				return false;
			}

		}, converter, blockEventProcessing);
	}

	/**
	 * Sends a message to the client/browser just like {@link #sendAsyncMessage(IToJSONWriter, IToJSONConverter)} does.
	 * But afterwards it waits for a response and return value from the client.
	 *
	 * @param blockEventProcessing if true then the event processing will be blocked until we get the expected resonse from the browser/client (or until a timeout expires).
	 * @return the value the client gave back.
	 * @throws CancellationException if cancelled for some reason while waiting for response
	 * @throws TimeoutException if it timed out while waiting for a response value. This can happen if blockEventProcessing == true.
	 */
	protected Object sendSyncMessage(IToJSONWriter<IBrowserConverterContext> dataWriter, IToJSONConverter<IBrowserConverterContext> converter,
		boolean blockEventProcessing) throws IOException, CancellationException, TimeoutException
	{
		Integer messageId = new Integer(nextMessageId.incrementAndGet());
		String sentText = sendMessageInternal(dataWriter, converter, messageId);
		return sentText != null ? endpoint.waitResponse(messageId, sentText, blockEventProcessing) : null;
	}

	/**
	 * Sends a message to the client/browser. The message will be written to dataWriter.
	 *
	 * If there are any pending service calls those will be sent to the client/attached to the message as well.
	 *
	 * @param dataWriter the writer where to write contents to send to client.
	 * @param converter converter for values to json.
	 * @throws IOException when such an exception occurs.
	 */
	protected void sendAsyncMessage(IToJSONWriter<IBrowserConverterContext> dataWriter, IToJSONConverter<IBrowserConverterContext> converter) throws IOException
	{
		sendMessageInternal(dataWriter, converter, null);
	}

	/**
	 * Returns the text that it really sent...
	 */
	protected String sendMessageInternal(IToJSONWriter<IBrowserConverterContext> dataWriter, IToJSONConverter<IBrowserConverterContext> converter,
		Integer smsgidOptional) throws IOException
	{
		if (dataWriter == null && serviceCalls.size() == 0 && delayedApiCalls.size() == 0) return null;

		if (endpoint == null)
		{
			throw new IOException("Endpoint was closed");
		}

		try
		{
			boolean hasContentToSend = false;
			JSONStringer w = new JSONStringer()
			{
				@Override
				public String toString()
				{
					return writer.toString();
				};
			};
			w.object();
			DataConversion clientDataConversions = new DataConversion();

			if (dataWriter != null)
			{
				clientDataConversions.pushNode("msg");
				hasContentToSend = dataWriter.writeJSONContent(w, "msg", converter, clientDataConversions) || hasContentToSend;
				clientDataConversions.popNode();
			}
			if (serviceCalls.size() > 0)
			{
				hasContentToSend = true;
				clientDataConversions.pushNode("services");
				FullValueToJSONConverter.INSTANCE.toJSONValue(w, "services", serviceCalls, serviceCallTypes, clientDataConversions,
					new BrowserConverterContext((ClientService)session.getClientService((String)serviceCalls.get(0).get("name")), PushToServerEnum.allow));
				clientDataConversions.popNode();
			}
			if (delayedApiCalls.size() > 0)
			{
				clientDataConversions.pushNode("calls");
				Iterator<Map<String, Object>> it = delayedApiCalls.iterator();
				boolean callObjectStarted = false;
				int callIdx = 0;
				while (it.hasNext())
				{
					Map<String, Object> delayedCall = it.next();
					WebComponent component = (WebComponent)delayedCall.get("component");
					if (formLoaded(component))
					{
						hasContentToSend = true;
						delayedCall.remove("component");
						it.remove();
						if (!callObjectStarted)
						{
							callObjectStarted = true;
							w.key("calls").array();
						}
						PropertyDescription callTypes = (PropertyDescription)delayedCall.remove("callTypes");
						w.object().key("call").object();
						clientDataConversions.pushNode(String.valueOf(callIdx));
						clientDataConversions.pushNode("call");
						JSONUtils.writeData(converter, w, delayedCall, callTypes, clientDataConversions,
							new BrowserConverterContext(component, PushToServerEnum.allow));
						clientDataConversions.popNode();
						clientDataConversions.popNode();
						callIdx++;
						w.endObject().endObject();
					}
				}
				clientDataConversions.popNode();
				if (callObjectStarted)
				{
					w.endArray();
				}
			}
			String text = null;

			if (hasContentToSend)
			{
				if (smsgidOptional != null)
				{
					w.key("smsgid").value(smsgidOptional);
				}
				JSONUtils.writeClientConversions(w, clientDataConversions);
				w.endObject();

				text = w.toString();
				endpoint.sendText(text);
				serviceCalls.clear();
			}

			return text;
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	public void sendChanges() throws IOException
	{
		// TODO this should not send to the currently active end-point, but to each of all end-points their own changes...
		// so that any change from 1 end-point request ends up in all the end points.
		sendAsyncMessage(new IToJSONWriter<IBrowserConverterContext>()
		{
			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
				DataConversion clientDataConversions) throws JSONException
			{
				JSONUtils.addKeyIfPresent(w, keyInParent);
				w.object();

				clientDataConversions.pushNode("forms");
				boolean changesFound = writeAllComponentsChanges(w, "forms", ChangesToJSONConverter.INSTANCE, clientDataConversions);
				clientDataConversions.popNode();

				clientDataConversions.pushNode("services");
				changesFound = writeAllServicesChanges(w, "services", ChangesToJSONConverter.INSTANCE, clientDataConversions) || changesFound;
				clientDataConversions.popNode();

				w.endObject();

				return changesFound;
			}
		}, ChangesToJSONConverter.INSTANCE);
	}


	public void flush() throws IOException
	{
		sendAsyncMessage(null, null, FullValueToJSONConverter.INSTANCE);
	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes,
		IToJSONWriter<IBrowserConverterContext> pendingChangesWriter, boolean blockEventProcessing) throws IOException
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
		try
		{
			return sendSyncMessage(pendingChangesWriter, ChangesToJSONConverter.INSTANCE, blockEventProcessing); // will return response from last service call
		}
		catch (CancellationException e)
		{
			throw new RuntimeException("Cancelled while executing service call " + serviceName + "." + functionName + "(...). Arguments: " +
				(arguments == null ? null : Arrays.asList(arguments)), e);
		}
		catch (TimeoutException e)
		{
			throw new RuntimeException("Timed out while executing service call " + serviceName + "." + functionName + "(...). Arguments: " +
				(arguments == null ? null : Arrays.asList(arguments)), e);
		}
	}

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
	}

	private void addServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		// {"services":[{name:serviceName,call:functionName,args:argumentsArray}]}
		Map<String, Object> serviceCall = new HashMap<>();
		PropertyDescription typesOfThisCall = AggregatedPropertyType.newAggregatedProperty();
		serviceCall.put("name", serviceName);
		serviceCall.put("call", functionName);
		if (argumentTypes != null && argumentTypes.getProperties().size() > 0)
		{
			int typesNumber = argumentTypes.getProperties().size();
			PropertyDescription pd = argumentTypes.getProperty(String.valueOf(typesNumber - 1));
			if (pd.getType() instanceof CustomVariableArgsType && arguments.length > typesNumber)
			{
				// handle variable args
				List<Object> varArgs = new ArrayList<Object>();
				for (int i = typesNumber - 1; i < arguments.length; i++)
				{
					varArgs.add(arguments[i]);
				}
				arguments[typesNumber - 1] = varArgs;
				arguments = Arrays.copyOf(arguments, typesNumber);
			}
		}

		serviceCall.put("args", arguments);
		if (argumentTypes != null) typesOfThisCall.putProperty("args", argumentTypes);
		serviceCalls.add(serviceCall);
		serviceCallTypes.putProperty(String.valueOf(serviceCalls.size() - 1), typesOfThisCall);
	}

	@Override
	public boolean hasEndpoint()
	{
		return endpoint != null;
	}

	/**
	 * @return the endpoint
	 */
	public IWebsocketEndpoint getEndpoint()
	{
		return endpoint;
	}

	@Override
	public synchronized boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
		DataConversion clientDataConversions) throws JSONException
	{
		boolean contentHasBeenWritten = false;

		for (Container fc : usedContainers.keySet())
		{
			if (fc.isVisible() && fc.isChanged())
			{
				if (!contentHasBeenWritten)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();
					contentHasBeenWritten = true;
				}
				String containerName = fc.getName();
				clientDataConversions.pushNode(containerName);
				fc.writeAllComponentsChanges(w, containerName, converter, clientDataConversions);
				clientDataConversions.popNode();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
	}

	public boolean writeAllServicesChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
		DataConversion clientDataConversions) throws JSONException
	{
		boolean contentHasBeenWritten = false;
		for (IClientService service : getSession().getServices().toArray(new IClientService[0])) // toArray is used here to try to avoid a ConcurrentModificationException while looping
		{
			TypedData<Map<String, Object>> changes = service.getAndClearChanges();
			if (changes.content.size() > 0)
			{
				if (!contentHasBeenWritten)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();
					contentHasBeenWritten = true;
				}
				String childName = service.getName();
				w.key(childName).object();
				clientDataConversions.pushNode(childName);
				service.writeProperties(converter, w, changes.content, changes.contentType, clientDataConversions);
				clientDataConversions.popNode();
				w.endObject();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
	}

	public Object invokeApi(WebComponent receiver, WebComponentApiDefinition apiFunction, Object[] arguments, PropertyDescription argumentTypes)
	{
		return invokeApi(receiver, apiFunction, arguments, argumentTypes, null);
	}

	protected Object invokeApi(final WebComponent receiver, final WebComponentApiDefinition apiFunction, final Object[] arguments,
		final PropertyDescription argumentTypes, final Map<String, Object> callContributions)
	{
		// {"call":{"form":"product","bean":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}}
		if (isDelayedApiCall(receiver, apiFunction))
		{
			Map<String, Object> call = getApiCallObject(receiver, apiFunction, arguments, argumentTypes, callContributions);
			call.put("component", receiver);
			addDelayedCall(apiFunction, call);
			return null;
		}
		try
		{
			Object ret = sendSyncMessage(new IToJSONWriter<IBrowserConverterContext>()
			{
				@Override
				public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
					DataConversion clientDataConversions) throws JSONException
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();

					clientDataConversions.pushNode("forms");
					writeAllComponentsChanges(w, "forms", converter, clientDataConversions);
					clientDataConversions.popNode();
					Map<String, Object> call = getApiCallObject(receiver, apiFunction, arguments, argumentTypes, callContributions);
					PropertyDescription callTypes = (PropertyDescription)call.remove("callTypes");
					w.key("call").object();
					clientDataConversions.pushNode("call");
					JSONUtils.writeData(converter, w, call, callTypes, clientDataConversions, new BrowserConverterContext(receiver, PushToServerEnum.allow));
					clientDataConversions.popNode();
					w.endObject();

					w.endObject();
					return true;
				}
			}, ChangesToJSONConverter.INSTANCE, apiFunction.getBlockEventProcessing());

			if (apiFunction.getReturnType() != null)
			{
				try
				{
					return JSONUtils.fromJSON(null, ret, apiFunction.getReturnType(), new BrowserConverterContext(receiver, PushToServerEnum.allow), null);
				}
				catch (Exception e)
				{
					log.error("Cannot parse api call return value JSON for: " + ret + " for api call: " + apiFunction, e);
				}
			}
		}
		catch (IOException e)
		{
			log.warn("IOException occurred", e);
		}
		catch (CancellationException e)
		{
			throw new RuntimeException(
				"Cancelled while invoking API: " + apiFunction + ". Arguments: " + (arguments == null ? null : Arrays.asList(arguments)) + ". On: " + receiver,
				e);
		}
		catch (TimeoutException e)
		{
			throw new RuntimeException(
				"Timed out while invoking API: " + apiFunction + ". Arguments: " + (arguments == null ? null : Arrays.asList(arguments)) + ". On: " + receiver,
				e);
		}

		return null;
	}

	private Map<String, Object> getApiCallObject(final WebComponent receiver, final WebComponentApiDefinition apiFunction, final Object[] arguments,
		final PropertyDescription argumentTypes, final Map<String, Object> callContributions)
	{
		Map<String, Object> call = new HashMap<>();
		PropertyDescription callTypes = AggregatedPropertyType.newAggregatedProperty();
		if (callContributions != null) call.putAll(callContributions);
		Container topContainer = receiver.getParent();
		while (topContainer != null && topContainer.getParent() != null)
		{
			topContainer = topContainer.getParent();
		}
		call.put("form", topContainer.getName());
		call.put("bean", receiver.getName());
		call.put("api", apiFunction.getName());
		if (arguments != null && arguments.length > 0)
		{
			call.put("args", arguments);
			if (argumentTypes != null) callTypes.putProperty("args", argumentTypes);
		}
		call.put("callTypes", callTypes);
		return call;
	}

	protected void addDelayedCall(final WebComponentApiDefinition apiFunction, Map<String, Object> call)
	{
		if (apiFunction.isGlobalExclusive())
		{
			Iterator<Map<String, Object>> it = delayedApiCalls.iterator();
			while (it.hasNext())
			{
				Map<String, Object> delayedCall = it.next();
				if (apiFunction.getName().equals(delayedCall.get("api")))
				{
					it.remove();
				}
			}
		}
		delayedApiCalls.add(call);
	}

	protected boolean formLoaded(WebComponent component)
	{
		return true;
	}

	protected boolean isDelayedApiCall(WebComponent receiver, WebComponentApiDefinition apiFunction)
	{
		return apiFunction.getReturnType() == null && apiFunction.isDelayUntilFormLoad();
	}
}
