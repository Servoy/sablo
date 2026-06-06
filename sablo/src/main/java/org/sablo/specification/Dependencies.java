/*
 * Copyright (C) 2021 Servoy BV
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

package org.sablo.specification;

import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author jcompanger
 * @since 2021.09
 *
 */
@SuppressWarnings("nls")
public class Dependencies
{

	private final JSONObject json;

	/**
	 * @param json can be null
	 */
	public Dependencies(JSONObject json)
	{
		this.json = json;
	}

	public String getServerscript()
	{
		return json != null ? json.optString("serverscript", null) : null;
	}

	/**
	 * @return
	 */
	public Set<CssLib> getCssLibrary()
	{
		if (json != null && json.has("csslibrary"))
		{
			JSONArray jsonArray = json.getJSONArray("csslibrary");
			CssLibSet lst = new CssLibSet();
			jsonArray.forEach(item -> lst.add(new CssLib((String)item)));
			return lst;
		}
		return null;
	}
}
