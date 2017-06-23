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

import java.awt.Font;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
public class FontPropertyType extends DefaultPropertyType<Font> implements IClassPropertyType<Font>
{

	public static final FontPropertyType INSTANCE = new FontPropertyType();
	public static final String TYPE_NAME = "font";

	protected FontPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Font fromJSON(Object newValue, Font previousValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		String fontFamily = previousValue != null ? previousValue.getFamily() : null;
		int size = previousValue != null ? previousValue.getSize() : 12;
		int style = 0;
		if (newValue instanceof JSONObject)
		{
			fontFamily = ((JSONObject)newValue).optString("fontFamily", fontFamily);
			String fontSize = ((JSONObject)newValue).optString("fontSize");
			if (fontSize != null)
			{
				if (fontSize.endsWith("px")) fontSize = fontSize.substring(0, fontSize.length() - 2);
				size = Integer.parseInt(fontSize);
			}
			if ("bold".equals(((JSONObject)newValue).opt("fontWeight")))
			{
				style += Font.BOLD;
			}
			if ("italic".equals(((JSONObject)newValue).opt("fontStyle")))
			{
				style += Font.ITALIC;
			}
		}
		if (fontFamily == null) fontFamily = "Arial";
		else if (fontFamily.endsWith(", Verdana, Arial")) fontFamily = fontFamily.substring(0, (fontFamily.length() - ", Verdana, Arial".length()));
		Font font = Font.getFont(fontFamily);
		if (font == null) font = Font.decode(fontFamily);
		return font.deriveFont(style, size);
	}

	@Override
	public JSONWriter toJSON(JSONWriter w, String key, Font font, PropertyDescription pd, DataConversion clientConversion,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		JSONUtils.addKeyIfPresent(w, key);
		w.object();
		if (font.isBold())
		{
			w.key("fontWeight").value("bold");
		}
		else
		{
			w.key("fontWeight").value("normal");
		}
		if (font.isItalic())
		{
			w.key("fontStyle").value("italic"); //$NON-NLS-1$
		}
		else
		{
			w.key("fontStyle").value("normal"); //$NON-NLS-1$
		}
		w.key("fontSize").value(font.getSize() + "px");
		w.key("fontFamily").value(font.getFamily() + ", Verdana, Arial");
		return w.endObject();
	}

	@Override
	public Object parseConfig(JSONObject json)
	{
		return null;
	}

	@Override
	public Class<Font> getTypeClass()
	{
		return Font.class;
	}
}
