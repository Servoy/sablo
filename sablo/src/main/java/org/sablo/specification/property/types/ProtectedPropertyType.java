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
public class ProtectedPropertyType extends DefaultPropertyType<Boolean>
{

	public static final ProtectedPropertyType INSTANCE = new ProtectedPropertyType();
	public static final String TYPE_NAME = "protected";

	private ProtectedPropertyType()
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
		return Boolean.FALSE;
	}

	@Override
	public ProtectedConfig parseConfig(JSONObject json)
	{
		return ProtectedConfig.parse(json, true);
	}

	@Override
	public boolean isProtecting()
	{
		return true;
	}

}
