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

import java.awt.Insets;

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
public class InsetsPropertyType extends DefaultPropertyType<Insets> implements IClassPropertyType<Insets>
{

	public static final InsetsPropertyType INSTANCE = new InsetsPropertyType();
	public static final String TYPE_NAME = "insets";

	protected InsetsPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Insets fromJSON(Object newValue, Insets previousValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		int top = 0;
		int right = 0;
		int bottom = 0;
		int left = 0;
		if (newValue instanceof JSONObject)
		{
			String paddingTop = ((JSONObject)newValue).optString("paddingTop", "0px");
			top = Integer.parseInt(paddingTop.substring(0, paddingTop.length() - 2));
			String paddingLeft = ((JSONObject)newValue).optString("paddingLeft", "0px");
			left = Integer.parseInt(paddingLeft.substring(0, paddingLeft.length() - 2));
			String paddingBottom = ((JSONObject)newValue).optString("paddingBottom", "0px");
			bottom = Integer.parseInt(paddingBottom.substring(0, paddingBottom.length() - 2));
			String paddingRight = ((JSONObject)newValue).optString("paddingRight", "0px");
			right = Integer.parseInt(paddingRight.substring(0, paddingRight.length() - 2));
		}
		return new Insets(top, left, bottom, right);
	}


	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Insets i, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		writer.object();
		writer.key("paddingTop").value(i.top + "px");
		writer.key("paddingBottom").value(i.bottom + "px");
		writer.key("paddingLeft").value(i.left + "px");
		writer.key("paddingRight").value(i.right + "px");
		return writer.endObject();
	}

	@Override
	public Class<Insets> getTypeClass()
	{
		return Insets.class;
	}

}
