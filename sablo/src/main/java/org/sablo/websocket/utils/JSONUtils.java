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

package org.sablo.websocket.utils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyConverterForBrowser;
import org.sablo.specification.property.IPropertyConverterForBrowserWithDynamicClientType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.IPropertyWithClientSideConversions;
import org.sablo.specification.property.ISupportsGranularUpdates;
import org.sablo.specification.property.ISupportsGranularUpdatesWithDynamicClientType;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.ObjectPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.util.DebugFriendlyJSONStringer;
import org.sablo.util.ValueReference;
import org.sablo.websocket.TypedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility methods for JSON usage.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class JSONUtils
{

	public static final String CONVERSION_CL_SIDE_TYPE_KEY = "_T";
	public static final String VALUE_KEY = "_V";

	private static final Logger log = LoggerFactory.getLogger(JSONUtils.class.getCanonicalName());
	private static ObjectPropertyType objectPropertyType;


	public static JSONWriter writeDataAsFullToJSON(JSONWriter writer, Map<String, ? > data, PropertyDescription dataTypes, BrowserConverterContext context)
		throws JSONException
	{
		writeData(FullValueToJSONConverter.INSTANCE, writer, data, dataTypes, context);
		return writer;
	}

	public static <ContextT> void writeData(IToJSONConverter<ContextT> converter, JSONWriter writer, Map<String, ? > data, PropertyDescription dataTypes,
		ContextT contextObject) throws JSONException
	{
		for (Entry<String, ? > entry : data.entrySet())
		{
			converter.toJSONValue(writer, entry.getKey(), entry.getValue(), dataTypes != null ? dataTypes.getProperty(entry.getKey()) : null, contextObject);
		}
	}

	public static String writeDataAsFullToJSON(Map<String, ? > data, PropertyDescription dataTypes, BrowserConverterContext context) throws JSONException
	{
		return writeData(FullValueToJSONConverter.INSTANCE, data, dataTypes, context);
	}

	public static String writeChanges(Map<String, ? > data, PropertyDescription dataTypes, BrowserConverterContext context) throws JSONException
	{
		return writeData(ChangesToJSONConverter.INSTANCE, data, dataTypes, context);
	}

	public static <ContextT> String writeData(IToJSONConverter<ContextT> converter, Map<String, ? > data, PropertyDescription dataTypes, ContextT contextObject)
		throws JSONException
	{
		JSONWriter writer = new JSONStringer().object();
		writeData(converter, writer, data, dataTypes, contextObject);
		return writer.endObject().toString();
	}

	public static <ContextT> String writeComponentChanges(WebComponent component, ChangesToJSONConverter converter) throws JSONException
	{
		JSONWriter writer = new JSONStringer().object();
		if (component.writeOwnChanges(writer, "comp", component.getName(), converter)) writer.endObject();
		return writer.endObject().toString();
	}

	/**
	 * Shortcut for using {@link FullValueToJSONConverter} directly.
	 *
	 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 */
	public static JSONWriter toBrowserJSONFullValue(JSONWriter writer, String key, Object value, PropertyDescription valueType,
		IBrowserConverterContext context) throws JSONException, IllegalArgumentException
	{
		return JSONUtils.FullValueToJSONConverter.INSTANCE.toJSONValue(writer, key, value, valueType, context);
	}

	/**
	 * Shortcut for using {@link ChangesToJSONConverter} directly.
	 *
	 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 */
	public static JSONWriter changesToBrowserJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType,
		IBrowserConverterContext context) throws JSONException, IllegalArgumentException
	{
		return JSONUtils.ChangesToJSONConverter.INSTANCE.toJSONValue(writer, key, value, valueType, context);
	}

	public static JSONWriter addKeyIfPresent(JSONWriter writer, String key) throws JSONException
	{
		if (key != null) writer.key(key);
		return writer;
	}

	/**
	 * Writes the given object into the JSONWriter. (it is meant to be used for transforming the basic types that can be sent by beans/components)
	 *
	 * @param toJSONConverter
	 * @param writer the JSONWriter.
	 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 * @param value the value to be written to the writer.
	 * @param valueType the types of the value; can be null in which case a 'best-effort' to JSON conversion will take place.
	 * @return true if the given value could be written using default logic and false otherwise.
	 * @throws IllegalArgumentException if the given object could not be written to JSON for some reason.
	 */
	public static <ContextObject> void defaultToJSONValue(IToJSONConverter<ContextObject> toJSONConverter, JSONWriter w, String key, Object value,
		PropertyDescription valueType, ContextObject contextObject) throws JSONException, IllegalArgumentException
	{
		if (objectPropertyType == null)
		{
			objectPropertyType = (ObjectPropertyType)TypesRegistry.getType(ObjectPropertyType.TYPE_NAME); // this will throw an exception if not found
		}

		objectPropertyType.toJSONValueImpl(toJSONConverter, w, key, value, valueType, contextObject);
	}

	public static <ContextObject> IJSONStringWithClientSideType getDefaultConvertedValueWithClientType(IToJSONConverter<ContextObject> toJSONConverter,
		Object value, PropertyDescription valueType, ContextObject context)
	{
		if (objectPropertyType == null)
		{
			objectPropertyType = (ObjectPropertyType)TypesRegistry.getType(ObjectPropertyType.TYPE_NAME); // this will throw an exception if not found
		}

		return objectPropertyType.getJSONAndClientSideType(toJSONConverter, value, valueType, context);
	}

	public static Object defaultFromJSON(Object newJSONValue, Object previousSabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext context,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (objectPropertyType == null)
		{
			objectPropertyType = (ObjectPropertyType)TypesRegistry.getType(ObjectPropertyType.TYPE_NAME); // this will throw an exception if not found
		}

		return objectPropertyType.fromJSON(newJSONValue, previousSabloValue, propertyDescription, context, returnValueAdjustedIncommingValue);
	}

	/**
	 * This method handles sending typed default values to client; it will generate instead of a value an object with two keys:
	 * { _T: "typeName", v: ... } that will be handled correctly by the appropriate default conversion code client-side (if found in a value with unknown client conversion type).<br/><br/>
	 *
	 * This is not that nice but helps remove a lot of code where types were kept in parallel to the written values. But now as all static needed client side types are sent separately to each window
	 * when a container is shown in that window - the only thing that still needed to send client side conversion types when writing the values are default conversions and dynamic types (types that send
	 * determine their client side type at runtime) => the need for this hackish method.
	 *
	 * @param w the json writer used to send content to browser.
	 * @param key the key that this value should be written to - if any value will be written.
	 * @param typeOfValue could be anything that a {@link IPropertyWithClientSideConversions#writeClientSideTypeName(JSONWriter, String, PropertyDescription)} returns. Usually just a client side type name.
	 * @param valueWriter the callable that writes the actual value to w.
	 */
	public static void writeConvertedValueWithClientType(JSONWriter w, String key, JSONString typeOfValue, Callable<Void> valueWriter) throws JSONException
	{
		addKeyIfPresent(w, key);
		w.object();
		w.key(CONVERSION_CL_SIDE_TYPE_KEY);
		w.value(typeOfValue).key(VALUE_KEY);
		try
		{
			valueWriter.call();
		}
		catch (Exception e)
		{
			if (e instanceof JSONException) throw (JSONException)e;
			else log.error("Error while trying to write default converted value with type: " + typeOfValue + " and key: " + key, e);

		}
		w.endObject();
	}

	public static void writeConvertedValueWithClientType(JSONWriter w, String key, EmbeddableJSONWriter typeOfValue, Callable<Void> valueWriter)
		throws JSONException
	{
		if (typeOfValue.isEmpty())
		{
			// it doesn't actually want to write a client side type although it appeared to have one
			addKeyIfPresent(w, key);
			try
			{
				valueWriter.call();
			}
			catch (Exception e)
			{
				if (e instanceof JSONException) throw (JSONException)e;
				else log.error("Error while trying to write default converted value with type: " + typeOfValue + " and key: " + key, e);

			}
		}
		else writeConvertedValueWithClientType(w, key, (JSONString)typeOfValue, valueWriter);
	}

	public static EmbeddableJSONWriter getClientSideTypeJSONString(PropertyDescription pd)
	{
		if (!(pd.getType() instanceof IPropertyWithClientSideConversions)) return null;

		return getClientSideTypeJSONString((IPropertyWithClientSideConversions< ? >)pd.getType(), pd);
	}

	public static EmbeddableJSONWriter getClientSideTypeJSONString(IPropertyWithClientSideConversions< ? > type, PropertyDescription pd)
	{
		EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true);
		return type.writeClientSideTypeName(ejw, null, pd) ? ejw : null;
	}

	public static Object fromJSONUnwrapped(Object previousComponentValue, Object newJSONValue, PropertyDescription pd,
		IBrowserConverterContext dataConversionContext, ValueReference<Boolean> returnValueAdjustedIncommingValue) throws JSONException
	{
		Object value = fromJSON(previousComponentValue, newJSONValue, pd, dataConversionContext, returnValueAdjustedIncommingValue);
		if (pd != null && pd.getType() instanceof IWrapperType< ? , ? >)
		{
			// will probably never happen as all this fromJSON thing was only meant for Dates (at least currently)
			IWrapperType wType = ((IWrapperType< ? , ? >)pd.getType());
			value = wType.unwrap(value);
		}
		return value;
	}

	/**
	 * Returns the object to be set directly in a BaseWebObject properties map. For wrapper types this means a wrapped value directly.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object fromJSON(Object oldValue, Object newValue, PropertyDescription pd, IBrowserConverterContext dataConversionContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue) throws JSONException
	{
		if (newValue == JSONObject.NULL) newValue = null;
		if (pd != null)
		{
			IPropertyType< ? > type = pd.getType();
			if (type instanceof IPropertyConverterForBrowser< ? >)
			{
				return ((IPropertyConverterForBrowser)type).fromJSON(newValue, oldValue, pd, dataConversionContext, returnValueAdjustedIncommingValue);
			}
		}
		else return defaultFromJSON(newValue, oldValue, pd, dataConversionContext, returnValueAdjustedIncommingValue);

		return newValue;
	}

	public static String safeToString(Object value)
	{
		try
		{
			return String.valueOf(value);
		}
		catch (Exception e)
		{
			return "toString failed for instanceof " + value.getClass();
		}
	}


	/**
	 * Validates a String to be valid JSON content and normalizes it.
	 * @param json the json content to check.
	 * @return the given JSON normalized.
	 * @throws JSONException if the given JSON is not valid
	 */
	public static String validateAndTrimJSON(String json) throws JSONException
	{
		if (json == null) return null;

		return new JSONObject(json).toString(); // just to validate - can we do this nicer with available lib (we might not need the "normalize" part)?
	}

//	/**
//	 * Adds all properties of the given object as key-value pairs in the writer.
//	 * @param propertyWriter the writer.
//	 * @param objectToMerge the object contents to be merged into the writer prepared object.
//	 * @throws JSONException if the writer is not prepared (to write object contents) or other json exception occurs.
//	 */
//	public static void addObjectPropertiesToWriter(JSONWriter propertyWriter, JSONObject objectToMerge) throws JSONException
//	{
//		Iterator< ? > it = objectToMerge.keys();
//		while (it.hasNext())
//		{
//			String key = (String)it.next();
//			propertyWriter.key(key).value(objectToMerge.get(key));
//		}
//	}

	public static interface JSONWritable
	{
		TypedData<Map<String, Object>> toMap();
	}

	public static interface IToJSONConverter<ContextType>
	{
		/**
		 * Converts from a value to JSON form (that can be sent to the browser) and writes to "writer".
		 * @param writer the JSON writer to write to
		 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
		 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
		 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
		 * @param value the value to be converted and written.
		 * @param valueType the type of the property as described in the spec file
		 * @param clientConversion client conversion markers that can be set and if set will be used client side to interpret the data properly.
		 * @param context an object representing a state that the conversions of this type might need.
		 * @return the JSON writer for easily continuing the write process in the caller.
		 */
		JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, ContextType context)
			throws JSONException, IllegalArgumentException;

		/**
		 * Should do the same as {@link #toJSONValue(JSONWriter, String, Object, PropertyDescription, Object)} above but instead of directly
		 * writing the value (which for {@link IPropertyConverterForBrowserWithDynamicClientType} or {@link ISupportsGranularUpdatesWithDynamicClientType} might mean also writing the type in the value,
		 * and for static client side types would mean not writing the type as it is assumed that it is known on client)
		 * it will try to return the actual value and the client side type of that value (if any and taking into account "returnOnlyDynamicTypes" param).<br/><br/>
		 *
		 * Use {@link #toJSONValue(JSONWriter, String, Object, PropertyDescription, Object)} instead when possible; it does less string concatenations.
		 *
		 * @param returnOnlyDynamicTypes if true, only types for IPropertyConverterForBrowserWithDynamicClientType will be returned; static IPropertyWithClientSideConversions values are just written without returning the type; this is only useful for when sending component property values - where client already knows the IPropertyWithClientSideConversions client-side
		 */
		IJSONStringWithClientSideType getConvertedValueWithClientType(Object value, PropertyDescription valueType,
			ContextType context, boolean returnOnlyDynamicTypes);

	}

	public static class FullValueToJSONConverter implements IToJSONConverter<IBrowserConverterContext>
	{

		public static final FullValueToJSONConverter INSTANCE = new FullValueToJSONConverter();

		@Override
		public JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, IBrowserConverterContext context)
			throws JSONException, IllegalArgumentException
		{
			ValueReference<Boolean> typeDeterminedBasedOnClassOfValue = new ValueReference<Boolean>(Boolean.FALSE);
			IPropertyConverterForBrowser convertingTypeToUse = findConvertingTypeToBrowser(value, valueType, typeDeterminedBasedOnClassOfValue);
			if (convertingTypeToUse != null)
			{
				try
				{
					if (typeDeterminedBasedOnClassOfValue.value.booleanValue() && convertingTypeToUse instanceof IPropertyWithClientSideConversions)
					{
						// so the IPropertyType that will be used when writing toJSON was initially unknown and then determined based on the java class of 'value' to be a IPropertyWithClientSideConversions...
						// (for example if it's a Date)
						// this is a similar scenario to writing a dynamic type (it was dynamically determined), even though the type is not a IPropertyConverterForBrowserWithDynamicClientType itself but just IPropertyWithClientSideConversions.
						// so because the code that calls this toJSON and the one that will use this value on client do not know it's type based on .spec
						// the client side type needs to be sent here as well
						JSONUtils.writeConvertedValueWithClientType(writer, key,
							JSONUtils.getClientSideTypeJSONString((IPropertyWithClientSideConversions< ? >)convertingTypeToUse, valueType),
							() -> {
								convertingTypeToUse.toJSON(writer, null, value, valueType, context);
								return null;
							});
					}
					else
					{
						// usual scenario where PropertyDescription gives type or the determined type has no client side implementation
						return convertingTypeToUse.toJSON(writer, key, value, valueType, context);
					}
					// here if convertingTypeToUse is also IPropertyConverterForBrowserWithDynamicClientType then it would write itself the type to JSON - if needed
				}
				catch (Exception ex)
				{
					log.error("Error while converting value (toJSON): " + safeToString(value) + " of key: " + key + " to type: " + convertingTypeToUse +
						" current json: " + writer.toString(), ex);
					return writer;
				}
			}
			else defaultToJSONValue(this, writer, key, value, valueType, context);

			return writer;
		}

		protected IPropertyConverterForBrowser< ? > findConvertingTypeToBrowser(Object value, PropertyDescription valueType,
			ValueReference<Boolean> typeDeterminedBasedOnClassOfValue)
		{
			if (value != null && valueType != null)
			{
				IPropertyType< ? > type = valueType.getType();

				if (type instanceof ObjectPropertyType)
				{
					// for 'object' defined in .spec, which does implement IPropertyConverterForBrowser
					// but it is just a default conversion actually, we will give precedence to
					// any IClassPropertyType implementations
					IPropertyConverterForBrowser< ? > classType = findClassPropertyType(value, typeDeterminedBasedOnClassOfValue);
					if (classType != null) return classType;
					// else go to next if and return the ObjectPropertyType
				}

				if (type instanceof IPropertyConverterForBrowser) // this includes IWrapperType
				{
					return (IPropertyConverterForBrowser< ? >)type;
				}
			}

			return findClassPropertyType(value, typeDeterminedBasedOnClassOfValue);
		}

		private IPropertyConverterForBrowser< ? > findClassPropertyType(Object value, ValueReference<Boolean> typeDeterminedBasedOnClassOfValue)
		{
			// best-effort to still find a way to write data and convert if needed follows
			IClassPropertyType< ? > classType = value == null ? null : TypesRegistry.getType(value.getClass());
			if (classType != null)
			{
				if (typeDeterminedBasedOnClassOfValue != null) typeDeterminedBasedOnClassOfValue.value = Boolean.TRUE;
				return classType;
			} // else we have no usable converting type; caller should use default conversion

			return null;
		}

		/**
		 * This method returns a IJSONStringWithClientSideType that contains the output of writing a property's value to client side and separately the client side type for that value (if any).<br/>
		 * This is meant to be called by {@link IPropertyConverterForBrowserWithDynamicClientType} properties when computing the value and the type they actually have based on another type (that here are
		 * represented by "valueType" arg.<br/><br/>
		 *
		 * For example a data driven type can decide at runtime if it's data is a number (no client side conversion) or a date ('date' client side conversion).<br/><br/>
		 *
		 * <ul>
		 * <li>if "valueType"'s type is itself a {@link IPropertyConverterForBrowserWithDynamicClientType} it will just ask it for what it needs;</li>
		 * <li>if "valueType"'s type is just of a{@link IPropertyWithClientSideConversions} type it will just use {@link FullValueToJSONConverter#toJSONValue(JSONWriter, String, Object, PropertyDescription, IBrowserConverterContext)}} to get the content and will get the type from {@link IPropertyWithClientSideConversions#writeClientSideTypeName(JSONWriter, String, PropertyDescription)};</li>
		 * <li>otherwise, it will just write the value to JSON using {@link FullValueToJSONConverter#toJSONValue(JSONWriter, String, Object, PropertyDescription, IBrowserConverterContext)}} and have a null client side type.
		 *
		 * @param value the value of a property
		 * @param valueType the property description of that property.
		 * @param context context
		 * @param returnOnlyDynamicTypes if true, only types for IPropertyConverterForBrowserWithDynamicClientType will be returned; static IPropertyWithClientSideConversions values are just written without returning the type; this is only useful for when sending component property values - where client already knows the IPropertyWithClientSideConversions client-side
		 *
		 * @return a IJSONStringWithClientSideType representing the value (that can be embedded in a larger JSON) and the client side conversion type (if any).
		 */
		@Override
		public IJSONStringWithClientSideType getConvertedValueWithClientType(Object value, PropertyDescription valueType, IBrowserConverterContext context,
			boolean returnOnlyDynamicTypes)
		{
			ValueReference<Boolean> typeDeterminedBasedOnClassOfValue = new ValueReference<Boolean>(Boolean.FALSE);
			IPropertyType< ? > type = (IPropertyType< ? >)findConvertingTypeToBrowser(value, valueType, typeDeterminedBasedOnClassOfValue);

			if (type instanceof IPropertyConverterForBrowserWithDynamicClientType)
			{
				EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true); // that 'true' is a workaround for allowing directly a value instead of object or array
				JSONString clientSideConversionType;
				try
				{
					clientSideConversionType = ((IPropertyConverterForBrowserWithDynamicClientType)type).toJSONWithDynamicClientSideType(ejw, value, valueType,
						context);
					return ejw.isEmpty() ? null : new JSONStringWithClientSideType(ejw.toJSONString(), clientSideConversionType);
				}
				catch (Exception ex)
				{
					log.error("Error while converting value (with dynamic type) (toJSON with type): " + safeToString(value) + " to type: " + type +
						" current json: " + ejw.toString(), ex);
					return null;
				}
			}
			else if (type instanceof IPropertyConverterForBrowser)
			{
				JSONString clientSideConversionType;
				EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true); // that 'true' is a workaround for allowing directly a value instead of object or array
				try
				{
					((IPropertyConverterForBrowser)type).toJSON(ejw, null, value, valueType, context);
					// in the condition below, typeDeterminedBasedOnClassOfValue.value.booleanValue() can be assimilated to a dynamic type scenario as the type was determined based on java class of value, it was not known initially
					if ((!returnOnlyDynamicTypes || typeDeterminedBasedOnClassOfValue.value.booleanValue()) &&
						type instanceof IPropertyWithClientSideConversions)
					{
						clientSideConversionType = JSONUtils.getClientSideTypeJSONString((IPropertyWithClientSideConversions)type,
							valueType != null && valueType.getType() == type ? valueType : null);
					} // IPropertyWithDynamicClientSideConversions was already handled in code above so it won't be that here!
					else clientSideConversionType = null;

					return ejw.isEmpty() ? null : new JSONStringWithClientSideType(ejw.toJSONString(), clientSideConversionType);
				}
				catch (Exception ex)
				{
					log.error("Error while converting value (toJSON with type): " + safeToString(value) + " to type: " + type +
						" current json: " + ejw.toString(), ex);
					return null;
				}
			}
			else
			{
				// default conversion
				return getDefaultConvertedValueWithClientType(this, value, valueType, context);
			}
		}

	}

	public static class ChangesToJSONConverter extends FullValueToJSONConverter
	{

		public static final ChangesToJSONConverter INSTANCE = new ChangesToJSONConverter();

		@Override
		public JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, IBrowserConverterContext context)
			throws JSONException, IllegalArgumentException
		{
			if (value != null && valueType != null)
			{
				IPropertyType< ? > type = valueType.getType();
				if (type instanceof ISupportsGranularUpdates)
				{
					// good, we now know that it can send changes only
					try
					{
						return ((ISupportsGranularUpdates)type).changesToJSON(writer, key, value, valueType, context);
						// if type is ISupportsGranularUpdatesWithDynamicClientType it would write the dynamic type to JSON as well if needed
					}
					catch (Exception ex)
					{
						log.error("Error while writing changes for value (changesToJSON): " + safeToString(value) + " to type: " + type + " current json: " +
							writer.toString(), ex);
						return writer;
					}
				}
			}

			// for most values that don't support granular updates use full value to JSON
			super.toJSONValue(writer, key, value, valueType, context);

			return writer;
		}

		@Override
		public IJSONStringWithClientSideType getConvertedValueWithClientType(Object value, PropertyDescription valueType, IBrowserConverterContext context,
			boolean returnOnlyDynamicTypes)
		{
			ValueReference<Boolean> typeDeterminedBasedOnClassOfValue = new ValueReference<Boolean>(Boolean.FALSE);
			IPropertyType< ? > type = (IPropertyType< ? >)findConvertingTypeToBrowser(value, valueType, typeDeterminedBasedOnClassOfValue);

			if (type instanceof ISupportsGranularUpdatesWithDynamicClientType)
			{
				EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true); // that 'true' is a workaround for allowing directly a value instead of object or array
				JSONString clientSideConversionType;
				try
				{
					clientSideConversionType = ((ISupportsGranularUpdatesWithDynamicClientType)type).changesToJSONWithDynamicClientSideType(ejw,
						value, valueType, context);
					return ejw.isEmpty() ? null : new JSONStringWithClientSideType(ejw.toJSONString(), clientSideConversionType);
				}
				catch (Exception ex)
				{
					log.error("Error while writing changes for (changes with dynamic client side type) value (changesToJSON with type): " +
						safeToString(value) + " to type: " + type + " current json: " + ejw.toString(), ex);
					return null;
				}
			}
			else if (type instanceof ISupportsGranularUpdates)
			{
				// good, we now know that it can send changes only
				JSONString clientSideConversionType;
				EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true); // that 'true' is a workaround for allowing directly a value instead of object or array
				try
				{
					((ISupportsGranularUpdates)type).changesToJSON(ejw, null, value, valueType, context);
					// in the condition below, typeDeterminedBasedOnClassOfValue.value.booleanValue() can be assimilated to a dynamic type scenario as the type was determined based on java class of value, it was not known initially
					if ((!returnOnlyDynamicTypes || typeDeterminedBasedOnClassOfValue.value.booleanValue()) &&
						type instanceof IPropertyWithClientSideConversions)
					{
						clientSideConversionType = JSONUtils.getClientSideTypeJSONString(valueType);
					}
					else clientSideConversionType = null;

					return ejw.isEmpty() ? null : new JSONStringWithClientSideType(ejw.toJSONString(), clientSideConversionType);
				}
				catch (Exception ex)
				{
					log.error(
						"Error while writing changes for value (changesToJSON with type): " + safeToString(value) + " to type: " + type + " current json: " +
							ejw.toString(),
						ex);
					return null;
				}
			}
			else return super.getConvertedValueWithClientType(value, valueType, context, returnOnlyDynamicTypes);
		}

	}

	/**
	 * A JSONStringer that is able to be appended to another JSONWriter directly - without re-parsing the value into a JSONObject for example.
	 * @author acostescu
	 */
	public static class EmbeddableJSONWriter extends DebugFriendlyJSONStringer implements JSONString
	{

		public EmbeddableJSONWriter()
		{
			this(false);
		}

		/**
		 * @param hackForValueOnly if you really need to write directly a value (not array or object) initially, this should be true. It simulates
		 * an open object initially without writing anything to the underlying StringBuffer.
		 */
		public EmbeddableJSONWriter(boolean hackForValueOnly)
		{
			super();
			if (hackForValueOnly) this.mode = 'o'; // simulate being in an object already without anything written to char buffer
		}

		/**
		 * If 'hackForValueOnly' is used this method should be called only once!
		 */
		@Override
		public String toJSONString()
		{
			return writer.toString();
		}

		/**
		 * Returns true if nothing was written to this EmbeddableJSONWriter. False if something was.
		 */
		public boolean isEmpty()
		{
			// writer should always be StringWriter here because this class extends JSONStringer that gives the writer; but check anyway
			if (writer instanceof StringWriter)
				return ((StringWriter)writer).getBuffer().length() == 0;
			else
			{
				String str = writer.toString();
				return str == null || str.length() == 0;
			}
		}

	}

	/**
	 * Interface for easy grouping of typed content to be written to JSON.
	 *
	 * @author acostescu
	 */
	public static interface IJSONStringWithClientSideType extends JSONString
	{

		JSONString getClientSideType();

	}

	public static class JSONStringWrapper implements JSONString
	{
		public String wrappedString;

		public JSONStringWrapper()
		{
		}

		public JSONStringWrapper(String wrappedString)
		{
			this.wrappedString = wrappedString;
		}

		@Override
		public String toJSONString()
		{
			return wrappedString != null ? wrappedString : "null";
		}
	}

	/**
	 * Class for easy grouping of typed content to be written to JSON.
	 * @author acostescu
	 */
	public static class JSONStringWithClientSideType implements IJSONStringWithClientSideType
	{

		protected final String jsonString;
		protected final JSONString clientSideType;

		public JSONStringWithClientSideType(String jsonString)
		{
			this.jsonString = jsonString;
			clientSideType = null;
		}

		public JSONStringWithClientSideType(String jsonString, JSONString clientSideType)
		{
			this.jsonString = jsonString;
			this.clientSideType = clientSideType;
		}

		@Override
		public String toJSONString()
		{
			return jsonString;
		}

		@Override
		public JSONString getClientSideType()
		{
			return clientSideType;
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ((clientSideType == null) ? 0 : clientSideType.hashCode());
			result = prime * result + ((jsonString == null) ? 0 : jsonString.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			JSONStringWithClientSideType other = (JSONStringWithClientSideType)obj;
			if (clientSideType == null)
			{
				if (other.clientSideType != null) return false;
			}
			else if (!clientSideType.equals(other.clientSideType)) return false;
			if (jsonString == null)
			{
				if (other.jsonString != null) return false;
			}
			else if (!jsonString.equals(other.jsonString)) return false;
			return true;
		}

		@Override
		public String toString()
		{
			return "JSONString:" + toJSONString();
		}

	}

	public static String[] getJavaStringArrayFromJSONArray(JSONArray jsonArray)
	{
		if (jsonArray == null) return null;

		String[] javaArray = new String[jsonArray.length()];
		for (int i = javaArray.length - 1; i >= 0; i--)
			javaArray[i] = jsonArray.optString(i, null);

		return javaArray;
	}

	// THESE METHODS FOR JSON comparison are copy pasted from servoy_shared's Utils to avoid a dependency; if you alter them please update code in both places!
	// BEGIN -------------------------
	/**
	 * Identical to {@link #areJSONEqual(Object, Object, String)} where keysToIgnoreRegex is null (so it doesn't skip any keys during comparison).
	 */
	public static boolean areJSONEqual(Object json1, Object json2)
	{
		return areJSONEqual(json1, json2, null);
	}

	/**
	 * Compares 2 json values for deep-equality.
	 * json1 and json2 can be JSONObject, JSONArray (those are compared by content) or something else that is compared by ref/equals (JSON primitives).
	 *
	 * @param json1 the first JSON value to be compared.
	 * @param json2 the second JSON value to be compared.
	 * @return true if the two json values are deep-equal (ignoring key orders in case of JSONObject).
	 */
	public static boolean areJSONEqual(Object json1, Object json2, String keysToIgnoreRegex)
	{
		Object obj1Converted = convertJsonElement(json1, keysToIgnoreRegex);
		Object obj2Converted = convertJsonElement(json2, keysToIgnoreRegex);

		return obj1Converted == null ? obj1Converted == null : obj1Converted.equals(obj2Converted);
	}

	private static Object convertJsonElement(Object elem, String keysToIgnoreRegex)
	{
		if (elem instanceof JSONObject)
		{
			JSONObject jsonObj = (JSONObject)elem;
			Iterator<String> keys = jsonObj.keys();
			Map<String, Object> map = new HashMap<>();
			while (keys.hasNext())
			{
				String key = keys.next();
				if (keysToIgnoreRegex == null || !key.matches(keysToIgnoreRegex)) map.put(key, convertJsonElement(jsonObj.opt(key), keysToIgnoreRegex));
			}
			return map;
		}
		else if (elem instanceof JSONArray)
		{
			JSONArray jsonArray = (JSONArray)elem;
			List<Object> list = new ArrayList<>(jsonArray.length());
			for (int i = 0; i < jsonArray.length(); i++)
			{
				list.add(convertJsonElement(jsonArray.opt(i), keysToIgnoreRegex));
			}
			return list;
		}
		else
		{
			return elem;
		}
	}

	// END -------------------------

}
