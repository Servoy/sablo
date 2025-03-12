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
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.IWrappingContext;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;


/**
 * @author rgansevles
 *
 */
public class VisiblePropertyType extends DefaultPropertyType<Boolean> implements IWrapperType<Boolean, VisibleSabloValue>
{

	public static final VisiblePropertyType INSTANCE = new VisiblePropertyType();
	public static final String TYPE_NAME = "visible";


	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Boolean defaultValue(PropertyDescription pd)
	{
		return null;
	}

	@Override
	public ProtectedConfig parseConfig(JSONObject json)
	{
		if (json == null)
		{
			return ProtectedConfig.DEFAULTBLOCKING_FALSE;
		}

		return new ProtectedConfig(ForentriesConfig.parse(json), false);
	}

	@Override
	public boolean isProtecting()
	{
		return true;
	}

	@Override
	public VisibleSabloValue fromJSON(Object newJSONValue, VisibleSabloValue previousSabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext context, ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		return previousSabloValue;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, VisibleSabloValue sabloValue, PropertyDescription propertyDescription,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		return sabloValue.toJSON(writer);
	}

	@Override
	public VisibleSabloValue wrap(Boolean newValue, VisibleSabloValue oldValue, PropertyDescription propertyDescription, IWrappingContext dataConverterContext)
	{
		if (oldValue != null)
		{
			oldValue.setValue(newValue.booleanValue());
		}
		else
		{
			return new VisibleSabloValue(newValue.booleanValue(), dataConverterContext);
		}
		return oldValue;
	}

	@Override
	public Boolean unwrap(VisibleSabloValue value)
	{
		return Boolean.valueOf(value.getValue());
	}
}
