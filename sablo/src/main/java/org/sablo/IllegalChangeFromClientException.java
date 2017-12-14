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

import org.sablo.specification.property.IPropertyType;

/**
 * Exception for illegal changes being sent from client to server on a component/component property.
 *
 * @author rgansevles
 */
@SuppressWarnings("nls")
public class IllegalChangeFromClientException extends RuntimeException
{

	private final String blockedByProperty;
	private final String blockReason;
	private final String componentName;
	private final String property;
	private IllegalChangeFromClientException e;

	/**
	 * @param blockedByProperty can be null if the blockedProperty is itself 'protecting' (so it can never be changed from client anyway) or has 'pushToServer' set to 'reject'. {@link IPropertyType#isProtecting()}
	 */
	public IllegalChangeFromClientException(String blockedByProperty, String blockReason, String componentName, String blockedProperty)
	{
		this.blockedByProperty = blockedByProperty;
		this.blockReason = blockReason;
		this.componentName = componentName;
		this.property = blockedProperty;
		this.e = null;
	}

	/**
	 * @param blockedByProperty can be null if the blockedProperty is itself 'protecting' (so it can never be changed from client anyway) or has 'pushToServer' set to 'reject'. {@link IPropertyType#isProtecting()}
	 */
	public IllegalChangeFromClientException(String blockedByProperty, String blockReason, String name, String eventType, IllegalChangeFromClientException e)
	{
		this(blockedByProperty, blockReason, name, eventType);
		this.e = e;
	}

	/**
	 * The property that blocked the change of the blocked property.
	 *
	 * @return the 'protecting' property that block change from client for the blocked property; can be null if the blockedProperty is itself 'protecting' (so it can never be changed from client anyway) or has 'pushToServer' set to 'reject'. {@link IPropertyType#isProtecting()}
	 */
	public String getBlockedByProperty()
	{
		return blockedByProperty;
	}

	public String getBlockReason()
	{
		return blockReason;
	}

	public String getComponentName()
	{
		return componentName;
	}

	public String getBlockedProperty()
	{
		return property;
	}

	@Override
	public String getMessage()
	{
		String result = new StringBuilder("Change sent from client to server for property '").append(getBlockedProperty()).append("' of component '").append(
			getComponentName()).append("' was denied ").append(
				getBlockedByProperty() != null ? " (blocked by property named '" + getBlockedByProperty() + "')" : "").append(
					".Block reason: " + getBlockReason()).toString();
		if (e != null)
		{
			result += ". Caused by: " + e.getMessage();
		}
		return result;
	}

}
