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
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * @author rgansevles
 *
 */
public class TextUtilsTest
{
	@Test
	public void shoulEscapeDoubleQuote()
	{
		assertEquals("\\\"", TextUtils.escapeForDoubleQuotedJavascript("\""));
		assertEquals("abc\\\"", TextUtils.escapeForDoubleQuotedJavascript("abc\""));
		assertEquals("\\\"xyz", TextUtils.escapeForDoubleQuotedJavascript("\"xyz"));
		assertEquals("\\\"x\\\"", TextUtils.escapeForDoubleQuotedJavascript("\"x\""));
	}

	@Test
	public void shoulEscapeEscape()
	{
		assertEquals("\\\\", TextUtils.escapeForDoubleQuotedJavascript("\\"));
		assertEquals("a\\\\tb", TextUtils.escapeForDoubleQuotedJavascript("a\\tb"));
		assertEquals("a\\\\\\\"b", TextUtils.escapeForDoubleQuotedJavascript("a\\\"b"));
	}

	@Test
	public void shouldNotEscapeSingleQuote()
	{
		assertEquals("'", TextUtils.escapeForDoubleQuotedJavascript("'"));
		assertEquals("x'y", TextUtils.escapeForDoubleQuotedJavascript("x'y"));
	}

	@Test
	public void shouldNotEscapeNull()
	{
		assertNull(TextUtils.escapeForDoubleQuotedJavascript(null));
	}
}
