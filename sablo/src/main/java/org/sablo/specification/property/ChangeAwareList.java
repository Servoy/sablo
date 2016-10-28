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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.sablo.BaseWebObject;
import org.sablo.IChangeListener;

/**
 * This list is able to do handle/keep track of server side changes.
 * Those changes can then be sent to browser through full array or granular updates (depending on what changed an what the implementation supports).
 * (as JSON through the web-socket)
 *
 * It also implements ISmartPropertyValue so that it can handle correctly any child 'smart' value types.
 *
 * @author acostescu
 */
public class ChangeAwareList<ET, WT> implements List<ET>, ISmartPropertyValue
{

	// TODO this class should keep a kind of pks to avoid a scenario where server and browser get modified at the same time
	// and a granular update ends up doing incorrect modifications (as it's being applied in wrong place); for now we just drop
	// the browser changes when this happens through the 'version' mechanism
	private int version;
	private int lastResetDueToOutOfSyncVersion = 0;

	protected List<ET> baseList;

	protected List<IndexChangeListener> changeHandlers = new ArrayList<>();

	// TODO in order to have fine-grained add/remove operations as well in the future we would need a list of change operations that can be add/remove/change instead
	protected IChangeListener changeMonitor;
	protected BaseWebObject component;

	protected Set<Integer> changedIndexes = new HashSet<Integer>();
	protected boolean allChanged;

	protected boolean mustSendTypeToClient;


	public ChangeAwareList(List<ET> baseList)
	{
		this(baseList, 1);
	}

	public ChangeAwareList(List<ET> baseList, int initialVersion)
	{
		this.baseList = baseList;
		this.version = initialVersion;

		if (baseList instanceof IAttachAware) ((IAttachAware<WT>)baseList).setAttachHandler(new IAttachHandler<WT>()
		{
			@Override
			public void attachToBaseObjectIfNeeded(int i, WT value)
			{
				if (changeMonitor != null) attachToBaseObject(i, value, false);
			}

			@Override
			public void detachFromBaseObjectIfNeeded(int i, WT value)
			{
				detachIfNeeded(i, value, false);
			}
		});
	}

	/**
	 * This interface can be used when this change aware list is based on a list that can change it's returned contents
	 * by other means then through this proxy wrapper. It provides a way to attach / detach elements directly from the base list.
	 */
	public static interface IAttachAware<WT>
	{
		void setAttachHandler(IAttachHandler<WT> attachHandler);
	}

	/**
	 * This interface can be used when this change aware list is based on a map that can change it's returned contents
	 * by other means then through this proxy wrapper. It provides a way to attach / detach elements directly from the base list.
	 */
	public static interface IAttachHandler<WT>
	{
		void attachToBaseObjectIfNeeded(int i, WT value);

		void detachFromBaseObjectIfNeeded(int i, WT value);
	}

	/**
	 * You should not change the contents of the returned Set.
	 */
	public Set<Integer> getChangedIndexes()
	{
		return changedIndexes;
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
		changedIndexes.clear();
	}

	/**
	 * Don't use the returned list for operations that make changes that need to be sent to browser! That list isn't tracked for changes.
	 */
	public List<ET> getBaseList()
	{
		return baseList;
	}

	/**
	 * Don't use the returned list for operations that make changes that need to be sent to browser! That list isn't tracked for changes.
	 * If you need to make a set directly in wrapper base list please use {@link #setInWrappedBaseList()} instead.
	 */
	protected List<WT> getWrappedBaseListForReadOnly()
	{
		List<WT> wrappedBaseList;
		if (baseList instanceof IWrappedBaseListProvider< ? >)
		{
			wrappedBaseList = ((IWrappedBaseListProvider<WT>)baseList).getWrappedBaseList();
		}
		else
		{
			wrappedBaseList = (List<WT>)baseList; // ET == WT in this case; no wrapping
		}

		return wrappedBaseList;
	}

	// privately we can use that list for making changes, as we know what has to be done when that happens
	private List<WT> getWrappedBaseList()
	{
		return getWrappedBaseListForReadOnly();
	}

	public WT setInWrappedBaseList(int index, WT value, boolean markChanged)
	{
		WT tmp = getWrappedBaseListForReadOnly().set(index, value);
		if (tmp != value)
		{
			detachIfNeeded(index, tmp, false);
			attachToBaseObjectIfNeeded(index, value, false);
		}
		if (markChanged) markElementChanged(index);

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
		if (changedIndexes.size() == 0 && !allChanged && !oldMustSendTypeToClient && changeMonitor != null) changeMonitor.valueChanged();
	}

	protected void markElementChanged(int i)
	{
		if (changedIndexes.add(Integer.valueOf(i)) && !allChanged && !mustSendTypeToClient && changeMonitor != null) changeMonitor.valueChanged();
	}

	public void markAllChanged()
	{
		boolean alreadyCh = allChanged;
		allChanged = true;
		if (!alreadyCh && changedIndexes.size() == 0 && !mustSendTypeToClient && changeMonitor != null) changeMonitor.valueChanged();
	}

	protected void attachToBaseObjectIfNeeded(int i, WT el, boolean insert)
	{
		if (changeMonitor != null) attachToBaseObject(i, el, insert);
	}

	@Override
	public void attachToBaseObject(final IChangeListener changeMonitor, BaseWebObject component)
	{
		this.changeMonitor = changeMonitor;
		this.component = component;

		List<WT> wrappedBaseList = getWrappedBaseList();
		int i = 0;
		for (WT el : wrappedBaseList)
		{
			attachToBaseObject(i, el, false);
			i++;
		}

		if (isChanged()) changeMonitor.valueChanged();
	}

	protected boolean isChanged()
	{
		return (allChanged || mustSendTypeToClient || changedIndexes.size() > 0);
	}

	// called whenever a new element was added or inserted into the array
	// TODO currently here we use the wrapped value for ISmartPropertyValue, but BaseWebObject uses the unwrapped value; I think the BaseWebObject
	// should be changes to use wrapped as well; either way, it should be the same (currently this works as we don't have any wrapper type with 'smart' values for which the wrapped value differs from the unwrapped value)
	protected void attachToBaseObject(final int i, WT el, boolean insert)
	{
		if (el instanceof ISmartPropertyValue)
		{
			ChangeAwareList<ET, WT>.IndexChangeListener changeHandler = new IndexChangeListener(i);
			changeHandlers.add(changeHandler);
			((ISmartPropertyValue)el).attachToBaseObject(changeHandler, component);
		}

		if (insert)
		{
			// an insert happened in array; update change handler indexes if needed
			Iterator<IndexChangeListener> it = changeHandlers.iterator();
			while (it.hasNext())
			{
				IndexChangeListener ch = it.next();
				if (ch.attachedToIdx >= i) ch.attachedToIdx++;
			}
		}
	}

	protected class IndexChangeListener implements IChangeListener
	{

		private int attachedToIdx;

		public IndexChangeListener(int attachedToIdx)
		{
			this.attachedToIdx = attachedToIdx;
		}

		@Override
		public void valueChanged()
		{
			markElementChanged(attachedToIdx);
		}

		@Override
		public String toString()
		{
			return "IndexChangeListener:" + attachedToIdx;
		}

	}

	@Override
	public void detach()
	{
		List<WT> wrappedBaseList = getWrappedBaseList();
		int i = 0;
		for (WT el : wrappedBaseList)
		{
			detach(i, el, false);
			i++;
		}

		component = null;
		changeMonitor = null;
	}

	// TODO currently here we use the wrapped value for ISmartPropertyValue, but BaseWebObject uses the unwrapped value; I think the BaseWebObject
	// should be changes to use wrapped as well; either way, it should be the same (currently this works as we don't have any wrapper type with 'smart' values for which the wrapped value differs from the unwrapped value)
	protected void detach(int idx, WT el, boolean remove)
	{
		if (el instanceof ISmartPropertyValue)
		{
			// if the wrapper list still has this el then don't call detach on it.
			if (!getWrappedBaseList().contains(el))
			{
				((ISmartPropertyValue)el).detach();
			}
			Iterator<IndexChangeListener> it = changeHandlers.iterator();
			while (it.hasNext())
			{
				IndexChangeListener ch = it.next();
				if (ch.attachedToIdx == idx) it.remove();
				else if (remove && ch.attachedToIdx > idx) ch.attachedToIdx--;
			}
		}
		else if (remove)
		{
			// other change handler indexes might need to be updated
			Iterator<IndexChangeListener> it = changeHandlers.iterator();
			while (it.hasNext())
			{
				IndexChangeListener ch = it.next();
				if (ch.attachedToIdx > idx) ch.attachedToIdx--;
			}
		}
	}

	protected void detachIfNeeded(int idx, WT el, boolean remove)
	{
		if (changeMonitor != null) detach(idx, el, remove);
	}

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
		return baseList.contains(o);
	}

	@Override
	public Iterator<ET> iterator()
	{
		final Iterator<ET> it = baseList.iterator();
		return new Iterator<ET>()
		{

			int i = -1;

			@Override
			public boolean hasNext()
			{
				return it.hasNext();
			}

			@Override
			public ET next()
			{
				i++;
				return it.next();
			}

			@Override
			public void remove()
			{
				it.remove();
				detachIfNeeded(i, getWrappedBaseList().get(i), true);
				i--;
				markAllChanged();
			}
		};
	}

	@Override
	public Object[] toArray()
	{
		return baseList.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a)
	{
		return baseList.toArray(a);
	}

	@Override
	public boolean add(ET e)
	{
		boolean tmp = baseList.add(e);
		attachToBaseObjectIfNeeded(baseList.size() - 1, getWrappedBaseList().get(baseList.size() - 1), false);
		markAllChanged();
		return tmp;
	}

	@Override
	public boolean remove(Object o)
	{
		int idx = baseList.indexOf(o);
		if (idx >= 0)
		{
			WT oldWrappedValue = getWrappedBaseList().get(idx);
			baseList.remove(idx);
			detachIfNeeded(idx, oldWrappedValue, true);
			markAllChanged();
			return true;
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection< ? > c)
	{
		return baseList.containsAll(c);
	}

	@Override
	public boolean addAll(Collection< ? extends ET> c)
	{
		if (c.size() > 0)
		{
			for (ET el : c)
				add(el);
			return true;
		}
		return false;
	}

	@Override
	public boolean addAll(int index, Collection< ? extends ET> c)
	{
		if (c.size() > 0)
		{
			int j = index;
			for (ET el : c)
				add(j++, el);
			return true;
		}
		return false;
	}

	@Override
	public boolean removeAll(Collection< ? > c)
	{
		boolean changed = false;
		for (Object o : c)
			changed = changed || remove(o);

		return changed;
	}

	@Override
	public boolean retainAll(Collection< ? > c)
	{
		int i;
		boolean changed = false;
		for (i = baseList.size() - 1; i >= 0; i--)
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
		int size = baseList.size();
		for (int i = size - 1; i >= 0; i--)
			remove(i);
		if (size > 0) markAllChanged();
	}

	@Override
	public ET get(int index)
	{
		return baseList.get(index);
	}

	@Override
	public ET set(int index, ET element)
	{
		WT oldWV = getWrappedBaseList().get(index);
		ET tmp = baseList.set(index, element);
		detachIfNeeded(index, oldWV, false);
		attachToBaseObjectIfNeeded(index, getWrappedBaseList().get(index), false);
		markElementChanged(index);
		return tmp;
	}

	@Override
	public void add(int index, ET element)
	{
		baseList.add(index, element);
		attachToBaseObjectIfNeeded(index, getWrappedBaseList().get(index), true);
		markAllChanged();
	}

	@Override
	public ET remove(int index)
	{
		WT oldWV = getWrappedBaseList().get(index);
		ET tmp = baseList.remove(index);
		detachIfNeeded(index, oldWV, true);
		markAllChanged();
		return tmp;
	}

	@Override
	public int indexOf(Object o)
	{
		return baseList.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o)
	{
		return baseList.lastIndexOf(o);
	}

	@Override
	public ListIterator<ET> listIterator()
	{
		return new ChangeAwareListIterator<ET>(baseList.listIterator());
	}

	@Override
	public ListIterator<ET> listIterator(int index)
	{
		return new ChangeAwareListIterator<ET>(baseList.listIterator(index));
	}

	@Override
	public List<ET> subList(int fromIndex, int toIndex)
	{
		return baseList.subList(fromIndex, toIndex);
	}

	protected class ChangeAwareListIterator<ET> implements ListIterator<ET>
	{

		protected final ListIterator<ET> it;

		public ChangeAwareListIterator(ListIterator<ET> it)
		{
			this.it = it;
		}

		@Override
		public boolean hasNext()
		{
			return it.hasNext();
		}

		@Override
		public ET next()
		{
			return it.next();
		}

		@Override
		public boolean hasPrevious()
		{
			return it.hasPrevious();
		}

		@Override
		public ET previous()
		{
			return it.previous();
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
			int i = it.previousIndex() + 1;
			it.remove();
			detachIfNeeded(i, getWrappedBaseList().get(i), true);
			markAllChanged();
		}

		@Override
		public void set(ET e)
		{
			int i = it.previousIndex() + 1;
			WT oldWV = getWrappedBaseList().get(i);
			it.set(e);
			detachIfNeeded(i, oldWV, false);
			attachToBaseObjectIfNeeded(i, getWrappedBaseList().get(i), false);
			markElementChanged(i);
		}

		@Override
		public void add(ET e)
		{
			int i = it.nextIndex();
			it.add(e);
			attachToBaseObjectIfNeeded(i, getWrappedBaseList().get(i), true);
			markAllChanged();
		}
	}

	/**
	 * If client sends updates for a version that was already changed on server, we might need to give the client the server's value again - to keep
	 * things in sync. Client value and update will be discarded as the server is leading.
	 */
	public void resetDueToOutOfSyncIfNeeded(int clientUpdateVersion)
	{
		// dropped browser update because server object changed meanwhile;
		// will send a full update if needed to have the correct value browser-side as well again (currently server side is leading / has more prio because not all server side values might support being recreated from client values)

		// if the list was already re-sent to client with a version higher to correct differences before for that client version, we shouldn't re-send it again as the client will already get the already sent full value correction;
		// otherwise we can get into a race-loop where client sends updates for an out-of-date value multiple times (let's say twice), server sends back full value twice and then client sees new full
		// value 1 and wants to send updates for it but server is already 2 versions ahead and triggers another full value send to client and the cycle never ends
		if (clientUpdateVersion >= lastResetDueToOutOfSyncVersion)
		{
			lastResetDueToOutOfSyncVersion = version + 1; // remember that we already corrected these differences for any previous version; so don't try to correct it again for previous versions in the future if updates still come for those
			markAllChanged();
		}
	}

	@Override
	public String toString()
	{
		return "#CAL# " + getBaseList().toString();
	}

}
