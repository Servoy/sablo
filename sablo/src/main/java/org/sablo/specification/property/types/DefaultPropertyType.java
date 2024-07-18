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
import org.sablo.specification.property.IPropertyType;

/**
 * @author jcompagner
 *
 */
public abstract class DefaultPropertyType<T> implements IPropertyType<T>
{
	private final boolean primitive;

	public DefaultPropertyType()
	{
		this(false);
	}

	public DefaultPropertyType(boolean primitive)
	{
		this.primitive = primitive;
	}

	@Override
	public Object parseConfig(JSONObject config)
	{
		return null;
	}

	@Override
	public T defaultValue(PropertyDescription pd)
	{
		return null;
	}

	@Override
	public boolean isProtecting()
	{
		return false;
	}


	@Override
	public boolean isPrimitive()
	{
		return primitive;
	}
}
