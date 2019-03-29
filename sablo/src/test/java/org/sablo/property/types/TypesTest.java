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
import static org.junit.Assert.assertSame;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Locale;

import javax.swing.plaf.ColorUIResource;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.types.DimensionPropertyType;
import org.sablo.specification.property.types.DoublePropertyType;
import org.sablo.specification.property.types.FloatPropertyType;
import org.sablo.specification.property.types.IntPropertyType;
import org.sablo.specification.property.types.LongPropertyType;
import org.sablo.specification.property.types.PointPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.BaseWebsocketSession;
import org.sablo.websocket.BaseWindow;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.WebsocketSessionKey;

/**
 * @author jcompagner
 *
 */
public class TypesTest
{

	private static class TestBaseWebsocketSession extends BaseWebsocketSession
	{
		private Locale locale = Locale.getDefault();

		public TestBaseWebsocketSession(WebsocketSessionKey getSessionKey)
		{
			super(getSessionKey);
		}

		@Override
		public Locale getLocale()
		{
			return locale;
		}

		public void setLocale(Locale locale)
		{
			this.locale = locale;
		}
	}

	@Before
	public void setUp() throws Exception
	{
		TestBaseWebsocketSession wsSession = new TestBaseWebsocketSession(new WebsocketSessionKey("1", 42));
		CurrentWindow.set(new BaseWindow(wsSession, 11, "Test"));
		Assert.assertNotNull("no window", CurrentWindow.get());
		Assert.assertNotNull("no wsSession", CurrentWindow.get().getSession());
	}

	@Test
	public void testDimensionType() throws Exception
	{
		DimensionPropertyType type = DimensionPropertyType.INSTANCE;

		Dimension dim = type.defaultValue(null);
		assertNotNull(dim);

		assertEquals(0, dim.height);
		assertEquals(0, dim.width);

		dim.height = 10;
		dim.width = 10;

		JSONStringer writer = new JSONStringer();
		type.toJSON(writer, null, dim, null, null, null);

		String json = writer.toString();

		assertEquals("{\"width\":10,\"height\":10}", json);

		JSONObject object = new JSONObject(json);

		Dimension result = type.fromJSON(object, dim, null, null, null);

		assertEquals(dim, result);
	}


	@Test
	public void testPointType() throws Exception
	{
		PointPropertyType type = PointPropertyType.INSTANCE;

		Point point = type.defaultValue(null);
		assertNotNull(point);

		assertEquals(0, point.x);
		assertEquals(0, point.y);

		point.x = 10;
		point.y = 10;

		JSONStringer writer = new JSONStringer();
		type.toJSON(writer, null, point, null, null, null);

		String json = writer.toString();

		assertEquals("{\"x\":10,\"y\":10}", json);

		JSONObject object = new JSONObject(json);

		Point result = type.fromJSON(object, point, null, null, null);

		assertEquals(point, result);
	}

	@Test
	public void testSubclassType() throws Exception
	{

		IClassPropertyType< ? > type = TypesRegistry.getType(Color.class);
		assertNotNull(type);

		IClassPropertyType< ? > type2 = TypesRegistry.getType(ColorUIResource.class);

		assertNotNull(type2);

		assertSame(type, type2);
	}

	@Test
	public void testNumberTypesToNullConversion()
	{
		assertNull(DoublePropertyType.INSTANCE.fromJSON(null, null, null, null, null));
		assertNull(DoublePropertyType.INSTANCE.fromJSON("", null, null, null, null));

		assertNull(IntPropertyType.INSTANCE.fromJSON(null, null, null, null, null));
		assertNull(IntPropertyType.INSTANCE.fromJSON("", null, null, null, null));

		assertNull(LongPropertyType.INSTANCE.fromJSON(null, null, null, null, null));
		assertNull(LongPropertyType.INSTANCE.fromJSON("", null, null, null, null));

		assertNull(FloatPropertyType.INSTANCE.fromJSON(null, null, null, null, null));
		assertNull(FloatPropertyType.INSTANCE.fromJSON("", null, null, null, null));

	}

	@Test
	public void testDoubleTypeConversion()
	{
		TestBaseWebsocketSession session = (TestBaseWebsocketSession)CurrentWindow.get().getSession();

		assertEquals(1, DoublePropertyType.INSTANCE.fromJSON(Double.valueOf(1), null, null, null, null).doubleValue(), 0);
		assertEquals(1.1, DoublePropertyType.INSTANCE.fromJSON(Double.valueOf(1.1), null, null, null, null).doubleValue(), 0);
		assertEquals(1, DoublePropertyType.INSTANCE.fromJSON("1", null, null, null, null).doubleValue(), 0);

		// test with english locale
		session.setLocale(new Locale("en", "US"));
		assertEquals(1111.11, DoublePropertyType.INSTANCE.fromJSON("1,111.11", null, null, null, null).doubleValue(), 0);
		assertEquals(1.1, DoublePropertyType.INSTANCE.fromJSON("1.1", null, null, null, null).doubleValue(), 0);

		// test with dutch locale
		session.setLocale(new Locale("nl", "NL"));
		assertEquals(1111.11, DoublePropertyType.INSTANCE.fromJSON("1.111,11", null, null, null, null).doubleValue(), 0);
		assertEquals(1.1, DoublePropertyType.INSTANCE.fromJSON("1,1", null, null, null, null).doubleValue(), 0);

	}

	@Test
	public void testFloatTypeConversion()
	{
		TestBaseWebsocketSession session = (TestBaseWebsocketSession)CurrentWindow.get().getSession();

		assertEquals(1, FloatPropertyType.INSTANCE.fromJSON(Float.valueOf(1), null, null, null, null).floatValue(), 0);
		assertEquals(1.1f, FloatPropertyType.INSTANCE.fromJSON(Float.valueOf(1.1f), null, null, null, null).floatValue(), 0);
		assertEquals(1, FloatPropertyType.INSTANCE.fromJSON("1", null, null, null, null).doubleValue(), 0);

		// test with english locale
		session.setLocale(new Locale("en", "US"));
		assertEquals(1111.11f, FloatPropertyType.INSTANCE.fromJSON("1,111.11", null, null, null, null).floatValue(), 0);
		assertEquals(1.1f, FloatPropertyType.INSTANCE.fromJSON("1.1", null, null, null, null).floatValue(), 0);

		// test with dutch locale
		session.setLocale(new Locale("nl", "NL"));
		assertEquals(1111.11f, FloatPropertyType.INSTANCE.fromJSON("1.111,11", null, null, null, null).floatValue(), 0);
		assertEquals(1.1f, FloatPropertyType.INSTANCE.fromJSON("1,1", null, null, null, null).floatValue(), 0);

	}

	@Test
	public void testIntegerTypeConversion()
	{
		//TestBaseWebsocketSession session = (TestBaseWebsocketSession)CurrentWindow.get().getSession();

		assertEquals(1, IntPropertyType.INSTANCE.fromJSON(Integer.valueOf(1), null, null, null, null).intValue());
		assertEquals(1, IntPropertyType.INSTANCE.fromJSON("1", null, null, null, null).intValue());

		// test with english locale
		//session.setLocale(new Locale("en", "US"));
		assertEquals(1, IntPropertyType.INSTANCE.fromJSON("1,111", null, null, null, null).intValue());

		// test with dutch locale
		//session.setLocale(new Locale("nl", "NL"));
		assertEquals(1, IntPropertyType.INSTANCE.fromJSON("1.111", null, null, null, null).intValue());

		assertEquals(1111, IntPropertyType.INSTANCE.fromJSON("1.111,11", null, null, null, null).intValue());
		assertEquals(1111, IntPropertyType.INSTANCE.fromJSON("1,111.11", null, null, null, null).intValue());


	}

	@Test
	public void testLongTypeConversion()
	{
		//TestBaseWebsocketSession session = (TestBaseWebsocketSession)CurrentWindow.get().getSession();

		assertEquals(1, LongPropertyType.INSTANCE.fromJSON(Long.valueOf(1), null, null, null, null).longValue());
		assertEquals(1, LongPropertyType.INSTANCE.fromJSON("1", null, null, null, null).longValue());

		// test with english locale
		//session.setLocale(new Locale("en", "US"));
		assertEquals(1, LongPropertyType.INSTANCE.fromJSON("1,111", null, null, null, null).intValue());

		// test with dutch locale
		//session.setLocale(new Locale("nl", "NL"));
		assertEquals(1, LongPropertyType.INSTANCE.fromJSON("1.111", null, null, null, null).intValue());

		assertEquals(1111, LongPropertyType.INSTANCE.fromJSON("1.111,11", null, null, null, null).intValue());
		assertEquals(1111, LongPropertyType.INSTANCE.fromJSON("1,111.11", null, null, null, null).intValue());

	}
}
