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
package org.sablo.property.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.Dimension;
import java.awt.Point;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sablo.specification.property.types.ObjectPropertyType;
import org.sablo.util.TestBaseWebsocketSession;
import org.sablo.websocket.BaseWindow;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.WebsocketSessionKey;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IJSONStringWithClientSideType;

/**
 * Regression test for SVY-21358: the generic 'object' serializer must handle
 * {@link java.awt.Dimension} and {@link java.awt.Point} gracefully (safety net) instead
 * of hitting the final 'unsupported value type' error branch, while still returning null
 * (firing that branch) for genuinely unknown types.
 *
 * @author sdd
 */
public class ObjectPropertyTypeSerializationTest
{
	@Before
	public void setUp() throws Exception
	{
		TestBaseWebsocketSession wsSession = new TestBaseWebsocketSession(new WebsocketSessionKey("1", 42));
		CurrentWindow.set(new BaseWindow(wsSession, 11, "Test"));
		Assert.assertNotNull("no window", CurrentWindow.get());
		Assert.assertNotNull("no wsSession", CurrentWindow.get().getSession());
	}

	@Test
	public void testDimensionSerializesWithoutError() throws Exception
	{
		Dimension dim = new Dimension(140, 20);

		IJSONStringWithClientSideType result = ObjectPropertyType.INSTANCE.getJSONAndClientSideType(
			JSONUtils.FullValueToJSONConverter.INSTANCE, dim, null, null);

		assertNotNull("Dimension should be serializable by the object type (safety net)", result);

		JSONObject json = new JSONObject(result.toJSONString());
		assertEquals(140, json.getDouble("width"), 0);
		assertEquals(20, json.getDouble("height"), 0);
		assertEquals("only width/height keys expected", 2, json.length());
	}

	@Test
	public void testPointSerializesWithoutError() throws Exception
	{
		Point point = new Point(10, 20);

		IJSONStringWithClientSideType result = ObjectPropertyType.INSTANCE.getJSONAndClientSideType(
			JSONUtils.FullValueToJSONConverter.INSTANCE, point, null, null);

		assertNotNull("Point should be serializable by the object type (safety net)", result);

		JSONObject json = new JSONObject(result.toJSONString());
		assertEquals(10, json.getDouble("x"), 0);
		assertEquals(20, json.getDouble("y"), 0);
		assertEquals("only x/y keys expected", 2, json.length());
	}

	@Test
	public void testUnsupportedTypeStillReturnsNull() throws Exception
	{
		Object unsupported = new UnsupportedPojo();

		IJSONStringWithClientSideType result = ObjectPropertyType.INSTANCE.getJSONAndClientSideType(
			JSONUtils.FullValueToJSONConverter.INSTANCE, unsupported, null, null);

		assertNull("genuinely unsupported type must still hit the generic error branch (return null)", result);
	}

	private static class UnsupportedPojo
	{
		@SuppressWarnings("unused")
		final int someField = 1;
	}
}
