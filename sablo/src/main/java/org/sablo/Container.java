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
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.types.EnabledSabloValue;
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
	protected final Map<String, WebComponent> components = new HashMap<>();
	protected boolean changed;

	public Container(String name, WebComponentSpecification spec)
	{
		super(name, spec);
	}

	/**
	 * Called when it changes or any of it's children change.
	 */
	protected void markAsChanged()
	{
		changed = true;
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

	@Override
	public void dispose()
	{
		super.dispose();
		for (WebComponent component : components.values().toArray(new WebComponent[0]))
		{
			component.dispose();
		}
		components.clear();
	}

	public boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter<IBrowserConverterContext> converter,
		DataConversion clientDataConversions) throws JSONException
	{
		boolean contentHasBeenWritten = this.writeOwnComponentChanges(w, keyInParent, "", converter, clientDataConversions);
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = wc.writeOwnComponentChanges(w, contentHasBeenWritten ? null : keyInParent, wc.getName(), converter, clientDataConversions) ||
					contentHasBeenWritten;
		}
		if (contentHasBeenWritten) w.endObject();
		changed = false;
		return contentHasBeenWritten;
	}

	public boolean writeAllComponentsProperties(JSONWriter w, IToJSONConverter<IBrowserConverterContext> converter) throws JSONException
	{
		CurrentWindow.get().registerContainer(this);

		DataConversion clientDataConversions = new DataConversion();
		boolean contentHasBeenWritten = writeComponentProperties(w, converter, "", clientDataConversions);
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = wc.writeComponentProperties(w, converter, wc.getName(), clientDataConversions) || contentHasBeenWritten;
		}

		JSONUtils.writeClientConversions(w, clientDataConversions);
		changed = false;
		return contentHasBeenWritten;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sablo.WebComponent#flagPropertyAsDirty(java.lang.String, boolean)
	 */
	@Override
	public boolean flagPropertyAsDirty(String key, boolean dirty)
	{
		boolean modified = super.flagPropertyAsDirty(key, dirty);
		if (getRawPropertyValue(key, true) instanceof EnabledSabloValue)
		{
			for (WebComponent component : getComponents())
			{
				component.flagPropertyAsDirty(key, dirty);
			}
		}
		return modified;
	}
}
