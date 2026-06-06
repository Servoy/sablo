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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * This class wraps a List, representing the data in that array list in a converted form.
 * So any element operation from the outside world goes through a 2-way conversion until it reaches the 'base' List.
 *
 * For example add(X) would actually do a baseList.add(convertToBase(X)).
 *
 * @author acostescu
 *
 * @param <ExternalT> the base list's element types.
 * @param <BaseT> the element types of this list
 */
public abstract class ConvertedList<ExternalT, BaseT> extends AbstractList<ExternalT>/* mostly for subList() impl */implements List<ExternalT>
{

	protected List<BaseT> baseList;

	public ConvertedList()
	{
		this.baseList = new ArrayList<>();
	}

	public ConvertedList(List<BaseT> base)
	{
		this.baseList = base;
	}

	protected void initFromExternal(List<ExternalT> external)
	{
		for (ExternalT e : external)
		{
			baseList.add(convertToBase(-1, e));
		}
	}

	public List<BaseT> getBaseList()
	{
		return baseList;
	}

	/**
	 * Converts the given base list element value into external world form.
	 *
	 * @param index the index that the given element has in the list.
	 * @param value the base list element value to convert.
	 * @return the converted element value.
	 */
	protected abstract ExternalT convertFromBase(int index, BaseT value);

	/**
	 * Converts the given external world element value into a base list element value.
	 *
	 * @param index the index that "value" used to have before; can be -1 if not applicable.
	 * @param value the external world element value to convert.
	 * @return the base converted element value.
	 */
	protected abstract BaseT convertToBase(int index, ExternalT value);

	@Override
	public int size()
	{
		return baseList.size();
	}

	@Override
	public boolean isEmpty()
	{
		return baseList.isEmpty();
	}

	@Override
	public boolean contains(Object o)
	{
		return baseList.contains(convertToBase(-1, (ExternalT)o));
	}

	@Override
	public Iterator<ExternalT> iterator()
	{
		final Iterator<BaseT> it = baseList.iterator();
		return new Iterator<ExternalT>()
		{
			int i = -1;

			@Override
			public boolean hasNext()
			{
				return it.hasNext();
			}

			@Override
			public ExternalT next()
			{
				i++;
				return convertFromBase(i, it.next());
			}

			@Override
			public void remove()
			{
				i--;
				it.remove();
			}
		};
	}

	@Override
	public Object[] toArray()
	{
		List<ExternalT> arrayList = createAsArrayList();
		return arrayList.toArray();
	}

	protected List<ExternalT> createAsArrayList()
	{
		List<ExternalT> arrayList = new ArrayList<ExternalT>();
		int i = 0;
		for (BaseT el : baseList)
		{
			arrayList.add(convertFromBase(i++, el));
		}
		return arrayList;
	}

	@Override
	public <T> T[] toArray(T[] a)
	{
		return createAsArrayList().toArray(a);
	}

	@Override
	public boolean add(ExternalT e)
	{
		return baseList.add(convertToBase(-1, e));
	}

	@Override
	public boolean remove(Object o)
	{
		return baseList.remove(convertToBase(-1, (ExternalT)o));
	}

	@Override
	public boolean containsAll(Collection< ? > c)
	{
		for (Object el : c)
		{
			if (!contains(el))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection< ? extends ExternalT> c)
	{
		for (ExternalT el : c)
		{
			add(el);
		}
		return c.size() > 0;
	}

	@Override
	public boolean addAll(int index, Collection< ? extends ExternalT> c)
	{
		int j = index;
		for (ExternalT el : c)
		{
			add(j, el);
			j++;
		}
		return c.size() > 0;
	}

	@Override
	public boolean removeAll(Collection< ? > c)
	{
		boolean removed = false;
		for (Object el : c)
		{
			removed = removed || remove(el);
		}
		return removed;
	}

	@Override
	public boolean retainAll(Collection< ? > c)
	{
		boolean changed = false;
		for (int i = baseList.size() - 1; i >= 0; i--)
		{
			if (!c.contains(get(i)))
			{
				remove(i);
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public void clear()
	{
		baseList.clear();
	}

	@Override
	public ExternalT get(int index)
	{
		return convertFromBase(index, baseList.get(index));
	}

	@Override
	public ExternalT set(int index, ExternalT element)
	{
		return convertFromBase(index, baseList.set(index, convertToBase(index, element)));
	}

	@Override
	public void add(int index, ExternalT element)
	{
		baseList.add(index, convertToBase(-1, element));
	}

	@Override
	public ExternalT remove(int index)
	{
		return convertFromBase(index, baseList.remove(index));
	}

	@Override
	public int indexOf(Object o)
	{
		return baseList.indexOf(convertToBase(-1, (ExternalT)o));
	}

	@Override
	public int lastIndexOf(Object o)
	{
		return baseList.lastIndexOf(convertToBase(-1, (ExternalT)o));
	}

	@Override
	public ListIterator<ExternalT> listIterator()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ListIterator<ExternalT> listIterator(int index)
	{
		// TODO Auto-generated method stub
		return null;
	}

	protected class ConvertedListIterator implements ListIterator<ExternalT>
	{

		protected final ListIterator<BaseT> it;
		int i = -1;

		public ConvertedListIterator(ListIterator<BaseT> it)
		{
			this.it = it;
		}

		@Override
		public boolean hasNext()
		{
			return it.hasNext();
		}

		@Override
		public ExternalT next()
		{
			i++;
			return convertFromBase(i, it.next());
		}

		@Override
		public boolean hasPrevious()
		{
			return it.hasPrevious();
		}

		@Override
		public ExternalT previous()
		{
			i--;
			return convertFromBase(i, it.previous());
		}

		@Override
		public int nextIndex()
		{
			return it.nextIndex();
		}

		@Override
		public int previousIndex()
		{
			return it.previousIndex();
		}

		@Override
		public void remove()
		{
			i--;
			it.remove();
		}

		@Override
		public void set(ExternalT e)
		{
			it.set(convertToBase(i, e));
		}

		@Override
		public void add(ExternalT e)
		{
			i++;
			it.add(convertToBase(-1, e));
		}
	}

}