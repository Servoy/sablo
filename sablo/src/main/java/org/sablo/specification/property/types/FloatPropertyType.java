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
public class FloatPropertyType extends DefaultPropertyType<Float> implements IPropertyConverterForBrowser<Number>
{
	protected static final Logger log = LoggerFactory.getLogger(FloatPropertyType.class.getCanonicalName());
	public static final FloatPropertyType INSTANCE = new FloatPropertyType();
	public static final String TYPE_NAME = "float";

	private FloatPropertyType()
	{
		super(true);
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Float defaultValue(PropertyDescription pd)
	{
		return Float.valueOf(0);
	}

	@Override
	public Number fromJSON(Object newJSONValue, Number previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newJSONValue == null || newJSONValue instanceof Float) return (Float)newJSONValue;
		if (newJSONValue instanceof Number) return Float.valueOf(((Number)newJSONValue).floatValue());
		if (newJSONValue instanceof String)
		{
			if (((String)newJSONValue).trim().length() == 0) return null;

			return Float.valueOf(PropertyUtils.getAsDouble((String)newJSONValue).floatValue());
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
