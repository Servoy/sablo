/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2025 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
*/

package org.sablo.specification.property;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * VERY IMPORTANT
 * getDependencies methods can be used safely during the design time (when Servoy Developer is loading) - from PropertyDescription and
 * types which are implementing this interface. There are no interferences between these two call moments.
 * Calling them outside PropertyDescription class - can lead to unpredictable results
 *
 * @author marianvid
 *
 */
public interface IPropertyCanDependsOn
{

	public static final String[] DEPENDS_ON_ALL = new String[] { "depends_on_all" };

	default String[] getDependencies()
	{
		return DEPENDS_ON_ALL;
	}

	@SuppressWarnings("nls")
	default String[] getDependencies(JSONObject json, String[] dependencies)
	{
		if (json == null) return dependencies; //nothing to set OR already set
		String[] result = null;
		if (json.has("for"))
		{
			Object object = json.get("for");
			if (object instanceof JSONArray)
			{
				JSONArray arr = (JSONArray)object;
				result = new String[arr.length()];
				for (int i = 0; i < arr.length(); i++)
				{
					result[i] = arr.getString(i);
				}
			}
			else if (object instanceof String)
			{
				result = new String[] { (String)object };
			}
		}
		return result;
	}
}
