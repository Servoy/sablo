/*
 * Copyright (C) 2017 Servoy BV
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
package org.sablo.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sablo.util.HTTPUtils.generateQueryString;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;

/**
 * @author rgansevles
 */
@SuppressWarnings("nls")
public class HTTPUtilsTest
{

	@Test
	public void generateQueryString_emptyMap() throws Exception
	{
		assertEquals("", generateQueryString(Collections.<String, String[]> emptyMap()));
	}

	@Test
	public void generateQueryString_simple() throws Exception
	{
		Map<String, String[]> parameterMap = new HashMap<>();
		parameterMap.put("foo", new String[] { "bar" });

		assertQueryString("foo=bar", generateQueryString(parameterMap));
	}

	@Test
	public void generateQueryString_multipleValues() throws Exception
	{
		Map<String, String[]> parameterMap = new HashMap<>();
		parameterMap.put("foo", new String[] { "bar", "abc" });

		assertQueryString("foo=bar&foo=abc", generateQueryString(parameterMap));
	}

	@Test
	public void generateQueryString_multipleKeys() throws Exception
	{
		Map<String, String[]> parameterMap = new HashMap<>();
		parameterMap.put("foo", new String[] { "bar" });
		parameterMap.put("emp", new String[] { "doe" });

		assertQueryString("foo=bar&emp=doe", generateQueryString(parameterMap));
	}

	@Test
	public void generateQueryString_encodeValue() throws Exception
	{
		Map<String, String[]> parameterMap = new HashMap<>();
		parameterMap.put("foo", new String[] { "bar doe" });

		assertQueryString("foo=bar+doe", generateQueryString(parameterMap));
	}

	@Test
	public void generateQueryString_encodeKey() throws Exception
	{
		Map<String, String[]> parameterMap = new HashMap<>();
		parameterMap.put("foo bar", new String[] { "doe" });

		assertQueryString("foo+bar=doe", generateQueryString(parameterMap));
	}

	// assert that query strings are the same regardless of the ordering
	private static void assertQueryString(String expected, String actual)
	{
		assertTrue(actual + " not equal to " + expected,
			new HashSet<>(Arrays.asList(expected.split("&"))).equals(new HashSet<>(Arrays.asList(actual.split("&")))));
	}

}
