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

/**
 * @author jcompagner
 *
 */
public class FunctionPropertyType extends DefaultPropertyType<Object>
{
	public static final FunctionPropertyType INSTANCE = new FunctionPropertyType();

	protected FunctionPropertyType()
	{
	}

	public String getName()
	{
		return "function";
	}

	@Override
	public Object parseConfig(JSONObject json)
	{
		return Boolean.valueOf(json != null && json.optBoolean("adddefault"));
	}
}
