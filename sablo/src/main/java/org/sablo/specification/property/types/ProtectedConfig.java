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

package org.sablo.specification.property.types;

import org.json.JSONObject;

/**
 * Property configuration for protected types.
 *
 * TODO: validate if for-entries refer to existing properties
 *
 * @author rgansevles
 *
 */
public class ProtectedConfig
{
	public static final ProtectedConfig DEFAULTBLOCKING_TRUE = new ProtectedConfig(ForentriesConfig.DEFAULT, true);
	public static final ProtectedConfig DEFAULTBLOCKING_FALSE = new ProtectedConfig(ForentriesConfig.DEFAULT, false);

	private final ForentriesConfig forEntries;

	private final boolean blockingOn;

	protected ProtectedConfig(ForentriesConfig forEntries, boolean blockingOn)
	{
		this.forEntries = forEntries;
		this.blockingOn = blockingOn;
	}

	public ForentriesConfig getForEntries()
	{
		return forEntries;
	}

	public boolean getBlockingOn()
	{
		return blockingOn;
	}

	public static ProtectedConfig parse(JSONObject json, boolean defaultBlockingOn)
	{
		if (json == null)
		{
			return defaultBlockingOn ? DEFAULTBLOCKING_TRUE : DEFAULTBLOCKING_FALSE;
		}

		return new ProtectedConfig(ForentriesConfig.parse(json), json.has("blockingOn") ? json.optBoolean("blockingOn", defaultBlockingOn) : defaultBlockingOn);
	}
}
