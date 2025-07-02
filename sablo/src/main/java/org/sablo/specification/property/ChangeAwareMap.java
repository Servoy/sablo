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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.sablo.CustomObjectContext;
import org.sablo.IChangeListener;
import org.sablo.IWebObjectContext;
import org.sablo.specification.PropertyDescription;

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
	private int lastResetDueToOutOfSyncVersion = 0;

	protected IPropertyType<ET> type;
	protected Map<String, ET> baseMap;

	protected Map<String, KeyChangeListener> changeHandlers = new HashMap<>();

	// TODO in order to have fine-grained add/remove operations as well in the future we would need a list of change operations that can be add/remove/change instead
	protected IChangeListener changeMonitor;
	protected IWebObjectContext webObjectContext;

	protected EntrySet entrySet;

	private CustomObjectContext<ET, WT> componentOrServiceExtension;
	private PropertyDescription customObjectPD;
	private ChangeAwareMap<ET, WT>.Changes changes;

	public ChangeAwareMap(Map<String, ET> baseMap, CustomObjectContext<ET, WT> componentOrServiceExtension, PropertyDescription customObjectPD)
	{
		this(baseMap, 1, componentOrServiceExtension, customObjectPD);
	}

	public ChangeAwareMap(Map<String, ET> baseMap, int initialVersion, CustomObjectContext<ET, WT> customObjectContext, PropertyDescription customObjectPD)
	{
		this.componentOrServiceExtension = customObjectContext;
		this.customObjectPD = customObjectPD;

		this.baseMap = baseMap;
		this.version = initialVersion;

		this.changes = new Changes();

		if (customObjectContext != null) customObjectContext.setPropertyValues(this);
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
	 * Gets the current changes (in immutable mode). PLEASE MAKE SURE TO call {@link Changes#doneHandling()} once you are done handling the changes and will no longer use the returned reference.<br/><br/>
	 * The idea is that if change aware map receives new updates while the changes are in immutable mode (so before doneHandling is called which means someone is still reading/iterating on them in toJSON probably),
	 * the changes object used by the map will switch to another reference to keep what this method returns immutable; but in order to not recreate changes all the time, once {@link Changes#doneHandling()} will
	 * be called, changes object will exit "immutable mode" and will be cleared/prepared for reuse.
	 *
	 * @return the current changes.
	 */
	public ChangeAwareMap<ET, WT>.Changes getChangesImmutableAndPrepareForReset()
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
	public ChangeAwareMap<ET, WT>.Changes getChanges()
	{
		return changes;
	}

	public static interface IChangeSetter
	{
		public void markElementChangedByRef(String key);

		public void markAllChanged();
	}

	public class Changes implements IChangeSetter
	{
		private final Set<String> keysWithUpdates = new HashSet<String>();
		private final Set<String> keysChangedByRef = new HashSet<String>();
		private boolean allChanged;

		private boolean immutableMode = false;

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

		private void clearChanges()
		{
			allChanged = false;
			keysWithUpdates.clear();
			keysChangedByRef.clear();
		}

		public Set<String> getKeysWithUpdates()
		{
			return keysWithUpdates;
		}

		public Set<String> getKeysChangedByRef()
		{
			return keysChangedByRef;
		}

		public boolean mustSendAll()
		{
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

		private void markElementContentsUpdated(String key)
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			// add it only if it is not already changed by ref - in which case it will be sent wholly anyway
			if (changeMonitor != null && !changes.keysChangedByRef.contains(key) && changes.keysWithUpdates.add(key) && changes.keysChangedByRef.size() == 0 &&
				!changes.allChanged) changeMonitor.valueChanged();
		}

		public void markElementChangedByRef(String key)
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			if (changes.keysChangedByRef.add(key))
			{
				if (changeMonitor != null && changes.keysWithUpdates.size() == 0 && !changes.allChanged)
					changeMonitor.valueChanged();
				else changes.keysWithUpdates.remove(key); // if it was in 'keysWithUpdates' already it did change content previously but now it changed completely by ref; don't send it twice to client in changes
			}
		}

		public void markAllChanged()
		{
			changeInstanceIfCurrentlyImmutable(); // this can change the "changes" ref. that is why below we always use changes. instead of directly the properties

			boolean alreadyCh = changes.allChanged;
			changes.allChanged = true;
			if (changeMonitor != null && !alreadyCh && changes.keysWithUpdates.size() == 0 && changes.keysChangedByRef.size() == 0)
				changeMonitor.valueChanged();
		}

		public boolean isChanged()
		{
			return (allChanged || keysWithUpdates.size() > 0 || keysChangedByRef.size() > 0);
		}

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
		WT tmp = getWrappedBaseMap().put(key, value);
		if (tmp != value)
		{
			detachIfNeeded(key, tmp);
			if (componentOrServiceExtension != null) componentOrServiceExtension.triggerPropertyChange(key, tmp, value);
			attachToBaseObjectIfNeeded(key, value);

			if (markChanged) changes.markElementChangedByRef(key);
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

	public int getListContentVersion()
	{
		return version;
	}


	protected void attachToBaseObjectIfNeeded(String key, WT el)
	{
		if (changeMonitor != null) attachToBaseObject(key, el);
	}

	@Override
	public void attachToBaseObject(final IChangeListener changeMntr, IWebObjectContext webObjectCntxt)
	{
		this.changeMonitor = changeMntr;
		this.webObjectContext = webObjectCntxt;

		Map<String, WT> wrappedBaseList = getWrappedBaseMap();
		TreeSet<String> sortedKeys = new TreeSet<String>(new Comparator<String>()
		{
			public int compare(String o1, String o2)
			{
				int compare = o1.compareTo(o2);
				if (compare == 0) return 0;
				WT value1 = wrappedBaseList.get(o1);
				WT value2 = wrappedBaseList.get(o2);
				if (value1 instanceof ISmartSortOrderPrevalence && value2 instanceof ISmartSortOrderPrevalence)
				{
					return ((ISmartSortOrderPrevalence)value1).getPrevalence() - ((ISmartSortOrderPrevalence)value2).getPrevalence();
				}
				if (value1 instanceof ISmartSortOrderPrevalence)
				{
					return -1;
				}
				if (value2 instanceof ISmartSortOrderPrevalence)
				{
					return 1;
				}
				return compare;
			};
		}); // just make sure it always attaches them in the same order to avoid random bugs
		sortedKeys.addAll(wrappedBaseList.keySet());
		for (String key : sortedKeys)
		{
			attachToBaseObject(key, wrappedBaseList.get(key));
		}

		if (changes.isChanged()) changeMntr.valueChanged();
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
			((ISmartPropertyValue)el).attachToBaseObject(changeHandler, getOrCreateComponentOrServiceExtension());
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
			changes.markElementContentsUpdated(attachedToKey);
		}

	}

	@Override
	public void detach()
	{
		if (webObjectContext == null) return; // it is already detached

		changeMonitor = null;

		Map<String, WT> wrappedBaseList = getWrappedBaseMap();
		for (java.util.Map.Entry<String, WT> e : wrappedBaseList.entrySet())
		{
			detach(e.getKey(), e.getValue());
		}

		if (componentOrServiceExtension != null)
		{
			componentOrServiceExtension.dispose();
			componentOrServiceExtension = null;
		}

		webObjectContext = null;
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
		WT oldWrappedVal = getWrappedBaseMapForReadOnly().get(key);
		ET tmp = baseMap.put(key, value);
		WT newWrappedValue = getWrappedBaseMapForReadOnly().get(key);

		if (componentOrServiceExtension != null) componentOrServiceExtension.triggerPropertyChange(key, oldWrappedVal, newWrappedValue);

		if (oldWrappedVal != newWrappedValue)
		{
			detachIfNeeded(key, oldWrappedVal);
			attachToBaseObjectIfNeeded(key, newWrappedValue);
			changes.markElementChangedByRef(key);
		}
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
					changes.markAllChanged();
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

	/**
	 * If client sends updates for a version that was already changed on server, we might need to give the client the server's value again - to keep
	 * things in sync. Client value and update will be discarded as the server is leading.
	 */
	public void resetDueToOutOfSyncIfNeeded(int clientUpdateVersion)
	{
		// dropped browser update because server object changed meanwhile;
		// will send a full update to have the correct value browser-side as well again (currently server side is leading / has more prio because not all server side values might support being recreated from client values)

		// if the object was already re-sent to client with a version higher to correct differences before for that client version, we shouldn't re-send it again as the client will already get the already sent full value correction;
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
			return "#CAM# " + getBaseMap().toString();
		}
		catch (Exception e)
		{
			CustomJSONPropertyType.log.error("Error in toString of CAM", e);
			return "CAM: Error in stringify the map of size: " + size();
		}
	}

	public CustomObjectContext<ET, WT> getOrCreateComponentOrServiceExtension()
	{
		if (componentOrServiceExtension == null)
		{
			componentOrServiceExtension = new CustomObjectContext<ET, WT>(customObjectPD, webObjectContext);
			componentOrServiceExtension.setPropertyValues(this);
		}
		return componentOrServiceExtension;
	}

}
