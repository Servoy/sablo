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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IWindow;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Container object is a component that can contain other components.
 * @author jblok
 */
public abstract class Container extends WebComponent
{
	private Map<String, WebComponent> components = new HashMap<>();
	protected boolean changed;

	private IWindow alreadyRegisteredToWindow;

	public Container(String name, WebObjectSpecification spec)
	{
		super(name, spec);
	}

	public Container(String name, WebObjectSpecification spec, boolean waitForPropertyInitBeforeAttach)
	{
		super(name, spec, waitForPropertyInitBeforeAttach);
	}

	/**
	 * Called when it changes or any of it's children change.
	 */
	protected void markAsChanged()
	{
		changed = true;
		if (parent != null) parent.markAsChanged();
	}

	public boolean isChanged()
	{
		return changed || hasChanges();
	}

	public void add(WebComponent component)
	{
		WebComponent old = components.put(component.getName(), component);
		if (old != null)
		{ // should never happen I think
			old.parent = null;
		}
		component.parent = this;
		if (component.hasChanges()) markAsChanged();
	}

	public void remove(WebComponent component)
	{
		components.remove(component.getName());
		component.parent = null;
	}

	/**
	 * Get a direct child component of this container - by name. It will not search inside child containers.
	 */
	public WebComponent getComponent(String name)
	{
		return components.get(name);
	}

	/**
	 * Returns the direct child components of this container; it will not go inside child containers to search for components.
	 */
	public Collection<WebComponent> getComponents()
	{
		return Collections.unmodifiableCollection(components.values());
	}

	public void clearComponents()
	{
		for (WebComponent component : components.values().toArray(new WebComponent[0]))
		{
			component.dispose();
		}
		components = new HashMap<>();
	}

	@Override
	public void dispose()
	{
		super.dispose();
		if (alreadyRegisteredToWindow != null) alreadyRegisteredToWindow.unregisterContainer(this);
		clearComponents();
	}

	public void writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
	{
		// converter here is always ChangesToJSONConverter except for some unit tests
		changed = false; // do this first just in case one of the writeOwnChanges below triggers another change to set the flag again (which should not happen normally but it will get logged as a warning by code in BaseWebObject if it does)
		boolean contentHasBeenWritten = this.writeOwnChanges(w, keyInParent, "", converter);
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = wc.writeOwnChanges(w, contentHasBeenWritten ? null : keyInParent, wc.getName(), converter) || contentHasBeenWritten;
		}
		if (contentHasBeenWritten) w.endObject();
	}

	public boolean writeAllComponentsProperties(JSONWriter w, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
	{
		IWindow currentWindow = CurrentWindow.get();
		if (alreadyRegisteredToWindow != currentWindow)
		{
			if (alreadyRegisteredToWindow != null) alreadyRegisteredToWindow.unregisterContainer(this);
			alreadyRegisteredToWindow = currentWindow;
			currentWindow.registerContainer(this); // keeps this in a weak hashmap
		}

		changed = false; // do this first just in case one of the writeComponentProperties below triggers another change to set the flag again (which should not happen normally but if it does we should not loose that change by just clearing the changed flag afterwards)
		boolean contentHasBeenWritten = writeComponentProperties(w, converter, "");
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = wc.writeComponentProperties(w, converter, wc.getName()) || contentHasBeenWritten;
		}

		return contentHasBeenWritten;
	}

	public void clearRegisteredToWindow()
	{
		alreadyRegisteredToWindow = null;
	}

}
