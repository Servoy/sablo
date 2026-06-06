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

import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jcompagner
 * @since 2022.03
 *
 */
@SuppressWarnings("nls")
public class CssLibTest
{
	@Test
	public void testDuplicatesWithoutPriority() throws Exception
	{
		TreeSet<CssLib> set = new TreeSet<CssLib>();
		set.add(new CssLib("test.css"));
		set.add(new CssLib("test.css"));
		Assert.assertEquals(1, set.size());
	}

	@Test
	public void testDuplicatesWithOnePriorityAddedBefore() throws Exception
	{
		CssLibSet set = new CssLibSet();
		set.add(new CssLib("test1.css;priority=1"));
		set.add(new CssLib("test1.css"));
		set.add(new CssLib("test2.css"));
		set.add(new CssLib("test3.css"));
		Assert.assertEquals(3, set.size());
		Assert.assertArrayEquals(new String[] { "test2.css", "test3.css", "test1.css" }, set.stream().map(lib -> lib.getUrl()).toArray());
	}

	@Test
	public void testDuplicatesWithOnePriorityAddedAfter() throws Exception
	{
		CssLibSet set = new CssLibSet();
		set.add(new CssLib("test1.css"));
		set.add(new CssLib("test1.css;priority=1"));
		set.add(new CssLib("test2.css"));
		set.add(new CssLib("test3.css"));
		Assert.assertEquals(3, set.size());
		Assert.assertArrayEquals(new String[] { "test2.css", "test3.css", "test1.css" }, set.stream().map(lib -> lib.getUrl()).toArray());
	}

	@Test
	public void testDuplicatesWithPriority() throws Exception
	{
		TreeSet<CssLib> set = new TreeSet<CssLib>();
		set.add(new CssLib("test.css;priority=1"));
		set.add(new CssLib("test.css;priority=2"));
		Assert.assertEquals(1, set.size());
	}


	@Test
	public void testDuplicatesWithTheSamePriority() throws Exception
	{
		TreeSet<CssLib> set = new TreeSet<CssLib>();
		set.add(new CssLib("test.css;priority=2"));
		set.add(new CssLib("test.css;priority=2"));
		Assert.assertEquals(1, set.size());
	}

	@Test
	public void testWithoutPriority() throws Exception
	{
		TreeSet<CssLib> set = new TreeSet<CssLib>();
		set.add(new CssLib("test3.css"));
		set.add(new CssLib("test1.css"));
		set.add(new CssLib("test2.css"));
		Assert.assertEquals(3, set.size());
		Assert.assertArrayEquals(new String[] { "test1.css", "test2.css", "test3.css" }, set.stream().map(lib -> lib.getUrl()).toArray());
	}

	@Test
	public void testOneWithPriority() throws Exception
	{
		TreeSet<CssLib> set = new TreeSet<CssLib>();
		set.add(new CssLib("test3.css"));
		set.add(new CssLib("test1.css;priority=1"));
		set.add(new CssLib("test2.css"));
		Assert.assertEquals(3, set.size());
		Assert.assertArrayEquals(new String[] { "test2.css", "test3.css", "test1.css" }, set.stream().map(lib -> lib.getUrl()).toArray());
	}

	@Test
	public void testAllWithSamePriority() throws Exception
	{
		TreeSet<CssLib> set = new TreeSet<CssLib>();
		set.add(new CssLib("test3.css;priority=1"));
		set.add(new CssLib("test1.css;priority=1"));
		set.add(new CssLib("test2.css;priority=1"));
		Assert.assertEquals(3, set.size());
		Assert.assertArrayEquals(new String[] { "test1.css", "test2.css", "test3.css" }, set.stream().map(lib -> lib.getUrl()).toArray());
	}


	@Test
	public void testAllWithDifferntPriority() throws Exception
	{
		TreeSet<CssLib> set = new TreeSet<CssLib>();
		set.add(new CssLib("test3.css;priority=1"));
		set.add(new CssLib("test1.css;priority=2"));
		set.add(new CssLib("test2.css;priority=3"));
		Assert.assertEquals(3, set.size());
		Assert.assertArrayEquals(new String[] { "test2.css", "test1.css", "test3.css" }, set.stream().map(lib -> lib.getUrl()).toArray());
	}

	@Test
	public void testCopyInSetWithMultilyeDuplicates() throws Exception
	{
		CssLibSet set = new CssLibSet();
		set.add(new CssLib("test1.css;priority=1"));
		set.add(new CssLib("test2.css;priority=2"));
		set.add(new CssLib("test3.css"));

		set.add(new CssLib("test1.css"));
		set.add(new CssLib("test2.css"));
		set.add(new CssLib("test4.css"));

		Assert.assertEquals(4, set.size());
		Assert.assertArrayEquals(new String[] { "test3.css", "test4.css", "test2.css", "test1.css" }, set.stream().map(lib -> lib.getUrl()).toArray());
	}

	@Test
	public void testCopySetInSet() throws Exception
	{
		CssLibSet set = new CssLibSet();
		set.add(new CssLib("test1.css;priority=1"));
		set.add(new CssLib("test2.css;priority=2"));
		set.add(new CssLib("test3.css"));

		CssLibSet set2 = new CssLibSet();
		set2.add(new CssLib("test1.css"));
		set2.add(new CssLib("test2.css"));
		set2.add(new CssLib("test4.css"));
		set2.add(new CssLib("test5.css"));

		set.addAll(set2);
		Assert.assertEquals(5, set.size());
		Assert.assertArrayEquals(new String[] { "test3.css", "test4.css", "test5.css", "test2.css", "test1.css" },
			set.stream().map(lib -> lib.getUrl()).toArray());
	}
}

