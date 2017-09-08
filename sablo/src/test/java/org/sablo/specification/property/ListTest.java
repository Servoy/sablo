/*
 * Copyright (C) 2016 Servoy BV
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Test;
import org.sablo.IChangeListener;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.TypesRegistry;

/**
 * @author jcompagner
 *
 */
public class ListTest
{

	@Test
	public void spliceTestBeginningOfList() throws Exception
	{
		ChangeAwareList<ChangeAwareMap<String, Object>, Object> lst = new ChangeAwareList<ChangeAwareMap<String, Object>, Object>(
			new ArrayList<ChangeAwareMap<String, Object>>());

		final boolean[] changed = new boolean[1];
		IChangeListener listener = new IChangeListener()
		{
			@Override
			public void valueChanged()
			{
				changed[0] = true;
			}
		};
		lst.attachToBaseObject(listener, null);
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));

		assertEquals(4, lst.addedIndexes.size());
		assertTrue(changed[0]);

		lst.clearChanges();
		changed[0] = false;

		assertEquals(0, lst.getIndexesChangedByRef().size());
		assertEquals(0, lst.getIndexesWithContentUpdates().size());
		lst.get(0).put("test", "test");
		assertEquals(0, lst.getIndexesChangedByRef().size());
		assertEquals(1, lst.getIndexesWithContentUpdates().size());

		assertEquals(new Integer(0), lst.getIndexesWithContentUpdates().toArray()[0]);

		lst.clearChanges();

		lst.set(0, lst.get(1));
		lst.set(1, lst.get(2));
		lst.remove(2);
		lst.get(1).put("test1", "test123");

		assertEquals(2, lst.getIndexesChangedByRef().size());
		assertEquals(0, lst.getIndexesWithContentUpdates().size());
		assertEquals(new Integer(0), lst.getIndexesChangedByRef().toArray()[0]);
		assertEquals(new Integer(1), lst.getIndexesChangedByRef().toArray()[1]);

		assertEquals(1, lst.removedIndexes.size());
		assertEquals(new Integer(2), lst.removedIndexes.toArray()[0]);
		assertTrue(lst.mustSendAll());

		lst.clearChanges();
		lst.get(1).clearChanges();

		lst.get(1).put("test1", "test1");
		assertEquals(1, lst.getIndexesWithContentUpdates().size());
		assertEquals(0, lst.getIndexesChangedByRef().size());

		assertEquals(new Integer(1), lst.getIndexesWithContentUpdates().toArray()[0]);

		assertEquals(3, lst.changeHandlers.size());
	}

	@Test
	public void spliceTestEndOfList() throws Exception
	{
		ChangeAwareList<ChangeAwareMap<String, Object>, Object> lst = new ChangeAwareList<ChangeAwareMap<String, Object>, Object>(
			new ArrayList<ChangeAwareMap<String, Object>>());

		final boolean[] changed = new boolean[1];
		IChangeListener listener = new IChangeListener()
		{
			@Override
			public void valueChanged()
			{
				changed[0] = true;
			}
		};
		lst.attachToBaseObject(listener, null);
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));

		assertEquals(4, lst.addedIndexes.size());
		assertTrue(changed[0]);

		lst.clearChanges();
		changed[0] = false;

		assertEquals(0, lst.getIndexesChangedByRef().size());
		assertEquals(0, lst.getIndexesWithContentUpdates().size());
		lst.get(0).put("test", "test");
		assertEquals(0, lst.getIndexesChangedByRef().size());
		assertEquals(1, lst.getIndexesWithContentUpdates().size());

		assertEquals(new Integer(0), lst.getIndexesWithContentUpdates().toArray()[0]);

		lst.clearChanges();

		lst.set(1, lst.get(2));
		lst.set(2, lst.get(3));
		lst.remove(2);
		lst.get(1).put("test1", "test123");

		assertEquals(2, lst.getIndexesChangedByRef().size());
		assertEquals(0, lst.getIndexesWithContentUpdates().size());
		assertEquals(new Integer(1), lst.getIndexesChangedByRef().toArray()[0]);
		assertEquals(new Integer(2), lst.getIndexesChangedByRef().toArray()[1]);

		assertEquals(1, lst.removedIndexes.size());
		assertEquals(new Integer(2), lst.removedIndexes.toArray()[0]);
		assertTrue(lst.mustSendAll());

		lst.clearChanges();
		lst.get(1).clearChanges();

		lst.get(1).put("test1", "test1");
		assertEquals(1, lst.getIndexesWithContentUpdates().size());
		assertEquals(0, lst.getIndexesChangedByRef().size());

		assertEquals(new Integer(1), lst.getIndexesWithContentUpdates().toArray()[0]);

		assertEquals(3, lst.changeHandlers.size());
	}

	@Test
	public void spliceTestMiddleOfList() throws Exception
	{
		ChangeAwareList<ChangeAwareMap<String, Object>, Object> lst = new ChangeAwareList<ChangeAwareMap<String, Object>, Object>(
			new ArrayList<ChangeAwareMap<String, Object>>());

		final boolean[] changed = new boolean[1];
		IChangeListener listener = new IChangeListener()
		{
			@Override
			public void valueChanged()
			{
				changed[0] = true;
			}
		};
		lst.attachToBaseObject(listener, null);
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));

		assertEquals(5, lst.addedIndexes.size());
		assertTrue(changed[0]);

		lst.clearChanges();
		changed[0] = false;

		assertEquals(0, lst.getIndexesChangedByRef().size());
		assertEquals(0, lst.getIndexesWithContentUpdates().size());
		lst.get(0).put("test", "test");
		assertEquals(0, lst.getIndexesChangedByRef().size());
		assertEquals(1, lst.getIndexesWithContentUpdates().size());

		assertEquals(new Integer(0), lst.getIndexesWithContentUpdates().toArray()[0]);

		lst.clearChanges();

		lst.set(1, lst.get(2));
		lst.set(2, lst.get(3));
		lst.remove(2);
		lst.get(1).put("test1", "test123");

		assertEquals(2, lst.getIndexesChangedByRef().size());
		assertEquals(0, lst.getIndexesWithContentUpdates().size());
		assertEquals(new Integer(1), lst.getIndexesChangedByRef().toArray()[0]);
		assertEquals(new Integer(2), lst.getIndexesChangedByRef().toArray()[1]);

		assertEquals(1, lst.removedIndexes.size());
		assertEquals(new Integer(2), lst.removedIndexes.toArray()[0]);
		assertTrue(lst.mustSendAll());

		lst.clearChanges();
		lst.get(1).clearChanges();

		lst.get(1).put("test1", "test1");
		assertEquals(1, lst.getIndexesWithContentUpdates().size());
		assertEquals(0, lst.getIndexesChangedByRef().size());

		assertEquals(new Integer(1), lst.getIndexesWithContentUpdates().toArray()[0]);

		assertEquals(4, lst.changeHandlers.size());
	}

	private PropertyDescription getDummyCustomObjectPD()
	{
		CustomJSONObjectType dummyCustomObjectTypeForChildRelationInfo = (CustomJSONObjectType)TypesRegistry.createNewType(CustomJSONObjectType.TYPE_NAME,
			"svy__dummyCustomObjectTypeForDeprecatedFMServiceChildRelationInfo");
		PropertyDescription dummyPD = new PropertyDescription("", dummyCustomObjectTypeForChildRelationInfo);
		dummyCustomObjectTypeForChildRelationInfo.setCustomJSONDefinition(dummyPD);
		return dummyPD;
	}

	@Test
	public void removeIndexTest() throws Exception
	{
		ChangeAwareList<ChangeAwareMap<String, Object>, Object> lst = new ChangeAwareList<ChangeAwareMap<String, Object>, Object>(
			new ArrayList<ChangeAwareMap<String, Object>>());

		final boolean[] changed = new boolean[1];
		IChangeListener listener = new IChangeListener()
		{
			@Override
			public void valueChanged()
			{
				changed[0] = true;
			}
		};
		lst.attachToBaseObject(listener, null);
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));

		assertTrue(changed[0]);

		changed[0] = false;

		lst.clearChanges();

		lst.remove(1);

		assertTrue(changed[0]);

		lst.clearChanges();

		lst.get(1).put("test1", "test1");

		assertEquals(1, lst.getIndexesWithContentUpdates().size());
		assertEquals(0, lst.getIndexesChangedByRef().size());
		assertEquals(new Integer(1), lst.getIndexesWithContentUpdates().toArray()[0]);

	}

	/*
	 * @Test public void sameValueRemoveIndexTest() { ChangeAwareList<ChangeAwareMap<String, Object>, Object> lst = new ChangeAwareList<ChangeAwareMap<String,
	 * Object>, Object>( new ArrayList<ChangeAwareMap<String, Object>>());
	 *
	 * final boolean[] changed = new boolean[1]; IChangeListener listener = new IChangeListener() {
	 *
	 * @Override public void valueChanged() { changed[0] = true; } }; lst.attachToBaseObject(listener, null);
	 *
	 * ChangeAwareMap<String, Object> value = new ChangeAwareMap<String, Object>(new HashMap<String, String>()); lst.add(value); lst.add(value); lst.add(value);
	 * lst.add(value);
	 *
	 * assertTrue(changed[0]);
	 *
	 * changed[0] = false;
	 *
	 * lst.clearChanges();
	 *
	 * lst.remove(1);
	 *
	 * assertTrue(changed[0]);
	 *
	 * lst.clearChanges();
	 *
	 * lst.get(1).put("test1", "test1");
	 *
	 * assertEquals(1, lst.changedIndexes.size()); assertEquals(new Integer(1), lst.changedIndexes.toArray()[0]); }
	 */
}
