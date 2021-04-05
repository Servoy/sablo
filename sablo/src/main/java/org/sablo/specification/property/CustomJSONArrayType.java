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
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.ClientSideTypeCache;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Type for what in spec files you see like 'mytype[]'.
 * It should to be a kind of proxy for all possible conversion types to it's elements.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class CustomJSONArrayType<ET, WT> extends CustomJSONPropertyType<Object>
	implements IAdjustablePropertyType<Object>, IWrapperType<Object, ChangeAwareList<ET, WT>>, ISupportsGranularUpdates<ChangeAwareList<ET, WT>>,
	IPushToServerSpecialType, IPropertyWithClientSideConversions<Object>
{

	public static final String TYPE_NAME = "JSON_arr";

	protected static final String CONTENT_VERSION = "vEr";

	protected static final String CHANGE_TYPE_UPDATES = "u";
	protected static final String CHANGE_TYPE_BY_REF = "x";
	protected static final String CHANGE_TYPE_REMOVES = "r";
	protected static final String CHANGE_TYPE_ADDITIONS = "a";

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

	public String getGenericName()
	{
		return TYPE_NAME;
	}

	@Override
	public Object unwrap(ChangeAwareList<ET, WT> value)
	{
		// this type will wrap an [] or List into a list; unwrap will simply return that list that will further wrap/unwrap elements as needed on any operation
		// look at this wrapped list as the external portal of the list property as far as BaseWebObjects are concerned
		return value;
	}

	@Override
	public ChangeAwareList<ET, WT> wrap(Object value, ChangeAwareList<ET, WT> previousValue, PropertyDescription pd, IWrappingContext dataConverterContext)
	{
		if (value instanceof ChangeAwareList< ? , ? >) return (ChangeAwareList<ET, WT>)value;

		List<ET> wrappedList = wrapList(value, pd, dataConverterContext);
		if (wrappedList != null)
		{
			// ok now we have the list or wrap list (depending on if type is IWrapperType or not)
			// wrap this further into a change-aware list; this is used to be able to track changes and perform server to browser full or granular updates
			return new ChangeAwareList<ET, WT>(wrappedList/* , dataConverterContext */, previousValue != null ? previousValue.getListContentVersion() + 1 : 1);
		}
		return null;
	}

	protected IPropertyType<ET> getElementType()
	{
		return (IPropertyType<ET>)getCustomJSONTypeDefinition().getType();
	}

	private List<ET> wrapList(Object value, PropertyDescription pd, IWrappingContext dataConverterContext)
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
					value.getClass().getCanonicalName() + ".\nProperty description: " + pd);
				return null;
			}

			if (elementType instanceof IWrapperType< ? , ? >)
			{
				return new WrapperList<ET, WT>(baseList, (IWrapperType<ET, WT>)elementType, pd, dataConverterContext, true);
			}
			else
			{
				return baseList; // in this case ET == WT
			}
		}
		return null;
	}

	@Override
	public ChangeAwareList<ET, WT> fromJSON(Object newJSONValue, ChangeAwareList<ET, WT> previousChangeAwareList, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext, ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		// maybe these 2 pushToServer values should be made only 1... but for example one could have array 'allow' and child elements of type 'object' with 'deep' (if only child element changes are to be automatically sent from angulat deep watches to server)
		PushToServerEnum pushToServerOnWholeArray = BrowserConverterContext.getPushToServerValue(dataConverterContext);
		PushToServerEnum pushToServerComputedOnElements = pushToServerOnWholeArray
			.combineWithChild(getCustomJSONTypeDefinition().getPushToServerAsDeclaredInSpecFile());

		if (newJSONValue instanceof JSONObject)
		{
			JSONObject clientReceivedJSON = (JSONObject)newJSONValue;
			if (clientReceivedJSON.has(NO_OP)) return previousChangeAwareList;

			try
			{
				if (previousChangeAwareList == null || clientReceivedJSON.getInt(CONTENT_VERSION) == previousChangeAwareList.getListContentVersion())
				{
					IBrowserConverterContext elementDataConverterContext = dataConverterContext == null ? null
						: dataConverterContext.newInstanceWithPushToServer(pushToServerComputedOnElements);
					if (clientReceivedJSON.has(CHANGE_TYPE_UPDATES))
					{
						if (previousChangeAwareList == null)
						{
							log.warn("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
								"' is typed as array; it got browser updates but server-side it is null; ignoring browser update. Update JSON: " +
								newJSONValue);
						}
						else
						{
							if ((getCustomJSONTypeDefinition().getType() instanceof IPushToServerSpecialType &&
								((IPushToServerSpecialType)getCustomJSONTypeDefinition().getType()).shouldAlwaysAllowIncommingJSON()) ||
								PushToServerEnum.allow.compareTo(pushToServerComputedOnElements) <= 0)
							{
								JSONObject updates = (JSONObject)newJSONValue;

								// here we operate directly on (wrapper) base list as this change doesn't need to be sent back to browser
								// as browser initiated it; also JSON conversions work on wrapped values
								List<WT> wrappedBaseListReadOnly = previousChangeAwareList.getWrappedBaseListForReadOnly();

								JSONArray updatedRows = updates.getJSONArray(CHANGE_TYPE_UPDATES);
								for (int i = updatedRows.length() - 1; i >= 0; i--)
								{
									JSONObject row = updatedRows.getJSONObject(i);
									int idx = row.getInt(INDEX);
									Object val = row.get(VALUE);

									if (wrappedBaseListReadOnly.size() > idx)
									{
										ValueReference<Boolean> returnValueAdjustedIncommingValueForIndex = new ValueReference<Boolean>(Boolean.FALSE);
										WT newWrappedEl = (WT)JSONUtils.fromJSON(wrappedBaseListReadOnly.get(idx), val, getCustomJSONTypeDefinition(),
											elementDataConverterContext,
											returnValueAdjustedIncommingValueForIndex);
										previousChangeAwareList.setInWrappedBaseList(idx, newWrappedEl, false);
										if (returnValueAdjustedIncommingValueForIndex.value.booleanValue())
											previousChangeAwareList.getChangeSetter().markElementChangedByRef(idx);
									}
									else
									{
										log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
											"'. Custom array property updates from browser are incorrect. Index out of bounds: idx (" +
											wrappedBaseListReadOnly.size() + ")");
									}
								}
							}
							else
							{
								log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
									"' that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to update array element values serverside. Denying and attempting to send back full value! Update JSON: " +
									newJSONValue);
								if (previousChangeAwareList != null) previousChangeAwareList.getChangeSetter().markAllChanged();
								return previousChangeAwareList;
							}
						}
						return previousChangeAwareList;
					}
					else
					{
						if (PushToServerEnum.allow.compareTo(pushToServerOnWholeArray) > 0)
						{
							log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
								"' that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to change the full array value serverside. Denying and attempting to send back full value! Update JSON: " +
								newJSONValue);
							if (previousChangeAwareList != null) previousChangeAwareList.getChangeSetter().markAllChanged();
							return previousChangeAwareList;
						}

						// full replace
						return fullValueReplaceFromBrowser(previousChangeAwareList, pd, elementDataConverterContext, clientReceivedJSON.getJSONArray(VALUE),
							returnValueAdjustedIncommingValue);
					}
				}
				else
				{
					log.info("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
						"' is typed as array; it got browser updates (" + clientReceivedJSON.getInt(CONTENT_VERSION) +
						") but expected server version (" + previousChangeAwareList.getListContentVersion() +
						")  - so server changed meanwhile; ignoring browser update. Update JSON: " + newJSONValue);

					// dropped browser update because server object changed meanwhile;
					// will send a full update if needed to have the correct value browser-side as well again (currently server side is leading / has more prio because not all server side values might support being recreated from client values)
					previousChangeAwareList.resetDueToOutOfSyncIfNeeded(clientReceivedJSON.getInt(CONTENT_VERSION));

					return previousChangeAwareList;
				}
			}
			catch (JSONException e)
			{
				log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
					"'. Cannot correctly parse custom array property updates/values from browser. Update JSON: " + newJSONValue, e);
				return previousChangeAwareList;
			}
		}
		else if (newJSONValue == null)
		{
			if (previousChangeAwareList != null && PushToServerEnum.allow.compareTo(pushToServerOnWholeArray) > 0)
			{
				log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
					"' that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to change the array value serverside to null. Denying and attempting to send back full value! Update JSON: " +
					newJSONValue);
				previousChangeAwareList.getChangeSetter().markAllChanged();
				return previousChangeAwareList;
			}
			return null;
		}
		else if (newJSONValue instanceof JSONArray)
		{
			log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
				"' is trying to send a full JSONArray from client without going through client side conversion. Denying and attempting to send back old full value! Update JSON: " +
				newJSONValue);

			if (previousChangeAwareList != null) previousChangeAwareList.getChangeSetter().markAllChanged();
			return previousChangeAwareList;
		}
		else
		{
			log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
				"' is typed as array, but the value is not a supported update value: " + newJSONValue);
			return previousChangeAwareList;
		}
	}

	private ChangeAwareList<ET, WT> fullValueReplaceFromBrowser(ChangeAwareList<ET, WT> previousChangeAwareList, PropertyDescription pd,
		IBrowserConverterContext elementDataConverterContext, JSONArray array, ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if ((getCustomJSONTypeDefinition().getType() instanceof IPushToServerSpecialType &&
			((IPushToServerSpecialType)getCustomJSONTypeDefinition().getType()).shouldAlwaysAllowIncommingJSON()) ||
			PushToServerEnum.allow.compareTo(elementDataConverterContext.getComputedPushToServerValue()) <= 0)
		{
			List<WT> list = new ArrayList<WT>();
			List<WT> previousWrappedBaseList = (previousChangeAwareList != null ? previousChangeAwareList.getWrappedBaseListForReadOnly() : null);
			List<Integer> adjustedNewValueIndexes = new ArrayList<>();

			for (int i = 0; i < array.length(); i++)
			{
				WT oldVal = null;
				if (previousWrappedBaseList != null && previousWrappedBaseList.size() > i)
				{
					oldVal = previousWrappedBaseList.get(i);
				}
				try
				{
					ValueReference<Boolean> returnValueAdjustedIncommingValueForIndex = new ValueReference<Boolean>(Boolean.FALSE);
					list.add((WT)JSONUtils.fromJSON(oldVal, array.opt(i), getCustomJSONTypeDefinition(), elementDataConverterContext,
						returnValueAdjustedIncommingValueForIndex));
					if (returnValueAdjustedIncommingValueForIndex.value.booleanValue()) adjustedNewValueIndexes.add(i);
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
				IWrappingContext wrappingContext = (elementDataConverterContext instanceof IWrappingContext ? (IWrappingContext)elementDataConverterContext
					: new WrappingContext(elementDataConverterContext.getWebObject(), pd.getName()));
				newBaseList = new WrapperList<ET, WT>(list, (IWrapperType<ET, WT>)elementType, pd, wrappingContext);
			}
			else
			{
				newBaseList = (List<ET>)list; // in this case ET == WT
			}

			// TODO how to handle previous null value here; do we need to re-send to client or not (for example initially both client and server had values, at the same time server==null client sends full update); how do we know case server version is unknown then
			ChangeAwareList<ET, WT> retVal = new ChangeAwareList<ET, WT>(newBaseList/* , dataConverterContext */,
				previousChangeAwareList != null ? previousChangeAwareList.increaseContentVersion() : 1);


			for (Integer idx : adjustedNewValueIndexes)
				retVal.getChangeSetter().markElementChangedByRef(idx.intValue());

			return retVal;
		}
		else
		{
			log.error("Property (" + pd + ") of '" + (elementDataConverterContext != null ? elementDataConverterContext.getWebObject() : null) +
				"' that doesn't define a suitable pushToServer value (allow/shallow/deep) on elements tried to update array element values through full update serverside." +
				"Denying and attempting to send back full value! Update JSON: " +
				array);
			if (previousChangeAwareList != null) previousChangeAwareList.getChangeSetter().markAllChanged();
			else if (returnValueAdjustedIncommingValue != null) returnValueAdjustedIncommingValue.value = Boolean.TRUE;
			// else no way to tell the system to re-send the server value to client when an client to server change deny happened

			return previousChangeAwareList;
		}
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, ChangeAwareList<ET, WT> changeAwareList, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		return toJSON(writer, key, changeAwareList, true, JSONUtils.FullValueToJSONConverter.INSTANCE, dataConverterContext);
	}

	@Override
	public JSONWriter changesToJSON(JSONWriter writer, String key, ChangeAwareList<ET, WT> changeAwareList, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		return toJSON(writer, key, changeAwareList, false, JSONUtils.FullValueToJSONConverter.INSTANCE, dataConverterContext);
	}

	protected JSONWriter toJSON(JSONWriter writer, String key, ChangeAwareList<ET, WT> changeAwareList, boolean fullValue,
		IToJSONConverter<IBrowserConverterContext> toJSONConverterForFullValue, IBrowserConverterContext dataConverterContext) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		if (changeAwareList != null)
		{
			ChangeAwareList<ET, WT>.Changes changes = changeAwareList.getChangesImmutableAndPrepareForReset();

			List<WT> wrappedBaseListReadOnly = changeAwareList.getWrappedBaseListForReadOnly();
			writer.object();

			if (changes.mustSendAll() || fullValue)
			{
				// send all (currently we don't support granular updates for add/remove but we could in the future)
				writer.key(CONTENT_VERSION).value(changeAwareList.increaseContentVersion());

				writer.key(VALUE).array();
				for (WT element : wrappedBaseListReadOnly)
				{
					toJSONConverterForFullValue.toJSONValue(writer, null, element, getCustomJSONTypeDefinition(), dataConverterContext);
				}
				writer.endArray();
			}
			else if (changes.getIndexesWithContentUpdates().size() > 0 || changes.getIndexesChangedByRef().size() > 0 ||
				changes.getRemovedIndexes().size() > 0 || changes.getAddedIndexes().size() > 0)
			{
				// else write changed indexes / granular update:
				writer.key(CONTENT_VERSION).value(changeAwareList.getListContentVersion());

				if (changes.getRemovedIndexes().size() > 0)
				{
					writer.key(CHANGE_TYPE_REMOVES).array();
					for (Integer idx : changes.getRemovedIndexes())
					{
						writer.value(idx);
					}
					writer.endArray();
				}
				if (changes.getAddedIndexes().size() > 0)
				{
					writeValues(writer, dataConverterContext, changes.getAddedIndexes(), wrappedBaseListReadOnly, CHANGE_TYPE_ADDITIONS);
				}
				if (changes.getIndexesChangedByRef().size() > 0)
				{
					writeValues(writer, dataConverterContext, changes.getIndexesChangedByRef(), wrappedBaseListReadOnly, CHANGE_TYPE_BY_REF);
				}
				if (changes.getIndexesWithContentUpdates().size() > 0)
				{
					writeValues(writer, dataConverterContext, changes.getIndexesWithContentUpdates(), wrappedBaseListReadOnly, CHANGE_TYPE_UPDATES);
				}
			}
			else
			{
				writer.key(NO_OP).value(true);
			}
			writer.endObject();
			changes.doneHandling();
		}
		else
		{
			writer.value(JSONObject.NULL); // TODO how to handle null values which have no version info (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?
		}
		return writer;
	}

	private void writeValues(JSONWriter writer, IBrowserConverterContext dataConverterContext, Collection<Integer> changes, List<WT> wrappedBaseListReadOnly,
		String changeType)
	{
		writer.key(changeType == CHANGE_TYPE_BY_REF ? CHANGE_TYPE_UPDATES : changeType).array(); // client doesn't care if it was a full or contents change - it only needs to know it was an update
		int i = 0;
		for (Integer idx : changes)
		{
			writer.object().key(INDEX).value(idx);
			if (changeType == CHANGE_TYPE_UPDATES)
			{
				JSONUtils.changesToBrowserJSONValue(writer, VALUE, wrappedBaseListReadOnly.get(idx.intValue()), getCustomJSONTypeDefinition(),
					dataConverterContext);
			}
			else // this has to be CHANGE_TYPE_ADDITIONS or CHANGE_TYPE_BY_REF - both should sent full value of that element
			{
				JSONUtils.toBrowserJSONFullValue(writer, VALUE, wrappedBaseListReadOnly.get(idx.intValue()), getCustomJSONTypeDefinition(),
					dataConverterContext);
			}
			writer.endObject();
		}
		writer.endArray();
	}

	@Override
	public boolean shouldAlwaysAllowIncommingJSON()
	{
		return true;
	}

	@Override
	public boolean writeClientSideTypeName(JSONWriter w, String keyToAddTo, PropertyDescription pd)
	{
		// writes ["JSON_arr", type] or ["JSON_arr", [type, elementPushToServer]] if the element declared a pushToServer value in spec file (through "elementConfig")
		JSONUtils.addKeyIfPresent(w, keyToAddTo);

		w.array().value(TYPE_NAME);
		PropertyDescription elementPD = getCustomJSONTypeDefinition();
		if (elementPD.getPushToServerAsDeclaredInSpecFile() != null) w.object().key(ClientSideTypeCache.PROPERTY_TYPE);
		if (elementPD.getType() instanceof IPropertyWithClientSideConversions< ? >)
		{
			boolean written = ((IPropertyWithClientSideConversions< ? >)elementPD.getType()).writeClientSideTypeName(w, null, elementPD);
			if (!written) w.value(null); // value type doesn't need client side conversions...
		}
		else
		{
			// value type doesn't need client side conversions...
			w.value(null);
		}
		if (elementPD.getPushToServerAsDeclaredInSpecFile() != null)
		{
			w.key(ClientSideTypeCache.PROPERTY_PUSH_TO_SERVER_VALUE).value(elementPD.getPushToServerAsDeclaredInSpecFile().ordinal());
			w.endObject();
		}
		w.endArray();

		return true;
	}

}
