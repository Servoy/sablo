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

import java.io.IOException;

import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectFunctionDefinition;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.websocket.CurrentWindow;

/**
 * Server side representation of an angular webcomponent in the browser. It is
 * defined by a strong specification api,event and property-model wise
 *
 * @author jblok
 */
public class WebComponent extends BaseWebObject
{
	Container parent;

	public WebComponent(String componentType, String name)
	{
		this(componentType, name, false);
	}

	public WebComponent(String name, WebObjectSpecification spec)
	{
		this(name, spec, false);
	}

	public WebComponent(String componentType, String name, boolean waitForPropertyInitBeforeAttach)
	{
		this(name, WebComponentSpecProvider.getSpecProviderState().getWebComponentSpecification(componentType), waitForPropertyInitBeforeAttach);
		properties.put("name", name);
	}

	public WebComponent(String name, WebObjectSpecification spec, boolean waitForPropertyInitBeforeAttach)
	{
		super(name, spec, waitForPropertyInitBeforeAttach);
	}

	/**
	 * Returns the parent container
	 *
	 * @return the parent container
	 */
	public final Container getParent()
	{
		return parent;
	}

	@Override
	public boolean markPropertyAsChangedByRef(String key)
	{
		boolean modified = super.markPropertyAsChangedByRef(key);
		if (modified && parent != null) parent.markAsChanged();
		return modified;
	}

	@Override
	public boolean markPropertyContentsUpdated(String key)
	{
		boolean modified = super.markPropertyContentsUpdated(key);
		if (modified && parent != null) parent.markAsChanged();
		return modified;
	}

	/**
	 * Finds the first container parent of this component of the given class.
	 *
	 * @param <Z> type of parent
	 * @param c class to search for
	 * @return First container parent that is an instance of the given class, or null if none can be
	 *         found
	 */
	public final <Z> Z findParent(final Class<Z> c)
	{
		Container current = getParent();
		while (current != null)
		{
			if (c.isInstance(current))
			{
				return c.cast(current);
			}
			current = current.getParent();
		}
		return null;
	}

	@Override
	protected void checkProtection(String eventType)
	{
		super.checkProtection(eventType);

		// Check if container is not protected or invisible
		if (parent != null)
		{
			try
			{
				parent.checkProtection(null);
			}
			catch (IllegalChangeFromClientException e)
			{
				throw new IllegalChangeFromClientException(e.getBlockedByProperty(),
					"Parent container of this component currently blocks incoming client changes.", this.getName(), eventType, e);
			}

		}
	}

	@Override
	public void dispose()
	{
		super.dispose();
		if (parent != null)
		{
			parent.remove(this);
		}
	}

	/**
	 * Invoke apiFunction by name, fails silently if not found
	 *
	 * @param apiFunctionName
	 *            the function name
	 * @param args
	 *            the args
	 * @return the value if any
	 */
	public Object invokeApi(String apiFunctionName, Object[] args)
	{
		WebObjectFunctionDefinition apiFunction = specification.getApiFunction(apiFunctionName);
		if (apiFunction != null)
		{
			return invokeApi(apiFunction, args);
		}
		return null;
	}

	/**
	 * Invoke apiFunction
	 *
	 * @param apiFunction
	 *            the function
	 * @param args
	 *            the args
	 * @return the value if any
	 */
	public Object invokeApi(WebObjectFunctionDefinition apiFunction, Object[] args)
	{
		return CurrentWindow.get().invokeApi(this, apiFunction, args, getParameterTypes(apiFunction));
	}

	/**
	 * Execute a service function defined in the client.
	 * Synchronous call, blocks until the result is returned.
	 *
	 * @param service
	 *            the service name
	 * @param functionName
	 *            the functionName name
	 * @param arguments
	 *            the arguments
	 * @return the value if any
	 * @throws IOException
	 */
	public Object executeServiceCall(String service, String functionName, Object[] arguments) throws IOException
	{
		return CurrentWindow.get().getSession().getClientService(service).executeServiceCall(functionName, arguments);
	}

	/**
	 * Execute a service function defined in the client.
	 * Asynchronous non-blocking call.
	 *
	 * @param service
	 *            the service name
	 * @param functionName
	 *            the functionName name
	 * @param arguments
	 *            the arguments
	 * @throws IOException
	 */
	public void executeAsyncServiceCall(String service, String functionName, Object[] arguments)
	{
		CurrentWindow.get().getSession().getClientService(service).executeAsyncServiceCall(functionName, arguments);
	}

	@Override
	public final boolean isEnabled()
	{
		return super.isEnabled() && (parent == null || parent.isEnabled());
	}
}
