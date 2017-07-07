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
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;

/**
 * Context for data converters to/from browser.
 * @author gboros
 */
public interface IBrowserConverterContext
{

	BaseWebObject getWebObject();

	/**
	 * .spec file can define "pushToServer" setting for each property. But for nested properties we want that setting to be used by all
	 * children of that property. So if the root property is defined as 'reject' then all nested properties will be reject as well.
	 *
	 * We cannot store this in the PropertyDescriptions directly cause the same custom/nested type can be used in multiple properties in the spec,
	 * sometimes with pushToServer reject for example and sometimes with pushToServer shallow for example.
	 *
	 * @return one of the PushToServerEnum values as specified in the spec file (for root property).
	 */
	PushToServerEnum getPushToServerValue();

}
