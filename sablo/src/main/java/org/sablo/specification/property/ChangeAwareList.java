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

import org.sablo.IChangeListener;
import org.sablo.IWebObjectContext;

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

	public static final Set<String> GRANULAR_UPDATE_OP = new HashSet<>();
	private static final Set<String> FULL_UPDATE_BY_REF_OP = null; // do not change the value; it needs to be null to work well with ArrayGranularChangeKeeper impl.

	// TODO this class should keep a kind of pks to avoid a scenario where server and browser get modified at the same time
	// and a granular update ends up doing incorrect modifications (as it's being applied in wrong place); for now we just drop
	// the browser changes when this happens through the 'version' mechanism
	private int version;
	private int lastResetDueToOutOfSyncVersion = 0;

	protected List<ET> baseList;

	protected List<IndexChangeListener> changeHandlers = new ArrayList<>();

	// TODO in order to have fine-grained add/remove operations as well in the future we would need a list of change operations that can be add/remove/change instead
	protected IChangeListener changeMonitor;
	protected IWebObjectContext webObjectContext;

	private ChangeAwareList<ET, WT>.Changes changes;

	public ChangeAwareList(List<ET> baseList)
	{
		this(baseList, 1);
	}

	public ChangeAwareList(List<ET> baseList, int initialVersion)
	{
		this.baseList = baseList;
		this.version = initialVersion;
		changes = new Changes();

		if (baseList instanceof IAttachAware) ((IAttachAware<WT>)baseList).setAttachHandler(new IAttachHandler<WT>()
		{
			@Override
			public void attachToBaseObjectIfNeeded(int i, WT value)
			{
				ChangeAwareList.this.attachToBaseObjectIfNeeded(i, value, false);
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
	 * Gets the current changes (in immutable mode). PLEASE MAKE SURE TO call {@link Changes#doneHandling()} once you are done handling the changes and will no longer use the returned reference.<br/><br/>
	 * The idea is that if change aware list receives new updates while the changes are in immutable mode (so before doneHandling is called which means someone is still reading/iterating on them in toJSON probably),
	 * the changes object used by the map will switch to another reference to keep what this method returns immutable; but in order to not recreate changes all the time, once {@link Changes#doneHandling()} will
	 * be called, changes object will exit "immutable mode" and will be cleared/prepared for reuse.
	 *
	 * @return the current changes.
	 */
	public ChangeAwareList<ET, WT>.Changes getChangesImmutableAndPrepareForReset()
	{
		// mark 'changes' as immutable until doneHandling() will get called on it
		// if getChangesImmutableAndPrepareForReset is called twice without Changes.doneHandling() getting called on reference
		// returned the first time before the second call (so changes is already in immutable mode) then the "changes" ref will get switched to a new blank reference in call below
		changes.startImmutableMode();
		return changes;
	}

	public IChangeSetter getChangeSetter()
	{
		return changes;
	}

	/**
	 * DO NOT CALL THIS METHOD when code is running inside a toJSON (writing server-to-client property changes/values). Use {@link #getChangesImmutableAndPrepareForReset()} instead there.</br>
	 * This methods just gives a way to check changes in tests or whenever it is helpful to see what changed outside of a toJSON (so current running stack should not be in a toJSON).<br/><br/>
	 *
	 * It is likely that this only needs to be called from unit tests...
	 *
	 * @deprecated deprecated just to make you read the javadoc and avoid using this method where you should not
	 */
	@Deprecated
	public ChangeAwareList<ET, WT>.Changes getChanges()
	{
		return changes;
	}

	public static interface IChangeSetter
	{
		void markElementChangedByRef(int i);

		void markAllChanged();
	}

	public class Changes implements IChangeSetter
	{

		private final ArrayGranularChangeKeeper granularUpdatesKeeper = new ArrayGranularChangeKeeper();

		private boolean allChanged;

		private boolean immutableMode = false;

		public Changes()
		{
			clearChanges();
		}

		private void startImmutableMode()
		{
			if (immutableMode) changes = new Changes(); // should never happen that it is already immutable if doneHandling() is used properly
			changes.immutableMode = true;
		}

		public void doneHandling()
		{
			// careful, this will/should get called on the old changes obj, if immutable mode resulted in a new changes object being created
			immutableMode = false;
			clearChanges();
		}

		public ArrayGranularChangeKeeper getGranularUpdatesKeeper()
		{
			return granularUpdatesKeeper;
		}

		public boolean mustSendAll()
		{
			// we should send all if allChanged is true or if we have more types of changes that are not compatible with easily being sent to client //TODO this could be improved further to allow granular updates for all things that are just changes in indexes
			return allChanged;
		}

		private void changeInstanceIfCurrentlyImmutable()
		{
			if (immutableMode)
			{
				if (CustomJSONPropertyType.log.isDebugEnabled()) CustomJSONPropertyType.log.debug(
					"A new change was registered while previous changes are being handled; probably one property's toJSON ends up marking another property as dirty. This should be avoided. See associated stack trace", //$NON-NLS-1$
					new RuntimeException("Stack trace")); //$NON-NLS-1$
				changes = new Changes();
			}
		}

		private void clearChanges()
		{
			allChanged = false;
			granularUpdatesKeeper.reset(0, ChangeAwareList.this.size() - 1);
		}

		protected void markElementContentsUpdated(int i)
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean hadViewportChanges = changes.granularUpdatesKeeper.hasChanges();
			changes.granularUpdatesKeeper.processOperation(new ArrayOperation(i, i, ArrayOperation.CHANGE, GRANULAR_UPDATE_OP));

			if (!hadViewportChanges && !changes.allChanged && changeMonitor != null)
				changeMonitor.valueChanged();
		}

		public void markElementChangedByRef(int i)
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean hadViewportChanges = changes.granularUpdatesKeeper.hasChanges();
			changes.granularUpdatesKeeper.processOperation(new ArrayOperation(i, i, ArrayOperation.CHANGE, FULL_UPDATE_BY_REF_OP));

			if (!hadViewportChanges && !changes.allChanged && changeMonitor != null)
				changeMonitor.valueChanged();
		}

		private void markElementRemoved(int i)
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean hadViewportChanges = changes.granularUpdatesKeeper.hasChanges();
			changes.granularUpdatesKeeper.processOperation(new ArrayOperation(i, i, ArrayOperation.DELETE));

			if (!hadViewportChanges && !changes.allChanged && changeMonitor != null)
				changeMonitor.valueChanged();
		}

		private void markElementInserted(int i)
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean hadViewportChanges = changes.granularUpdatesKeeper.hasChanges();
			changes.granularUpdatesKeeper.processOperation(new ArrayOperation(i, i, ArrayOperation.INSERT));

			if (!hadViewportChanges && !changes.allChanged && changeMonitor != null)
				changeMonitor.valueChanged();
		}

		public void markAllChanged()
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean alreadyCh = changes.allChanged;
			changes.allChanged = true;
			if (!alreadyCh && !changes.granularUpdatesKeeper.hasChanges() &&
				changeMonitor != null) changeMonitor.valueChanged();
		}

		protected boolean isChanged()
		{
			return (allChanged || changes.granularUpdatesKeeper.hasChanges());
		}

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
			if (markChanged) changes.markElementChangedByRef(index);
			detachIfNeeded(index, tmp, false);
			attachToBaseObjectIfNeeded(index, value, false);
		}

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

	protected void attachToBaseObjectIfNeeded(int i, WT el, boolean insert)
	{
		if (changeMonitor != null) attachToBaseObject(i, el, insert, false);
	}

	@Override
	public void attachToBaseObject(final IChangeListener changeMonitor, IWebObjectContext webObjectContext)
	{
		this.changeMonitor = changeMonitor;
		this.webObjectContext = webObjectContext;

		List<WT> wrappedBaseList = getWrappedBaseList();
		int i = 0;
		for (WT el : wrappedBaseList)
		{
			attachToBaseObject(i, el, false, true);
			i++;
		}

		if (changes.isChanged()) changeMonitor.valueChanged();
	}

	// called whenever a new element was added or inserted into the array
	// TODO currently here we use the wrapped value for ISmartPropertyValue, but BaseWebObject uses the unwrapped value; I think the BaseWebObject
	// should be changes to use wrapped as well; either way, it should be the same (currently this works as we don't have any wrapper type with 'smart' values for which the wrapped value differs from the unwrapped value)
	protected void attachToBaseObject(final int i, WT el, boolean insert, boolean dueToFullArrayAttach)
	{
		if (insert)
		{
			// an insert happened in array; update change handler indexes if needed
			for (ChangeAwareList<ET, WT>.IndexChangeListener ch : changeHandlers)
			{
				if (ch.attachedToIdx >= i) ch.attachedToIdx++;
			}
		}

		if (el instanceof ISmartPropertyValue)
		{
			if (CustomJSONPropertyType.log.isDebugEnabled()) CustomJSONPropertyType.log.debug("[CAL] Checking to ATTACH idx " + i);

			ChangeAwareList<ET, WT>.IndexChangeListener changeHandler = getChangeHandler(el);
			if (changeHandler == null || dueToFullArrayAttach) // hmm I think if dueToFullArrayAttach is true then changeHandler would always be null (as elements would not be attached either...) but just in case
			{
				// new value, so attach it and give it a change handler
				changeHandler = new IndexChangeListener(i, el);
				changeHandlers.add(changeHandler);
				((ISmartPropertyValue)el).attachToBaseObject(changeHandler, webObjectContext);
			}
			else
			{
				// note: if we already have a change handler for this value, that means that the value was already in the list before being added;
				// that can happen when array operations such as splice are performed on the array from JS (for example a splice that remove one element
				// from JS does copy elements after the removed one 1 by one to lower index; so it first attaches an already attached value to a lower index, then detaches it from it's previous index.
				// we want in this case to not trigger either attach or detach - as basically the element is still in the array, but we must adjust change handler indexes

				// just adjust the index of the change handler
				changeHandler.attachedToIdx = i;
				if (CustomJSONPropertyType.log.isDebugEnabled())
					CustomJSONPropertyType.log.debug("[CAL] SKIPPING ATTACH due to el. already being added in the list (splice?) for idx " + i);
			}
		}
	}

	private ChangeAwareList<ET, WT>.IndexChangeListener getChangeHandler(WT el)
	{
		// we have to iterate here to find the change handler for the value; we cannot change "changeHandlers" into a map of "value -> change handler" to enhance searching because
		// the values are mutable and can change hashCode, so they ("el") cannot be used as keys in a map (for example EL could be a ChangeAwareMap that can change contents so it can change hashcode)
		for (IndexChangeListener ch : changeHandlers)
			if (el == ch.forValue) return ch;

		return null;
	}

	protected class IndexChangeListener implements IChangeListener
	{

		private final Object forValue;
		private int attachedToIdx;

		public IndexChangeListener(int attachedToIdx, Object forValue)
		{
			this.attachedToIdx = attachedToIdx;
			this.forValue = forValue;
		}

		@Override
		public void valueChanged()
		{
			if (this.attachedToIdx != -1) changes.markElementContentsUpdated(attachedToIdx);
		}

		public void disable()
		{
			this.attachedToIdx = -1;
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
		if (webObjectContext == null) return; // it is already detached

		changeMonitor = null;

		List<WT> wrappedBaseList = getWrappedBaseList();
		int i = 0;
		for (WT el : wrappedBaseList)
		{
			detach(i, el, false, true);
			i++;
		}

		webObjectContext = null;
	}

	// TODO currently here we use the wrapped value for ISmartPropertyValue, but BaseWebObject uses the unwrapped value; I think the BaseWebObject
	// should be changed to use wrapped as well; either way, it should be the same (currently this works as we don't have any wrapper type with 'smart' values for which the wrapped value differs from the unwrapped value)
	protected void detach(int idx, WT el, boolean remove, boolean dueToFullArrayDetach)
	{
		if (el instanceof ISmartPropertyValue)
		{
			if (CustomJSONPropertyType.log.isDebugEnabled()) CustomJSONPropertyType.log.debug("[CAL] Checking to DETACH idx " + idx);

			// if the wrapper list still has this el then don't call detach on it.
			if (dueToFullArrayDetach || (!containsByReference(getWrappedBaseList(), el)))
			{
				Iterator<IndexChangeListener> it = changeHandlers.iterator();
				while (it.hasNext())
				{
					IndexChangeListener ch = it.next();
					if (ch.forValue == el || ch.attachedToIdx == idx)
					{
						ch.disable();
						it.remove();
					}
				}

				((ISmartPropertyValue)el).detach();
			}
			else
			{
				// it is still in the list after being removed from idx;
				// that can happen when array operations such as splice are performed on the array from JS (for example a splice that remove one element
				// from JS does copy elements after the removed one 1 by one to lower index; so it first sets an already attached value to a lower index, then removes/replaces it from it's previous index.
				// we want in this case to not trigger either attach or detach - as basically the element is still in the array, but we must adjust change handler indexes;
				// so we do nothing in this case as the
				if (CustomJSONPropertyType.log.isDebugEnabled())
					CustomJSONPropertyType.log.debug("[CAL] SKIPPING DETACH of still present element at idx " + idx);
			}
		}

		if (remove)
		{
			// other change handler indexes might need to be updated
			for (ChangeAwareList<ET, WT>.IndexChangeListener ch : changeHandlers)
			{
				if (ch.attachedToIdx > idx) ch.attachedToIdx--;
			}
		}
	}

	/**
	 * Search by reference, not using .equals(), because list.contains() would match similar maps/arrays/... even if the references are different.
	 * When we don't want that, when we are only interested in references then we use this method.
	 */
	private static <X> boolean containsByReference(List<X> list, X elToSearchFor)
	{
		for (X el : list)
			if (elToSearchFor == el) return true;
		return false;
	}

	protected void detachIfNeeded(int idx, WT el, boolean remove)
	{
		if (changeMonitor != null) detach(idx, el, remove, false);
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
				changes.markElementRemoved(i);
				detachIfNeeded(i, getWrappedBaseList().get(i), true);
				i--;
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
		changes.markElementInserted(baseList.size() - 1);
		attachToBaseObjectIfNeeded(baseList.size() - 1, getWrappedBaseList().get(baseList.size() - 1), false);
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
			changes.markElementRemoved(idx);
			detachIfNeeded(idx, oldWrappedValue, true);
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
		int initialSize = baseList.size();
		for (int i = initialSize - 1; i >= 0; i--)
			remove(i);
		if (initialSize > 0) changes.markAllChanged();
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
		WT newWV = getWrappedBaseList().get(index);

		if (oldWV != newWV)
		{
			changes.markElementChangedByRef(index);
			detachIfNeeded(index, oldWV, false);
			attachToBaseObjectIfNeeded(index, newWV, false);
		}
		return tmp;
	}

	@Override
	public void add(int index, ET element)
	{
		baseList.add(index, element);
		changes.markElementInserted(index);
		attachToBaseObjectIfNeeded(index, getWrappedBaseList().get(index), true);
	}

	@Override
	public ET remove(int index)
	{
		WT oldWV = getWrappedBaseList().get(index);
		ET tmp = baseList.remove(index);
		changes.markElementRemoved(index);
		detachIfNeeded(index, oldWV, true);
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
			changes.markElementRemoved(i);
			detachIfNeeded(i, getWrappedBaseList().get(i), true);
		}

		@Override
		public void set(ET e)
		{
			int i = it.previousIndex() + 1;
			WT oldWV = getWrappedBaseList().get(i);
			it.set(e);
			WT newWV = getWrappedBaseList().get(i);

			if (oldWV != newWV)
			{
				changes.markElementChangedByRef(i);
				detachIfNeeded(i, oldWV, false);
				attachToBaseObjectIfNeeded(i, newWV, false);
			}
		}

		@Override
		public void add(ET e)
		{
			int i = it.nextIndex();
			it.add(e);
			changes.markElementInserted(i);
			attachToBaseObjectIfNeeded(i, getWrappedBaseList().get(i), true);
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
			changes.markAllChanged();
		}
	}

	@Override
	public String toString()
	{
		try
		{
			return "#CAL# " + getBaseList().toString();
		}
		catch (Exception e)
		{
			CustomJSONPropertyType.log.error("Error in toString of CAL", e);
			return "CAL: Error in stringify the list of size: " + size();
		}
	}

}
