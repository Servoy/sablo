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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.WebsocketEndpoint;
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
			old.setDirtyPropertyListener(null);
		}
		component.parent = this;
		component.setDirtyPropertyListener(new IDirtyPropertyListener()
		{
			@Override
			public void propertyFlaggedAsDirty(String propertyName)
			{
				markAsChanged();
			}
		});
	}

	public void remove(WebComponent component)
	{
		components.remove(component.getName());
		component.parent = null;
		component.setDirtyPropertyListener(null);
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

	public boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		changed = false;
		ArrayList<WebComponent> allComponents = new ArrayList<WebComponent>();
		allComponents.add(this); // add the container itself
		allComponents.addAll(getComponents());

		boolean contentHasBeenWritten = false;
		for (WebComponent wc : allComponents)
		{
			TypedData<Map<String, Object>> changes = wc.getChanges();
			if (changes.content.size() > 0)
			{
				if (!contentHasBeenWritten)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();
					contentHasBeenWritten = true;
				}
				String childName = wc == this ? "" : wc.getName();
				w.key(childName).object();
				clientDataConversions.pushNode(childName);
				JSONUtils.writeData(converter, w, changes.content, changes.contentType, clientDataConversions, wc);
				clientDataConversions.popNode();
				w.endObject();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		changed = false;
		return contentHasBeenWritten;
	}

	public boolean writeAllComponentsProperties(JSONWriter w, IToJSONConverter converter) throws JSONException
	{
		WebsocketEndpoint.get().registerContainer(this);
		DataConversion clientDataConversions = new DataConversion();

		ArrayList<WebComponent> allComponents = new ArrayList<WebComponent>();
		allComponents.add(this); // add the form itself
		allComponents.addAll(getComponents());

		boolean contentHasBeenWritten = false;
		for (WebComponent wc : allComponents)
		{
			TypedData<Map<String, Object>> changes = wc.getProperties();
			wc.clearChanges();
			String childName = (wc == this ? "" : wc.getName());
			if (changes.content.size() > 0)
			{
				w.key(childName).object();
				contentHasBeenWritten = true;
				clientDataConversions.pushNode(childName);
				JSONUtils.writeData(converter, w, changes.content, changes.contentType, clientDataConversions, wc);
				clientDataConversions.popNode();
				w.endObject();
			}
		}
		JSONUtils.writeClientConversions(w, clientDataConversions);
		changed = false;
		return contentHasBeenWritten;
	}
}
