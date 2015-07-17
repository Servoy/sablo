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

package org.sablo.specification.property;

import java.util.Map;

import org.sablo.specification.property.types.IPropertyTypeFactory;

/**
 * Creates custom JSON Object types based on what's present in the spec file.
 *
 * @author acostescu
 */
public class CustomJSONObjectTypeFactory implements IPropertyTypeFactory<String, Map<String, ? >>
{

	@Override
	public IAdjustablePropertyType<Map<String, ? >> createType(String customTypeName)
	{
		return new CustomJSONObjectType(customTypeName, null); // that null is temporary - it will get populated later
	}

	@Override
	public String toString()
	{
		return "Default Sablo custom JSON Object type factory.";
	}

}
