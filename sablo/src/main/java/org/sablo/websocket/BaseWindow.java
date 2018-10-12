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
import org.sablo.BaseWebObject;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectFunctionDefinition;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
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

	/**
	 *
	 */
	private static final String API_KEY_DELAY_UNTIL_FORM_LOADS = "delayUntilFormLoads"; //$NON-NLS-1$
	private static final String API_SERVER_ONLY_KEY_FORM_CONTAINER = "forServerOnly_formContainer"; //$NON-NLS-1$
	private static final String API_SERVER_ONLY_KEY_COMPONENT = "forServerOnly_component"; //$NON-NLS-1$
	private static final String API_SERVER_ONLY_KEY_ARG_TYPES = "forServerOnly_callTypes"; //$NON-NLS-1$
	private static final String API_SERVER_ONLY_KEY_SERVICE = "forServerOnly_service"; //$NON-NLS-1$
	private static final String API_KEY_NAME = "name"; //$NON-NLS-1$
	private static final String API_KEY_CALL = "call"; //$NON-NLS-1$
	private static final String API_KEY_ARGS = "args"; //$NON-NLS-1$
	private static final String API_KEY_FUNCTION_NAME = "api"; //$NON-NLS-1$
	private static final String API_KEY_COMPONENT_NAME = "bean"; //$NON-NLS-1$
	private static final String API_KEY_FORM_NAME = "form"; //$NON-NLS-1$
	private static final String API_PRE_DATA_SERVICE_CALL = "pre_data_service_call"; //$NON-NLS-1$

	private static final Logger log = LoggerFactory.getLogger(BaseWindow.class.getCanonicalName());

	private volatile IWebsocketEndpoint endpoint;
	private volatile int endpointRefcount = 0;

	private final IWebsocketSession session;
	private final String uuid;
	private final String name;

	private final AtomicInteger nextMessageId = new AtomicInteger(0);
	private final AtomicInteger lastSentMessage = new AtomicInteger(0);

	private final List<Map<String, ? >> serviceCalls = new ArrayList<>();
	private final List<Map<String, Object>> delayedOrAsyncApiCalls = new ArrayList<>();
	private final PropertyDescription serviceCallTypes = AggregatedPropertyType.newAggregatedProperty();

	private final WeakHashMap<Container, Object> usedContainers = new WeakHashMap<>(3); // set of used container in order to collect all changes

	private String currentFormUrl;

	public BaseWindow(IWebsocketSession session, String uuid, String name)
	{
		this.session = session;
		this.uuid = uuid;
		this.name = name;
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
	 * When we have an endpoint set which is being replaced, it may happen that the new endpoint is set before the old one is being cleared.
	 * For example:
	 *   1. setEndpoint(A) -> this.endpoint = A, endpointRefcount = 1
	 *   2. setEndpoint(B) -> this.endpoint = B, endpointRefcount = 2
	 *   3. setEndpoint(null) [A was closed] -> this.endpoint = B, endpointRefcount = 1
	 */
	@Override
	public void setEndpoint(IWebsocketEndpoint endpoint)
	{
		synchronized (this)
		{
			if (endpoint == null)
			{
				// endpoint was closed, only clear if this was the last one
				if (--endpointRefcount == 0)
				{
					this.endpoint = null;
				}
			}
			else
			{
				if (this.endpoint != null)
				{
					try
					{
						this.endpoint.sendText("p");
					}
					catch (IOException e)
					{
					}
				}
				endpointRefcount++;
				this.endpoint = endpoint;
			}
		}

		if (endpoint == null)
		{
			session.updateLastAccessed(this);
		}
	}

	@Override
	public Container getForm(String formName)
	{
		return null;
	}

	@Override
	public long getLastPingTime()
	{
		IWebsocketEndpoint ep = getEndpoint();
		if (ep != null) return ep.getLastPingTime();
		return 0;
	}

	public int getNextMessageNumber()
	{
		if (lastSentMessage.get() >= 1000)
		{
			lastSentMessage.set(0);
		}
		return lastSentMessage.incrementAndGet();
	}

	@Override
	public void onOpen(Map<String, List<String>> requestParams)
	{
		if (requestParams != null)
		{
			List<String> lastServerMessageNumberParameter = requestParams.get("lastServerMessageNumber");
			if (lastServerMessageNumberParameter != null && lastServerMessageNumberParameter.size() == 1)
			{
				String clientLastMessageReceived = lastServerMessageNumberParameter.get(0);
				if (!String.valueOf(lastSentMessage.get()).equals(clientLastMessageReceived))
				{
					// client is out-of-sync
					cancelSession("CLIENT-OUT-OF-SYNC");
				}

				// Client sent a lastServerMessageNumber, so this is a reconnect, no need to send the services etc.
				return;
			}
		}

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
				serviceData.put(service.getScriptingName(), sd.content);
			}
			if (sd.contentType != null) serviceDataTypes.putProperty(service.getScriptingName(), sd.contentType);
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
							String serviceName = service.getScriptingName();
							Map<String, Object> dataForThisService = serviceData.get(serviceName);
							if (dataForThisService != null && dataForThisService.size() > 0)
							{
								clientDataConversions.pushNode(serviceName);
								w.key(serviceName);
								w.object();
								// here converter is FullValueToJSONConverter; see below arg
								service.writeProperties(converter, null, w,
									new TypedData<Map<String, Object>>(dataForThisService, serviceDataTypes.getProperty(serviceName)), clientDataConversions);
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
	public final void dispose()
	{
		onDispose();
		IWebsocketEndpoint ep = getEndpoint();
		if (ep != null)
		{
			ep.closeSession(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Window disposed"));
		}
	}

	protected void onDispose()
	{
	}

	protected Container getFormContainer(WebComponent component)
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
		IWebsocketEndpoint ep = getEndpoint();
		if (ep != null)
		{
			ep.cancelSession(reason);
		}
	}

	@Override
	public void closeSession()
	{
		IWebsocketEndpoint ep = getEndpoint();
		if (ep != null)
		{
			ep.closeSession();
		}
	}

	@Override
	public void registerContainer(Container container)
	{
		usedContainers.put(container, new Object());
	}

	@Override
	public void unregisterContainer(Container container)
	{
		usedContainers.remove(container);
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
		if (sentText != null)
		{
			IWebsocketEndpoint ep = getEndpoint();
			if (ep == null)
			{
				throw new IOException("Endpoint was closed when trying to wait for a sync message"); //$NON-NLS-1$
			}
			return ep.waitResponse(messageId, sentText, blockEventProcessing);

		}
		return null;
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
		if (dataWriter == null && serviceCalls.size() == 0 && delayedOrAsyncApiCalls.size() == 0) return null;

		if (getEndpoint() == null)
		{
			throw new IOException("Endpoint was closed"); //$NON-NLS-1$
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
				}
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
				w.key("services");

				w.array();
				for (int i = 0; i < serviceCalls.size(); i++)
				{
					if (clientDataConversions != null) clientDataConversions.pushNode(String.valueOf(i));
					ClientService clientService = (ClientService)serviceCalls.get(i).remove(API_SERVER_ONLY_KEY_SERVICE);
					FullValueToJSONConverter.INSTANCE.toJSONValue(w, null, serviceCalls.get(i), serviceCallTypes.getProperty(String.valueOf(i)),
						clientDataConversions, new BrowserConverterContext(clientService, PushToServerEnum.allow));
					if (clientDataConversions != null) clientDataConversions.popNode();
				}
				w.endArray();

				clientDataConversions.popNode();
			}
			if (delayedOrAsyncApiCalls.size() > 0)
			{
				clientDataConversions.pushNode("calls");
				Iterator<Map<String, Object>> it = delayedOrAsyncApiCalls.iterator();
				boolean callObjectStarted = false;
				int callIdx = 0;
				while (it.hasNext())
				{
					Map<String, Object> delayedCall = it.next();
					WebComponent component = (WebComponent)delayedCall.get(API_SERVER_ONLY_KEY_COMPONENT);
					Container formContainer = (Container)delayedCall.get(API_SERVER_ONLY_KEY_FORM_CONTAINER);
					if (!((Boolean)delayedCall.get(API_KEY_DELAY_UNTIL_FORM_LOADS)).booleanValue() || isFormResolved(formContainer))
					{
						// so it is either async (so not 'delayUntilFormLoads') in which case it must execute anyway or it is 'delayUntilFormLoads' and the form is loaded/resolved so it can get executed on client
						hasContentToSend = true;

						// the following field(s) were just passed in the map in order to be used above (still on server side) - they are not meant to reach client
						delayedCall.remove(API_SERVER_ONLY_KEY_COMPONENT);
						delayedCall.remove(API_SERVER_ONLY_KEY_FORM_CONTAINER);
						// delayedCall.remove(API_KEY_DELAY_UNTIL_FORM_LOADS); we keep and do send this to client just in case form is no longer there for some reason when the call arrives - and it shouldn't try to force-load it on client

						it.remove();

						if (!callObjectStarted)
						{
							callObjectStarted = true;
							w.key("calls").array();
						}
						PropertyDescription callTypes = (PropertyDescription)delayedCall.remove(API_SERVER_ONLY_KEY_ARG_TYPES);
						w.object().key(API_KEY_CALL).object();
						clientDataConversions.pushNode(String.valueOf(callIdx));
						clientDataConversions.pushNode(API_KEY_CALL);
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
				sendMessageText(text);
				serviceCalls.clear();
			}

			return text;
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	/**
	 * Send the message text prepended with the message number.
	 *
	 * @param text
	 * @throws IOException
	 */
	private void sendMessageText(String text) throws IOException
	{
		IWebsocketEndpoint ep = getEndpoint();
		if (ep == null)
		{
			throw new IOException("Endpoint was closed"); //$NON-NLS-1$
		}
		ep.sendText(getNextMessageNumber(), text);
	}

	public void sendChanges() throws IOException
	{
		// TODO this should not send to the currently active end-point, but to each of all end-points their own changes...
		// so that any change from 1 end-point request ends up in all the needed/affected end points (depending on where the form is).
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
				changesFound = writeAllServicesChanges(w, "services", clientDataConversions) || changesFound;
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
	public Object executeServiceCall(IClientService clientService, String functionName, Object[] arguments, WebObjectFunctionDefinition apiFunction,
		IToJSONWriter<IBrowserConverterContext> pendingChangesWriter, boolean blockEventProcessing) throws IOException
	{
		PropertyDescription argumentTypes = (apiFunction != null ? BaseWebObject.getParameterTypes(apiFunction) : null);
		addServiceCall(clientService, functionName, arguments, argumentTypes);
		try
		{
			return sendSyncMessage(pendingChangesWriter, ChangesToJSONConverter.INSTANCE, blockEventProcessing); // will return response from last service call
		}
		catch (CancellationException e)
		{
			throw new RuntimeException("Cancelled while executing service call " + clientService.getName() + "." + functionName + "(...). Arguments: " +
				(arguments == null ? null : Arrays.asList(arguments)), e);
		}
		catch (TimeoutException e)
		{
			throw new RuntimeException("Timed out while executing service call " + clientService.getName() + "." + functionName + "(...). Arguments: " +
				(arguments == null ? null : Arrays.asList(arguments)), e);
		}
	}

	@Override
	public void executeAsyncServiceCall(IClientService clientService, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		addServiceCall(clientService, functionName, arguments, argumentTypes);
	}

	private void addServiceCall(IClientService clientService, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		// {"services":[{name:serviceName,call:functionName,args:argumentsArray}]}
		Map<String, Object> serviceCall = new HashMap<>();
		PropertyDescription typesOfThisCall = AggregatedPropertyType.newAggregatedProperty();
		serviceCall.put(API_SERVER_ONLY_KEY_SERVICE, clientService);
		serviceCall.put(API_KEY_NAME, clientService.getScriptingName());
		serviceCall.put(API_KEY_CALL, functionName);
		WebObjectFunctionDefinition handler = clientService.getSpecification().getApiFunction(functionName);
		if (handler != null && handler.isPreDataServiceCall()) serviceCall.put(API_PRE_DATA_SERVICE_CALL, Boolean.TRUE);
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

		serviceCall.put(API_KEY_ARGS, arguments);
		if (argumentTypes != null) typesOfThisCall.putProperty(API_KEY_ARGS, argumentTypes);
		serviceCalls.add(serviceCall);
		serviceCallTypes.putProperty(String.valueOf(serviceCalls.size() - 1), typesOfThisCall);
	}

	@Override
	public synchronized boolean hasEndpoint()
	{
		return endpoint != null && endpoint.hasSession();
	}

	/**
	 * @return the endpoint
	 */
	public synchronized IWebsocketEndpoint getEndpoint()
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
			if (fc.isChanged() && shouldSendChangesToClientWhenAvailable(fc))
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

	/**
	 * Can be overriden. If it returns true, it means that when the given container has changes it will send them to browser.
	 * If this returns false, no changes are sent for that container, even if it does have changes.
	 *
	 * @param formContainer the container that has changes
	 * @return see description.
	 */
	protected boolean shouldSendChangesToClientWhenAvailable(Container formContainer)
	{
		return formContainer.isVisible(); // subclasses may decide to override this and also send changes for hidden forms
	}

	public boolean writeAllServicesChanges(JSONWriter w, String keyInParent, DataConversion clientDataConversions) throws JSONException
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
				String childName = service.getScriptingName();
				w.key(childName).object();
				clientDataConversions.pushNode(childName);
				service.writeProperties(ChangesToJSONConverter.INSTANCE, FullValueToJSONConverter.INSTANCE, w, changes, clientDataConversions);
				clientDataConversions.popNode();
				w.endObject();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
	}

	public Object invokeApi(WebComponent receiver, WebObjectFunctionDefinition apiFunction, Object[] arguments, PropertyDescription argumentTypes)
	{
		return invokeApi(receiver, apiFunction, arguments, argumentTypes, null);
	}

	protected Object invokeApi(final WebComponent receiver, final WebObjectFunctionDefinition apiFunction, final Object[] arguments,
		final PropertyDescription argumentTypes, final Map<String, Object> callContributions)
	{
		// {"call":{"form":"product","bean":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}}
		boolean delayedCall = isDelayedApiCall(receiver, apiFunction);

		if (delayedCall || isAsyncApiCall(receiver, apiFunction))
		{
			Map<String, Object> call = getApiCallObject(receiver, apiFunction, arguments, argumentTypes, callContributions);
			addDelayedOrAsyncCall(apiFunction, call, receiver, delayedCall);
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
					writeAllComponentsChanges(w, "forms", converter, clientDataConversions); // converter here is ChangesToJSONConverter.INSTANCE (see below arg to 'sendSyncMessage')
					clientDataConversions.popNode();
					Map<String, Object> call = getApiCallObject(receiver, apiFunction, arguments, argumentTypes, callContributions);
					PropertyDescription callTypes = (PropertyDescription)call.remove(API_SERVER_ONLY_KEY_ARG_TYPES);
					w.key(API_KEY_CALL).object();
					clientDataConversions.pushNode(API_KEY_CALL);
					JSONUtils.writeData(FullValueToJSONConverter.INSTANCE, w, call, callTypes, clientDataConversions,
						new BrowserConverterContext(receiver, PushToServerEnum.allow));
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

	private Map<String, Object> getApiCallObject(final WebComponent receiver, final WebObjectFunctionDefinition apiFunction, final Object[] arguments,
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
		call.put(API_KEY_FORM_NAME, topContainer.getName());
		call.put(API_KEY_COMPONENT_NAME, receiver.getName());
		call.put(API_KEY_FUNCTION_NAME, apiFunction.getName());
		if (arguments != null && arguments.length > 0)
		{
			call.put(API_KEY_ARGS, arguments);
			if (argumentTypes != null) callTypes.putProperty(API_KEY_ARGS, argumentTypes);
		}
		call.put(API_SERVER_ONLY_KEY_ARG_TYPES, callTypes);
		return call;
	}

	protected void addDelayedOrAsyncCall(final WebObjectFunctionDefinition apiFunction, Map<String, Object> call, WebComponent component, boolean isDelayedCall)
	{
		if (isDelayedCall)
		{
			// just keep the needed information about this delayed call in there (not to be sent to client necessarily, but to be able to check if the form is available on client or not)
			call.put(API_SERVER_ONLY_KEY_COMPONENT, component);
			call.put(API_SERVER_ONLY_KEY_FORM_CONTAINER, getFormContainer(component));
			call.put(API_KEY_DELAY_UNTIL_FORM_LOADS, Boolean.valueOf(isDelayedCall));
		}

		if (apiFunction.shouldDiscardPreviouslyQueuedSimilarCalls())
		{
			// for example requestFocus uses that - so that only the last .requestFocus() actually executes (if the form is loaded)
			Iterator<Map<String, Object>> it = delayedOrAsyncApiCalls.iterator();
			while (it.hasNext())
			{
				Map<String, Object> delayedOrAsyncCall = it.next();
				if (apiFunction.getName().equals(delayedOrAsyncCall.get(API_KEY_FUNCTION_NAME)))
				{
					it.remove();
				}
			}
		}
		delayedOrAsyncApiCalls.add(call);
	}

	protected boolean hasPendingDelayedCalls(Container formContainer)
	{
		boolean hasPendingDelayedCalls = false;
		if (delayedOrAsyncApiCalls.size() > 0)
		{
			Iterator<Map<String, Object>> it = delayedOrAsyncApiCalls.iterator();
			while (it.hasNext())
			{
				Map<String, Object> delayedCall = it.next();
				if (((Boolean)delayedCall.get(API_KEY_DELAY_UNTIL_FORM_LOADS)).booleanValue() &&
					formContainer == (Container)delayedCall.get(API_SERVER_ONLY_KEY_FORM_CONTAINER))
				{
					hasPendingDelayedCalls = true;
					break;
				}
			}
		}
		return hasPendingDelayedCalls;
	}

	protected boolean isFormResolved(Container formContainer)
	{
		return true;
	}

	protected boolean isDelayedApiCall(WebComponent receiver, WebObjectFunctionDefinition apiFunction)
	{
		return apiFunction.getReturnType() == null && apiFunction.shouldDelayUntilFormLoads();
	}

	protected boolean isAsyncApiCall(WebComponent receiver, WebObjectFunctionDefinition apiFunction)
	{
		return apiFunction.getReturnType() == null && apiFunction.isAsync();
	}
}
