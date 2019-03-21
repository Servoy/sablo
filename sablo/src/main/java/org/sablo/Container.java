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
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Container object is a component that can contain other components.
 * @author jblok
 */
public abstract class Container extends WebComponent
{
	private Map<String, WebComponent> components = new HashMap<>();
	protected boolean changed;

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
		return changed;
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

	public WebComponent getComponent(String name)
	{
		return components.get(name);
	}

	public Collection<WebComponent> getComponents()
	{
		return Collections.unmodifiableCollection(components.values());
	}

	public void clearComponents()
	{
		components = new HashMap<>();
	}

	@Override
	public void dispose()
	{
		super.dispose();
		for (WebComponent component : components.values().toArray(new WebComponent[0]))
		{
			component.dispose();
		}
		clearComponents();
	}

	public void writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
		DataConversion clientDataConversions) throws JSONException
	{
		// converter here is always ChangesToJSONConverter except for some unit tests
		changed = false; // do this first just in case one of the writeOwnChanges below triggers another change to set the flag again (which should not happen normally but it will get logged as a warning by code in BaseWebObject if it does)
		boolean contentHasBeenWritten = this.writeOwnChanges(w, keyInParent, "", converter, clientDataConversions);
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = wc.writeOwnChanges(w, contentHasBeenWritten ? null : keyInParent, wc.getName(), converter, clientDataConversions) ||
				contentHasBeenWritten;
		}
		if (contentHasBeenWritten) w.endObject();
	}

	public boolean writeAllComponentsProperties(JSONWriter w, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
	{
		// FIXME if this container was previously registered to another window, unregister it from there as well
		// if (this.getParentOfType(window) != CurrentWindow.get()) ... // problem is now that containers are not required to have parents so can we really rely on parent hierarchy?
		CurrentWindow.get().registerContainer(this);

		changed = false; // do this first just in case one of the writeComponentProperties below triggers another change to set the flag again (which should not happen normally but if it does we should not loose that change by just clearing the changed flag afterwards)
		DataConversion clientDataConversions = new DataConversion();
		boolean contentHasBeenWritten = writeComponentProperties(w, converter, "", clientDataConversions);
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = wc.writeComponentProperties(w, converter, wc.getName(), clientDataConversions) || contentHasBeenWritten;
		}

		JSONUtils.writeClientConversions(w, clientDataConversions);
		return contentHasBeenWritten;
	}
}
