/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;


/**
 * @author acostescu
 *
 */
@SuppressWarnings("nls")
public class ArrayGranularChangeKeeperTest
{

	private ArrayGranularChangeKeeper changeKeeper;

	@Before
	public void prepareArrayGranularChangeKeeper()
	{
		if (changeKeeper == null) changeKeeper = new ArrayGranularChangeKeeper();
		changeKeeper.reset(0, 5);
	}

	private void assertChangeOpEquals(int startIdx, int endIdx, Set<String> columnNames, int type, ArrayOperation equivalentOps)
	{
		assertEquals(type, equivalentOps.type);
		assertEquals(startIdx, equivalentOps.startIndex);
		assertEquals(endIdx, equivalentOps.endIndex);
		assertEquals(columnNames, equivalentOps.cellNames);
	}

	@Test
	public void updatesAndPartialUpdates1()
	{
		// one update
		assertFalse(changeKeeper.hasChanges());
		changeKeeper.processOperation(new ArrayOperation(2, 2, ArrayOperation.CHANGE));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);

		// partial update of the same row
		changeKeeper.processOperation(new ArrayOperation(2, 2, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);

		// partial update of another row
		changeKeeper.processOperation(new ArrayOperation(4, 4, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 4, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[1]);

		// another partial update of row 4
		changeKeeper.processOperation(new ArrayOperation(4, 4, ArrayOperation.CHANGE, Collections.singleton("columnB")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 4, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertEquals(4, changeKeeper.getNumberOfUnchangedRows());

		// duplicate partial update of row 4
		changeKeeper.processOperation(new ArrayOperation(4, 4, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 4, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertEquals(4, changeKeeper.getNumberOfUnchangedRows());

		// partial update on rows 3-5 columnA
		changeKeeper.processOperation(new ArrayOperation(3, 5, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(4, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(3, 3, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(4, 4, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[2]);
		assertChangeOpEquals(5, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[3]);
		assertEquals(2, changeKeeper.getNumberOfUnchangedRows());

		// partial update on rows 3-5 columnB - now it should match row 4 as well
		changeKeeper.processOperation(new ArrayOperation(3, 5, ArrayOperation.CHANGE, Collections.singleton("columnB")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(3, 5, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertEquals(2, changeKeeper.getNumberOfUnchangedRows());

		// full update that overlaps partial update
		changeKeeper.processOperation(new ArrayOperation(4, 5, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(3, 3, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(4, 5, null, ArrayOperation.CHANGE, equivalentOps[2]);
		assertEquals(2, changeKeeper.getNumberOfUnchangedRows());

		// full update that overlaps all previous updates
		changeKeeper.processOperation(new ArrayOperation(2, 5, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 5, null, ArrayOperation.CHANGE, equivalentOps[0]);
	}

	@Test
	public void updatesAndPartialUpdates2()
	{
		assertFalse(changeKeeper.hasChanges());

		// partial update on 4 rows
		changeKeeper.processOperation(new ArrayOperation(2, 5, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);

		// same partial update on 2 rows in the middle
		changeKeeper.processOperation(new ArrayOperation(3, 4, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);

		// different partial update on 2 rows in the beginning
		changeKeeper.processOperation(new ArrayOperation(2, 3, ArrayOperation.CHANGE, Collections.singleton("columnB")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 3, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[1]);

		// different partial update on 1 row in the end
		changeKeeper.processOperation(new ArrayOperation(5, 5, ArrayOperation.CHANGE, Collections.singleton("columnB")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(2, 3, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 4, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(5, 5, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[2]);
	}

	@Test
	public void updatesAndPartialUpdates3()
	{
		assertFalse(changeKeeper.hasChanges());

		// partial update on 4 rows
		changeKeeper.processOperation(new ArrayOperation(2, 5, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);

		// different partial update on 2 rows in the middle (it should split it into 3)
		changeKeeper.processOperation(new ArrayOperation(3, 4, ArrayOperation.CHANGE, Collections.singleton("columnB")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(2, 2, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(3, 4, new HashSet<String>(Arrays.asList("columnA", "columnB")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(5, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[2]);
	}

	@Test
	public void updatesAndPartialUpdates4()
	{
		assertFalse(changeKeeper.hasChanges());

		// partial update on 4 rows
		changeKeeper.processOperation(new ArrayOperation(2, 5, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);

		// full update on 2 rows in the middle (it should split it into 3)
		changeKeeper.processOperation(new ArrayOperation(3, 4, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(2, 2, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(3, 4, null, ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(5, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[2]);
	}

	@Test
	public void updatesAndPartialUpdates5()
	{
		assertFalse(changeKeeper.hasChanges());

		// partial update on 4 rows
		changeKeeper.processOperation(new ArrayOperation(2, 5, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);

		// full update on 2 rows in the beginning
		changeKeeper.processOperation(new ArrayOperation(2, 3, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 3, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 5, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[1]);

		// full update on 1 row in the end
		changeKeeper.processOperation(new ArrayOperation(5, 5, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(2, 3, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 4, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(5, 5, null, ArrayOperation.CHANGE, equivalentOps[2]);
	}

	@Test
	public void insertAndDeleteSplittingPartialUpdate()
	{
		changeKeeper.reset(5, 30);

		assertFalse(changeKeeper.hasChanges());

		// partial update on 4 rows
		changeKeeper.processOperation(new ArrayOperation(10, 25, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(10, 25, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);

		// insert 3 inside partial changes
		changeKeeper.processOperation(new ArrayOperation(12, 14, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(10, 11, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(12, 14, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(15, 28, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[2]);
		assertEquals(10, changeKeeper.getNumberOfUnchangedRows());

		// delete 4 inside partial changes
		changeKeeper.processOperation(new ArrayOperation(19, 22, ArrayOperation.DELETE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(5, equivalentOps.length);
		assertChangeOpEquals(10, 11, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(12, 14, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(15, 18, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[2]);
		assertChangeOpEquals(19, 22, null, ArrayOperation.DELETE, equivalentOps[3]);
		assertChangeOpEquals(19, 24, new HashSet<String>(Arrays.asList("columnA")), ArrayOperation.CHANGE, equivalentOps[4]);
		assertEquals(10, changeKeeper.getNumberOfUnchangedRows());
	}

	@Test
	public void changesAndPartialChangesAtBeginningOrEndOfUnchangedInterval()
	{
		// partial update in the beginning of unchanged interval
		changeKeeper.processOperation(new ArrayOperation(0, 0, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(0, 0, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[0]);

		// full update at the end of unchanged interval
		changeKeeper.processOperation(new ArrayOperation(4, 5, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(0, 0, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(4, 5, null, ArrayOperation.CHANGE, equivalentOps[1]);

		// full update at the end of unchanged interval
		changeKeeper.processOperation(new ArrayOperation(3, 3, ArrayOperation.CHANGE, Collections.singleton("columnB")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(0, 0, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(3, 3, Collections.singleton("columnB"), ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(4, 5, null, ArrayOperation.CHANGE, equivalentOps[2]);
	}

	@Test
	public void overlappingChanges()
	{
		changeKeeper.reset(100, 1000);

		// partial update in the beginning of unchanged interval
		changeKeeper.processOperation(new ArrayOperation(150, 200, ArrayOperation.CHANGE));
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(150, 200, null, ArrayOperation.CHANGE, equivalentOps[0]);

		// full update at the end of unchanged interval
		changeKeeper.processOperation(new ArrayOperation(175, 255, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(150, 255, null, ArrayOperation.CHANGE, equivalentOps[0]);

		// full update at the end of unchanged interval
		changeKeeper.processOperation(new ArrayOperation(107, 165, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(107, 255, null, ArrayOperation.CHANGE, equivalentOps[0]);

		// end of unchanged interval partial update with larger size
		changeKeeper.processOperation(new ArrayOperation(106, 106, ArrayOperation.CHANGE, Collections.singleton("columnC")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(106, 106, Collections.singleton("columnC"), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(107, 255, null, ArrayOperation.CHANGE, equivalentOps[1]);

		// start of unchanged interval partial update with larger size
		changeKeeper.processOperation(new ArrayOperation(100, 100, ArrayOperation.CHANGE, Collections.singleton("columnD")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(100, 100, Collections.singleton("columnD"), ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(106, 106, Collections.singleton("columnC"), ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(107, 255, null, ArrayOperation.CHANGE, equivalentOps[2]);
	}

	@Test
	public void insertsWithALargeNumberOfRows()
	{
		// before unchanged interval
		assertFalse(changeKeeper.hasChanges());
		changeKeeper.processOperation(new ArrayOperation(0, 15, ArrayOperation.INSERT));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(0, 15, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertEquals(6, changeKeeper.getNumberOfUnchangedRows());

		// after unchanged interval
		changeKeeper.processOperation(new ArrayOperation(22, 25, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(0, 15, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(22, 25, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertEquals(6, changeKeeper.getNumberOfUnchangedRows());

		// middle of unchanged interval
		changeKeeper.processOperation(new ArrayOperation(20, 50, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(0, 15, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(20, 50, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(53, 56, null, ArrayOperation.INSERT, equivalentOps[2]);
		assertEquals(6, changeKeeper.getNumberOfUnchangedRows());
	}

	@Test
	public void insertAndChangeBasics()
	{
		changeKeeper.processOperation(new ArrayOperation(2, 2, ArrayOperation.INSERT));
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.INSERT, equivalentOps[0]);

		changeKeeper.processOperation(new ArrayOperation(4, 4, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 2, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(4, 4, null, ArrayOperation.INSERT, equivalentOps[1]);

		changeKeeper.processOperation(new ArrayOperation(2, 4, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 5, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(7, 7, null, ArrayOperation.INSERT, equivalentOps[1]);

		changeKeeper.processOperation(new ArrayOperation(3, 3, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 6, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(8, 8, null, ArrayOperation.INSERT, equivalentOps[1]);

		changeKeeper.processOperation(new ArrayOperation(9, 10, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 6, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(8, 10, null, ArrayOperation.INSERT, equivalentOps[1]);

		changeKeeper.processOperation(new ArrayOperation(7, 8, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(2, 9, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(10, 10, null, ArrayOperation.CHANGE, equivalentOps[1]);

		changeKeeper.processOperation(new ArrayOperation(0, 0, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(0, 0, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(2, 9, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(10, 10, null, ArrayOperation.CHANGE, equivalentOps[2]);

		changeKeeper.processOperation(new ArrayOperation(12, 12, ArrayOperation.CHANGE, Collections.singleton("columnA")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(4, equivalentOps.length);
		assertChangeOpEquals(0, 0, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(2, 9, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(10, 10, null, ArrayOperation.CHANGE, equivalentOps[2]);
		assertChangeOpEquals(12, 12, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[3]);

		changeKeeper.processOperation(new ArrayOperation(13, 13, ArrayOperation.INSERT));
		changeKeeper.processOperation(new ArrayOperation(12, 12, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(6, equivalentOps.length);
		assertChangeOpEquals(0, 0, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(2, 9, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(10, 10, null, ArrayOperation.CHANGE, equivalentOps[2]);
		assertChangeOpEquals(12, 12, null, ArrayOperation.INSERT, equivalentOps[3]);
		assertChangeOpEquals(13, 13, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[4]);
		assertChangeOpEquals(14, 14, null, ArrayOperation.INSERT, equivalentOps[5]);

		changeKeeper.processOperation(new ArrayOperation(3, 3, ArrayOperation.CHANGE, Collections.singleton("columnB")));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(6, equivalentOps.length);
		assertChangeOpEquals(0, 0, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(2, 9, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(10, 10, null, ArrayOperation.CHANGE, equivalentOps[2]);
		assertChangeOpEquals(12, 12, null, ArrayOperation.INSERT, equivalentOps[3]);
		assertChangeOpEquals(13, 13, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[4]);
		assertChangeOpEquals(14, 14, null, ArrayOperation.INSERT, equivalentOps[5]);

		changeKeeper.processOperation(new ArrayOperation(15, 16, ArrayOperation.INSERT));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(6, equivalentOps.length);
		assertChangeOpEquals(0, 0, null, ArrayOperation.CHANGE, equivalentOps[0]);
		assertChangeOpEquals(2, 9, null, ArrayOperation.INSERT, equivalentOps[1]);
		assertChangeOpEquals(10, 10, null, ArrayOperation.CHANGE, equivalentOps[2]);
		assertChangeOpEquals(12, 12, null, ArrayOperation.INSERT, equivalentOps[3]);
		assertChangeOpEquals(13, 13, Collections.singleton("columnA"), ArrayOperation.CHANGE, equivalentOps[4]);
		assertChangeOpEquals(14, 16, null, ArrayOperation.INSERT, equivalentOps[5]);
		assertEquals(3, changeKeeper.getNumberOfUnchangedRows());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testException1()
	{
		// processing Viewport operations with unsupported type should throw an exception
		changeKeeper.processOperation(new ArrayOperation(1, 2, 987654)); // bogus type
	}

	@Test(expected = IllegalArgumentException.class)
	public void testException6()
	{
		// try to apply out-of-viewport-bounds operations
		changeKeeper.processOperation(new ArrayOperation(-1, 3, ArrayOperation.CHANGE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testException7()
	{
		// try to apply out-of-viewport-bounds operations
		changeKeeper.processOperation(new ArrayOperation(-10, -3, ArrayOperation.DELETE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testException8()
	{
		// try to apply out-of-viewport-bounds operations
		changeKeeper.processOperation(new ArrayOperation(6, 6, ArrayOperation.CHANGE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testException9()
	{
		// try to apply out-of-viewport-bounds operations
		changeKeeper.processOperation(new ArrayOperation(3, 8, ArrayOperation.DELETE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testException10()
	{
		// try to apply out-of-viewport-bounds operations
		changeKeeper.processOperation(new ArrayOperation(7, 15, ArrayOperation.INSERT));
	}

	@Test
	public void deleteBasics()
	{
		changeKeeper.reset(5, 30);

		assertFalse(changeKeeper.hasChanges());
		changeKeeper.processOperation(new ArrayOperation(8, 9, ArrayOperation.DELETE));
		assertTrue(changeKeeper.hasChanges());
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(1, equivalentOps.length);
		assertChangeOpEquals(8, 9, null, ArrayOperation.DELETE, equivalentOps[0]);

		changeKeeper.processOperation(new ArrayOperation(8, 8, ArrayOperation.CHANGE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(8, 9, null, ArrayOperation.DELETE, equivalentOps[0]);
		assertChangeOpEquals(8, 8, null, ArrayOperation.CHANGE, equivalentOps[1]);

		changeKeeper.processOperation(new ArrayOperation(5, 5, ArrayOperation.DELETE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(3, equivalentOps.length);
		assertChangeOpEquals(5, 5, null, ArrayOperation.DELETE, equivalentOps[0]);
		assertChangeOpEquals(7, 8, null, ArrayOperation.DELETE, equivalentOps[1]);
		assertChangeOpEquals(7, 7, null, ArrayOperation.CHANGE, equivalentOps[2]);

		changeKeeper.processOperation(new ArrayOperation(26, 27, ArrayOperation.DELETE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(4, equivalentOps.length);
		assertChangeOpEquals(5, 5, null, ArrayOperation.DELETE, equivalentOps[0]);
		assertChangeOpEquals(7, 8, null, ArrayOperation.DELETE, equivalentOps[1]);
		assertChangeOpEquals(7, 7, null, ArrayOperation.CHANGE, equivalentOps[2]);
		assertChangeOpEquals(26, 27, null, ArrayOperation.DELETE, equivalentOps[3]);

		changeKeeper.processOperation(new ArrayOperation(5, 10, ArrayOperation.DELETE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();
		assertEquals(2, equivalentOps.length);
		assertChangeOpEquals(5, 13, null, ArrayOperation.DELETE, equivalentOps[0]);
		assertChangeOpEquals(20, 21, null, ArrayOperation.DELETE, equivalentOps[1]);
	}

	@Test
	public void mixedOperations()
	{
		changeKeeper.processOperation(new ArrayOperation(2, 2, ArrayOperation.CHANGE));
		changeKeeper.processOperation(new ArrayOperation(0, 3, ArrayOperation.INSERT));
		changeKeeper.processOperation(new ArrayOperation(3, 5, ArrayOperation.DELETE));
		changeKeeper.processOperation(new ArrayOperation(0, 1, ArrayOperation.INSERT));
		changeKeeper.processOperation(new ArrayOperation(7, 7, ArrayOperation.INSERT));
		changeKeeper.processOperation(new ArrayOperation(8, 8, ArrayOperation.CHANGE));
		ArrayOperation[] equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();

		assertEquals(4, equivalentOps.length);
		assertChangeOpEquals(0, 2, null, ArrayOperation.INSERT, equivalentOps[0]);
		assertChangeOpEquals(3, 5, null, ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(7, 7, null, ArrayOperation.INSERT, equivalentOps[2]);
		assertChangeOpEquals(8, 8, null, ArrayOperation.CHANGE, equivalentOps[3]);


		changeKeeper.processOperation(new ArrayOperation(0, 4, ArrayOperation.DELETE));
		equivalentOps = changeKeeper.getEquivalentSequenceOfOperations();

		assertEquals(4, equivalentOps.length);
		assertChangeOpEquals(0, 1, null, ArrayOperation.DELETE, equivalentOps[0]);
		assertChangeOpEquals(0, 0, null, ArrayOperation.CHANGE, equivalentOps[1]);
		assertChangeOpEquals(2, 2, null, ArrayOperation.INSERT, equivalentOps[2]);
		assertChangeOpEquals(3, 3, null, ArrayOperation.CHANGE, equivalentOps[3]);
	}

}