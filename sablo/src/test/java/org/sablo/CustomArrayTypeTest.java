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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.ChangeAwareList;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author acostescu
 */
@SuppressWarnings("nls")
public class CustomArrayTypeTest
{

	@BeforeClass
	public static void setUp() throws Exception
	{
		InputStream is = CustomArrayTypeTest.class.getResourceAsStream("WebComponentTest.manifest");
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		String manifest = new String(bytes);
		is.close();

		is = CustomArrayTypeTest.class.getResourceAsStream("WebComponentTest-mycomponent.spec");
		bytes = new byte[is.available()];
		is.read(bytes);
		String comp = new String(bytes);
		is.close();

		HashMap<String, String> components = new HashMap<>();
		components.put("mycomponent.spec", comp);
		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(manifest, components) });
	}

	@AfterClass
	public static void tearDown()
	{
		WebComponentSpecProvider.disposeInstance();
	}

	@Test
	public void shouldNotAllowSetValueWhenRejectSmartElements() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		assertNull(component.getProperty("typesReject"));

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
		assertTrue(component.setProperty("typesReject", array));

		ChangeAwareList<Object, Object> array2 = (ChangeAwareList)component.getProperty("typesReject"); // ChangeAwareList

		boolean same = true;
		int i = 0;
		for (Object o : array)
			same = same | (o != array2.get(i++));
		assertTrue(same);

		HashMap<String, Object> data = new HashMap<>();
		TypedData<Map<String, Object>> properties = component.getProperties();
		data.put("msg", properties.content);

		PropertyDescription messageTypes = AggregatedPropertyType.newAggregatedProperty();
		messageTypes.putProperty("msg", properties.contentType);

		String msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"typesReject\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}],\"svy_types\":{\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}},\"name\":\"test\"},\"svy_types\":{\"msg\":{\"typesReject\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to change whole thing... should be blocked by array property
		component.putBrowserProperty("typesReject",
			new JSONObject("{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":2,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"typesReject\":{\"vEr\":3,\"v\":[{\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":3,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}],\"svy_types\":{\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}},\"name\":\"test\"},\"svy_types\":{\"msg\":{\"typesReject\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to update by setting one whole value of the array... should be blocked by element custom object type
		component.putBrowserProperty("typesReject",
			new JSONObject("{\"vEr\":3,\"u\":[ {\"i\":'1', 'v': {\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}}}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"typesReject\":{\"vEr\":4,\"v\":[{\"vEr\":4,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":4,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}],\"svy_types\":{\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}},\"name\":\"test\"},\"svy_types\":{\"msg\":{\"typesReject\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to update by updating one value of the array... should be blocked by element custom object type
		component.putBrowserProperty("typesReject",
			new JSONObject("{\"vEr\":4,\"u\":[ {\"i\":'1', 'v': {\"vEr\":4,\"u\":[{ k: 'name', v: 'some_modified_text' }]}}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"typesReject\":{\"vEr\":5,\"v\":[{\"vEr\":5,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":5,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}],\"svy_types\":{\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}},\"name\":\"test\"},\"svy_types\":{\"msg\":{\"typesReject\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());
	}

	@Test
	public void shouldAllowSetValueWhenAllowSmartElements() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
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

		HashMap<String, Object> data = new HashMap<>();
		TypedData<Map<String, Object>> properties = component.getProperties();
		data.put("msg", properties.content);

		PropertyDescription messageTypes = AggregatedPropertyType.newAggregatedProperty();
		messageTypes.putProperty("msg", properties.contentType);

		String msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"name\":\"test\",\"types\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}],\"svy_types\":{\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}}},\"svy_types\":{\"msg\":{\"types\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to change whole thing... should not be blocked by array property
		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":2,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"name\":\"test\",\"types\":{\"vEr\":4,\"v\":[{\"vEr\":4,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":4,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}],\"svy_types\":{\"2\":\"JSON_obj\",\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}}},\"svy_types\":{\"msg\":{\"types\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to update by setting one whole value of the array...
		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":4,\"u\":[ {\"i\":'1', 'v': {\"vEr\":4,\"v\":{\"name\":\"myname100\",\"active\":true,\"foreground\":\"#00ff00\"}}}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"name\":\"test\",\"types\":{\"vEr\":5,\"v\":[{\"vEr\":5,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":6,\"v\":{\"name\":\"myname100\",\"active\":true,\"foreground\":\"#00ff00\"}},{\"vEr\":3,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}],\"svy_types\":{\"2\":\"JSON_obj\",\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}}},\"svy_types\":{\"msg\":{\"types\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to update by updating one value of the array...
		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":5,\"u\":[ {\"i\":'1', 'v': {\"vEr\":6,\"u\":[{ k: 'name', v: 'some_modified_text' }]}}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"name\":\"test\",\"types\":{\"vEr\":6,\"v\":[{\"vEr\":6,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":7,\"v\":{\"name\":\"some_modified_text\",\"active\":true,\"foreground\":\"#00ff00\"}},{\"vEr\":4,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}],\"svy_types\":{\"2\":\"JSON_obj\",\"1\":\"JSON_obj\",\"0\":\"JSON_obj\"}}},\"svy_types\":{\"msg\":{\"types\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());
	}

	@Test
	public void shouldNotAllowSetValueWhenRejectDumbElements() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		assertNull(component.getProperty("simpleArrayReject"));

		Object[] array = new Object[] { 1, 2, 3, 4 };
		assertTrue(component.setProperty("simpleArrayReject", array));

		ChangeAwareList<Object, Object> array2 = (ChangeAwareList)component.getProperty("simpleArrayReject"); // ChangeAwareList

		boolean same = true;
		int i = 0;
		for (Object o : array)
			same = same | (o != array2.get(i++));
		assertTrue(same);

		HashMap<String, Object> data = new HashMap<>();
		TypedData<Map<String, Object>> properties = component.getProperties();
		data.put("msg", properties.content);

		PropertyDescription messageTypes = AggregatedPropertyType.newAggregatedProperty();
		messageTypes.putProperty("msg", properties.contentType);

		String msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"name\":\"test\",\"simpleArrayReject\":{\"vEr\":2,\"v\":[1,2,3,4]}},\"svy_types\":{\"msg\":{\"simpleArrayReject\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to change whole thing... should be blocked by array property
		component.putBrowserProperty("simpleArrayReject", new JSONObject("{\"vEr\":2,\"v\":[ 5, 4, 3, 2 ,1]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"name\":\"test\",\"simpleArrayReject\":{\"vEr\":3,\"v\":[1,2,3,4]}},\"svy_types\":{\"msg\":{\"simpleArrayReject\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());

		// try to update by setting one whole value of the array... should be blocked by element custom object type
		component.putBrowserProperty("simpleArrayReject", new JSONObject("{\"vEr\":3,\"u\":[ {\"i\":'1', 'v': 15}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			new JSONObject(
				"{\"msg\":{\"name\":\"test\",\"simpleArrayReject\":{\"vEr\":4,\"v\":[1,2,3,4]}},\"svy_types\":{\"msg\":{\"simpleArrayReject\":\"JSON_arr\"}}}").toString(),
			new JSONObject(msg).toString());
	}

	@Test
	public void shouldAllowSetValueWhenAllowDumbElements() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		assertNull(component.getProperty("simpleArrayAllow"));

		// arrays are wrapped in a smart array that can wrap and convert/handle changes automatically
		Object[] array = new Object[] { 1, 2, 3, 4 };
		assertTrue(component.setProperty("simpleArrayAllow", array));

		ChangeAwareList<Object, Object> array2 = (ChangeAwareList)component.getProperty("simpleArrayAllow"); // ChangeAwareList

		boolean same = true;
		int i = 0;
		for (Object o : array)
			same = same | (o != array2.get(i++));
		assertTrue(same);

		HashMap<String, Object> data = new HashMap<>();
		TypedData<Map<String, Object>> properties = component.getProperties();
		data.put("msg", properties.content);

		PropertyDescription messageTypes = AggregatedPropertyType.newAggregatedProperty();
		messageTypes.putProperty("msg", properties.contentType);

		String msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			"{\"msg\":{\"simpleArrayAllow\":{\"vEr\":2,\"v\":[1,2,3,4]},\"name\":\"test\"},\"svy_types\":{\"msg\":{\"simpleArrayAllow\":\"JSON_arr\"}}}", msg);

		// try to change whole thing...
		component.putBrowserProperty("simpleArrayAllow", new JSONObject("{\"vEr\":2,\"v\":[ 5, 4, 3, 2, 1 ]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			"{\"msg\":{\"simpleArrayAllow\":{\"vEr\":4,\"v\":[5,4,3,2,1]},\"name\":\"test\"},\"svy_types\":{\"msg\":{\"simpleArrayAllow\":\"JSON_arr\"}}}",
			msg);

		// try to update by setting one whole value of the array...
		component.putBrowserProperty("simpleArrayAllow", new JSONObject("{\"vEr\":4,\"u\":[ {\"i\":'1', 'v': 150}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		data.put("msg", properties.content);
		msg = JSONUtils.writeDataWithConversions(data, messageTypes, allowDataConverterContext);
		assertEquals(
			"{\"msg\":{\"simpleArrayAllow\":{\"vEr\":5,\"v\":[5,150,3,2,1]},\"name\":\"test\"},\"svy_types\":{\"msg\":{\"simpleArrayAllow\":\"JSON_arr\"}}}",
			msg);
	}

}
