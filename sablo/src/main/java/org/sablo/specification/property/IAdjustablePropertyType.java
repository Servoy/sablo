/*
 * Copyright (C) 2015 Servoy BV
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

import org.sablo.specification.property.types.IPropertyTypeFactory;
import org.sablo.specification.property.types.TypesRegistry;

/**
 * Interface for type implementations that can spawn multiple types adjusted based on some parameters (usually these types are created using {@link IPropertyTypeFactory}).
 * So instead of being registered through {@link TypesRegistry#addType(IPropertyType)} they are registered through {@link TypesRegistry#addTypeFactory(String, IPropertyTypeFactory)}
 *
 * @author acostescu
 */
public interface IAdjustablePropertyType<T> extends IPropertyType<T>
{

	/**
	 * Usually parameterized types will have customized names for each instance.
	 * But sometimes it is needed to know the that a type instance is part of a more generalized type implementation.
	 *
	 * For example custom objects/arrays have different behavior based on how their subtypes are declared in the .spec file, but they do share the same implementation (all
	 * have specific object behavior or all have specific array behavior).
	 *
	 * @return the base/generalized (representing an (same) implementation) name of this type.
	 */
	String getGenericName();

}
