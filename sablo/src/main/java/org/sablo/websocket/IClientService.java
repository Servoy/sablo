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
import org.sablo.websocket.utils.DataConversion;
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
	 * @param serviceName
	 * @param functionName
	 * @param arguments
	 */
	public void executeAsyncServiceCall(String functionName, Object[] arguments);

	/**
	 * Execute a service call synchronously.. It will be called on the current window.
	 * @param serviceName
	 * @param functionName
	 * @param arguments
	 * @return remote result
	 * @throws IOException
	 */
	public Object executeServiceCall(String functionName, Object[] arguments) throws IOException;

	/**
	 * Called when a property on the client is changed, json conversion should be done.
	 *
	 * @param property
	 * @param value
	 */
	public void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException;

	/**
	 * put a model value, will be send to the client.
	 *
	 * @param property
	 * @param value
	 * @param sourceOfValue
	 */
	public boolean setProperty(String propertyName, Object propertyValue);

	/**
	 * get a model value
	 * @param property
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
		JSONWriter w, TypedData<Map<String, Object>> propertiesToWrite, DataConversion clientDataConversions) throws IllegalArgumentException, JSONException;

}
