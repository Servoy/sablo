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

package org.sablo.websocket;

import org.sablo.specification.PropertyDescription;

/**
 * Data + corresponding types (prepared to be sent to the client).
 *
 * @author acostescu
 */
public class TypedData<T>
{
	/**
	 * The data (for example to be sent to the client).
	 */
	public T content;
	/**
	 * The description of the data's structure; can be null or might have a corresponding type that can be used for "to JSON" conversion.
	 */
	public PropertyDescription contentType;

	public TypedData(T content, PropertyDescription contentType)
	{
		this.content = content;
		this.contentType = contentType;
	}

}
