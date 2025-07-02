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
package org.sablo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sablo.services.server.FormServiceHandler;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.PropertyDescriptionBuilder;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.WebObjectSpecificationBuilder;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.ChangeAwareList;
import org.sablo.specification.property.types.DimensionPropertyType;
import org.sablo.specification.property.types.VisiblePropertyType;
import org.sablo.util.TestBaseWebsocketSession;
import org.sablo.websocket.BaseWindow;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.WebsocketSessionKey;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
@SuppressWarnings("nls")
public class WebComponentTest
{
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUp() throws Exception
	{
		InputStream is = WebComponentTest.class.getResourceAsStream("WebComponentTest.manifest");
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		String manifest = new String(bytes);
		is.close();

		is = WebComponentTest.class.getResourceAsStream("WebComponentTest-mycomponent.spec");
		bytes = new byte[is.available()];
		is.read(bytes);
		String comp = new String(bytes);
		is.close();

		HashMap<String, String> components = new HashMap<>();
		components.put("mycomponent.spec", comp);
		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(manifest, components) }, null);
	}

	@AfterClass
	public static void tearDown()
	{
		WebComponentSpecProvider.disposeInstance();
	}

	@Test
	public void testNotExistingProperty()
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		assertNull(component.getProperty("doesnotexisits"));

		component.setProperty("doesnotexisits", "test");

		assertEquals("test", component.getProperty("doesnotexisits"));
	}

	@Test
	public void testDimension() throws JSONException
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		assertEquals(new Dimension(0, 0), component.getProperty("size"));

		// put in something illegal
		component.setProperty("size", Color.black);
		assertNull(component.getProperty("size"));

		assertTrue(component.setProperty("size", new Dimension(10, 10)));

		Map<String, Object> properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("size"));

		assertEquals(new Dimension(10, 10), properties.get("size"));
		assertEquals(new Dimension(10, 10), component.getProperty("size"));

		String msg = JSONUtils.writeDataAsFullToJSON(properties, null, null);
		assertEquals(new JSONObject("{\"name\":\"test\",\"size\":{\"width\":10,\"height\":10}}"), new JSONObject(msg), true);

		component.putBrowserProperty("size", new JSONObject("{\"width\":20,\"height\":20}"));
		properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("size"));

		assertEquals(new Dimension(20, 20), properties.get("size"));
		assertEquals(new Dimension(20, 20), component.getProperty("size"));
	}

	@Test
	public void testColor() throws JSONException
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		assertNull(component.getProperty("background"));

		// put in something illegal
		component.setProperty("background", new Dimension());
		assertNull(component.getProperty("background"));

		assertTrue(component.setProperty("background", Color.black));

		Map<String, Object> properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("background"));

		assertEquals(Color.black, properties.get("background"));
		assertEquals(Color.black, component.getProperty("background"));

		String msg = JSONUtils.writeChanges(properties, null, null);
		// Use JSONObject.similar() to compare JSON content regardless of property order
		JSONObject actual = new JSONObject(msg);
		JSONObject expected = new JSONObject("{\"background\":\"#000000\",\"name\":\"test\"}");
		assertTrue("JSON content should be equivalent regardless of property order", expected.similar(actual));

		component.putBrowserProperty("background", "#ff0000");
		properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("background"));

		assertEquals(Color.red, properties.get("background"));
		assertEquals(Color.red, component.getProperty("background"));
	}

	@Test
	public void testFont() throws JSONException
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		assertNull(component.getProperty("font"));

		// put in something illegal
		component.setProperty("font", new Dimension());
		assertNull(component.getProperty("font"));

		assertTrue(component.setProperty("font", new Font("SansSerif", Font.BOLD, 12)));

		Map<String, Object> properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("font"));

		assertEquals(new Font("SansSerif", Font.BOLD, 12), properties.get("font"));
		assertEquals(new Font("SansSerif", Font.BOLD, 12), component.getProperty("font"));

		String msg = JSONUtils.writeDataAsFullToJSON(properties, null, null);
		assertEquals(new JSONObject(
			"{\"font\":{\"fontWeight\":\"bold\",\"fontStyle\":\"normal\",\"fontSize\":\"12px\",\"fontFamily\":\"SansSerif, Verdana, Arial\"},\"name\":\"test\"}"),
			new JSONObject(msg), true);

		component.putBrowserProperty("font", new JSONObject("{\"fontStyle\":\"italic\",\"fontSize\":\"10px\",\"fontFamily\":\"SansSerif, Verdana, Arial\"}"));
		properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("font"));

		Font font = (Font)component.getProperty("font");
		assertEquals("SansSerif", font.getName());
		assertFalse(font.isBold());
		assertTrue(font.isItalic());
		assertEquals(10, font.getSize());
	}

	@Test
	public void testPoint() throws JSONException
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		assertEquals(new Point(0, 0), component.getProperty("location"));

		// put in something illegal
		component.setProperty("location", Color.black);
		assertNull(component.getProperty("location"));

		assertTrue(component.setProperty("location", new Point(10, 10)));

		Map<String, Object> properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("location"));

		assertEquals(new Point(10, 10), properties.get("location"));
		assertEquals(new Point(10, 10), component.getProperty("location"));

		String msg = JSONUtils.writeDataAsFullToJSON(properties, null, null);
		assertEquals(new JSONObject("{\"location\":{\"x\":10,\"y\":10},\"name\":\"test\"}"), new JSONObject(msg), true);

		component.putBrowserProperty("location", new JSONObject("{\"x\":20,\"y\":20}"));
		properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("location"));

		assertEquals(new Point(20, 20), properties.get("location"));
		assertEquals(new Point(20, 20), component.getProperty("location"));
	}

	@Test
	public void testCustomType() throws JSONException
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		assertNull(component.getProperty("atype"));

		// custom types are always a Map of values..
		Map<String, Object> customType = new HashMap<>();
		customType.put("name", "myname");
		customType.put("active", Boolean.TRUE);
		customType.put("foreground", Color.black);

		assertTrue(component.setProperty("atype", customType));

		Map<String, Object> properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("atype"));

		assertEquals(customType, properties.get("atype"));
		assertEquals(customType, component.getProperty("atype"));

		// the following uses just a default conversion not customObjectType; but test that anyway to see that Color type works based on class type conversions
		String msg = JSONUtils.writeDataAsFullToJSON(properties, null, null);
		// Use JSONObject.similar() to compare JSON content regardless of property order
		JSONObject actual1 = new JSONObject(msg);
		JSONObject expected1 = new JSONObject(
			"{\"atype\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"},\"name\":\"test\"}");
		assertTrue("JSON content should be equivalent regardless of property order", expected1.similar(actual1));

		// now do it as it's supposed to be done
		msg = JSONUtils.writeDataAsFullToJSON(properties, component.getSpecification(), null);
		JSONObject actual2 = new JSONObject(msg);
		JSONObject expected2 = new JSONObject(
			"{\"atype\":{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},\"name\":\"test\"}");
		assertTrue("JSON content should be equivalent regardless of property order", expected2.similar(actual2));

		component.putBrowserProperty("atype",
			new JSONObject(
				"{\"vEr\":2,\"v\":{\"name\":\"myNAME\",\"active\":false,\"text\":\"test\",\"foreground\":\"#ffffff\",\"size\":{\"width\":10,\"height\":10}}}"));
		properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("atype"));
		assertSame(properties.get("atype"), component.getProperty("atype"));

		customType = (Map<String, Object>)component.getProperty("atype");
		assertEquals("myNAME", customType.get("name"));
		assertEquals(Boolean.FALSE, customType.get("active"));
		assertEquals(new Dimension(10, 10), customType.get("size"));
		assertEquals(Color.white, customType.get("foreground"));

		// TODO also for custom types none existing properties should just be added? Like in the component itself?
		// for now we don't allow it..
		component.putBrowserProperty("atype",
			new JSONObject("{\"vEr\":0,\"v\":{\"name\":\"YOURname\",\"notintype\":false,\"text\":\"test\",\"size\":{\"width\":10,\"height\":10}}}"));
		properties = component.getRawPropertiesWithoutDefaults();
		assertNotNull(properties.get("atype"));
		assertSame(properties.get("atype"), component.getProperty("atype"));

		customType = (Map<String, Object>)component.getProperty("atype");
		assertEquals("YOURname", customType.get("name"));
		assertNull(customType.get("active"));
		assertNull(customType.get("notintype"));

		// TODO add partial updates for custom types?
		// but how do we know that? that the previous value must be taken as a base, and updates should overwrite, and somehow properties marked as deletes should be deleted?
	}

	@Test
	public void testCustomTypeAsArray() throws JSONException
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		BrowserConverterContext shallowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.shallow);
		assertNull(component.getProperty("types"));

		// custom types are always a Map of values..
		Map<String, Object> customType1 = new HashMap<>();
		customType1.put("name", "myname");
		customType1.put("active", Boolean.TRUE);
		customType1.put("foreground", Color.black);

		Map<String, Object> customType2 = new HashMap<>();
		customType2.put("name", "myname2");
		customType2.put("active", Boolean.FALSE);
		customType2.put("foreground", Color.white);

		// arrays are wrapped in a smart array that can wrap and convert/handle changes automatically
		Object[] array = new Object[] { customType1, customType2 };
		assertTrue(component.setProperty("types", array));

		ChangeAwareList<Object, Object> array2 = (ChangeAwareList)component.getProperty("types"); // ChangeAwareList

		boolean same = true;
		int i = 0;
		for (Object o : array)
			same = same | (o != array2.get(i++));
		assertTrue(same);

		TypedData<Map<String, Object>> properties = component.getProperties();

		String msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"name\":\"test\",\"types\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}]}}"),
			new JSONObject(msg), true);

		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, shallowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"name\":\"test\",\"types\":{\"vEr\":3,\"v\":[{\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":3,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}]}}"),
			new JSONObject(msg), true);

		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":3,\"v\":[{\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":3,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":3,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}"));

		ChangeAwareList<Object, Object> array3 = (ChangeAwareList)component.getProperty("types");

		assertEquals(3, array3.size());

		assertEquals("myname", ((Map< ? , ? >)array3.get(0)).get("name"));
		assertEquals("myname2", ((Map< ? , ? >)array3.get(1)).get("name"));
		assertEquals("myname3", ((Map< ? , ? >)array3.get(2)).get("name"));
		assertEquals(Color.red, ((Map< ? , ? >)array3.get(1)).get("foreground"));
		assertNull(((Map< ? , ? >)array3.get(2)).get("foreground"));
	}

	@Test
	public void setColorPropertyWithOldValue()
	{
		Map<String, PropertyDescription> properties = new HashMap<>();
		properties.put("size", new PropertyDescriptionBuilder().withName("size").withType(DimensionPropertyType.INSTANCE).build());
		properties.put("visible", new PropertyDescriptionBuilder().withName("visible").withType(VisiblePropertyType.INSTANCE).build());

		WebObjectSpecification formSpec = new WebObjectSpecificationBuilder().withName("form_spec").withPackageType(
			IPackageReader.WEB_COMPONENT).withProperties(properties).build();

		final Container form = new Container("form", formSpec)
		{
		};

		final WebComponent testcomponent = new WebComponent("mycomponent", "test");
		testcomponent.setProperty("background", Color.BLACK);
		form.add(testcomponent);

		CurrentWindow.runForWindow(new BaseWindow(new TestBaseWebsocketSession(new WebsocketSessionKey("1", 42)), 99, "test")
		{
			@Override
			public Container getForm(String formName)
			{
				return form;
			}
		}, () -> {
			assertEquals(Color.BLACK, testcomponent.getProperty("background"));

			try
			{
				JSONObject json = new JSONObject();
				json.put("formname", "test");
				json.put("beanname", testcomponent.getName());
				JSONObject changes = new JSONObject();
				changes.put("background", "#0000FF");
				json.put("changes", changes);

				FormServiceHandler.INSTANCE.executeMethod("dataPush", json);

				// should be changed.
				Assert.assertEquals(Color.BLUE, testcomponent.getProperty("background"));

				changes.put("background", "#FF0000");
				JSONObject oldvalues = new JSONObject();
				oldvalues.put("background", "#0000FF");
				json.put("oldvalues", oldvalues);

				FormServiceHandler.INSTANCE.executeMethod("dataPush", json);

				// should be changed, old value was really the old value.
				Assert.assertEquals(Color.RED, testcomponent.getProperty("background"));

				changes.put("background", "#00FF00");

				// should not be changed, still RED
				FormServiceHandler.INSTANCE.executeMethod("dataPush", json);
				Assert.assertEquals(Color.RED, testcomponent.getProperty("background"));
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}

		});
	}

	@Test
	public void setIntPropertyWithOldValue()
	{
		Map<String, PropertyDescription> properties = new HashMap<>();
		properties.put("size", new PropertyDescriptionBuilder().withName("size").withType(DimensionPropertyType.INSTANCE).build());
		properties.put("visible", new PropertyDescriptionBuilder().withName("visible").withType(VisiblePropertyType.INSTANCE).build());

		WebObjectSpecification formSpec = new WebObjectSpecificationBuilder().withName("form_spec").withPackageType(
			IPackageReader.WEB_COMPONENT).withProperties(properties).build();

		final Container form = new Container("form", formSpec)
		{
		};

		final WebComponent testcomponent = new WebComponent("mycomponent", "test");
		testcomponent.setProperty("changeintallow", Integer.valueOf(1));
		form.add(testcomponent);

		CurrentWindow.runForWindow(new BaseWindow(new TestBaseWebsocketSession(new WebsocketSessionKey("1", 42)), 99, "test")
		{
			@Override
			public Container getForm(String formName)
			{
				return form;
			}
		}, () -> {
			assertEquals(Integer.valueOf(1), testcomponent.getProperty("changeintallow"));

			try
			{
				JSONObject json = new JSONObject();
				json.put("formname", "test");
				json.put("beanname", testcomponent.getName());
				JSONObject changes = new JSONObject();
				changes.put("changeintallow", Integer.valueOf(2));
				json.put("changes", changes);

				FormServiceHandler.INSTANCE.executeMethod("dataPush", json);

				// should be changed.
				Assert.assertEquals(Integer.valueOf(2), testcomponent.getProperty("changeintallow"));

				changes.put("changeintallow", Integer.valueOf(3));
				JSONObject oldvalues = new JSONObject();
				oldvalues.put("changeintallow", Integer.valueOf(2));
				json.put("oldvalues", oldvalues);

				FormServiceHandler.INSTANCE.executeMethod("dataPush", json);

				// should be changed, old value was really the old value.
				Assert.assertEquals(Integer.valueOf(3), testcomponent.getProperty("changeintallow"));

				changes.put("changeintallow", Integer.valueOf(4));

				// should not be changed, still 3
				FormServiceHandler.INSTANCE.executeMethod("dataPush", json);
				Assert.assertEquals(Integer.valueOf(3), testcomponent.getProperty("changeintallow"));

				changes.put("changeintallow", new Double(4));
				oldvalues.put("changeintallow", new Double(3));

				FormServiceHandler.INSTANCE.executeMethod("dataPush", json);

				// should be changed, old value was really the old value.
				Assert.assertEquals(Integer.valueOf(4), testcomponent.getProperty("changeintallow"));
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}

		});
	}

	@Test(expected = IllegalChangeFromClientException.class)
	public void shouldNotAllowSetValueByDefault() throws Exception
	{
		new WebComponent("mycomponent", "test").putBrowserProperty("nochangeint1", Integer.valueOf(42));
	}

	@Test(expected = IllegalChangeFromClientException.class)
	public void shouldNotAllowSetValueByDefaultType() throws Exception
	{
		new WebComponent("mycomponent", "test").putBrowserProperty("nochangeint2", Integer.valueOf(42));
	}

	@Test(expected = IllegalChangeFromClientException.class)
	public void shouldNotAllowSetValueWhenReject() throws Exception
	{
		new WebComponent("mycomponent", "test").putBrowserProperty("nochangeint3", Integer.valueOf(42));
	}

	@Test
	public void shouldAllowSetValueAllow() throws Exception
	{
		WebComponent webComponent = new WebComponent("mycomponent", "test");

		assertEquals(Integer.valueOf(0), webComponent.getProperty("changeintallow"));

		webComponent.putBrowserProperty("changeintallow", Integer.valueOf(42));

		assertEquals(Integer.valueOf(42), webComponent.getProperty("changeintallow"));
	}

	@Test
	public void shouldAllowSetValueShallow() throws Exception
	{
		WebComponent webComponent = new WebComponent("mycomponent", "test");

		assertEquals(Integer.valueOf(0), webComponent.getProperty("changeintshallow"));

		webComponent.putBrowserProperty("changeintshallow", Integer.valueOf(42));

		assertEquals(Integer.valueOf(42), webComponent.getProperty("changeintshallow"));
	}

	@Test
	public void shouldAllowSetValueDeep() throws Exception
	{
		WebComponent webComponent = new WebComponent("mycomponent", "test");

		assertEquals(Integer.valueOf(0), webComponent.getProperty("changeintdeep"));

		webComponent.putBrowserProperty("changeintdeep", Integer.valueOf(42));

		assertEquals(Integer.valueOf(42), webComponent.getProperty("changeintdeep"));
	}

}
