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


import java.math.BigDecimal;

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
				// it just has a comma replace this with a .
				return Double.valueOf(numberString.replace(',', '.'));
			}
		}
		catch (NumberFormatException ex)
		{
			log.warn("Parse exception while processing " + numberString + " as a double", ex);
			return 0d;
		}
	}

	/**
	 * Parses a string into a Number. Returns a Double if the number string can be
	 * represented by a double without loss of precision and within the double's finite range.
	 * Otherwise, returns a BigDecimal.
	 *
	 * @param numberStr The string representation of the number.
	 * @return A Double or BigDecimal or null if the input string is null or empty.
	 */
	public static Number parseToDoubleOrBigDecimal(String numberStr)
	{
		if (numberStr == null || numberStr.trim().isEmpty())
		{
			return null;
		}

		BigDecimal bd;
		try
		{
			// Use a constructor that doesn't lose precision from the string
			bd = new BigDecimal(numberStr);
		}
		catch (NumberFormatException e)
		{
			// Re-throw with a more informative message or handle as per application needs
			throw new NumberFormatException("Invalid number string for BigDecimal: \"" + numberStr + "\" - " + e.getMessage());
		}

		// Attempt to convert the BigDecimal to a double
		double dValue = bd.doubleValue();

		// Check if the conversion to double resulted in an infinite value.
		// If bd was a finite number but dValue is infinite, it means bd was
		// outside the representable range of a finite double.
		if (Double.isInfinite(dValue))
		{
			// The number is too large or too small for a finite double.
			// Keep it as BigDecimal to preserve its original magnitude.
			return bd;
		}

		// Check for NaN. This should ideally not happen if BigDecimal parsing from a valid
		// number string was successful, as BigDecimal itself doesn't represent NaN.
		// If dValue is NaN here, it might indicate an extremely unusual case or an issue
		// with how an underlying double (if the string was from a double initially)
		// was handled. Safest to return BigDecimal.
		if (Double.isNaN(dValue))
		{
			return bd;
		}

		// Now, the crucial check: convert the double back to BigDecimal and compare.
		// If BigDecimal.valueOf(dValue) is numerically equal to the original bd,
		// then the double representation dValue is accurate for bd.
		// BigDecimal.valueOf(double) is preferred over new BigDecimal(double)
		// as it uses the canonical string representation of the double.
		if (bd.compareTo(BigDecimal.valueOf(dValue)) == 0)
		{
			// The value fits perfectly into a double without loss of precision
			// or being clamped to infinity (already checked).
			return Double.valueOf(dValue);
		}
		else
		{
			// Precision would be lost, or the value is a tiny number that
			// becomes 0.0 as a double but wasn't BigDecimal.ZERO.
			// Keep it as BigDecimal to preserve full precision/value.
			return bd;
		}
	}

	public static Long getAsLong(String numberString)
	{
		if (numberString == null) return null;
		try
		{
			int comma = numberString.lastIndexOf(",");
			int point = numberString.lastIndexOf(".");
			if (comma == -1)
			{
				if (point == -1)
				{
					// it doesn't have any , or . we can just parse this
					return Long.valueOf(numberString);
				}
				else if ((numberString.length() - point) == 3)
				{
					// if the point could be a grouping position, lets interpreted like that
					return Long.valueOf(numberString.replaceAll("\\.", ""));
				}
				else
				{
					// strip until the decimal separator
					return Long.valueOf(numberString.substring(0, point));
				}
			}
			else if (point != -1)
			{
				// it has a command and a point
				if (point > comma)
				{
					// the point is the last, just ignore the comma
					return Long.valueOf(numberString.substring(0, point).replaceAll(",", ""));
				}
				else
				{
					// the point is before the comma, so the comma is decimal
					return Long.valueOf(numberString.substring(0, comma).replaceAll("\\.", ""));
				}
			}
			else
			{
				if ((numberString.length() - comma) == 3)
				{
					// if the comma could be a grouping position, lets interpreted like that
					return Long.valueOf(numberString.replace(",", ""));
				}
				else
				{
					// strip until the decimal separator
					return Long.valueOf(numberString.substring(0, comma));
				}
			}
		}
		catch (NumberFormatException ex)
		{
			log.warn("Parse exception while processing " + numberString + " as a long", ex);
			return 0l;
		}
	}
}
