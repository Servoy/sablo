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

import org.json.JSONObject;

/**
 * @author jcompanger
 * @since 2021.09
 */
@SuppressWarnings("nls")
public class NG2Config
{

	private final JSONObject json;

	/**
	 * @param json
	 */
	public NG2Config(JSONObject json)
	{
		this.json = json;
	}

	public Dependencies getDependencies()
	{
		return new Dependencies(this.json.optJSONObject("dependencies"));
	}

	public Assets getAssets()
	{
		return new Assets(this.json.optJSONArray("assets"));
	}

	/**
	 * @return
	 */
	public String getPackageName()
	{
		return json.optString("packageName", null);
	}

	/**
	 * @return
	 */
	public String getEntryPoint()
	{
		return json.optString("entryPoint", null);
	}

	/**
	 * @return
	 */
	public String getServiceName()
	{
		return json.optString("serviceName", null);
	}

	/**
	 * @return
	 */
	public String getModuleName()
	{
		return json.optString("moduleName", null);
	}
}
