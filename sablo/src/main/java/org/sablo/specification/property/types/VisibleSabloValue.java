/*
 * Copyright (C) 2025 Servoy BV
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
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.specification.property.IWrappingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lvostinar
 *
 */
public class VisibleSabloValue
{
	private static final Logger log = LoggerFactory.getLogger(VisibleSabloValue.class.getCanonicalName());

	protected boolean value;
	protected final IWrappingContext context;

	public VisibleSabloValue(boolean value, IWrappingContext dataConverterContext)
	{
		this.value = value;
		this.context = dataConverterContext;
	}

	public JSONWriter toJSON(JSONWriter writer)
	{
		try
		{
			writer.value(getValue());
		}
		catch (JSONException e)
		{
			log.error(e.getMessage());
		}
		return writer;
	}

	public boolean getValue()
	{
		return value;
	}


	public void setValue(boolean newValue)
	{
		if (value != newValue)
		{
			this.value = newValue;
			flagChanged(context.getWebObject(), context.getPropertyName());
		}
	}

	protected void flagChanged(BaseWebObject comp, String propName)
	{
		comp.markPropertyAsChangedByRef(propName);
	}
}
