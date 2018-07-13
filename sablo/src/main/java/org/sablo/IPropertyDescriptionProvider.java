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

package org.sablo;

import java.util.Collection;

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IPropertyType;


/**
 * Classes implementing this interface are able to provide PropertyDescription s by type, name, ...
 *
 * @author acostescu
 */
public interface IPropertyDescriptionProvider
{

	PropertyDescription getPropertyDescription(String name);

	Collection<PropertyDescription> getProperties(IPropertyType< ? > type);

}