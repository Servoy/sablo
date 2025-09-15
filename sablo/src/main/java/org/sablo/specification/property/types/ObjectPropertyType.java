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
package org.sablo.specification.property.types;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IPropertyConverterForBrowser;
import org.sablo.specification.property.IPropertyConverterForBrowserWithDynamicClientType;
import org.sablo.util.ValueReference;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IJSONStringWithClientSideType;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.JSONWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For 'object' typed values in .spec files.<br/><br/>
 *
 * It is also used through {@link #getJSONAndClientSideType(IToJSONConverter, Object, PropertyDescription, Object)} for default server to client conversions ({@link JSONUtils#defaultToJSONValue(IToJSONConverter, JSONWriter, String, Object, PropertyDescription, Object)})
 * that will most of the time not need to send any client side type/conversions but sometimes it also needs type.
 *
 * @author jcompagner, acostescu
 */
public class ObjectPropertyType extends DefaultPropertyType<Object> implements
	IPropertyConverterForBrowser<Object>, IPropertyConverterForBrowserWithDynamicClientType<Object>
{

	public static final ObjectPropertyType INSTANCE = new ObjectPropertyType();
	public static final String TYPE_NAME = "object";

	public static final JSONString OBJECT_TYPE_JSON_STRING = new JSONUtils.JSONStringWrapper('"' + TYPE_NAME + '"');
	private static final Logger log = LoggerFactory.getLogger(ObjectPropertyType.class.getCanonicalName());

	private static DatePropertyType dateType;
	private static JSONString dateTypeClientSideType;

	protected ObjectPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Object sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		return toJSONValueImpl(JSONUtils.FullValueToJSONConverter.INSTANCE, writer, key, sabloValue, propertyDescription, dataConverterContext);
	}

	public <ContextObject> JSONWriter toJSONValueImpl(IToJSONConverter<ContextObject> toJSONConverter, JSONWriter w, String key, Object value,
		PropertyDescription valueType, ContextObject contextObject) throws JSONException, IllegalArgumentException
	{
		IJSONStringWithClientSideType jsonValue = getJSONAndClientSideType(toJSONConverter, value, valueType,
			contextObject);

		if (jsonValue != null)
		{
			JSONUtils.addKeyIfPresent(w, key);

			if (jsonValue.getClientSideType() != null)
			{
				JSONUtils.writeConvertedValueWithClientType(w, null, jsonValue.getClientSideType(), () -> {
					w.value(jsonValue);
					return null;
				});
			}
			else w.value(jsonValue);
		} // else there was an error writing the value, which should already be reported in log (getJSONAndClientSideType does it); we write nothing for this value then
		return w;
	}

	@Override
	public Object fromJSON(Object newJSONValue, Object previousSabloValue, PropertyDescription propertyDescription, IBrowserConverterContext context,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newJSONValue instanceof JSONObject)
		{
			JSONObject newObjectValue = (JSONObject)newJSONValue;
			String dynamicType = newObjectValue.optString(JSONUtils.CONVERSION_CL_SIDE_TYPE_KEY);
			if (dynamicType != null && DatePropertyType.TYPE_NAME.equals(dynamicType))
			{
				if (dateType == null)
				{
					dateType = (DatePropertyType)TypesRegistry.getType(DatePropertyType.TYPE_NAME);
				}
				return dateType.fromJSON(newObjectValue.opt(JSONUtils.VALUE_KEY), previousSabloValue instanceof Date ? (Date)previousSabloValue : null,
					null, context, returnValueAdjustedIncommingValue); // registered date type must be ok with pd == null
			}
			else
			{
				for (String key : newObjectValue.keySet())
				{
					newObjectValue.put(key,
						fromJSON(newObjectValue.get(key), previousSabloValue, propertyDescription, context, returnValueAdjustedIncommingValue));
				}
			}
		}
		return newJSONValue;
	}

	/**
	 * The Object type which is also impl for JSONUtils.defaultToJSONValue will try to give (besides the JSON value) an 'object' client side type but only if it's nested and
	 * some child nested value (on any level) needs to send conversion info (basically 'date' type, or something from arg propertyDescription - TODO is propertyDescription still used by default conversions? or can it be removed?)
	 * It can also return a 'date' type if the value is a date.
	 *
	 * @returns null if the value could not be written; a value and type (the type can be null) if it could be written.
	 */
	public <ContextObject> IJSONStringWithClientSideType getJSONAndClientSideType(IToJSONConverter<ContextObject> toJSONConverter, Object sabloValue,
		PropertyDescription propertyDescription,
		ContextObject contextObject)
	{
		JSONString type = null;
		EmbeddableJSONWriter w = new EmbeddableJSONWriter(true);
		// there is no clear conversion; see if we find a primitive/default (including arrays/maps) or Class based conversion, use those
		Object converted = sabloValue;
		boolean valueWasWritten = true;

		if (converted == null || converted == JSONObject.NULL)
		{
			w.value(null); // null is allowed
		}
		else if (converted instanceof JSONArray || converted instanceof JSONObject || converted instanceof JSONString)
		{
			// we have no idea what contents are in 'converted' - it might contain client side type information; so write types
			// type = OBJECT_TYPE_JSON_STRING;
			// ACTUALLY, even before the client side types impl, when we'd send types in a separate conversion tree, this if would not write any type information;
			// so I guess no default conversion that contains the things in this if check needed to add type info
			w.value(converted);
		}
		else if (converted instanceof List || converted instanceof Object[])
		{
			List< ? > lst;
			if (converted instanceof Object[]) lst = Arrays.asList((Object[])converted);
			else lst = (List< ? >)converted;

			w.array();
			for (int i = 0; i < lst.size(); i++)
			{
				PropertyDescription elType = getArrayElementType(propertyDescription, i);
				IJSONStringWithClientSideType elValWithType = toJSONConverter.getConvertedValueWithClientType(lst.get(i), elType, contextObject, false);
				if (elValWithType != null && elValWithType.getClientSideType() != null)
				{
					type = OBJECT_TYPE_JSON_STRING; // will need 'object' nested conversions client-side

					JSONUtils.writeConvertedValueWithClientType(w, null,
						elValWithType.getClientSideType(),
						() -> {
							w.value(elValWithType);
							return null;
						});
				}
				else
				{
					w.value(elValWithType);
				}
			}
			w.endArray();
		}
		else if (converted instanceof Map)
		{
			w.object();
			Map<String, ? > map = (Map<String, ? >)converted;
			for (Entry<String, ? > entry : map.entrySet())
			{
				PropertyDescription subPropType = (propertyDescription != null ? propertyDescription.getProperty(entry.getKey()) : null);
				IJSONStringWithClientSideType subPropValWithType = toJSONConverter.getConvertedValueWithClientType(entry.getValue(), subPropType,
					contextObject, false);
				if (subPropValWithType != null)
				{
					if (subPropValWithType.getClientSideType() != null)
					{
						type = OBJECT_TYPE_JSON_STRING; // will need 'object' nested conversions client-side

						JSONUtils.writeConvertedValueWithClientType(w, entry.getKey(),
							subPropValWithType.getClientSideType(),
							() -> {
								w.value(subPropValWithType);
								return null;
							});
					}
					else
					{
						JSONUtils.addKeyIfPresent(w, entry.getKey());
						w.value(subPropValWithType);
					}
				}
			}
			w.endObject();
		}
		else if (converted instanceof JSONWritable)
		{
			TypedData<Map<String, Object>> dm = ((JSONWritable)converted).toMap();
			return getJSONAndClientSideType(toJSONConverter, dm.content, dm.contentType, contextObject);
		}
		// best-effort to still find a way to write data and convert if needed follows
		else if (converted instanceof Integer || converted instanceof Long)
		{
			w.value(((Number)converted).longValue());
		}
		else if (converted instanceof Boolean)
		{
			w.value(((Boolean)converted).booleanValue());
		}
		else if (converted instanceof Number)
		{
			double convertedDouble = ((Number)converted).doubleValue();
			if (Double.isNaN(convertedDouble) || Double.isInfinite(convertedDouble))
			{
				w.value(null);
			}
			else
			{
				w.value(convertedDouble);
			}
		}
		else if (converted instanceof String)
		{
			w.value(converted);
		}
		else if (converted instanceof CharSequence)
		{
			w.value(converted.toString());
		}
		else if (converted instanceof Date)
		{
			if (dateType == null)
			{
				dateType = (DatePropertyType)TypesRegistry.getType(DatePropertyType.TYPE_NAME);
				EmbeddableJSONWriter cltw = new EmbeddableJSONWriter(true);
				dateType.writeClientSideTypeName(cltw, null, null); // we rely here on the fact that the currently registered date type knows how to handle null PD
				dateTypeClientSideType = cltw;
			}

			type = OBJECT_TYPE_JSON_STRING; // as client side will remember the type from server, give it 'object' not directly 'date' because client side might want to assign later non-dates to this 'object' property and we do not want client to server conversion to fail with exception because it uses date converter...

			JSONUtils.writeConvertedValueWithClientType(w, null, dateTypeClientSideType,
				() -> {
					dateType.toJSON(w, null, (Date)converted, null, null); // we rely here on the fact that the currently registered date type knows how to handle null PD or context
					return null;
				});
		}
		else
		{
			valueWasWritten = false;
			// write nothing here, neither key nor value as we know not how to do the conversion...
			log.error(
				"[default or 'object' type toJSONValue] unsupported value type:" + propertyDescription + " for value: " +
					JSONUtils.safeToString(sabloValue) + " current json: " + w.toString(),
				new IllegalArgumentException("unsupported value type; see value in log entry"));
		}

		return valueWasWritten ? new JSONUtils.JSONStringWithClientSideType(w.toJSONString(), type) : null;
	}


	private static PropertyDescription getArrayElementType(PropertyDescription valueType, int i)
	{
		PropertyDescription elValueType = null;
		if (valueType != null)
		{
			elValueType = valueType.getProperty(String.valueOf(i));
		}
		return elValueType;
	}

	@Override
	public JSONString toJSONWithDynamicClientSideType(JSONWriter writer, Object sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		IJSONStringWithClientSideType valueAndType = getJSONAndClientSideType(FullValueToJSONConverter.INSTANCE, sabloValue, propertyDescription,
			dataConverterContext);
		if (valueAndType != null)
		{
			writer.value(valueAndType);
			return valueAndType.getClientSideType(); // should we always return here OBJECT_TYPE_JSON_STRING instead? maybe foundset or other types that
			// work with IPropertyConverterForBrowserWithDynamicClientType would compress better the types then, but that would also mean that all such
			// object client-side values would be iterated on at least one level, even if they don't actually need a client-side conversion; it's just
			// an optimization q., both approaches should work correctly
		}
		return null;
	}

}