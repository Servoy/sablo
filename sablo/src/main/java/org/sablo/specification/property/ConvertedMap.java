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

package org.sablo.specification.property;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This class wraps a Map, representing the data in that Map in a converted form.
 * So any element operation from the outside world goes through a 2-way conversion until it reaches the 'base' List.
 *
 * For example put(..., X) would actually do a baseMap.put(..., convertToBase(X)).
 *
 * @author acostescu
 *
 * @param <ExternalT> the base map's element types.
 * @param <BaseT> the value types of this map
 */
//TODO these ET and WT are improper - as for object type they can represent multiple types (a different set for each child key), but they help to avoid some bugs at compile-time
//TODO here we should allow String and Number as key as JS & Rhino do allow for JS objects, instead of just String so extends AbstractMap<Object, ExternalT>
public abstract class ConvertedMap<ExternalT, BaseT> extends AbstractMap<String, ExternalT> /* for values() implementation */
{

	protected Map<String, BaseT> baseMap;
	protected ConvertedMap<ExternalT, BaseT>.EntrySet entrySet;

	public ConvertedMap()
	{
		this.baseMap = new HashMap<>();
	}

	public ConvertedMap(Map<String, BaseT> base)
	{
		this.baseMap = base;
	}

	protected void initFromExternal(Map<String, ExternalT> external)
	{
		for (Entry<String, ExternalT> e : external.entrySet())
		{
			baseMap.put(e.getKey(), convertToBase(e.getKey(), true, e.getValue()));
		}
	}

	public Map<String, BaseT> getBaseMap()
	{
		return baseMap;
	}

	/**
	 * Converts the given base map element value into external world form.
	 *
	 * @param forKey the key that the given element has in the map.
	 * @param value the base map element value to convert.
	 * @return the converted element value.
	 */
	protected abstract ExternalT convertFromBase(String forKey, BaseT value);

	/**
	 * Converts the given external world element value into a base map element value.
	 *
	 * @param forKey the key that "value" used to have before or will start having now; can be null if not applicable.
	 * @param ignoreOldValue if this is true, even no lookup for an old value should be done by convert (null/not present is assumed).
	 * @param value the external world element value to convert.
	 * @return the base converted element value.
	 */
	protected abstract BaseT convertToBase(String forKey, boolean ignoreOldValue, ExternalT value);

//	@Override
//	public int size()
//	{
//		return baseMap.size();
//	}
//
//	@Override
//	public boolean isEmpty()
//	{
//		return baseMap.isEmpty();
//	}
//
//	@Override
//	public boolean containsKey(Object key)
//	{
//		return baseMap.containsKey(key);
//	}
//
//	@Override
//	public boolean containsValue(Object value)
//	{
//		return baseMap.containsValue(convertToBase(null, (ExternalT)value));
//	}
//
//	@Override
//	public ExternalT get(Object key)
//	{
//		return convertFromBase(baseMap.get(key));
//	}

	@Override
	public ExternalT put(String key, ExternalT value)
	{
		return convertFromBase(key, baseMap.put(key, convertToBase(key, false, value)));
	}

//	@Override
//	public ExternalT remove(Object key)
//	{
//		return convertFromBase(baseMap.remove(key));
//	}
//
//	@Override
//	public void putAll(Map< ? extends String, ? extends ExternalT> m)
//	{
//		for (Entry< ? extends String, ? extends ExternalT> e : m.entrySet())
//		{
//			baseMap.put(e.getKey(), convertToBase(e.getKey(), e.getValue()));
//		}
//	}
//
//	@Override
//	public void clear()
//	{
//		baseMap.clear();
//	}
//
//	@Override
//	public Set<String> keySet()
//	{
//		return baseMap.keySet();
//	}

	@Override
	public Set<Map.Entry<String, ExternalT>> entrySet()
	{
		Set<Map.Entry<String, ExternalT>> es = entrySet;
		return es != null ? es : (entrySet = new EntrySet());
	}

	protected final class EntrySet extends AbstractSet<Map.Entry<String, ExternalT>>
	{
		@Override
		public Iterator<Map.Entry<String, ExternalT>> iterator()
		{
			final Iterator<java.util.Map.Entry<String, BaseT>> baseIterator = baseMap.entrySet().iterator();
			return new Iterator<Map.Entry<String, ExternalT>>()
			{

				@Override
				public boolean hasNext()
				{
					return baseIterator.hasNext();
				}

				@Override
				public java.util.Map.Entry<String, ExternalT> next()
				{
					final java.util.Map.Entry<String, BaseT> bv = baseIterator.next();
					return new Map.Entry<String, ExternalT>()
					{

						@Override
						public String getKey()
						{
							return bv.getKey();
						}

						@Override
						public ExternalT getValue()
						{
							return convertFromBase(bv.getKey(), bv.getValue());
						}

						@Override
						public ExternalT setValue(ExternalT value)
						{
							return convertFromBase(bv.getKey(), bv.setValue(convertToBase(bv.getKey(), false, value)));
						}

					};
				}

				@Override
				public void remove()
				{
					baseIterator.remove();
				}

			};
		}

		@Override
		public int size()
		{
			return baseMap.size();
		}

	}

}