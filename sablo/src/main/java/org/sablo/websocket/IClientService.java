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
package org.sablo.websocket;

import java.io.IOException;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Represents the client side service object on the server.
 * Can be used to execute functions client side, or the get or set model data.
 *
 * @author jcompagner
 *
 */
public interface IClientService
{

	/**
	 * Execute a service call asynchronously. It will be called on the current window.
	 * It is equivalent to executeAsyncServiceCall(functionName, arguments, true)
	 */
	public void executeAsyncServiceCall(String functionName, Object[] arguments);

	/**
	 * Execute a service call asynchronously. It will be called on the current window.
	 *
	 * @param sendToClientRightAwayIfPossibleAndNotCurrentlyProcessingMessageFromClient if true then:<ul>
	 *            <li>if we are not currently handling a message from client, it will try to send the message now.</li>
	 *            <li>if we are currently handling a message from client, it will register the call to be send as part of the response when the message from client is done (or a sync call kicks in).</li>
	 *            If false, it will just register the call to be sent to client later; use false if you know for sure that code that follows will trigger a send to client later. This is useful for example
	 * to avoid sending unexpected pending changes together with this async call that the caller wants to handle in a different way.
	 */
	public void executeAsyncServiceCall(String functionName, Object[] arguments,
		boolean sendToClientRightAwayIfPossibleAndNotCurrentlyProcessingMessageFromClient);

	/**
	 * Execute a (client/browser) async-now method; such methods are to be executed right away but do not wait for a return value.
	 * The async-now call does not send any component/service pending changes - or call other pending async/delayed api to client; it just calls the method.
	 */
	public void executeAsyncNowServiceCall(String functionName, Object[] arguments);

	/**
	 * Execute a service call synchronously.. It will be called on the current window.
	 */
	public Object executeServiceCall(String functionName, Object[] arguments) throws IOException;

	/**
	 * Called when a property on the client is changed, json conversion should be done.
	 */
	public void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException;

	/**
	 * put a model value, will be send to the client.
	 */
	public boolean setProperty(String propertyName, Object propertyValue);

	/**
	 * get a model value
	 * @return the value for this property in the model
	 */
	public Object getProperty(String property);

	/**
	 * Get all the changed values
	 *
	 * @return map of property->value
	 */
	public TypedData<Map<String, Object>> getProperties();

	/**
	 * Get all the changed values
	 *
	 * @return map of property->value & the type information for those values to be converted to JSON.
	 */
	public TypedData<Map<String, Object>> getAndClearChanges();

	/**
	 * get the name of this service.
	 *
	 * @return String the name
	 */
	public String getName();

	/**
	 * @see ClientService#convertToJSName(String).
	 */
	public String getScriptingName();

	public WebObjectSpecification getSpecification();

	void writeProperties(IToJSONConverter<IBrowserConverterContext> mainConverter, IToJSONConverter<IBrowserConverterContext> converterForSendingFullValue,
		JSONWriter w, TypedData<Map<String, Object>> propertiesToWrite) throws IllegalArgumentException, JSONException;

}
