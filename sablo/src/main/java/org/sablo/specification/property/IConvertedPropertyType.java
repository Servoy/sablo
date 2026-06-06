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


/**
 * A simple (non-wrapping) property type that needs special (JSON) conversion for web-socket traffic.<br/>
 *
 * @author acostescu
 */
public interface IConvertedPropertyType<T> extends IPropertyType<T>, IPropertyConverterForBrowser<T>
{

}