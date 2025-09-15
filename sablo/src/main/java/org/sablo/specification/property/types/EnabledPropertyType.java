/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
*/

package org.sablo.specification.property.types;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.IWrappingContext;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author emera
 */
public class EnabledPropertyType extends DefaultPropertyType<Boolean> implements IWrapperType<Boolean, EnabledSabloValue>
{

	public static final String TYPE_NAME = "enabled";
	public static final EnabledPropertyType INSTANCE = new EnabledPropertyType();

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}


	@Override
	public Boolean defaultValue(PropertyDescription pd)
	{
		return Boolean.TRUE;
	}

	@Override
	public ProtectedConfig parseConfig(JSONObject json)
	{
		if (json == null)
		{
			return ProtectedConfig.DEFAULTBLOCKING_FALSE;
		}
		return ProtectedConfig.parse(json, true);
	}

	@Override
	public boolean isProtecting()
	{
		return true;
	}

	@Override
	public EnabledSabloValue fromJSON(Object newJSONValue, EnabledSabloValue previousSabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext context, ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		return previousSabloValue;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, EnabledSabloValue sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		return sabloValue.toJSON(writer);
	}

	@Override
	public EnabledSabloValue wrap(Boolean newValue, EnabledSabloValue oldValue, PropertyDescription propertyDescription, IWrappingContext dataConverterContext)
	{
		if (oldValue != null)
		{
			oldValue.setEnabled(newValue.booleanValue());
		}
		else
		{
			return new EnabledSabloValue(newValue.booleanValue(), dataConverterContext);
		}
		return oldValue;
	}

	@Override
	public Boolean unwrap(EnabledSabloValue value)
	{
		return Boolean.valueOf(value.getValue());
	}

}
