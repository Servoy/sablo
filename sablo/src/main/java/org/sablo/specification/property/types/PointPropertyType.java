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

import java.awt.Point;

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
public class PointPropertyType extends DefaultPropertyType<Point> implements IClassPropertyType<Point>
{

	public static final PointPropertyType INSTANCE = new PointPropertyType();
	public static final String TYPE_NAME = "point";

	protected PointPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Point fromJSON(Object newValue, Point previousValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newValue instanceof JSONObject)
		{
			JSONObject json = (JSONObject)newValue;
			return new Point(json.optInt("x"), json.optInt("y"));
		}
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Point object, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		return writer.object().key("x").value(object.getX()).key("y").value(object.getY()).endObject();
	}

	@Override
	public Point defaultValue(PropertyDescription pd)
	{
		return new Point(0, 0);
	}

	@Override
	public Class<Point> getTypeClass()
	{
		return Point.class;
	}
}
