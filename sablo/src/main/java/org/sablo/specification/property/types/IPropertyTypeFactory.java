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

import org.sablo.specification.property.IAdjustablePropertyType;

/**
 * One can register type factories instead of direct types to a name.</BR>
 * This is useful for parameterized types. For example a CustomJSONObject or CustomJSONArray type depends on how it is defined in the spec file.
 *
 * @author acostescu
 */
public interface IPropertyTypeFactory<ParamT, T>
{

	/**
	 * Usually type factories are registered in order to create parameterized types.
	 * So the return value will be an instance of {@link IAdjustablePropertyType}.
	 * @param params the parameters that tweak the type's behavior.
	 * @return a type instance for the given parameters.
	 */
	IAdjustablePropertyType<T> createType(ParamT params);

}
