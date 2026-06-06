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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.junit.Test;
import org.sablo.IChangeListener;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.PropertyDescriptionBuilder;
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

		ChangeAwareList<ChangeAwareMap<String, Object>, Object>.Changes ch = lst.getChangesImmutableAndPrepareForReset();
		ArrayOperation[] opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertEquals(1, opSeq.length);
		assertGranularOpIs(0, 3, ArrayOperation.INSERT, null, opSeq[0]);
		assertTrue(changed[0]);

		ch.doneHandling();
		changed[0] = false;

		lst.get(0).put("test", "test");
		ch = lst.getChangesImmutableAndPrepareForReset();

		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(1, opSeq.length);
		assertGranularOpIs(0, 0, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);

		ch.doneHandling();
		changed[0] = false;

		// now simulate a splice with shift of elements
		lst.set(0, lst.get(1));
		lst.set(1, lst.get(2));
		lst.set(2, lst.get(3));
		lst.remove(3);
		lst.get(1).put("test1", "test123");

		ch = lst.getChangesImmutableAndPrepareForReset();
		assertTrue(changed[0]);
		assertFalse(ch.mustSendAll());
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertEquals(2, opSeq.length);
		assertGranularOpIs(0, 0, ArrayOperation.DELETE, null, opSeq[0]);
		assertGranularOpIs(0, 2, ArrayOperation.CHANGE, null, opSeq[1]);

		ch.doneHandling();
		changed[0] = false;
		lst.get(1).getChangesImmutableAndPrepareForReset().doneHandling();

		lst.get(1).put("test1", "test1");

		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(1, opSeq.length);
		assertGranularOpIs(1, 1, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);

		assertEquals(3, lst.changeHandlers.size());

		ch.doneHandling();
		lst.get(1).getChangesImmutableAndPrepareForReset().doneHandling();
	}

	public static void assertGranularOpIs(int startIndex, int endIndex, int opType, Set<String> columnNames, ArrayOperation opSeq)
	{
		assertEquals("startIndex check", startIndex, opSeq.startIndex);
		assertEquals("endIndex check", endIndex, opSeq.endIndex);
		assertEquals("opType check", opType, opSeq.type);
		assertEquals("columnName check", columnNames, opSeq.cellNames);
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

		ChangeAwareList<ChangeAwareMap<String, Object>, Object>.Changes ch = lst.getChangesImmutableAndPrepareForReset();
		assertTrue(changed[0]);
		ArrayOperation[] opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertEquals(1, opSeq.length);
		assertGranularOpIs(0, 3, ArrayOperation.INSERT, null, opSeq[0]);

		ch.doneHandling();
		changed[0] = false;

		lst.get(0).put("test", "test");
		ch = lst.getChangesImmutableAndPrepareForReset();
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(1, opSeq.length);
		assertGranularOpIs(0, 0, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);

		ch.doneHandling();
		changed[0] = false;

		// now simulate a splice with shift of elements
		lst.set(1, lst.get(2));
		lst.set(2, lst.get(3));
		lst.remove(3);
		lst.get(1).put("test1", "test123");

		ch = lst.getChangesImmutableAndPrepareForReset();
		assertTrue(changed[0]);
		assertFalse(ch.mustSendAll());
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertEquals(2, opSeq.length);
		assertGranularOpIs(1, 1, ArrayOperation.DELETE, null, opSeq[0]);
		assertGranularOpIs(1, 2, ArrayOperation.CHANGE, null, opSeq[1]);

		ch.doneHandling();
		changed[0] = false;
		lst.get(1).getChangesImmutableAndPrepareForReset().doneHandling();

		lst.get(1).put("test1", "test1");
		assertTrue(changed[0]);
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertEquals(1, opSeq.length);
		assertGranularOpIs(1, 1, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);

		assertEquals(3, lst.changeHandlers.size());

		ch.doneHandling();
		lst.get(1).getChangesImmutableAndPrepareForReset().doneHandling();
	}

	@Test
	public void spliceTestMiddleOfListPlusEndOfList() throws Exception
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

		ChangeAwareList<ChangeAwareMap<String, Object>, Object>.Changes ch = lst.getChangesImmutableAndPrepareForReset();
		ArrayOperation[] opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(1, opSeq.length);
		assertGranularOpIs(0, 4, ArrayOperation.INSERT, null, opSeq[0]);

		ch.doneHandling();
		changed[0] = false;

		lst.get(0).put("test", "test");
		ch = lst.getChangesImmutableAndPrepareForReset();
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(1, opSeq.length);
		assertGranularOpIs(0, 0, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);

		ch.doneHandling();
		changed[0] = false;

		// simulate a splice with shift of elements in the middle followed by an add (splice at the end) + some change ops in between
		lst.get(4).put("test1", "test007");
		lst.set(2, lst.get(3));
		lst.set(3, lst.get(4));
		lst.remove(4);
		lst.get(1).put("test1", "test123");
		lst.add(new ChangeAwareMap<String, Object>(new HashMap<String, String>(), null, getDummyCustomObjectPD()));
		ch = lst.getChangesImmutableAndPrepareForReset();

		assertFalse(ch.mustSendAll());
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(2, opSeq.length);
		assertGranularOpIs(1, 1, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);
		assertGranularOpIs(2, 4, ArrayOperation.CHANGE, null, opSeq[1]);

		ch.doneHandling();
		changed[0] = false;
		lst.get(1).getChangesImmutableAndPrepareForReset().doneHandling();

		lst.get(1).put("test1", "test1");
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(1, opSeq.length);
		assertGranularOpIs(1, 1, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);

		assertEquals(5, lst.changeHandlers.size());

		ch.doneHandling();
		lst.get(1).getChangesImmutableAndPrepareForReset().doneHandling();
	}

	private PropertyDescription getDummyCustomObjectPD()
	{
		CustomJSONObjectType dummyCustomObjectTypeForChildRelationInfo = (CustomJSONObjectType)TypesRegistry.createNewType(CustomJSONObjectType.TYPE_NAME,
			"svy__dummyCustomObjectTypeForDeprecatedFMServiceChildRelationInfo");
		PropertyDescription dummyPD = new PropertyDescriptionBuilder().withType(dummyCustomObjectTypeForChildRelationInfo).build();
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

		ChangeAwareList<ChangeAwareMap<String, Object>, Object>.Changes ch = lst.getChangesImmutableAndPrepareForReset();
		ch.doneHandling();

		lst.remove(1);

		assertTrue(changed[0]);

		ch = lst.getChangesImmutableAndPrepareForReset();
		ch.doneHandling();
		changed[0] = false;

		lst.get(1).put("test1", "test1");

		ArrayOperation[] opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertTrue(changed[0]);
		assertEquals(1, opSeq.length);
		assertGranularOpIs(1, 1, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);
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
