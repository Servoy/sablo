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
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectFunctionDefinition;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.utils.DataConversion;
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
	 * Get the websocket sessioninvoke
	 * @return
	 */
	IWebsocketSession getSession();

	/**
	 * @param endpoint
	 */
	void setEndpoint(IWebsocketEndpoint endpoint);

	/**
	 * @param formName
	 * @return
	 */
	Container getForm(String formName);

	/**
	 * @return the currentFormUrl
	 */
	String getCurrentFormUrl();

	/**
	 * @param currentFormUrl the currentFormUrl to set
	 */
	void setCurrentFormUrl(String currentFormUrl);

	/**
	 * Register a container at the websocket for traversal of changes
	 * @param container
	 */
	void registerContainer(Container container);

	String getUuid();

	/**
	 * @return
	 */
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
	 * @param serviceName the name of the service to call client side.
	 * @param functionName the name of the service's function to call.
	 * @param arguments the arguments to be passed to the service's function call.
	 * @param argumentTypes the types of arguments passed; can be null (the types are used for correct 'to JSON' conversion for websocket traffic).
	 */
	void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes);

	/**
	 * Execute a (client/browser) service call asynchronously and returns the resulting value.
	 *
	 * @param serviceName the name of the service to call client side.
	 * @param functionName the name of the service's function to call.
	 * @param arguments the arguments to be passed to the service's function call.
	 * @param apiFunction
	 * @param argumentTypes the types of arguments passed; can be null (the types are used for correct 'to JSON' conversion for web-socket traffic).
	 * @param pendingChangesWriter a writer that writes any pending changes of the service that must be sent with this request/api call to be in sync on client.
	 * @return remote result.
	 * @throws IOException if such an exception happens.
	 */
	Object executeServiceCall(String serviceName, String functionName, Object[] arguments, WebObjectFunctionDefinition apiFunction,
		IToJSONWriter<IBrowserConverterContext> pendingChangesWriter, boolean blockEventProcessing) throws IOException;

	/**
	 * Invoke an function on the webcomponent
	 * @param receiver the webcomponent to invoke on
	 * @param apiFunction the function to invoke
	 * @param arguments
	 */
	public Object invokeApi(WebComponent receiver, WebObjectFunctionDefinition apiFunction, Object[] arguments, PropertyDescription argumentTypes);

	/**
	 * It there an active session to the browser?
	 */
	boolean hasEndpoint();


	/**
	 * Writes as JSON changes from all components of all registered Containers.
	 * @param keyInParent a key (can be null in which case it should be ignored) that must be appended to 'w' initially if this method call writes content to it. If the method returns false, nothing should be written to the writer...
	 */
	boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
		DataConversion clientDataConversions) throws JSONException;

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


	/**
	 *
	 */
	void dispose();


	/**
	 *
	 */
	void sendChanges() throws IOException;


	/**
	 *
	 */
	void onOpen(Map<String, List<String>> requestParams);

	/**
	 * @return
	 */
	int getNextMessageNumber();
}
