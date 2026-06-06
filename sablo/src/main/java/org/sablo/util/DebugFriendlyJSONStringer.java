/*
 * Copyright (C) 2018 Servoy BV
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

package org.sablo.util;

import org.json.JSONStringer;

/**
 * Normally, JSONStringer only returns null from toString() until it's in done mode; this is annoying when debugging as you always have to expand it and see writer when you want to see partially written JSON.
 * This class does always print current contents of the JSONStringer's writer.
 *
 * @author acostescu
 */
public class DebugFriendlyJSONStringer extends JSONStringer
{

	@Override
	public String toString()
	{
		return writer.toString();
	}

}
