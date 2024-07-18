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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.CustomObjectContext;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Type for what in spec files you see defined in the types section. (custom javascript object types)
 * It should be a kind of proxy for all possible conversion types to it's child types.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
// TODO these ET and WT are improper - as for object type they can represent multiple types (a different set for each child key), but they help to avoid some bugs at compile-time
public class CustomJSONObjectType<ET, WT> extends CustomJSONPropertyType<Map<String, ET>>
	implements IAdjustablePropertyType<Map<String, ET>>, IWrapperType<Map<String, ET>, ChangeAwareMap<ET, WT>>,
	ISupportsGranularUpdates<ChangeAwareMap<ET, WT>>, IPushToServerSpecialType, IPropertyWithClientSideConversions<Map<String, ET>>
{

	public static final String TYPE_NAME = "JSON_obj";

	protected static final String CONTENT_VERSION = "vEr";
	protected static final String UPDATES = "u";
	protected static final String KEY = "k";
	protected static final String VALUE = "v";
	protected static final String INITIALIZE = "in";
	protected static final String NO_OP = "n";

	protected static Set<String> angularAutoAddedKeysToIgnore = new HashSet<>();

	{
		angularAutoAddedKeysToIgnore.add("$$hashKey");
	}

	protected Map<String, IWrapperType<ET, WT>> wrapperChildProps;


	/**
	 * Creates a new type that handles objects of the given key types (with their own set of default value, config object)
	 *
	 * @param definition the defined types of the object's values. (per key)
	 */
	public CustomJSONObjectType(String customTypeName, PropertyDescription definition)
	{
		super(customTypeName, definition);
	}

	@Override
	public Map<String, ET> unwrap(ChangeAwareMap<ET, WT> value)
	{
		// this type will wrap an [] or List into a list; unwrap will simply return that list that will further wrap/unwrap elements as needed on any operation
		// look at this wrapped list as the external portal of the list property as far as BaseWebObjects are concerned
		return value;
	}

	public String getGenericName()
	{
		return TYPE_NAME;
	}

	@Override
	public ChangeAwareMap<ET, WT> wrap(Map<String, ET> value, ChangeAwareMap<ET, WT> previousValue, PropertyDescription propertyDescription,
		IWrappingContext dataConverterContext)
	{
		return internalWrap(value, previousValue, propertyDescription, dataConverterContext, null);
	}

	protected ChangeAwareMap<ET, WT> internalWrap(Map<String, ET> value, ChangeAwareMap<ET, WT> previousValue, PropertyDescription propertyDescription,
		IWrappingContext dataConverterContext, CustomObjectContext<ET, WT> initialComponentOrServiceExtension)
	{
		if (value instanceof ChangeAwareMap< ? , ? >) return (ChangeAwareMap<ET, WT>)value;

		Map<String, ET> wrappedMap = wrapMap(value, propertyDescription, dataConverterContext);
		if (wrappedMap != null)
		{
			// ok now we have the map or wrap map (depending on if child types are IWrapperType or not)
			// wrap this further into a change-aware map; this is used to be able to track changes and perform server to browser full or granular updates
			return new ChangeAwareMap<ET, WT>(wrappedMap, previousValue != null ? previousValue.getListContentVersion() + 1 : 1,
				initialComponentOrServiceExtension, getCustomJSONTypeDefinition());
		}
		return null;
	}

	protected IPropertyType<ET> getElementType(String childPropertyName)
	{
		return (IPropertyType<ET>)getCustomJSONTypeDefinition().getProperty(childPropertyName).getType();
	}

	protected Map<String, ET> wrapMap(Map<String, ET> value, PropertyDescription pd, IWrappingContext dataConverterContext)
	{
		// this type will wrap (if needed; that means it will end up as a normal list if element type is not wrapped type
		// or a WrapperList otherwise) an [] or List into a list; unwrap will simply return that list that will further
		// wrap/unwrap elements as needed on any operation
		// look at this wrapped list as the external portal of the list property as far as BaseWebObjects are concerned
		if (value != null)
		{
			if (value instanceof IWrappedBaseMapProvider)
			{
				// it's already what we want; return it
				return value;
			}
			Map<String, IWrapperType<ET, WT>> wrappingChildren = getChildPropsThatNeedWrapping();

			if (wrappingChildren == null || wrappingChildren.isEmpty())
			{
				// it's already what we want; return it
				return value;
			}

			return new WrapperMap<ET, WT>(value, wrappingChildren, pd, dataConverterContext, true);
		}
		return null;
	}

	protected Map<String, IWrapperType<ET, WT>> getChildPropsThatNeedWrapping()
	{
		if (wrapperChildProps == null)
		{
			wrapperChildProps = new HashMap<>();

			for (Entry<String, PropertyDescription> entry : getCustomJSONTypeDefinition().getProperties().entrySet())
			{
				Object type = entry.getValue().getType();
				if (type instanceof IWrapperType< ? , ? >) wrapperChildProps.put(entry.getKey(), (IWrapperType<ET, WT>)type);
			}
		}
		return wrapperChildProps;
	}

	@Override
	public ChangeAwareMap<ET, WT> fromJSON(Object newJSONValue, ChangeAwareMap<ET, WT> previousChangeAwareMap, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext, ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		PushToServerEnum pushToServerForWholeCustomObject = BrowserConverterContext.getPushToServerValue(dataConverterContext);

		JSONObject clientReceivedJSON;
		if (newJSONValue instanceof JSONObject && (clientReceivedJSON = (JSONObject)newJSONValue).has(CONTENT_VERSION) &&
			(clientReceivedJSON.has(VALUE) || clientReceivedJSON.has(UPDATES)))
		{
			try
			{
				if (previousChangeAwareMap == null || clientReceivedJSON.getInt(CONTENT_VERSION) == previousChangeAwareMap.getListContentVersion() ||
					clientReceivedJSON.getInt(CONTENT_VERSION) == 0 /*
																	 * full value change on client currently doesn't check server contentVersion because in some
																	 * cases client or server will not have access to an old content version
																	 */)
				{
					if (clientReceivedJSON.has(UPDATES))
					{
						if (previousChangeAwareMap == null)
						{
							log.warn("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
								"' is typed as json object; it got browser updates but server-side it is null; ignoring browser update. Update JSON: " +
								newJSONValue);
						}
						else
						{
							// here we operate directly on (wrapper) base map as this change doesn't need to be sent back to browser
							// as browser initiated it; also JSON conversions work on wrapped values
							Map<String, WT> wrappedBaseMap = previousChangeAwareMap.getWrappedBaseMapForReadOnly();

							JSONArray updatedRows = clientReceivedJSON.getJSONArray(UPDATES);

							boolean someUpdateAccessDenied = false;
							for (int i = updatedRows.length() - 1; i >= 0; i--)
							{
								JSONObject row = updatedRows.getJSONObject(i);
								String key = row.getString(KEY);
								Object val = row.opt(VALUE);

								PropertyDescription keyPD = getCustomJSONTypeDefinition().getProperty(key);
								// check that these updates are allowed

								if (keyPD != null)
								{
									PushToServerEnum pushToServerComputedOfSubprop = pushToServerForWholeCustomObject
										.combineWithChild(keyPD.getPushToServerAsDeclaredInSpecFile());
									if ((keyPD.getType() instanceof IPushToServerSpecialType &&
										((IPushToServerSpecialType)keyPD.getType()).shouldAlwaysAllowIncommingJSON()) ||
										PushToServerEnum.allow
											.compareTo(pushToServerComputedOfSubprop) <= 0)
									{
										ValueReference<Boolean> returnValueAdjustedIncommingValueForKey = new ValueReference<Boolean>(Boolean.FALSE);
										WT newWrappedEl = (WT)JSONUtils.fromJSON(wrappedBaseMap.get(key), val, keyPD,
											dataConverterContext == null ? null
												: dataConverterContext.newInstanceWithPushToServer(pushToServerComputedOfSubprop),
											returnValueAdjustedIncommingValueForKey);
										previousChangeAwareMap.putInWrappedBaseList(key, newWrappedEl, false);

										if (returnValueAdjustedIncommingValueForKey.value.booleanValue())
											previousChangeAwareMap.getChangeSetter().markElementChangedByRef(key); // if for example type is INTEGER and we got 3.3 from client it will probably be converted to 3 and it needs to be resent to client
									}
									else
									{
										someUpdateAccessDenied = true;
										log.error("Property (" + pd + "), subkey " + keyPD + " of '" +
											(dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
											"' that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to update custom object element value '" +
											keyPD + "' serverside. Denying and will attempt to send back full value! Update JSON: " + newJSONValue);
									}
								}
								else
								{
									if (!angularAutoAddedKeysToIgnore.contains(key)) log.warn("Property (" + pd + ") of '" +
										(dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
										"'. Cannot set property '" + key + "' of custom JSON Object '" + getName() +
										"' as it's type is undefined. Update JSON: " +
										newJSONValue);
								}
							}
							if (someUpdateAccessDenied) previousChangeAwareMap.getChangeSetter().markAllChanged();
						}
						return previousChangeAwareMap;
					}
					else
					{
						if (PushToServerEnum.allow.compareTo(pushToServerForWholeCustomObject) > 0)
						{
							log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
								"' that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to change the full custom object value serverside. Denying and attempting to send back full value! Update JSON: " +
								newJSONValue);
							if (previousChangeAwareMap != null) previousChangeAwareMap.getChangeSetter().markAllChanged();
							return previousChangeAwareMap;
						}

						// full replace
						return fullValueReplaceFromBrowser(previousChangeAwareMap, pd, dataConverterContext, clientReceivedJSON.getJSONObject(VALUE),
							returnValueAdjustedIncommingValue);
					}
				}
				else
				{
					log.info("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
						"' is typed as JSON object; it got browser updates (" + clientReceivedJSON.getInt(CONTENT_VERSION) +
						") but expected server version (" + previousChangeAwareMap.getListContentVersion() +
						") - so server changed meanwhile; ignoring browser update. Update JSON: " + newJSONValue);

					// dropped browser update because server object changed meanwhile;
					// will send a full update to have the correct value browser-side as well again (currently server side is leading / has more prio because not all server side values might support being recreated from client values)
					previousChangeAwareMap.resetDueToOutOfSyncIfNeeded(clientReceivedJSON.getInt(CONTENT_VERSION));

					return previousChangeAwareMap;
				}
			}
			catch (JSONException e)
			{
				log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
					"'. Cannot correctly parse custom JSON object property updates/values from browser. Update JSON: " + newJSONValue, e);
				return previousChangeAwareMap;
			}
		}
		else if (newJSONValue == null)
		{
			if (PushToServerEnum.allow.compareTo(pushToServerForWholeCustomObject) > 0)
			{
				log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
					"' that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to change the full custom object value serverside to null. Denying and attempting to send back full value! Update JSON: " +
					newJSONValue);
				if (previousChangeAwareMap != null) previousChangeAwareMap.getChangeSetter().markAllChanged();
				return previousChangeAwareMap;
			}

			return null;
		}
		else if (newJSONValue instanceof JSONObject)
		{
			if (((JSONObject)newJSONValue).has(NO_OP)) return previousChangeAwareMap;

			log.error("Property (" + pd + ") of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
				"' tried to change something from client (to server) but with unsupported content: " + newJSONValue);
			if (previousChangeAwareMap != null) previousChangeAwareMap.getChangeSetter().markAllChanged();
			return previousChangeAwareMap;
		}
		else
		{
			log.error("Property " + pd + " of '" + (dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
				"' is typed as custom JSON object, but the value received from client (to server) is not an JSONObject or supported update value: " +
				newJSONValue);
			return previousChangeAwareMap;
		}
	}

	protected ChangeAwareMap<ET, WT> fullValueReplaceFromBrowser(ChangeAwareMap<ET, WT> previousChangeAwareMap, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext, JSONObject clientReceivedJSON, ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		PushToServerEnum pushToServerForWholeCustomObject = BrowserConverterContext.getPushToServerValue(dataConverterContext);

		Map<String, WT> map = new HashMap<String, WT>();
		Map<String, WT> previousWrappedBaseMap = (previousChangeAwareMap != null ? previousChangeAwareMap.getWrappedBaseMapForReadOnly() : null);
		List<String> adjustedNewValueKeys = new ArrayList<>();

		Iterator<String> it = clientReceivedJSON.keys();
		while (it.hasNext())
		{
			String key = it.next();
			WT oldVal = null;
			PropertyDescription keyPD = getCustomJSONTypeDefinition().getProperty(key);
			if (keyPD != null)
			{
				PushToServerEnum pushToServerComputedOfSubprop = pushToServerForWholeCustomObject
					.combineWithChild(keyPD.getPushToServerAsDeclaredInSpecFile());

				if ((keyPD.getType() instanceof IPushToServerSpecialType &&
					((IPushToServerSpecialType)keyPD.getType()).shouldAlwaysAllowIncommingJSON()) ||
					PushToServerEnum.allow
						.compareTo(pushToServerComputedOfSubprop) <= 0)
				{
					if (previousWrappedBaseMap != null)
					{
						oldVal = previousWrappedBaseMap.get(key);
					}
					try
					{
						ValueReference<Boolean> returnValueAdjustedIncommingValueForKey = new ValueReference<Boolean>(Boolean.FALSE);
						// TODO although this is a full change, we give oldVal because client side does the same for some reason,
						// but normally both should use undefined/null for old value of subprops as this is a full change; SVY-17854 is created for looking into this
						map.put(key, (WT)JSONUtils.fromJSON(oldVal, clientReceivedJSON.opt(key), getCustomJSONTypeDefinition().getProperty(key),
							dataConverterContext == null ? null : dataConverterContext.newInstanceWithPushToServer(pushToServerComputedOfSubprop),
							returnValueAdjustedIncommingValueForKey));

						if (returnValueAdjustedIncommingValueForKey.value.booleanValue()) adjustedNewValueKeys.add(key);
					}
					catch (JSONException e)
					{
						log.error("Cannot parse JSON object element browser JSON.", e);
					}
				}
				else
				{
					adjustedNewValueKeys.add(key); // re-send the server value to client when an client to server change deny happened
					log.error("Property (" + pd + "), subkey " + keyPD + " of '" +
						(dataConverterContext != null ? dataConverterContext.getWebObject() : null) +
						"' that doesn't define a suitable pushToServer value (allow/shallow/deep) tried to update custom object element value '" +
						keyPD + "' serverside (throught full obj. value). Denying and will attempt to send back full value! Update JSON: " +
						clientReceivedJSON);
				}
			}
			else
			{
				if (!angularAutoAddedKeysToIgnore.contains(key))
					log.warn("Cannot set property '" + key + "' of custom JSON Object '" + getName() + "' as it's type is undefined.");
			}
		}

		Map<String, ET> newBaseMap;
		Map<String, IWrapperType<ET, WT>> wrappingChildren = getChildPropsThatNeedWrapping();
		if (wrappingChildren != null)
		{
			IWrappingContext wrappingContext = (dataConverterContext instanceof IWrappingContext ? (IWrappingContext)dataConverterContext
				: new WrappingContext(dataConverterContext == null ? null : dataConverterContext.getWebObject(), pd.getName()));
			newBaseMap = new WrapperMap<ET, WT>(map, wrappingChildren, pd, wrappingContext);
		}
		else
		{
			newBaseMap = (Map<String, ET>)map; // in this case ET == WT
		}

		ChangeAwareMap<ET, WT> retVal = new ChangeAwareMap<ET, WT>(newBaseMap, 1,
			/* TODO we should have here access to webObjectContext ... and give ChangeAwareMap.getOrCreateComponentOrServiceExtension(webObjectContext); */null,
			getCustomJSONTypeDefinition());

		for (String key : adjustedNewValueKeys)
			retVal.getChangeSetter().markElementChangedByRef(key);

		return retVal;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, ChangeAwareMap<ET, WT> changeAwareMap, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		return toJSON(writer, key, changeAwareMap, true, JSONUtils.FullValueToJSONConverter.INSTANCE, dataConverterContext);
	}

	@Override
	public JSONWriter changesToJSON(JSONWriter writer, String key, ChangeAwareMap<ET, WT> changeAwareMap, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		return toJSON(writer, key, changeAwareMap, false, JSONUtils.FullValueToJSONConverter.INSTANCE, dataConverterContext);
	}

	protected JSONWriter toJSON(JSONWriter writer, String key, ChangeAwareMap<ET, WT> changeAwareMap, boolean fullValue,
		IToJSONConverter<IBrowserConverterContext> toJSONConverterForFullValue, IBrowserConverterContext dataConverterContext) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		if (changeAwareMap != null)
		{
			ChangeAwareMap<ET, WT>.Changes changes = changeAwareMap.getChangesImmutableAndPrepareForReset();

			Map<String, WT> wrappedBaseMap = changeAwareMap.getWrappedBaseMapForReadOnly();
			writer.object();
			if (changes.mustSendAll() || fullValue)
			{
				PushToServerEnum pushToServer = BrowserConverterContext.getPushToServerValue(dataConverterContext);
				// send all (currently we don't support granular updates for remove but we could in the future)
				writer.key(CONTENT_VERSION).value(changeAwareMap.increaseContentVersion());


				writer.key(VALUE).object();
				for (Entry<String, WT> e : wrappedBaseMap.entrySet())
				{
					PropertyDescription childPD = getCustomJSONTypeDefinition().getProperty(e.getKey());
					toJSONConverterForFullValue.toJSONValue(writer, e.getKey(), wrappedBaseMap.get(e.getKey()),
						childPD,
						dataConverterContext == null ? null : dataConverterContext.newInstanceWithPushToServer(
							pushToServer.combineWithChild(childPD != null ? childPD.getPushToServerAsDeclaredInSpecFile() : PushToServerEnum.reject)));
				}
				writer.endObject();
			}
			else
			{
				Set<String> keysWithUpdates = changes.getKeysWithUpdates();
				Set<String> keysChangedByRef = changes.getKeysChangedByRef();

				if (keysWithUpdates.size() > 0 || keysChangedByRef.size() > 0)
				{

					// else write changed indexes / granular update:
					writer.key(CONTENT_VERSION).value(changeAwareMap.getListContentVersion());

					writer.key(UPDATES).array();

					// we only get here if fullValue == false (so this is not a fullToJSON (or servoy initialToJSON))
					// we will write granular update with fully value of a changed key if we have changes by reference; for changed keys by content we will write updates from that key
					writeValueForChangedElements(writer, dataConverterContext, wrappedBaseMap, keysWithUpdates, true);
					writeValueForChangedElements(writer, dataConverterContext, wrappedBaseMap, keysChangedByRef, false);

					writer.endArray();
				}
				else
				{
					writer.key(NO_OP).value(true);
				}
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

	protected void writeValueForChangedElements(JSONWriter writer, IBrowserConverterContext dataConverterContext, Map<String, WT> wrappedBaseMap,
		Set<String> keysWithUpdates, boolean keysWithUpdatedContent)
	{
		PushToServerEnum pushToServer = BrowserConverterContext.getPushToServerValue(dataConverterContext);

		for (String k : keysWithUpdates)
		{
			writer.object().key(KEY).value(k);
			PropertyDescription childPD = getCustomJSONTypeDefinition().getProperty(k);
			if (keysWithUpdatedContent)
			{
				// this method is only called when the custom object is requested to send updates (not full values); so we can assume that we can send only changes if possible (this method will never be expected to fully send properties, no matter how they changed)
				// the value of these keys has changed content inside it - let it send only changes (if it is change aware of course)
				JSONUtils.changesToBrowserJSONValue(writer, VALUE, wrappedBaseMap.get(k), childPD,
					dataConverterContext == null ? null : dataConverterContext.newInstanceWithPushToServer(
						pushToServer.combineWithChild(childPD != null ? childPD.getPushToServerAsDeclaredInSpecFile() : PushToServerEnum.reject)));
			}
			else
			{
				// the value has changed completely by reference; send it's full contents
				JSONUtils.toBrowserJSONFullValue(writer, VALUE, wrappedBaseMap.get(k), childPD,
					dataConverterContext == null ? null : dataConverterContext.newInstanceWithPushToServer(
						pushToServer.combineWithChild(childPD != null ? childPD.getPushToServerAsDeclaredInSpecFile() : PushToServerEnum.reject)));
			}
			writer.endObject();
		}
	}

	@Override
	public boolean shouldAlwaysAllowIncommingJSON()
	{
		return true;
	}

	@Override
	public boolean writeClientSideTypeName(JSONWriter w, String keyToAddTo, PropertyDescription pd)
	{
		JSONUtils.addKeyIfPresent(w, keyToAddTo);

		w.array().value(TYPE_NAME).value(pd.getType().getName()).endArray();
		return true;
	}

}
