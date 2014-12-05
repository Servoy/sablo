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

package org.sablo.specification.property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Type for what in spec files you see like 'mytype[]'.
 * It should to be a kind of proxy for all possible conversion types to it's elements.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class CustomJSONArrayType<ET, WT> extends CustomJSONPropertyType<Object> implements IWrapperType<Object, ChangeAwareList<ET, WT>>,
	ISupportsGranularUpdates<ChangeAwareList<ET, WT>>
{

	public static final String TYPE_NAME = "JSON_arr";

	protected static final String CONTENT_VERSION = "vEr";
	protected static final String UPDATES = "u";
	protected static final String INDEX = "i";
	protected static final String VALUE = "v";
	protected static final String INITIALIZE = "in";
	protected static final String NO_OP = "n";

	public static final String ELEMENT_CONFIG_KEY = "elementConfig";

	/**
	 * Creates a new type that handles arrays of the given element types, with it's own set of default value, config object, ...
	 * @param definition the defined types of the array's elements
	 */
	public CustomJSONArrayType(PropertyDescription definition)
	{
		super(definition.getType().getName(), definition);
	}

	@Override
	public Object unwrap(ChangeAwareList<ET, WT> value)
	{
		// this type will wrap an [] or List into a list; unwrap will simply return that list that will further wrap/unwrap elements as needed on any operation
		// look at this wrapped list as the external portal of the list property as far as BaseWebObjects are concerned
		return value;
	}

	@Override
	public ChangeAwareList<ET, WT> wrap(Object value, ChangeAwareList<ET, WT> previousValue, IDataConverterContext dataConverterContext)
	{
		if (value instanceof ChangeAwareList< ? , ? >) return (ChangeAwareList<ET, WT>)value;

		List<ET> wrappedList = wrapList(value, dataConverterContext);
		if (wrappedList != null)
		{
			// ok now we have the list or wrap list (depending on if type is IWrapperType or not)
			// wrap this further into a change-aware list; this is used to be able to track changes and perform server to browser full or granular updates
			return new ChangeAwareList<ET, WT>(wrappedList, dataConverterContext, previousValue != null ? previousValue.getListContentVersion() + 1 : 1);
		}
		return null;
	}

	protected IPropertyType<ET> getElementType()
	{
		return (IPropertyType<ET>)getCustomJSONTypeDefinition().getType();
	}

	private List<ET> wrapList(Object value, IDataConverterContext dataConverterContext)
	{
		// this type will wrap (if needed; that means it will end up as a normal list if element type is not wrapped type
		// or a WrapperList otherwise) an [] or List into a list; unwrap will simply return that list that will further
		// wrap/unwrap elements as needed on any operation
		// look at this wrapped list as the external portal of the list property as far as BaseWebObjects are concerned
		if (value != null)
		{
			IPropertyType<ET> elementType = getElementType();
			if ((value instanceof List && !(elementType instanceof IWrapperType)) || value instanceof IWrappedBaseListProvider< ? >)
			{
				// it's already what we want; return it
				return (List<ET>)value;
			}

			List<ET> baseList;
			if (value.getClass().isArray())
			{
				// native array
				baseList = Arrays.asList((ET[])value);
			}
			else if (value instanceof List< ? >)
			{
				baseList = (List<ET>)value;
			}
			else
			{
				log.error("The value of this the property was supposed to be a native Array, a List or null, but it was " +
					value.getClass().getCanonicalName() + ".\nProperty description: " + dataConverterContext.getPropertyDescription());
				return null;
			}

			if (elementType instanceof IWrapperType< ? , ? >)
			{
				return new WrapperList<ET, WT>(baseList, (IWrapperType<ET, WT>)elementType, dataConverterContext, true);
			}
			else
			{
				return baseList; // in this case ET == WT
			}
		}
		return null;
	}

	@Override
	public ChangeAwareList<ET, WT> fromJSON(Object newJSONValue, ChangeAwareList<ET, WT> previousChangeAwareList, IDataConverterContext dataConverterContext)
	{
		if (newJSONValue instanceof JSONObject)
		{
			JSONObject clientReceivedJSON = (JSONObject)newJSONValue;
			if (clientReceivedJSON.has(NO_OP)) return previousChangeAwareList;

			try
			{
				if (previousChangeAwareList == null || clientReceivedJSON.getInt(CONTENT_VERSION) == previousChangeAwareList.getListContentVersion())
				{
					if (clientReceivedJSON.has(UPDATES))
					{
						if (previousChangeAwareList == null)
						{
							log.warn("property " + dataConverterContext.getPropertyDescription().getName() +
								" is typed as array; it got browser updates but server-side it is null; ignoring browser update.");
						}
						else
						{
							JSONObject updates = (JSONObject)newJSONValue;

							// here we operate directly on (wrapper) base list as this change doesn't need to be sent back to browser
							// as browser initiated it; also JSON conversions work on wrapped values
							List<WT> wrappedBaseListReadOnly = previousChangeAwareList.getWrappedBaseListForReadOnly();

							JSONArray updatedRows = updates.getJSONArray(UPDATES);
							for (int i = updatedRows.length() - 1; i >= 0; i--)
							{
								JSONObject row = updatedRows.getJSONObject(i);
								int idx = row.getInt(INDEX);
								Object val = row.get(VALUE);

								if (wrappedBaseListReadOnly.size() > idx)
								{
									WT newWrappedEl = (WT)JSONUtils.fromJSON(wrappedBaseListReadOnly.get(idx), val, getCustomJSONTypeDefinition(),
										dataConverterContext);
									previousChangeAwareList.setInWrappedBaseList(idx, newWrappedEl, false);
								}
								else
								{
									log.error("Custom array property updates from browser are incorrect. Index out of bounds: idx (" +
										wrappedBaseListReadOnly.size() + ")");
								}
							}
						}
						return previousChangeAwareList;
					}
					else
					{
						// full replace
						return fullValueReplaceFromBrowser(previousChangeAwareList, dataConverterContext, clientReceivedJSON.getJSONArray(VALUE));
					}
				}
				else
				{
					log.warn("property " + dataConverterContext.getPropertyDescription().getName() + " is typed as array; it got browser updates (" +
						clientReceivedJSON.getInt(CONTENT_VERSION) + ") but expected server version (" + (previousChangeAwareList.getListContentVersion() + 1) +
						")  - so server changed meanwhile; ignoring browser update.");

					// dropped browser update because server object changed meanwhile;
					// will send a full update to have the correct value browser-side as well again (currently server side is leading / has more prio because not all server side values might support being recreated from client values)
					previousChangeAwareList.markAllChanged();

					return previousChangeAwareList;
				}
			}
			catch (JSONException e)
			{
				log.error("Cannot correctly parse custom array property updates/values from browser.", e);
				return previousChangeAwareList;
			}
		}
		else if (newJSONValue == null)
		{
			return null;
		}
		else if (newJSONValue instanceof JSONArray)
		{
			// this can happen if the property was undefined before (so not even aware of type client side) and it was assigned a complete array value client side;
			// in this case we must update server value and send a request back to client containing the type and letting it know that it must start watching the new value (for granular updates)
			ChangeAwareList<ET, WT> newChangeAwareList = fullValueReplaceFromBrowser(previousChangeAwareList, dataConverterContext, (JSONArray)newJSONValue);
			newChangeAwareList.markMustSendTypeToClient();
			return newChangeAwareList;
		}
		else
		{
			log.error("property " + dataConverterContext.getPropertyDescription().getName() +
				" is typed as array, but the value is not an JSONArray or supported update value: " + newJSONValue);
			return previousChangeAwareList;
		}
	}

	private ChangeAwareList<ET, WT> fullValueReplaceFromBrowser(ChangeAwareList<ET, WT> previousChangeAwareList, IDataConverterContext dataConverterContext,
		JSONArray array)
	{
		List<WT> list = new ArrayList<WT>();
		List<WT> previousWrappedBaseList = (previousChangeAwareList != null ? previousChangeAwareList.getWrappedBaseListForReadOnly() : null);

		for (int i = 0; i < array.length(); i++)
		{
			WT oldVal = null;
			if (previousWrappedBaseList != null && previousWrappedBaseList.size() > i)
			{
				oldVal = previousWrappedBaseList.get(i);
			}
			try
			{
				list.add((WT)JSONUtils.fromJSON(oldVal, array.opt(i), getCustomJSONTypeDefinition(), dataConverterContext));
			}
			catch (JSONException e)
			{
				log.error("Cannot parse array element browser JSON.", e);
			}
		}

		List<ET> newBaseList;
		IPropertyType<ET> elementType = getElementType();
		if (elementType instanceof IWrapperType< ? , ? >)
		{
			newBaseList = new WrapperList<ET, WT>(list, (IWrapperType<ET, WT>)elementType, dataConverterContext);
		}
		else
		{
			newBaseList = (List<ET>)list; // in this case ET == WT
		}

		// TODO how to handle previous null value here; do we need to re-send to client or not (for example initially both client and server had values, at the same time server==null client sends full update); how do we kno case server version is unknown then
		return new ChangeAwareList<ET, WT>(newBaseList, dataConverterContext, previousChangeAwareList != null
			? previousChangeAwareList.increaseContentVersion() : 1);
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, ChangeAwareList<ET, WT> changeAwareList, DataConversion conversionMarkers,
		IDataConverterContext dataConverterContext) throws JSONException
	{
		return toJSON(writer, key, changeAwareList, conversionMarkers, true, JSONUtils.FullValueToJSONConverter.INSTANCE, dataConverterContext.getWebObject());
	}

	@Override
	public JSONWriter changesToJSON(JSONWriter writer, String key, ChangeAwareList<ET, WT> changeAwareList, DataConversion conversionMarkers,
		IDataConverterContext dataConverterContext) throws JSONException
	{
		return toJSON(writer, key, changeAwareList, conversionMarkers, false, JSONUtils.FullValueToJSONConverter.INSTANCE, dataConverterContext.getWebObject());
	}

	protected JSONWriter toJSON(JSONWriter writer, String key, ChangeAwareList<ET, WT> changeAwareList, DataConversion conversionMarkers, boolean fullValue,
		IToJSONConverter toJSONConverterForFullValue, BaseWebObject webObject) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		if (changeAwareList != null)
		{
			if (conversionMarkers != null) conversionMarkers.convert(CustomJSONArrayType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

			Set<Integer> changes = changeAwareList.getChangedIndexes();
			List<WT> wrappedBaseListReadOnly = changeAwareList.getWrappedBaseListForReadOnly();
			writer.object();

			if (changeAwareList.mustSendAll() || fullValue)
			{
				// send all (currently we don't support granular updates for add/remove but we could in the future)
				DataConversion arrayConversionMarkers = new DataConversion();
				writer.key(CONTENT_VERSION).value(changeAwareList.increaseContentVersion());
				writer.key(VALUE).array();
				for (int i = 0; i < wrappedBaseListReadOnly.size(); i++)
				{
					arrayConversionMarkers.pushNode(String.valueOf(i));
					toJSONConverterForFullValue.toJSONValue(writer, null, wrappedBaseListReadOnly.get(i), getCustomJSONTypeDefinition(),
						arrayConversionMarkers, webObject);
					arrayConversionMarkers.popNode();
				}
				writer.endArray();
				if (arrayConversionMarkers.getConversions().size() > 0)
				{
					writer.key("conversions").object();
					JSONUtils.writeConversions(writer, arrayConversionMarkers.getConversions());
					writer.endObject();
				}
			}
			else if (changes.size() > 0)
			{
				// else write changed indexes / granular update:
				writer.key(CONTENT_VERSION).value(changeAwareList.getListContentVersion());
				if (changeAwareList.mustSendTypeToClient())
				{
					// updates + mustSendTypeToClient can happen if child elements are also similar - and need to instrument their values client-side when set by reference/completely from browser
					writer.key(INITIALIZE).value(true);
				}

				writer.key(UPDATES).array();
				DataConversion arrayConversionMarkers = new DataConversion();
				int i = 0;
				for (Integer idx : changes)
				{
					arrayConversionMarkers.pushNode(String.valueOf(i++));
					writer.object().key(INDEX).value(idx);
					arrayConversionMarkers.pushNode(VALUE);
					JSONUtils.changesToBrowserJSONValue(writer, VALUE, wrappedBaseListReadOnly.get(idx.intValue()), getCustomJSONTypeDefinition(),
						arrayConversionMarkers, webObject);
					arrayConversionMarkers.popNode();
					writer.endObject();
					arrayConversionMarkers.popNode();
				}
				writer.endArray();
				if (arrayConversionMarkers.getConversions().size() > 0)
				{
					writer.key("conversions").object();
					JSONUtils.writeConversions(writer, arrayConversionMarkers.getConversions());
					writer.endObject();
				}
			}
			else if (changeAwareList.mustSendTypeToClient())
			{
				writer.key(CONTENT_VERSION).value(changeAwareList.getListContentVersion());
				writer.key(INITIALIZE).value(true);
			}
			else
			{
				writer.key(NO_OP).value(true);
			}
			writer.endObject();
			changeAwareList.clearChanges();
		}
		else
		{
			if (conversionMarkers != null) conversionMarkers.convert(CustomJSONArrayType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets
			writer.value(JSONObject.NULL); // TODO how to handle null values which have no version info (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?
		}
		return writer;
	}

}
