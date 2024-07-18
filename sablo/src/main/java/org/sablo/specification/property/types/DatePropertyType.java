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

import java.util.Date;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyWithClientSideConversions;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;

/**
 * Dates are also handled specially on both ends currently.
 * TODO Maybe this type can replace that special handling completely to be just as another type.
 *
 * @author jcompagner
 *
 */
public class DatePropertyType extends DefaultPropertyType<Date> implements IClassPropertyType<Date>, IPropertyWithClientSideConversions<Date>
{

	public static final String CLIENT_SIDE_TYPE_NAME = "Date";

	public static final DatePropertyType INSTANCE = new DatePropertyType();
	public static final String TYPE_NAME = "date";

	protected DatePropertyType()
	{
		super(true);
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Class<Date> getTypeClass()
	{
		return Date.class;
	}

	@Override
	public Date fromJSON(Object newValue, Date previousValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newValue instanceof Number) return new Date(((Number)newValue).longValue());
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Date value, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		return writer.value(value.getTime());
	}

	@Override
	public boolean writeClientSideTypeName(JSONWriter w, String keyToAddTo, PropertyDescription pd)
	{
		JSONUtils.addKeyIfPresent(w, keyToAddTo);
		w.value(CLIENT_SIDE_TYPE_NAME);
		return true;
	}

}
