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

import java.util.Collection;

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectApiFunctionDefinition;

/**
 * @author jcompagner
 */
public interface ICustomType<T> extends IPropertyType<T>
{

	/**
	 * Can be null while instance initializes; if it's not null then this type was defined as a custom type in JSON.
	 * It could also have special handling attached to it, depending on what other methods return. (so you could potentially declare a custom json type that has an implementation different then standard,
	 * but in designer still have default properties view usage TODO this might not work right now - as this was intended for foundset/-component type usage in desiger but those have special designer handling instead)
	 *
	 * @return the corresponding (JSON based) representation of this type's definition.
	 */
	PropertyDescription getCustomJSONTypeDefinition();

	void setCustomJSONDefinition(PropertyDescription definition);

	/**
	 * @param text
	 */
	void setDocumentation(String text);

	public String getDocumentation();

	public void setParent(ICustomType< ? > parent);

	/**
	 * @return the parent
	 */
	public ICustomType< ? > getParent();

	/**
	 * @param def
	 */
	void addApiFunction(WebObjectApiFunctionDefinition def);

	public WebObjectApiFunctionDefinition getApiFunction(String name);

	public Collection<WebObjectApiFunctionDefinition> getApiFunctions();
}
