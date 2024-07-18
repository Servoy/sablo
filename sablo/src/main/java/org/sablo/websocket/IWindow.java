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
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.IFunctionParameters;
import org.sablo.specification.WebObjectApiFunctionDefinition;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;


/**
 * Represents the client side window object on the server.
 *
 * @author rgansevles
 *
 */
public interface IWindow
{

	/**
	 * Get the websocket session.
	 */
	IWebsocketSession getSession();

	void setEndpoint(IWebsocketEndpoint endpoint);

	/**
	 * Get the websocket endpoint for this window.
	 */
	IWebsocketEndpoint getEndpoint();

	Container getForm(String formName);

	String getCurrentFormUrl();

	void setCurrentFormUrl(String currentFormUrl);

	/**
	 * Register a container on this window for traversal of changes (when changes are detected on that container after handling an event, they will be sent to client).<br/>
	 * The window will only keep a weak reference to the container, so it will automatically unregister it when that container can be garbage collected.
	 *
	 * @param container the container that is registered to this window.
	 */
	void registerContainer(Container container);

	/**
	 * Unregisters the given container from this window. This window will no longer look for change in this container.</br>
	 * Useful when for example a container is moved to another window completely.
	 *
	 * @param container the container that is registered to this window.
	 */
	void unregisterContainer(Container container);

	int getNr();

	String getName();

	/**
	 * Flush outstanding async service calls.
	 *
	 * @throws IOException
	 */
	void flush() throws IOException;

	/**
	 * Execute a (client/browser) service call asynchronously.
	 *
	 * @param clientService the service to call client side.
	 * @param functionName the name of the service's function to call.
	 * @param arguments the arguments to be passed to the service's function call.
	 * @param argumentTypes the types of arguments passed; can be null (the types are used for correct 'to JSON' conversion for websocket traffic).
	 */
	void executeAsyncServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes);

	/**
	 * Execute a (client/browser) async-now method; such methods are to be executed right away but do not wait for a return value.
	 * The async-now call does not send any component/service pending changes - or call other pending async/delayed api to client; it just calls the method.
	 * @param clientService the service to call client side.
	 * @param functionName the name of the service's function to call.
	 * @param arguments the arguments to be passed to the service's function call.
	 * @param argumentTypes the types of arguments passed; can be null (the types are used for correct 'to JSON' conversion for websocket traffic).
	 */
	void executeAsyncNowServiceCall(IClientService clientService, String functionName, Object[] arguments, IFunctionParameters argumentTypes);

	/**
	 * Execute a (client/browser) service call asynchronously and returns the resulting value.
	 *
	 * @param clientService the service to call client side.
	 * @param functionName the name of the service's function to call.
	 * @param arguments the arguments to be passed to the service's function call.
	 * @param apiFunction
	 * @param argumentTypes the types of arguments passed; can be null (the types are used for correct 'to JSON' conversion for web-socket traffic).
	 * @param pendingChangesWriter a writer that writes any pending changes of the service that must be sent with this request/api call to be in sync on client.
	 * @return remote result.
	 * @throws IOException if such an exception happens.
	 */
	Object executeServiceCall(IClientService serviceName, String functionName, Object[] arguments, WebObjectApiFunctionDefinition apiFunction,
		IToJSONWriter<IBrowserConverterContext> pendingChangesWriter, boolean blockEventProcessing) throws IOException;

	/**
	 * Invoke an function on the webcomponent
	 * @param receiver the webcomponent to invoke on
	 * @param apiFunction the function to invoke
	 * @param arguments
	 */
	public Object invokeApi(WebComponent receiver, WebObjectApiFunctionDefinition apiFunction, Object[] arguments);

	/**
	 * It there an active session to the browser?
	 */
	boolean hasEndpoint();


	/**
	 * Writes as JSON changes from all components of all registered Containers.
	 * @param keyInParent a key (can be null in which case it should be ignored) that must be appended to 'w' initially if this method call writes content to it. If the method returns false, nothing should be written to the writer...
	 */
	boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException;

	/**
	 * Close the browser session.
	 */
	void closeSession();

	/**
	 * Close the browser session with a cancel reason.
	 */
	void cancelSession(String reason);

	/**
	 * returns the last ping time that was received from the client
	 * return 0 if not known
	 */
	public long getLastPingTime();

	void dispose();

	void sendChanges() throws IOException;

	/**
	 * When client side calls an API from client to server and expects an result (through a defer/promise), we want to send that result together with
	 * any other changes that that API call generated server side and that need to be sent. So that they arrive in the same request to the client.<br/><br/>
	 *
	 * Some (client side) components need this result to arrive in the same message in order to ignore some incomming changes from server (if they were the ones
	 * that requested them for example).
	 *
	 * But do register the result here to be sent later - when all changes that happened as a result of this request are sent.
	 */
	void setClientToServerCallReturnValueForChanges(ClientToServerCallReturnValue clientToServerCallReturnValue);

	void onOpen(Map<String, List<String>> requestParams);

	int getNextMessageNumber();

}
