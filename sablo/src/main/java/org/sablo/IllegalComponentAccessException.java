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

package org.sablo;

/**
 * Exception or illegalaccess to a component.
 *
 * @author rgansevles
 *
 */
public class IllegalComponentAccessException extends RuntimeException
{

	private final String accessType;
	private final String componentName;
	private final String property;
	private IllegalComponentAccessException e;

	public IllegalComponentAccessException(String accessType, String componentName, String property)
	{
		this.accessType = accessType;
		this.componentName = componentName;
		this.property = property;
		this.e = null;
	}


	public IllegalComponentAccessException(String accessType2, String name, String eventType, IllegalComponentAccessException e)
	{
		this(accessType2, name, eventType);
		this.e = e;
	}

	/**
	 * @return the accessType
	 */
	public String getAccessType()
	{
		return accessType;
	}

	/**
	 * @return the componentName
	 */
	public String getComponentName()
	{
		return componentName;
	}

	/**
	* @return the property
	*/
	public String getProperty()
	{
		return property;
	}

	@Override
	public String getMessage()
	{
		String result = new StringBuilder("Access to property '").append(getProperty()).append("' from component '").append(getComponentName()).append(
			"' denied (blocked by value of property: '").append(getAccessType()).append("')").toString();
		if (e != null)
		{
			result += ". Warning was caused by: " + e.getMessage();
		}
		return result;
	}
}
