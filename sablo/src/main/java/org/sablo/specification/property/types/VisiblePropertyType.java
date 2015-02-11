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

import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;


/**
 * @author rgansevles
 *
 */
public class VisiblePropertyType extends DefaultPropertyType<Boolean>
{

	public static final VisiblePropertyType INSTANCE = new VisiblePropertyType();
	public static final String TYPE_NAME = "visible";

	private VisiblePropertyType()
	{
	}

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

		return new ProtectedConfig(ForentriesConfig.parse(json), false);
	}

	@Override
	public boolean isProtecting()
	{
		return true;
	}
}
