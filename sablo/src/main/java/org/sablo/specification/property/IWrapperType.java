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

import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;


/**
 * Property types that are wrapped into a custom class.<br/>
 * This is also a JSON converting type, but conversions happen to and from wrapped value directly.
 *
 * @param <T> the type the value of this property has to the outside world (when doing getProperty() or setProperty())
 * @param <W> the type of (underlying) value that this type stores in the {@link BaseWebObject}'s map.
 *
 * @author gboros
 */
public interface IWrapperType<T, W> extends IPropertyType<T>, IPropertyConverterForBrowser<W>
{

	T unwrap(W value);

	W wrap(T value, W previousValue, PropertyDescription propertyDescription, IWrappingContext dataConverterContext);

}