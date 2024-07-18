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

import static org.sablo.websocket.IWebsocketEndpoint.CLOSE_REASON_CLIENT_OUT_OF_SYNC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.CloseReason;

import org.json.JSONException;
import org.json.JSONString;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.IFunctionParameters;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.PropertyDescriptionBuilder;
import org.sablo.specification.WebObjectApiFunctionDefinition;
import org.sablo.specification.WebObjectFunctionDefinition;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.util.DebugFriendlyJSONStringer;
import org.sablo.websocket.impl.ClientService;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.ChangesToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A window is created for a websocket endpoint to communicate with the websocket session.
 * When the websocket connection is dropped and recreated (browser refresh), the window will be reused.
 *
 * @author jcompagner, rgansevles
 */
@SuppressWarnings("nls")
public class BaseWindow implements IWindow
{

	private static final String API_KEY_DELAY_UNTIL_FORM_LOADS = "delayUntilFormLoads"; //$NON-NLS-1$
	private static final String API_KEY_NAME = "name"; //$NON-NLS-1$
	private static final String API_KEY_CALL = "call"; //$NON-NLS-1$
	private static final String API_KEY_ARGS = "args"; //$NON-NLS-1$
	private static final String API_KEY_FUNCTION_NAME = "api"; //$NON-NLS-1$
	private static final String API_KEY_COMPONENT_NAME = "bean"; //$NON-NLS-1$
	private static final String API_KEY_FORM_NAME = "form"; //$NON-NLS-1$
	private static final String API_PRE_DATA_SERVICE_CALL = "pre_data_service_call"; //$NON-NLS-1$

	private static final String COMPONENT_CALLS = "componentApis";
	private static final String SERVICE_CALLS = "serviceApis";

	private static final String SERVICE_DATA = "services";

	// this system property is not publicly documented as normally toJSON should never generate new changes; it is there just in case a temporary increase is needed
	// until some unexpected property behavior can be corrected (warnings will be logged anyway if such a situation is detected)
	private static final int MAX_ALLOWED_TO_JSON_GENERATING_UNEXPECTED_CHANGE_ITERATIONS = Integer.parseInt(
		System.getProperty("sablo.conversions.to.client.matjguci", "5"));

	private static final Logger log = LoggerFactory.getLogger(BaseWindow.class.getCanonicalName());

	private volatile IWebsocketEndpoint endpoint;
	private volatile int endpointRefcount = 0;

	private final IWebsocketSession session;
	private final int windowNr;
	private final String name;

	private final AtomicInteger nextMessageId = new AtomicInteger(0);
	private final AtomicInteger lastSentMessage = new AtomicInteger(0);

	private final List<ServiceCall> serviceCalls = new ArrayList<>();
	private final List<ComponentCall> componentApiCalls = new ArrayList<>();

	private final ClientSideWindowState clientSideState = createClientSideWindowState();

	private ClientToServerCallReturnValue clientToServerCallReturnValue;

	public BaseWindow(IWebsocketSession session, int nr, String name)
	{
		this.session = session;
		this.windowNr = nr;
		this.name = name;
	}

	/**
	 * Gives the opportunity of creatin their own type of ClientSideWindowState to subclasses.
	 */
	protected ClientSideWindowState createClientSideWindowState()
	{
		return new ClientSideWindowState(this);
	}

	protected ClientSideWindowState getClientSideWindowState()
	{
		return clientSideState;
	}

	@Override
	public int getNr()
	{
		return windowNr;
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
		if (endpoint == null)
		{
			// endpoint was closed, only clear if this was the last one
			synchronized (this)
			{
				if (--endpointRefcount == 0)
				{
					this.endpoint = null;
				}
			}
		}
		else
		{
			IWebsocketEndpoint current = getEndpoint();
			if (current != null)
			{
				try
				{
					current.sendText("p");
				}
				catch (IOException e)
				{
				}
			}
			synchronized (this)
			{
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
		// dont use sync block (getEndpoint), this shouldn't block.
		IWebsocketEndpoint ep = endpoint;
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
				// so this is a RECONNECT because the client sent a lastServerMessageNumber; no need to send the services etc.
				String clientLastMessageReceived = lastServerMessageNumberParameter.get(0);
				if (!String.valueOf(lastSentMessage.get()).equals(clientLastMessageReceived))
				{
					// client is out-of-sync
					cancelSession(CLOSE_REASON_CLIENT_OUT_OF_SYNC);
				}

				clientSideState.handleBrowserReconnected();
				return;
			}
		}

		// this (server) window was connected to a fresh browser window (either new window of refreshed (F5) browser window)
		// so we need to send everything that is needed in the browser for this window
		try
		{
			sendWindowNr();
			clientSideState.handleFreshBrowserWindowConnected();
			sendUsedServicesCurrentState();
		}
		catch (IOException e)
		{
			log.error("Error sending services/windownr/current form/types to new endpoint (refresh or new browser tab)", e);
		}
	}

	protected void sendUsedServicesCurrentState() throws IOException
	{
		// send all previously touched services' service data to the browser
		// note: currently services are not instantiated in the session server side unless accessed - so they don't have initial/default model values for example
		final Map<String, Map<String, Object>> serviceData = new HashMap<>();
		final PropertyDescriptionBuilder serviceDataTypes = AggregatedPropertyType.newAggregatedPropertyBuilder();

		final Collection<IClientService> services = getSession().getServices();
		for (IClientService service : services)
		{
			TypedData<Map<String, Object>> sd = service.getProperties();
			if (!sd.content.isEmpty())
			{
				serviceData.put(service.getScriptingName(), sd.content);
			}
			if (sd.contentType != null) serviceDataTypes.withProperty(service.getScriptingName(), sd.contentType);
		}

		if (serviceData.size() > 0)
		{
			final PropertyDescription serviceDataTypesPD = serviceDataTypes.build();
			// we send each service changes independently so that the IBrowserConverterContext instance is correct (it needs the property type of each service property to be correct)
			sendAsyncMessage(new IToJSONWriter<IBrowserConverterContext>()
			{

				@Override
				public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
				{
					if (serviceData != null && serviceData.size() > 0)
					{
						JSONUtils.addKeyIfPresent(w, keyInParent);
						w.object().key(SERVICE_DATA).object();
						for (IClientService service : services)
						{
							String serviceName = service.getScriptingName();
							Map<String, Object> dataForThisService = serviceData.get(serviceName);
							if (dataForThisService != null && dataForThisService.size() > 0)
							{
								w.key(serviceName);
								w.object();
								// here converter is FullValueToJSONConverter; see below arg
								service.writeProperties(converter, null, w,
									new TypedData<Map<String, Object>>(dataForThisService, serviceDataTypesPD.getProperty(serviceName)));
								w.endObject();
							}
						}

						w.endObject().endObject();

						return true;
					}
					return false;
				}

				@Override
				public boolean checkForAndWriteAnyUnexpectedRemainingChanges(JSONStringer w, String keyInParent,
					IToJSONConverter<IBrowserConverterContext> converter)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();

					boolean changesFound = writeAllServicesChanges(w, SERVICE_DATA);

					w.endObject();

					return changesFound;

				}
			}, FullValueToJSONConverter.INSTANCE);
		}
	}

	protected void sendWindowNr() throws IOException
	{
		if (windowNr == -1)
		{
			throw new IllegalStateException("windowNr not set");
		}

		Map<String, String> msg = new HashMap<>();
		msg.put("clientnr", String.valueOf(getSession().getSessionKey().getClientnr()));
		msg.put("windownr", String.valueOf(windowNr));
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
		clientSideState.dispose();
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
		return clientSideState.getCurrentFormUrl();
	}

	/**
	 * @param currentFormUrl the currentFormUrl to set
	 */
	public void setCurrentFormUrl(String currentFormUrl)
	{
		clientSideState.setCurrentFormUrl(currentFormUrl);
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
	public synchronized void registerContainer(Container container)
	{
		getClientSideWindowState().putUsedContainer(container);
	}

	@Override
	public synchronized void unregisterContainer(Container container)
	{
		getClientSideWindowState().removeUsedContainer(container);
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
		sendAsyncMessage((data == null || data.size() == 0) ? null : new SimpleToJSONWriter<IBrowserConverterContext>()
		{
			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converterParam) throws JSONException
			{
				if (data != null && data.size() > 0)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					converterParam.toJSONValue(w, null, data, dataTypes, BrowserConverterContext.NULL_WEB_OBJECT_WITH_NO_PUSH_TO_SERVER);
					return true;
				}
				return false;
			}

		}, converter);
	}

//	/**
//	 * Sends a message to the client/browser, containing the given data (transformed into JSON based on given dataTypes).
//	 *
//	 * If there are any pending service calls those will be sent to the client/attached to the message as well.
//	 *
//	 * @param data the data to be sent to the client (converted to JSON format where needed).
//	 * @param dataTypes description of the data structure; each key in "data" might have a corresponding child "dataTypes.getProperty(key)" who's type can be used for "to JSON" conversion.
//	 * @param converter converter for values to json.
//	 * @param blockEventProcessing if true then the event processing will be blocked until we get the expected response from the browser/client (or until a timeout expires).
//	 * @return it will return whatever the client sends back as a response to this message.
//	 * @throws IOException when such an exception occurs.
//	 * @throws CancellationException if cancelled for some reason while waiting for response
//	 * @throws TimeoutException if it timed out while waiting for a response value. This can happen if blockEventProcessing == true.
//	 */
//	protected Object sendSyncMessage(final Map<String, ? > data, final PropertyDescription dataTypes, IToJSONConverter<IBrowserConverterContext> converter,
//		boolean blockEventProcessing, final ClientService service) throws IOException, CancellationException, TimeoutException
//	{
//		return sendSyncMessage((data == null || data.size() == 0) ? null : new SimpleToJSONWriter<IBrowserConverterContext>()
//		{
//			@Override
//			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converterParam) throws JSONException
//			{
//				if (data != null && data.size() > 0)
//				{
//					JSONUtils.addKeyIfPresent(w, keyInParent);
//					converterParam.toJSONValue(w, null, data, dataTypes, new BrowserConverterContext(service, PushToServerEnum.allow));
//					return true;
//				}
//				return false;
//			}
//
//		}, converter, blockEventProcessing);
//	}

	/**
	 * Sends a message to the client/browser just like {@link #sendAsyncMessage(IToJSONWriter, IToJSONConverter)} does.
	 * But afterwards it waits for a response and return value from the client.
	 *
	 * @param blockEventProcessing if true then the event processing will be blocked until we get the expected response from the browser/client (or until a timeout expires).
	 * @return the value the client gave back.
	 * @throws CancellationException if cancelled for some reason while waiting for response
	 * @throws TimeoutException if it timed out while waiting for a response value. This can happen if blockEventProcessing == true.
	 */
	protected Object sendSyncMessage(IToJSONWriter<IBrowserConverterContext> dataWriter, IToJSONConverter<IBrowserConverterContext> converter,
		boolean blockEventProcessing) throws IOException, CancellationException, TimeoutException
	{
		Integer messageId = new Integer(nextMessageId.incrementAndGet());
		if (sendMessageInternal(dataWriter, converter, messageId))
		{
			IWebsocketEndpoint ep = getEndpoint();
			if (ep == null)
			{
				throw new IOException("Endpoint was closed when trying to wait for a sync message"); //$NON-NLS-1$
			}
			return ep.waitResponse(messageId, blockEventProcessing);

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
	 * @return true if it really did send something through the websocket; can return false if nothing was written (no changes anyware, not even in given dataWriter)
	 */
	protected boolean sendMessageInternal(IToJSONWriter<IBrowserConverterContext> dataWriter, IToJSONConverter<IBrowserConverterContext> converter,
		Integer smsgidOptional) throws IOException
	{
		if (dataWriter == null && serviceCalls.size() == 0 && componentApiCalls.size() == 0 &&
			this.clientToServerCallReturnValue == null) return false;

		if (getEndpoint() == null)
		{
			throw new IOException("Endpoint was closed"); //$NON-NLS-1$
		}

		try
		{
			boolean hasContentToSend = false;
			boolean containsModelChanges = false;
			JSONStringer w = new DebugFriendlyJSONStringer();
			w.object();

			if (dataWriter != null)
			{
				containsModelChanges = dataWriter.writeJSONContent(w, "msg", converter);
				hasContentToSend = containsModelChanges || hasContentToSend;
			}

			if (serviceCalls.size() > 0)
			{
				hasContentToSend = true;
				w.key(SERVICE_CALLS);
				w.array();
				for (ServiceCall serviceCall : serviceCalls)
				{
					serviceCall.writeToJSON(w);
				}
				w.endArray();
			}
			if (componentApiCalls.size() > 0)
			{
				Iterator<ComponentCall> it = componentApiCalls.iterator();
				boolean callObjectStarted = false;
				boolean asyncAPIs = true;
				while (it.hasNext())
				{
					ComponentCall apiCall = it.next();
					if (!apiCall.async) asyncAPIs = false;
					if (!apiCall.delayUntilFormLoads || isFormResolved(apiCall.formContainer))
					{
						hasContentToSend = true;
						it.remove();

						if (!callObjectStarted)
						{
							callObjectStarted = true;
							w.key(COMPONENT_CALLS).array();
						}

						w.object();
						apiCall.writeToJSON(w);
						w.endObject();
					}
				}
				if (callObjectStarted)
				{
					w.endArray();
				}
				if (!containsModelChanges && callObjectStarted && asyncAPIs && !w.toString().contains("\"msg\":"))
				{
					// if we execute some async api and model is not already sent, we have to send it before execution, like we do for sync api
					w.key("msg");
					w.object();
					writeAllComponentsChanges(w, "forms", ChangesToJSONConverter.INSTANCE);
					w.endObject();
				}
			}

			if (clientToServerCallReturnValue != null)
			{
				hasContentToSend = true;
				w.key("cmsgid").value(clientToServerCallReturnValue.cmsgid);
				JSONUtils.defaultToJSONValue(FullValueToJSONConverter.INSTANCE, w, clientToServerCallReturnValue.success ? "ret" : "exception",
					clientToServerCallReturnValue.retValOrErrorMessage, // return value is already ready to be sent to client (via default conversion or it is already a JSONString (converted already), see IServerService.executeMethod javadoc)
					null, BrowserConverterContext.NULL_WEB_OBJECT_WITH_NO_PUSH_TO_SERVER);
			}

			if (hasContentToSend)
			{
				if (smsgidOptional != null)
				{
					w.key("smsgid").value(smsgidOptional);
				}
				w.endObject();

				sendMessageText(w.toString());
				serviceCalls.clear();
				clientToServerCallReturnValue = null;
			}

			hasContentToSend = checkForAndSendAnyUnexpectedRemainingChangesOfDataWriter(dataWriter, converter) || hasContentToSend;

			return hasContentToSend;
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	private boolean checkForAndSendAnyUnexpectedRemainingChangesOfDataWriter(IToJSONWriter<IBrowserConverterContext> dataWriter,
		IToJSONConverter<IBrowserConverterContext> converter) throws IOException
	{
		JSONStringer w;

		boolean hasContentToSend = false;
		if (dataWriter != null)
		{
			// code inside this if should normally not send/do anything (checkForAndWriteAnyUnexpectedRemainingChanges will normally return false)
			int i = MAX_ALLOWED_TO_JSON_GENERATING_UNEXPECTED_CHANGE_ITERATIONS;
			boolean keepGoing = true;
			while (keepGoing && i-- > 0)
			{
				w = new DebugFriendlyJSONStringer();

				w.object();
				keepGoing = dataWriter.checkForAndWriteAnyUnexpectedRemainingChanges(w, "msg", converter);
				if (keepGoing) // it did write stuff to JSON
				{
					if (i == MAX_ALLOWED_TO_JSON_GENERATING_UNEXPECTED_CHANGE_ITERATIONS - 1) log.debug(
						"A new change was registered on window while previous changes were being written; probably one property's toJSON ends up marking another property (in any form and any component in the same window) as dirty. This should be avoided. If you see this message without a stacktrace that explains it further it means that the properties that interact unexpectedly could be from different components/services.");
					// we do log above the warning but BaseWebObject, custom obj and array types also have code that will print stack traces as well when the unexpected interaction happens between child properties
					// if that does not appear in log and you can't debug, you could enable full websocket logging to see what exactly the messages being sent to client were (could help make an idea which properties from which base objects interact with other)

					hasContentToSend = true;
					w.endObject();

					sendMessageText(w.toString());
				}
			}
			if (keepGoing) log.error("The maximum number (" + MAX_ALLOWED_TO_JSON_GENERATING_UNEXPECTED_CHANGE_ITERATIONS +
				") of allowed iterations for toJSON generating new changes in already written properties was exceeded! Some changes will not be sent (at least until next event is handled)... Enabling debug logging on org.sablo.websocket.BaseWindow, org.sablo.specification.property.CustomJSONPropertyType and org.sablo.BaseWebObject could provide more information...");
		}
		return hasContentToSend;
	}

	/**
	 * This IGNORES any other pending API calls on services or components as well as any pending changes to be sent. It will not send those.
	 * Returns the text that it really sent...
	 */
	protected String sendOnlyThisMessageInternal(IToJSONWriter<IBrowserConverterContext> dataWriter, IToJSONConverter<IBrowserConverterContext> converter)
		throws IOException
	{
		if (dataWriter == null) return null;

		if (getEndpoint() == null)
		{
			throw new IOException("Endpoint was closed"); //$NON-NLS-1$
		}

		try
		{
			boolean hasContentToSend = false;
			JSONStringer w = new DebugFriendlyJSONStringer();
			w.object();

			if (dataWriter != null)
			{
				hasContentToSend = dataWriter.writeJSONContent(w, null, converter);
			}
			w.endObject();

			String text = null;

			if (hasContentToSend)
			{
				text = w.toString();
				sendMessageText(text);
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

	public void setClientToServerCallReturnValueForChanges(ClientToServerCallReturnValue clientToServerCallReturnValue)
	{
		this.clientToServerCallReturnValue = clientToServerCallReturnValue;
	}

	public void sendChanges() throws IOException
	{
		sendAsyncMessage(new IToJSONWriter<IBrowserConverterContext>()
		{
			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
			{
				JSONUtils.addKeyIfPresent(w, keyInParent);
				w.object();

				boolean changesFound = writeAllComponentsChanges(w, "forms", ChangesToJSONConverter.INSTANCE);

				changesFound = writeAllServicesChanges(w, SERVICE_DATA) || changesFound;

				w.endObject();

				return changesFound;
			}

			@Override
			public boolean checkForAndWriteAnyUnexpectedRemainingChanges(JSONStringer w, String keyInParent,
				IToJSONConverter<IBrowserConverterContext> converter)
			{
				return writeJSONContent(w, keyInParent, converter);
			}
		}, ChangesToJSONConverter.INSTANCE);
	}


	public void flush() throws IOException
	{
		sendAsyncMessage(null, null, FullValueToJSONConverter.INSTANCE);
	}

	@Override
	public Object executeServiceCall(IClientService clientService, String functionName, Object[] arguments, WebObjectApiFunctionDefinition apiFunction,
		IToJSONWriter<IBrowserConverterContext> pendingChangesWriter, boolean blockEventProcessing) throws IOException
	{
		IFunctionParameters argumentTypes = (apiFunction != null ? apiFunction.getParameters() : null);
		if (argumentTypes != null && argumentTypes.getDefinedArgsCount() == 0) argumentTypes = null;

		addServiceCall(clientService, functionName, arguments, argumentTypes);
		// the arguments giving here could be adjusted in the addServiceCalls to have a varargs List (suddenly arguments appear duplicate)
		return executeCall(clientService, functionName, arguments, pendingChangesWriter, blockEventProcessing, true);
	}

	@Override
	public void executeAsyncNowServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes)
	{
		try
		{
			sendOnlyThisMessageInternal(new SimpleToJSONWriter<IBrowserConverterContext>()
			{
				@Override
				public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
				{
					w.key(SERVICE_CALLS);

					w.array();
					createServiceCall(clientService, functionName, arguments, argumentTypes).writeToJSON(w);
					w.endArray();

					return true;
				}
			}, FullValueToJSONConverter.INSTANCE);
		}
		catch (IOException e)
		{
			log.warn("IOException occurred when trying to call async-now api call '" + functionName + "'. Arguments: " +
				(arguments == null ? null : Arrays.asList(arguments)) + ". On service " + clientService, e);
		}
	}

	private Object executeCall(Object webObjectForToStringInCaseOfErrors, String functionNameInCaseOfErrors, Object[] argumentsInCaseOfErrors,
		IToJSONWriter<IBrowserConverterContext> pendingChangesWriter, boolean blockEventProcessing, boolean retry) throws IOException
	{
		try
		{
			return sendSyncMessage(pendingChangesWriter, ChangesToJSONConverter.INSTANCE, blockEventProcessing); // will return response from last service call
		}
		catch (CancellationException e)
		{
			if (!retry)
			{
				throw new CancellationException(
					"Cancelled while executing service call " + webObjectForToStringInCaseOfErrors + "." + functionNameInCaseOfErrors +
						"(...). Arguments: " + (argumentsInCaseOfErrors == null ? null : Arrays.asList(argumentsInCaseOfErrors)));
			}
		}
		catch (TimeoutException e)
		{
			if (!retry)
			{
				throw new RuntimeException(
					"Timed out while executing service call " + webObjectForToStringInCaseOfErrors + "." + functionNameInCaseOfErrors + "(...). Arguments: " +
						(argumentsInCaseOfErrors == null ? null : Arrays.asList(argumentsInCaseOfErrors)),
					e);
			}
		}
		catch (IOException e)
		{
			if (!retry)
			{
				throw e;
			}
		}
		if (!hasEndpoint())
		{
			try
			{
				Thread.sleep(4000);
			}
			catch (InterruptedException e)
			{
				log.warn("InterruptedException occurred", e);
			}
		}
		return executeCall(webObjectForToStringInCaseOfErrors, functionNameInCaseOfErrors, argumentsInCaseOfErrors, pendingChangesWriter, blockEventProcessing,
			false);
	}

	@Override
	public void executeAsyncServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes)
	{
		addServiceCall(clientService, functionName, arguments, argumentTypes);
	}

	private ServiceCall createServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes)
	{
		WebObjectApiFunctionDefinition api = clientService.getSpecification().getApiFunction(functionName);
		return new ServiceCall(clientService, functionName, processVarArgsIfNeeded(arguments, argumentTypes), argumentTypes,
			(api != null && api.isPreDataServiceCall()));
	}

	public ComponentCall createComponentCall(WebComponent component, WebObjectFunctionDefinition apiFunction, Object[] arguments,
		Map<String, JSONString> callContributions)
	{
		return createComponentCall(component, apiFunction, arguments, callContributions, false, null, false);
	}

	private ComponentCall createComponentCall(WebComponent component, WebObjectFunctionDefinition apiFunction, Object[] arguments,
		Map<String, JSONString> callContributions,
		boolean delayUntilFormLoads, Container formContainer, boolean async)
	{
		return new ComponentCall(component, apiFunction, processVarArgsIfNeeded(arguments, apiFunction.getParameters()), callContributions, delayUntilFormLoads,
			formContainer, async);
	}

	private Object[] processVarArgsIfNeeded(Object[] arguments, IFunctionParameters parameters)
	{
		if (parameters != null && arguments != null && arguments.length >= parameters.getDefinedArgsCount() && parameters.isVarArgs())
		{
			int definedArgsCount = parameters.getDefinedArgsCount();
			// handle variable args
			List<Object> varArgs = new ArrayList<Object>();
			for (int i = definedArgsCount - 1; i < arguments.length; i++)
			{
				varArgs.add(arguments[i]);
			}
			arguments[definedArgsCount - 1] = varArgs;
			arguments = Arrays.copyOf(arguments, definedArgsCount);
		}
		return arguments;
	}

	private void addServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes)
	{
		serviceCalls.add(createServiceCall(clientService, functionName, arguments, argumentTypes));
	}

	@Override
	public synchronized boolean hasEndpoint()
	{
		return endpoint != null && endpoint.hasSession();
	}

	public synchronized IWebsocketEndpoint getEndpoint()
	{
		return endpoint;
	}

	@Override
	public synchronized boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter)
		throws JSONException
	{
		boolean contentHasBeenWritten = false;

		for (Container fc : getClientSideWindowState().getUsedContainers())
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
				fc.writeAllComponentsChanges(w, containerName, converter);
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

	public boolean writeAllServicesChanges(JSONWriter w, String keyInParent) throws JSONException
	{
		boolean contentHasBeenWritten = false;
		for (IClientService service : getSession().getServices())
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
				service.writeProperties(ChangesToJSONConverter.INSTANCE, FullValueToJSONConverter.INSTANCE, w, changes);
				w.endObject();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
	}

	@Override
	public Object invokeApi(WebComponent receiver, WebObjectApiFunctionDefinition apiFunction, Object[] arguments)
	{
		return invokeApi(receiver, apiFunction, arguments, null);
	}

	protected Object invokeApi(final WebComponent receiver, final WebObjectApiFunctionDefinition apiFunction, final Object[] arguments,
		final Map<String, JSONString> callContributions)
	{
		// {"call":{"form":"product","bean":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}}
		boolean delayedCall = isDelayedApiCall(apiFunction);

		if (delayedCall || isAsyncApiCall(apiFunction))
		{
			ComponentCall call = createComponentCall(receiver, apiFunction, arguments, callContributions, delayedCall,
				delayedCall ? getFormContainer(receiver) : null, isAsyncApiCall(apiFunction));
			addDelayedOrAsyncComponentCall(apiFunction, call);
		}
		else if (isAsyncNowApiCall(apiFunction))
		{
			try
			{
				sendOnlyThisMessageInternal(new SimpleToJSONWriter<IBrowserConverterContext>()
				{
					@Override
					public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
					{
						w.key(COMPONENT_CALLS).array().object();
						createComponentCall(receiver, apiFunction, arguments, callContributions).writeToJSON(w);
						w.endObject().endArray();

						return true;
					}
				}, FullValueToJSONConverter.INSTANCE);
			}
			catch (IOException e)
			{
				log.warn("IOException occurred when trying to call async-now api call '" + apiFunction + "'. Arguments: " +
					(arguments == null ? null : Arrays.asList(arguments)) + ". On component " + receiver, e);
			}
			return null;
		}
		else
		{
			// sync call; add it to the list to keep call order with any previous delayed/async api calls and then trigger the call
			componentApiCalls.add(createComponentCall(receiver, apiFunction, arguments, callContributions));
			try
			{
				Object ret = executeCall(receiver, apiFunction.getName(), arguments, new IToJSONWriter<IBrowserConverterContext>()
				{
					@Override
					public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
					{
						JSONUtils.addKeyIfPresent(w, keyInParent);
						w.object();

						boolean changesFound = writeAllComponentsChanges(w, "forms", converter); // converter here is ChangesToJSONConverter.INSTANCE (see below arg to 'sendSyncMessage')

						w.endObject();
						return changesFound;
					}

					@Override
					public boolean checkForAndWriteAnyUnexpectedRemainingChanges(JSONStringer w, String keyInParent,
						IToJSONConverter<IBrowserConverterContext> converter)
					{
						return writeJSONContent(w, keyInParent, converter);
					}

				}, apiFunction.getBlockEventProcessing(), true);

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
				log.warn("IOException occurred when trying to call sync api call '" + apiFunction + "'. Arguments: " +
					(arguments == null ? null : Arrays.asList(arguments)) + ". On component " + receiver, e);
			}
			catch (CancellationException e)
			{
				throw new RuntimeException("Cancelled while invoking API: " + apiFunction + ". Arguments: " +
					(arguments == null ? null : Arrays.asList(arguments)) + ". On: " + receiver, e);
			}
		}

		return null;
	}

	protected void addDelayedOrAsyncComponentCall(final WebObjectApiFunctionDefinition apiFunction, ComponentCall call)
	{
		if (apiFunction.shouldDiscardPreviouslyQueuedSimilarCalls())
		{
			// for example requestFocus uses that - so that only the last .requestFocus() actually executes (if the form is loaded)
			Iterator<ComponentCall> it = componentApiCalls.iterator();
			while (it.hasNext())
			{
				ComponentCall delayedOrAsyncCall = it.next();
				if (apiFunction.getName().equals(delayedOrAsyncCall.apiFunction.getName()))
				{
					it.remove();
				}
			}
		}
		componentApiCalls.add(call);
	}

	protected boolean hasPendingDelayedCalls(Container formContainer)
	{
		boolean hasPendingDelayedCalls = false;
		if (componentApiCalls.size() > 0)
		{
			for (ComponentCall delayedCall : componentApiCalls)
			{
				if (delayedCall.delayUntilFormLoads && formContainer == delayedCall.formContainer)
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

	protected static boolean isDelayedApiCall(WebObjectApiFunctionDefinition apiFunction)
	{
		return apiFunction.getReturnType() == null && apiFunction.shouldDelayUntilFormLoads();
	}

	protected static boolean isAsyncApiCall(WebObjectApiFunctionDefinition apiFunction)
	{
		return apiFunction.getReturnType() == null && apiFunction.isAsync();
	}

	protected static boolean isAsyncNowApiCall(WebObjectApiFunctionDefinition apiFunction)
	{
		return apiFunction.getReturnType() == null && apiFunction.isAsyncNow();
	}

	private static class ServiceCall
	{

		private final IClientService clientService;
		private final String functionName;
		private final Object[] arguments;
		private final IFunctionParameters argumentTypes;
		private final boolean preDataServiceCall;

		public ServiceCall(IClientService clientService, String functionName, Object[] arguments,
			IFunctionParameters argumentTypes, boolean preDataServiceCall)
		{
			this.clientService = clientService;
			this.functionName = functionName;
			this.arguments = arguments;
			this.argumentTypes = argumentTypes;
			this.preDataServiceCall = preDataServiceCall;
		}

		/**
		 * Writes to JSON what can then be sent to client in order to execute this service call.
		 */
		public void writeToJSON(JSONWriter w)
		{
			// {"services":[{name:serviceName,call:functionName,args:argumentsArray}]}
			w.object();
			w.key(API_KEY_NAME).value(clientService.getScriptingName());
			w.key(API_KEY_CALL).value(functionName);
			if (preDataServiceCall) w.key(API_PRE_DATA_SERVICE_CALL).value(true);
			if (arguments != null)
			{
				w.key(API_KEY_ARGS).array();
				for (int i = 0; i < arguments.length; i++)
				{
					FullValueToJSONConverter.INSTANCE.toJSONValue(w, null, arguments[i],
						argumentTypes != null ? argumentTypes.getParameterDefinition(i) : null,
						new BrowserConverterContext((ClientService)clientService, PushToServerEnum.allow));
				}
				w.endArray();
			}
			w.endObject();
		}

	}

	private static class ComponentCall
	{

		private final WebComponent component;
		private final Object[] arguments;
		private final WebObjectFunctionDefinition apiFunction;
		private final Map<String, JSONString> callContributions;
		private final boolean delayUntilFormLoads;
		private final boolean async;
		private final Container formContainer;

		public ComponentCall(WebComponent component, WebObjectFunctionDefinition apiFunction, Object[] arguments, Map<String, JSONString> callContributions,
			boolean delayUntilFormLoads, Container formContainer, boolean async)
		{
			this.component = component;
			this.apiFunction = apiFunction;
			this.arguments = arguments;
			this.callContributions = callContributions;
			this.delayUntilFormLoads = delayUntilFormLoads;
			this.formContainer = formContainer; // this is only relevant if delayUntilFormLoads == true
			this.async = async;
		}

		/**
		 * Writes to JSON what can then be sent to client in order to execute this component call.
		 */
		public void writeToJSON(JSONWriter w)
		{
			Container topContainer = component.getParent();
			while (topContainer != null && topContainer.getParent() != null)
			{
				topContainer = topContainer.getParent();
			}

			if (topContainer == null)
			{
				log.warn(
					"Trying to write a api call " + apiFunction.getName() + "  for component " + component.getName() +
						" but it was already removed from its parent",
					new RuntimeException());
				return;
			}

			w.key(API_KEY_FORM_NAME).value(topContainer.getName());
			w.key(API_KEY_COMPONENT_NAME).value(component.getName());
			w.key(API_KEY_FUNCTION_NAME).value(apiFunction.getName());

			if (arguments != null && arguments.length > 0)
			{
				w.key(API_KEY_ARGS).array();
				IFunctionParameters argumentTypes = apiFunction.getParameters();
				for (int i = 0; i < arguments.length; i++)
				{
					FullValueToJSONConverter.INSTANCE.toJSONValue(w, null, arguments[i],
						argumentTypes != null ? argumentTypes.getParameterDefinition(i) : null,
						new BrowserConverterContext(component, PushToServerEnum.allow));
				}
				w.endArray();
			}

			// we send this to client just in case form is no longer there for some reason when the call arrives - and it shouldn't try to force-load it on client
			if (delayUntilFormLoads) w.key(API_KEY_DELAY_UNTIL_FORM_LOADS).value(true);

			if (callContributions != null) callContributions.forEach((String key, JSONString value) -> {
				w.key(key).value(value);
			});
		}

	}

}
