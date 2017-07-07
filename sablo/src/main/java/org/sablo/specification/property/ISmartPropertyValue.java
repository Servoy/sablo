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

import org.sablo.IChangeListener;
import org.sablo.IWebObjectContext;

/**
 * The representation of a property value that is aware of it's surroundings.
 * For example property values that need to register listeners and clean them up when the property value is no longer used need to implement this interface.
 * This value can get updates from the browser component as needed.<br><br>
 *
 * @author acostescu
 */
public interface ISmartPropertyValue
{

	/**
	 * Method that will get called when this property value is attached to a component/service.<br>
	 *
	 * @param changeMonitor an object that can be used to notify the system that something in this property has changed.
	 * @param webObjectContext the component context to which the complex property has been attached (could be a BaseWebObject)
	 */
	void attachToBaseObject(IChangeListener changeMonitor, IWebObjectContext webObjectContext);

	/**
	 * Called when a property is detached from a component/service; cleanup can happen here.
	 */
	void detach();

}
