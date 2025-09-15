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

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IPropertyConverterForBrowser;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author jcompagner
 *
 */
public class IntPropertyType extends DefaultPropertyType<Integer> implements IPropertyConverterForBrowser<Number>
{
	protected static final Logger log = LoggerFactory.getLogger(IntPropertyType.class.getCanonicalName());
	public static final IntPropertyType INSTANCE = new IntPropertyType();
	public static final IntPropertyType INSTANCE_NULL_DEFAULT = new IntPropertyType()
	{
		@Override
		public Integer defaultValue(PropertyDescription pd)
		{
			return null;
		}
	};

	public static final String TYPE_NAME = "int"; //$NON-NLS-1$

	protected IntPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Integer defaultValue(PropertyDescription pd)
	{
		return Integer.valueOf(0);
	}

	@Override
	public Number fromJSON(Object newJSONValue, Number previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		//TODO isn't this the same as Column.getAsRightType ?
		if (newJSONValue == null || newJSONValue instanceof Integer) return (Integer)newJSONValue;
		if (newJSONValue instanceof Number)
		{
			Integer val = Integer.valueOf(((Number)newJSONValue).intValue());
			if (returnValueAdjustedIncommingValue != null && val.doubleValue() != ((Number)newJSONValue).doubleValue())
				returnValueAdjustedIncommingValue.value = Boolean.TRUE;
			return val;
		}
		if (newJSONValue instanceof String)
		{
			if (((String)newJSONValue).trim().length() == 0) return null;

			Long parsedValue = PropertyUtils.getAsLong((String)newJSONValue);
			Integer val = Integer.valueOf(parsedValue.intValue());
			Double parsedDouble = PropertyUtils.getAsDouble((String)newJSONValue);
			if (returnValueAdjustedIncommingValue != null && parsedDouble.doubleValue() != val.doubleValue())
				returnValueAdjustedIncommingValue.value = Boolean.TRUE;
			return val;
		}
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Number sabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		writer.value(sabloValue);
		return writer;
	}
}
