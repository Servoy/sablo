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


import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.CustomJSONObjectType;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility methods for properties / property types.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class PropertyUtils
{

	private static final Logger log = LoggerFactory.getLogger(PropertyUtils.class.getCanonicalName());

	/**
	 * Returns true if propertyType instanceof ICustomType.
	 *
	 * @param propertyType the type to test.
	 * @return true if propertyType instanceof ICustomType.
	 */
	public static boolean isCustomJSONProperty(IPropertyType< ? > propertyType)
	{
		return (propertyType instanceof ICustomType< ? >);
	}

	/**
	 * Returns true if propertyType instanceof ICustomType but it is not a custom array type.
	 *
	 * @param propertyType the type to test.
	 * @return true if propertyType instanceof ICustomType but it is not a custom array type.
	 */
	public static boolean isCustomJSONObjectProperty(IPropertyType< ? > propertyType)
	{
		return propertyType instanceof CustomJSONObjectType< ? , ? >;
	}

	/**
	 * Returns true if propertyType is a custom array type.
	 *
	 * @param propertyType the type to test.
	 * @return true if propertyType is a custom array type.
	 */
	public static boolean isCustomJSONArrayPropertyType(IPropertyType< ? > propertyType)
	{
		return propertyType instanceof CustomJSONArrayType< ? , ? >;
	}

	/**
	 * Gets the simple name from a complete custom JSON object property type name. If type's name has an unexpected format, it leaves it unchanged.
	 * @param propertyType the type.
	 * @return see description.
	 */
	public static String getSimpleNameOfCustomJSONTypeProperty(IPropertyType< ? > propertyType)
	{
		return getSimpleNameOfCustomJSONTypeProperty(propertyType.getName());
	}

	/**
	 * Gets the simple name from a complete custom JSON object property type name. If type's name has an unexpected format, it leaves it unchanged.
	 * @param typeName the type's name.
	 * @return see description.
	 */
	public static String getSimpleNameOfCustomJSONTypeProperty(String typeName)
	{
		int firstIndexOfDot = typeName.indexOf(".");
		return firstIndexOfDot >= 0 ? typeName.substring(firstIndexOfDot + 1) : typeName;
	}

	public static Double getAsDouble(String numberString)
	{
		if (numberString == null) return null;

		try
		{
			int comma = numberString.indexOf(",");
			int point = numberString.indexOf(".");
			if (comma == -1)
			{
				// it only has a point or no point at all, we can just parse this
				return Double.valueOf(numberString);
			}
			else if (point != -1)
			{
				// it has a command and a point
				if (point > comma)
				{
					// the point is the last, just ignore the comma
					return Double.valueOf(numberString.replaceAll(",", ""));
				}
				else
				{
					// the point is before the comma, so the comma is decimal
					return Double.valueOf(numberString.replaceAll("\\.", "").replace(',', '.'));
				}
			}
			else
			{
				// it just has a comma repace this with a .
				return Double.valueOf(numberString.replace(',', '.'));
			}
		}
		catch (NumberFormatException ex)
		{
			log.warn("Parse exception while processing " + numberString + " as a double", ex);
			return 0d;
		}
	}
}
