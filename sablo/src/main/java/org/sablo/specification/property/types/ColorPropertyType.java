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

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
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
public class ColorPropertyType extends DefaultPropertyType<Color> implements IClassPropertyType<Color>
{

	private static final Map<String, String> basicCssColors = new HashMap<String, String>();

	static
	{
		basicCssColors.put("black", "#000000");
		basicCssColors.put("silver", "#C0C0C0");
		basicCssColors.put("gray", "#808080");
		basicCssColors.put("white", "#FFFFFF");
		basicCssColors.put("maroon", "#800000");
		basicCssColors.put("red", "#FF0000");
		basicCssColors.put("purple", "#800080");
		basicCssColors.put("fuchsia", "#FF00FF");
		basicCssColors.put("green", "#008000");
		basicCssColors.put("lime", "#00FF00");
		basicCssColors.put("olive", "#808000");
		basicCssColors.put("yellow", "#FFFF00");
		basicCssColors.put("navy", "#000080");
		basicCssColors.put("blue", "#0000FF");
		basicCssColors.put("teal", "#008080");
		basicCssColors.put("aqua", "#00FFFF");
	}

	public static final String COLOR_RGBA_DEF = "rgba";
	public static final String TRANSPARENT = "transparent";
	public static final Color COLOR_TRANSPARENT = new Color(0, 0, 0, 0);

	public static final ColorPropertyType INSTANCE = new ColorPropertyType();
	public static final String TYPE_NAME = "color";

	protected ColorPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Color fromJSON(Object newValue, Color previousValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		Color retval = null;

		String ss = (String)newValue;
		if (newValue != null && (ss.length() == 4 || ss.length() == 7))
		{
			if (ss.length() == 4) // abbreviated
			{
				ss = new String(new char[] { ss.charAt(0), ss.charAt(1), ss.charAt(1), ss.charAt(2), ss.charAt(2), ss.charAt(3), ss.charAt(3) });
			}
			try
			{
				retval = Color.decode(ss);
			}
			catch (NumberFormatException e)
			{
				//ignore;
			}
		}
		if (TRANSPARENT.equals(newValue))
		{
			return COLOR_TRANSPARENT;
		}
		if (retval == null && ss != null)
		{
			try
			{
				Field field = Color.class.getField((String)newValue);
				return (Color)field.get(null);
			}
			catch (Exception e)
			{
				// ignore
				if (basicCssColors.containsKey(ss.toLowerCase())) return fromJSON(basicCssColors.get(ss.toLowerCase()), null, pd, dataConverterContext, null);
			}
		}
		return retval;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Color c, PropertyDescription pd, IBrowserConverterContext dataConverterContext) throws JSONException
	{
		if (c != null)
		{
			JSONUtils.addKeyIfPresent(writer, key);
			writer.value(getStringValue(c));
		}
		return writer;
	}

	protected String getStringValue(Color c)
	{
		String val = null;
		int alpha = c.getAlpha();
		if (alpha == 255)
		{
			String r = Integer.toHexString(c.getRed());
			if (r.length() == 1) r = "0" + r; //$NON-NLS-1$
			String g = Integer.toHexString(c.getGreen());
			if (g.length() == 1) g = "0" + g; //$NON-NLS-1$
			String b = Integer.toHexString(c.getBlue());
			if (b.length() == 1) b = "0" + b; //$NON-NLS-1$
			val = "#" + r + g + b; //$NON-NLS-1$
		}
		else if (alpha == 0)
		{
			val = TRANSPARENT;
		}
		else
		{
			val = COLOR_RGBA_DEF + '(' + c.getRed() + ',' + c.getGreen() + ',' + c.getBlue() + ',' + Math.round((alpha / 255f) * 10) / 10f + ')';
		}
		return val;
	}

	@Override
	public Class<Color> getTypeClass()
	{
		return Color.class;
	}
}
