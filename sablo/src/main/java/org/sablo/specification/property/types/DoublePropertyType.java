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
public class DoublePropertyType extends DefaultPropertyType<Double> implements IPropertyConverterForBrowser<Number>
{
	protected static final Logger log = LoggerFactory.getLogger(DoublePropertyType.class.getCanonicalName());
	public static final DoublePropertyType INSTANCE = new DoublePropertyType();
	public static final String TYPE_NAME = "double";

	private DoublePropertyType()
	{
		super(true);
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Double defaultValue(PropertyDescription pd)
	{
		return Double.valueOf(0);
	}

	@Override
	public Number fromJSON(Object newJSONValue, Number previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newJSONValue == null || newJSONValue instanceof Double) return (Double)newJSONValue;
		if (newJSONValue instanceof Number) return Double.valueOf(((Number)newJSONValue).doubleValue());
		if (newJSONValue instanceof String)
		{
			if (((String)newJSONValue).trim().length() == 0) return null;

			return PropertyUtils.getAsDouble((String)newJSONValue);
		}
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Number sabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		if (sabloValue != null && (Double.isNaN(sabloValue.doubleValue()) || Double.isInfinite(sabloValue.doubleValue()))) writer.value(null);
		else writer.value(sabloValue);
		return writer;
	}
}
