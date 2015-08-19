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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.sablo.BaseWebObject;
import org.sablo.IChangeListener;

/**
 * This map is able to do handle/keep track of server side changes.
 * Those changes can then be sent to browser through full map or granular updates (depending on what changed an what the implementation supports).
 * (as JSON through the web-socket)
 *
 * It also implements ISmartPropertyValue so that it can handle correctly any child 'smart' value types.
 *
 * @author acostescu
 */
//TODO these ET and WT are improper - as for object type they can represent multiple types (a different set for each child key), but they help to avoid some bugs at compile-time
public class ChangeAwareMap<ET, WT> extends AbstractMap<String, ET> implements ISmartPropertyValue
{

	// TODO this class should keep a kind of pks to avoid a scenario where server and browser get modified at the same time
	// and a granular update ends up doing incorrect modifications (as it's being applied in wrong place); for now we just drop
	// the browser changes when this happens through the 'version' mechanism
	private int version;

	protected IPropertyType<ET> type;
	protected Map<String, ET> baseMap;

	protected Map<String, KeyChangeListener> changeHandlers = new HashMap<>();

	// TODO in order to have fine-grained add/remove operations as well in the future we would need a list of change operations that can be add/remove/change instead
	protected IChangeListener changeMonitor;
	protected BaseWebObject component;

	protected Set<String> changedKeys = new HashSet<String>();
	protected boolean allChanged;

	protected boolean mustSendTypeToClient;

	protected EntrySet entrySet;

	public ChangeAwareMap(Map<String, ET> baseMap)
	{
		this(baseMap, 1);
	}

	public ChangeAwareMap(Map<String, ET> baseMap, int initialVersion)
	{
		this.baseMap = baseMap;
		this.version = initialVersion;

		if (baseMap instanceof IAttachAware) ((IAttachAware<WT>)baseMap).setAttachHandler(new IAttachHandler<WT>()
		{
			@Override
			public void attachToBaseObjectIfNeeded(String key, WT value)
			{
				if (changeMonitor != null) attachToBaseObject(key, value);
			}

			@Override
			public void detachFromBaseObjectIfNeeded(String key, WT value)
			{
				detachIfNeeded(key, value);
			}
		});
	}

	/**
	 * This interface can be used when this change aware map is based on a map that can change it's returned contents
	 * by other means then through this proxy wrapper. It provides a way to attach / detach elements directly from the base map.
	 */
	public static interface IAttachAware<WT>
	{
		void setAttachHandler(IAttachHandler<WT> attachHandler);
	}

	/**
	 * This interface can be used when this change aware map is based on a map that can change it's returned contents
	 * by other means then through this proxy wrapper. It provides a way to attach / detach elements directly from the base map.
	 */
	public static interface IAttachHandler<WT>
	{
		void attachToBaseObjectIfNeeded(String key, WT value);

		void detachFromBaseObjectIfNeeded(String key, WT value);
	}

	/**
	 * You should not change the contents of the returned Set.
	 */
	public Set<String> getChangedKeys()
	{
		return changedKeys;
	}

	public boolean mustSendTypeToClient()
	{
		return mustSendTypeToClient;
	}

	public boolean mustSendAll()
	{
		return allChanged;
	}

	public void clearChanges()
	{
		allChanged = false;
		mustSendTypeToClient = false;
		changedKeys.clear();
	}

	/**
	 * Don't use the returned map for operations that make changes that need to be sent to browser! That map isn't tracked for changes.
	 */
	public Map<String, ET> getBaseMap()
	{
		return baseMap;
	}

	/**
	 * Don't use the returned map for operations that make changes that need to be sent to browser! That map isn't tracked for changes.
	 * If you need to make a put directly in wrapper base list please use {@link #putInWrappedBaseList()} instead.
	 */
	protected Map<String, WT> getWrappedBaseMapForReadOnly()
	{
		Map<String, WT> wrappedBaseMap;
		if (baseMap instanceof IWrappedBaseMapProvider< ? >)
		{
			wrappedBaseMap = ((IWrappedBaseMapProvider<WT>)baseMap).getWrappedBaseMap();
		}
		else
		{
			wrappedBaseMap = (Map<String, WT>)baseMap; // ET == WT in this case; no wrapping
		}
		return wrappedBaseMap;
	}

	// privately we can use that map for making changes, as we know what has to be done when that happens
	private Map<String, WT> getWrappedBaseMap()
	{
		return getWrappedBaseMapForReadOnly();
	}

	public WT putInWrappedBaseList(String key, WT value, boolean markChanged)
	{
		WT tmp = getWrappedBaseMapForReadOnly().put(key, value);
		if (tmp != value)
		{
			detachIfNeeded(key, tmp);
			attachToBaseObjectIfNeeded(key, value);
		}
		if (markChanged) markElementChanged(key);

		return tmp;
	}

	protected int increaseContentVersion()
	{
		// currently this is only increased when a new version is sent client side; normally any changes on the list itself
		// and any updates from client should be executing in the same thread one after the other; that means that you shouldn't
		// get into a situation where this list is modified, the version is not yet increased (as it's not yet sent to client) and an
		// update comes and does granular updates verifying the old version (because before another task executes, when the list changes it should
		// also serialize changes to browser which would increase the version).
		return ++version;
	}

	protected int getListContentVersion()
	{
		return version;
	}

	protected void markMustSendTypeToClient()
	{
		boolean oldMustSendTypeToClient = mustSendTypeToClient;
		mustSendTypeToClient = true;
		if (changedKeys.size() == 0 && !allChanged && !oldMustSendTypeToClient && changeMonitor != null) changeMonitor.valueChanged();
	}

	protected void markElementChanged(String key)
	{
		if (changedKeys.add(key) && !allChanged && !mustSendTypeToClient && changeMonitor != null) changeMonitor.valueChanged();
	}

	public void markAllChanged()
	{
		boolean alreadyCh = allChanged;
		allChanged = true;
		if (!alreadyCh && changedKeys.size() == 0 && !mustSendTypeToClient && changeMonitor != null) changeMonitor.valueChanged();
	}

	protected void attachToBaseObjectIfNeeded(String key, WT el)
	{
		if (changeMonitor != null) attachToBaseObject(key, el);
	}

	@Override
	public void attachToBaseObject(final IChangeListener changeMonitor, BaseWebObject component)
	{
		this.changeMonitor = changeMonitor;
		this.component = component;

		Map<String, WT> wrappedBaseList = getWrappedBaseMap();
		for (java.util.Map.Entry<String, WT> e : wrappedBaseList.entrySet())
		{
			attachToBaseObject(e.getKey(), e.getValue());
		}

		if (isChanged()) changeMonitor.valueChanged();
	}

	protected boolean isChanged()
	{
		return (allChanged || mustSendTypeToClient || changedKeys.size() > 0);
	}

	// called whenever a new element was added or inserted into the array
	// TODO currently here we use the wrapped value for ISmartPropertyValue, but BaseWebObject uses the unwrapped value; I think the BaseWebObject
	// should be changes to use wrapped as well; either way, it should be the same (currently this works as we don't have any wrapper type with 'smart' values for which the wrapped value differs from the unwrapped value)
	protected void attachToBaseObject(final String key, WT el)
	{
		if (el instanceof ISmartPropertyValue)
		{
			ChangeAwareMap<ET, WT>.KeyChangeListener changeHandler = new KeyChangeListener(key);
			changeHandlers.put(key, changeHandler);
			((ISmartPropertyValue)el).attachToBaseObject(changeHandler, component);
		}
	}

	protected class KeyChangeListener implements IChangeListener
	{

		private final String attachedToKey;

		public KeyChangeListener(String attachedToKey)
		{
			this.attachedToKey = attachedToKey;
		}

		@Override
		public void valueChanged()
		{
			markElementChanged(attachedToKey);
		}

	}

	@Override
	public void detach()
	{
		Map<String, WT> wrappedBaseList = getWrappedBaseMap();
		for (java.util.Map.Entry<String, WT> e : wrappedBaseList.entrySet())
		{
			detach(e.getKey(), e.getValue());
		}

		component = null;
		changeMonitor = null;
	}

	// TODO currently here we use the wrapped value for ISmartPropertyValue, but BaseWebObject uses the unwrapped value; I think the BaseWebObject
	// should be changes to use wrapped as well; either way, it should be the same (currently this works as we don't have any wrapper type with 'smart' values for which the wrapped value differs from the unwrapped value)
	protected void detach(String key, WT el)
	{
		if (el instanceof ISmartPropertyValue)
		{
			((ISmartPropertyValue)el).detach();
			changeHandlers.remove(key);
		}
	}

	protected void detachIfNeeded(String key, WT el)
	{
		if (changeMonitor != null) detach(key, el);
	}

	@Override
	public ET put(String key, ET value)
	{
		ET tmp = baseMap.put(key, value);
		attachToBaseObjectIfNeeded(key, getWrappedBaseMap().get(key));
		markElementChanged(key);
		return tmp;
	}

	@Override
	public Set<java.util.Map.Entry<String, ET>> entrySet()
	{
		Set<Map.Entry<String, ET>> es = entrySet;
		return es != null ? es : (entrySet = new EntrySet());
	}

	protected final class EntrySet extends AbstractSet<Map.Entry<String, ET>>
	{
		@Override
		public Iterator<Map.Entry<String, ET>> iterator()
		{
			final Iterator<java.util.Map.Entry<String, ET>> baseIterator = baseMap.entrySet().iterator();
			return new Iterator<Map.Entry<String, ET>>()
			{

				private java.util.Map.Entry<String, ET> currentEl;

				@Override
				public boolean hasNext()
				{
					return baseIterator.hasNext();
				}

				@Override
				public java.util.Map.Entry<String, ET> next()
				{
					return currentEl = baseIterator.next();
				}

				@Override
				public void remove()
				{
					WT oldWrappedValue = getWrappedBaseMap().get(currentEl.getKey());
					baseIterator.remove();
					markAllChanged();
					detachIfNeeded(currentEl.getKey(), oldWrappedValue);
				}

			};
		}

		@Override
		public int size()
		{
			return baseMap.size();
		}

	}

//	@Override
//	public int size()
//	{
//		return baseList.size();
//	}
//
//	@Override
//	public boolean isEmpty()
//	{
//		return baseList.isEmpty();
//	}
//
//	@Override
//	public boolean contains(Object o)
//	{
//		return baseList.contains(o);
//	}
//
//	@Override
//	public Iterator<ET> iterator()
//	{
//		final Iterator<ET> it = baseList.iterator();
//		return new Iterator<ET>()
//		{
//
//			int i = -1;
//
//			@Override
//			public boolean hasNext()
//			{
//				return it.hasNext();
//			}
//
//			@Override
//			public ET next()
//			{
//				i++;
//				return it.next();
//			}
//
//			@Override
//			public void remove()
//			{
//				it.remove();
//				detachIfNeeded(i, getWrappedBaseList().get(i), true);
//				i--;
//				markAllChanged();
//			}
//		};
//	}
//
//	@Override
//	public Object[] toArray()
//	{
//		return baseList.toArray();
//	}
//
//	@Override
//	public <T> T[] toArray(T[] a)
//	{
//		return baseList.toArray(a);
//	}
//
//	@Override
//	public boolean add(ET e)
//	{
//		boolean tmp = baseList.add(e);
//		attachToBaseObjectIfNeeded(baseList.size() - 1, getWrappedBaseList().get(baseList.size() - 1), false);
//		markAllChanged();
//		return tmp;
//	}
//
//	@Override
//	public boolean remove(Object o)
//	{
//		int idx = baseList.indexOf(o);
//		if (idx >= 0)
//		{
//			WT oldWrappedValue = getWrappedBaseList().get(idx);
//			baseList.remove(idx);
//			detachIfNeeded(idx, oldWrappedValue, true);
//
//			markAllChanged();
//			return true;
//		}
//		return false;
//	}
//
//	@Override
//	public boolean containsAll(Collection< ? > c)
//	{
//		return baseList.containsAll(c);
//	}
//
//	@Override
//	public boolean addAll(Collection< ? extends ET> c)
//	{
//		if (c.size() > 0)
//		{
//			for (ET el : c)
//				add(el);
//			return true;
//		}
//		return false;
//	}
//
//	@Override
//	public boolean addAll(int index, Collection< ? extends ET> c)
//	{
//		if (c.size() > 0)
//		{
//			int j = index;
//			for (ET el : c)
//				add(j++, el);
//			return true;
//		}
//		return false;
//	}
//
//	@Override
//	public boolean removeAll(Collection< ? > c)
//	{
//		boolean changed = false;
//		for (Object o : c)
//			changed = changed || remove(o);
//
//		return changed;
//	}
//
//	@Override
//	public boolean retainAll(Collection< ? > c)
//	{
//		int i;
//		boolean changed = false;
//		for (i = baseList.size() - 1; i >= 0; i--)
//		{
//			if (!c.contains(get(i)))
//			{
//				remove(i);
//				changed = true;
//			}
//		}
//		return changed;
//	}
//
//	@Override
//	public void clear()
//	{
//		int size = baseList.size();
//		for (int i = size; i >= 0; i--)
//			remove(i);
//		if (size > 0) markAllChanged();
//	}
//
//	@Override
//	public ET get(int index)
//	{
//		return baseList.get(index);
//	}
//
//	@Override
//	public ET set(int index, ET element)
//	{
//		WT oldWV = getWrappedBaseList().get(index);
//		ET tmp = baseList.set(index, element);
//		detachIfNeeded(index, oldWV, false);
//		attachToBaseObjectIfNeeded(index, getWrappedBaseList().get(index), false);
//		markElementChanged(index);
//		return tmp;
//	}
//
//	@Override
//	public void add(int index, ET element)
//	{
//		baseList.add(index, element);
//		attachToBaseObjectIfNeeded(index, getWrappedBaseList().get(index), true);
//		markAllChanged();
//	}
//
//	@Override
//	public ET remove(int index)
//	{
//		WT oldWV = getWrappedBaseList().get(index);
//		ET tmp = baseList.remove(index);
//		detachIfNeeded(index, oldWV, true);
//		markAllChanged();
//		return tmp;
//	}
//
//	@Override
//	public int indexOf(Object o)
//	{
//		return baseList.indexOf(o);
//	}
//
//	@Override
//	public int lastIndexOf(Object o)
//	{
//		return baseList.lastIndexOf(o);
//	}
//
//	@Override
//	public ListIterator<ET> listIterator()
//	{
//		return new ChangeAwareListIterator<ET>(baseList.listIterator());
//	}
//
//	@Override
//	public ListIterator<ET> listIterator(int index)
//	{
//		return new ChangeAwareListIterator<ET>(baseList.listIterator(index));
//	}
//
//	@Override
//	public List<ET> subList(int fromIndex, int toIndex)
//	{
//		return baseList.subList(fromIndex, toIndex);
//	}
//
//	protected class ChangeAwareListIterator<ET> implements ListIterator<ET>
//	{
//
//		protected final ListIterator<ET> it;
//
//		public ChangeAwareListIterator(ListIterator<ET> it)
//		{
//			this.it = it;
//		}
//
//		@Override
//		public boolean hasNext()
//		{
//			return it.hasNext();
//		}
//
//		@Override
//		public ET next()
//		{
//			return it.next();
//		}
//
//		@Override
//		public boolean hasPrevious()
//		{
//			return it.hasPrevious();
//		}
//
//		@Override
//		public ET previous()
//		{
//			return it.previous();
//		}
//
//		@Override
//		public int nextIndex()
//		{
//			return it.nextIndex();
//		}
//
//		@Override
//		public int previousIndex()
//		{
//			return it.previousIndex();
//		}
//
//		@Override
//		public void remove()
//		{
//			int i = it.previousIndex() + 1;
//			it.remove();
//			detachIfNeeded(i, getWrappedBaseList().get(i), true);
//			markAllChanged();
//		}
//
//		@Override
//		public void set(ET e)
//		{
//			int i = it.previousIndex() + 1;
//			WT oldWV = getWrappedBaseList().get(i);
//			it.set(e);
//			detachIfNeeded(i, oldWV, false);
//			attachToBaseObjectIfNeeded(i, getWrappedBaseList().get(i), false);
//			markElementChanged(i);
//		}
//
//		@Override
//		public void add(ET e)
//		{
//			int i = it.nextIndex();
//			it.add(e);
//			attachToBaseObjectIfNeeded(i, getWrappedBaseList().get(i), true);
//			markAllChanged();
//		}
//	}

	@Override
	public String toString()
	{
		return "#CAM# " + getBaseMap().toString();
	}

}
