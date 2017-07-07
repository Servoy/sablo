/*
 * Copyright (C) 2017 Servoy BV
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

package org.sablo.websocket;

import java.util.Map;
import java.util.Set;

import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;

/**
 * Data and types representing root properties of a {@link BaseWebObject}.<br/>
 * It has information as well about which of the properties are changed by reference (fully changed) or just by content (so they could sent granular updates only).
 *
 * @author acostescu
 */
public class TypedDataWithChangeInfo extends TypedData<Map<String, Object>>
{

	protected Set<String> propertiesWithContentUpdateOnly;

	public TypedDataWithChangeInfo(Map<String, Object> content, PropertyDescription contentType, Set<String> propertiesWithContentUpdateOnly)
	{
		super(content, contentType);
		this.propertiesWithContentUpdateOnly = propertiesWithContentUpdateOnly;
	}

	public boolean hasOnlyContentUpdate(String propertyName)
	{
		return propertiesWithContentUpdateOnly != null && propertiesWithContentUpdateOnly.contains(propertyName);
	}

	public boolean hasFullyChanged(String propertyName)
	{
		return !hasOnlyContentUpdate(propertyName);
	}

}
