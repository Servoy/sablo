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
 *
 * @author gboros
 */
public interface IBrowserConverterContext
{

	BaseWebObject getWebObject();

	/**
	 * This will give the actual computed push to server level for the property based on it's parent properties chain. (if it's a nested prop)<br/><br/>
	 *
	 * <b>IMPORTANT:</b><br/><br/>
	 *
	 * We cannot store this in the PropertyDescription objects only (when parsing the spec file) and use it from there because the same custom/nested
	 * type (so PropertyDescription) can be used in multiple subtrees in properties in the spec file, sometimes with pushToServer 'reject' and sometimes
	 * with pushToServer 'shallow' for example somewhere in parents. So the same PropertyDescription's sub-properties can sometimes be 'reject',
	 * sometimes 'shallow' etc. (If we would want to do this - store it directly in PDs, we must take care to clone those nested custom object/array PDs
	 * and have a different PropertyDescription for them in each different place they are used in; and also client-side sent PDs (type and pushToServer info)
	 * then would need to work correctly with this as well...)
	 *
	 * @see PushToServerEnum#combineWithParent(PushToServerEnum) for details on how nested pushToServer levels are computed.
	 */
	PushToServerEnum getComputedPushToServerValue();

	IBrowserConverterContext newInstanceWithPushToServer(PushToServerEnum npts);

}
