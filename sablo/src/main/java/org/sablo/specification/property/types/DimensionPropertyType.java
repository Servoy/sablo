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

import java.awt.Dimension;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
public class DimensionPropertyType extends DefaultPropertyType<Dimension> implements IClassPropertyType<Dimension>
{

	public static final DimensionPropertyType INSTANCE = new DimensionPropertyType();
	public static final String TYPE_NAME = "dimension";

	protected DimensionPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Dimension fromJSON(Object newValue, Dimension previousValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newValue instanceof JSONObject)
		{
			JSONObject json = (JSONObject)newValue;
			return new Dimension(json.optInt("width"), json.optInt("height"));
		}
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Dimension object, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		return writer.object().key("width").value(object.getWidth()).key("height").value(object.getHeight()).endObject();
	}

	@Override
	public Dimension defaultValue(PropertyDescription pd)
	{
		return new Dimension(0, 0);
	}

	@Override
	public Class<Dimension> getTypeClass()
	{
		return Dimension.class;
	}
}
