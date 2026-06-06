/*
 * Copyright (C) 2022 Servoy BV
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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author jcomp
 *
 */
public class CssLibSet extends AbstractSet<CssLib>
{
	/**
	 * The backing map.
	 */
	private transient Map<CssLib, CssLib> m = new HashMap<>();

	@Override
	public boolean add(CssLib lib)
	{
		CssLib current = m.get(lib);
		if (current == null)
		{
			m.put(lib, lib);
		}
		else
		{
			if (current.getPriority() > lib.getPriority())
			{
				m.remove(current);
				m.put(lib, lib);
			}
			else
			{
				return false;
			}
		}
		return true;
	}


	@Override
	public boolean addAll(Collection< ? extends CssLib> c)
	{
		return c.stream().map(lib -> Boolean.valueOf(add(lib))).filter(b -> b.booleanValue() == true).count() > 0;
	}

	@Override
	public Iterator<CssLib> iterator()
	{
		return m.keySet().stream().sorted().iterator();
	}

	@Override
	public int size()
	{
		return m.size();
	}

}
