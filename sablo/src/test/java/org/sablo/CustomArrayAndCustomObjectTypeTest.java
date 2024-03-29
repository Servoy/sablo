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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

import java.awt.Color;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.ArrayOperation;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.ChangeAwareList;
import org.sablo.specification.property.ChangeAwareMap;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.ListTest;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.ChangesToJSONConverter;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * @author acostescu
 */
@SuppressWarnings("nls")
public class CustomArrayAndCustomObjectTypeTest extends Log4JToConsoleTest
{

	@BeforeClass
	public static void setUp() throws Exception
	{
		InputStream is = CustomArrayAndCustomObjectTypeTest.class.getResourceAsStream("WebComponentTest.manifest");
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		String manifest = new String(bytes);
		is.close();

		is = CustomArrayAndCustomObjectTypeTest.class.getResourceAsStream("WebComponentTest-mycomponent.spec");
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
			same = same && (o == array2.get(i++));
		assertFalse(same);

		TypedData<Map<String, Object>> properties = component.getProperties();

		String msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"typesReject\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}]},\"name\":\"test\"}"),
			new JSONObject(msg), true);

		// try to change whole thing... should be blocked by array property
		component.putBrowserProperty("typesReject",
			new JSONObject("{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":2,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"typesReject\":{\"vEr\":3,\"v\":[{\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":3,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}]},\"name\":\"test\"}"),
			new JSONObject(msg), true);

		// try to update by setting one whole value of the array... should be blocked by element custom object type
		component.putBrowserProperty("typesReject",
			new JSONObject("{\"vEr\":3,\"u\":[ {\"i\":'1', 'v': {\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}}}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"typesReject\":{\"vEr\":4,\"v\":[{\"vEr\":4,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":4,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}]},\"name\":\"test\"}"),
			new JSONObject(msg), true);

		// try to update by updating one value of the array... should be blocked by element custom object type
		component.putBrowserProperty("typesReject",
			new JSONObject("{\"vEr\":4,\"u\":[ {\"i\":'1', 'v': {\"vEr\":4,\"u\":[{ k: 'name', v: 'some_modified_text' }]}}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"typesReject\":{\"vEr\":5,\"v\":[{\"vEr\":5,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":5,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}],},\"name\":\"test\"}"),
			new JSONObject(msg), true);
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

		TypedData<Map<String, Object>> properties = component.getProperties();

		String msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"name\":\"test\",\"types\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}}]}}"),
			new JSONObject(msg), true);

		// try to change whole thing... should not be blocked by array property
		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":0,\"v\":[{\"vEr\":0,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":0,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}}," +
				"{\"vEr\":0,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"name\":\"test\",\"types\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}}"),
			new JSONObject(msg), true);

		// try to update by setting one whole value of the array...
		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":2,\"u\":[ {\"i\":'1', 'v': {\"vEr\":0,\"v\":{\"name\":\"myname100\",\"active\":true,\"foreground\":\"#00ff00\"}}}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"name\":\"test\",\"types\":{\"vEr\":3,\"v\":[{\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname100\",\"active\":true,\"foreground\":\"#00ff00\"}},{\"vEr\":3,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}}"),
			new JSONObject(msg), true);

		// try to update by updating one value of the array...
		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":3,\"u\":[ {\"i\":'1', 'v': {\"vEr\":2,\"u\":[{ k: 'name', v: 'some_modified_text' }]}}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"name\":\"test\",\"types\":{\"vEr\":4,\"v\":[{\"vEr\":4,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":3,\"v\":{\"name\":\"some_modified_text\",\"active\":true,\"foreground\":\"#00ff00\"}},{\"vEr\":4,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}}}"),
			new JSONObject(msg), true);
	}

	@Test
	public void shouldNotAllowSetValueWhenAllowSmartElementsButRejectOnChildren() throws Exception
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
		customType2.put("rejectedString", "aha");

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
			"{\"name\":\"test\",\"types\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"rejectedString\":\"aha\",\"active\":false,\"foreground\":\"#ffffff\"}}]}}"),
			new JSONObject(msg), true);

		// try to change whole thing... should not be blocked by array property,
		// but "rejectedString" sub-property should get blocked and object resent fully to client (so version 2)
		component.putBrowserProperty("types",
			new JSONObject(
				"{\"vEr\":0,\"v\":[{\"vEr\":0,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\",\"rejectedString\":\"or not\"}}," +
					"{\"vEr\":0,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}}," +
					"{\"vEr\":0,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}"));
		//	it should have changed all except "rejectedString" subproperty because only that is allowed by spec
		properties = component.getAndClearChanges();
		msg = JSONUtils.writeData(ChangesToJSONConverter.INSTANCE, properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"types\":{\"vEr\":1,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":1,\"u\":[{\"k\":\"rejectedString\",\"v\":null}]}]}]}}"),
			new JSONObject(msg), true);
		// also check new full value of properties
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"name\":\"test\",\"types\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}},{\"vEr\":2,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]}}"),
			new JSONObject(msg), true);

		// try to update by updating one "reject" value of the subprop...
		component.putBrowserProperty("types",
			new JSONObject("{\"vEr\":2,\"u\":[ {\"i\":'2', 'v': {\"vEr\":2,\"u\":[{ k: 'rejectedString', v: 'or not' }]}}]}"));
		//	it should NOT have changed because it's not allowed to by spec
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject(
			"{\"types\":{\"vEr\":3,\"v\":[{\"vEr\":3,\"v\":{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"}},{\"vEr\":3,\"v\":{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"}},{\"vEr\":3,\"v\":{\"name\":\"myname3\",\"active\":true,\"foreground\":null}}]},\"name\":\"test\"}"),
			new JSONObject(msg), true);
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

		TypedData<Map<String, Object>> properties = component.getProperties();

		String msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"name\":\"test\",\"simpleArrayReject\":{\"vEr\":2,\"v\":[1,2,3,4]}}"),
			new JSONObject(msg), true);

		// try to change whole thing... should be blocked by array property
		component.putBrowserProperty("simpleArrayReject", new JSONObject("{\"vEr\":2,\"v\":[ 5, 4, 3, 2 ,1]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"name\":\"test\",\"simpleArrayReject\":{\"vEr\":3,\"v\":[1,2,3,4]}}"),
			new JSONObject(msg), true);

		// try to update by setting one whole value of the array... should be blocked by element custom object type
		component.putBrowserProperty("simpleArrayReject", new JSONObject("{\"vEr\":3,\"u\":[ {\"i\":'1', 'v': 15}]}"));
		//	it shouldn't have changed because it's not allowed to by spec; so check that it's the same as before
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"name\":\"test\",\"simpleArrayReject\":{\"vEr\":4,\"v\":[1,2,3,4]}}"),
			new JSONObject(msg), true);
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

		TypedData<Map<String, Object>> properties = component.getProperties();

		String msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"simpleArrayAllow\":{\"vEr\":2,\"v\":[1,2,3,4]},\"name\":\"test\"}"), new JSONObject(msg), true);

		// try to change whole thing...
		component.putBrowserProperty("simpleArrayAllow", new JSONObject("{\"vEr\":0,\"v\":[ 5, 4, 3, 2, 1 ]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"simpleArrayAllow\":{\"vEr\":2,\"v\":[5,4,3,2,1]},\"name\":\"test\"}"), new JSONObject(msg), true);

		// try to update by setting one subvalue in the array...
		component.putBrowserProperty("simpleArrayAllow", new JSONObject("{\"vEr\":2,\"u\":[ {\"i\":'1', 'v': 150}]}"));
		//	it should have changed because it's allowed to by spec
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"simpleArrayAllow\":{\"vEr\":3,\"v\":[5,150,3,2,1]},\"name\":\"test\"}"), new JSONObject(msg), true);
	}

	@Test
	public void shouldAllowSetValueWhenRejectDumbElementsOnElementConfig() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		assertNull(component.getProperty("simpleArrayElReject"));

		// arrays are wrapped in a smart array that can wrap and convert/handle changes automatically
		Object[] array = new Object[] { 1, 2, 3, 4 };
		assertTrue(component.setProperty("simpleArrayElReject", array));

		ChangeAwareList<Object, Object> array2 = (ChangeAwareList)component.getProperty("simpleArrayElReject"); // ChangeAwareList

		boolean same = true;
		int i = 0;
		for (Object o : array)
			same = same | (o != array2.get(i++));
		assertTrue(same);

		TypedData<Map<String, Object>> properties = component.getProperties();

		String msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"simpleArrayElReject\":{\"vEr\":2,\"v\":[1,2,3,4]},\"name\":\"test\"}"), new JSONObject(msg), true);

		// try to change whole thing... it is allowed, but elements do not allow it...
		component.putBrowserProperty("simpleArrayElReject", new JSONObject("{\"vEr\":2,\"v\":[ 5, 4, 3, 2, 1 ]}"));
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		// denied because only elements have allow, not whole thing
		assertEquals(new JSONObject("{\"simpleArrayElReject\":{\"vEr\":3,\"v\":[1,2,3,4]},\"name\":\"test\"}"), new JSONObject(msg), true);

		// try to update by setting one subvalue in the array...
		component.putBrowserProperty("simpleArrayElReject", new JSONObject("{\"vEr\":3,\"u\":[ {\"i\":'1', 'v': 150}]}"));
		//	it should not have changed because it's not allowed to by spec
		properties = component.getProperties();
		msg = JSONUtils.writeDataAsFullToJSON(properties.content, properties.contentType, allowDataConverterContext);
		assertEquals(new JSONObject("{\"simpleArrayElReject\":{\"vEr\":4,\"v\":[1,2,3,4]},\"name\":\"test\"}"), new JSONObject(msg), true);
	}

	@Test
	public void shouldHandleDifferentlyChangeByRefAndChangeOfContentInElements() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		assertNull(component.getProperty("types"));

		// arrays are wrapped in a smart array that can wrap and convert/handle changes automatically
		Object[] array = new Object[0];
		assertTrue(component.setProperty("types", array));

		ChangeAwareList<Object, Object> typesArray = (ChangeAwareList)component.getProperty("types"); // ChangeAwareList

		// test array element reference change on component (should sent whole value, not a NO-OP)
		JSONAssert.assertEquals("{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"v\":[]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		// test content changed of 1 property
		Map<Object, Object> map = new HashMap<>();
		map.put("name", "firstMyType");

		typesArray.add(map);
		map.put("name", "firstMyType1"); // changing it should still send full value of the map - as a granular add of the array
		JSONAssert.assertEquals("{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,1],\"d\":[{\"vEr\":2,\"v\":{\"name\":\"firstMyType\"}}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		// set a whole new obj/map into an existing index of the array - that map should be sent fully as an update of the array, instead of NO-OP
		map = new HashMap<>();
		map.put("name", "hmm1");
		typesArray.set(0, map);

		JSONAssert.assertEquals("{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":4,\"v\":{\"name\":\"hmm1\"}}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);


		// only a change in the array and a change in the map should get sent
		map = (Map<Object, Object>)typesArray.get(0);
		map.put("text", "txthmm1");

		JSONAssert.assertEquals(
			"{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":4,\"u\":[{\"k\":\"text\",\"v\":\"txthmm1\"}]}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		// set an array by ref - updates of that full sub-array value should be sent
		map.put("subtypearray", new Object[0]);
		JSONAssert.assertEquals(
			"{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":4,\"u\":[{\"k\":\"subtypearray\",\"v\":{\"vEr\":2,\"v\":[]}}]}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		// once more to also test if it was not null previously
		map.put("subtypearray", new Object[0]);
		JSONAssert.assertEquals(
			"{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":4,\"u\":[{\"k\":\"subtypearray\",\"v\":{\"vEr\":4,\"v\":[]}}]}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		// now add something to that sub-array and make a granular change to it - to test the parent object will send only an update
		List<Map<String, Object>> list = (List<Map<String, Object>>)map.get("subtypearray");
		list.add(new HashMap<String, Object>());
		JSONAssert.assertEquals(
			"{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":4,\"u\":[{\"k\":\"subtypearray\",\"v\":{\"vEr\":4,\"g\":[{\"op\":[0,0,1],\"d\":[{\"vEr\":2,\"v\":{}}]}]}}]}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		list.get(0).put("caption", "captionhmm1");
		JSONAssert.assertEquals(
			"{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":4,\"u\":[{\"k\":\"subtypearray\",\"v\":{\"vEr\":4,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":2,\"u\":[{\"k\":\"caption\",\"v\":\"captionhmm1\"}]}]}]}}]}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		// set another property; see that only that granular update gets sent
		list.get(0).put("in_date", new Date(12345));
		JSONAssert.assertEquals(
			"{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":4,\"u\":[{\"k\":\"subtypearray\",\"v\":{\"vEr\":4,\"g\":[{\"op\":[0,0,0],\"d\":[{\"vEr\":2,\"u\":[{\"k\":\"in_date\",\"v\":12345}]}]}]}}]}]}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);
	}

	@Test
	public void shouldGivePrioToChangeByRefOnComponent() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		assertNull(component.getProperty("types"));

		// arrays are wrapped in a smart array that can wrap and convert/handle changes automatically
		Object[] array = new Object[0];
		assertTrue(component.setProperty("types", array));

		ChangeAwareList<Object, Object> typesArray = (ChangeAwareList)component.getProperty("types"); // ChangeAwareList

		// so now we have a ref change already for types; do add an element to notify a content change for types as well; ref
		// change should be leading still because the full value was not yet sent and has to be sent no matter if there were changes afterwards
		Map<Object, Object> map = new HashMap<>();
		map.put("name", "firstMyType");
		typesArray.add(map);

		// test array element reference change on component (should sent whole value, not a NO-OP or an update)
		JSONAssert.assertEquals("{\"comp\":{\"test\":{\"types\":{\"vEr\":2,\"v\":[{\"vEr\":2,\"v\":{\"name\":\"firstMyType\"}}]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);

		// now change by content and afterwards by ref; should still send only full prop. value
		typesArray.remove(0);
		component.setProperty("types", new Object[0]);
		JSONAssert.assertEquals("{\"comp\":{\"test\":{\"types\":{\"vEr\":4,\"v\":[]}}}}",
			JSONUtils.writeComponentChanges(component, ChangesToJSONConverter.INSTANCE), JSONCompareMode.NON_EXTENSIBLE);
	}

	@Test
	public void shouldOnlyAttachDetachAsNeededAndKeepIndexesUpToDateInChangeHandlersWhenDoingArraySplice() throws Exception
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		BrowserConverterContext allowDataConverterContext = new BrowserConverterContext(component, PushToServerEnum.allow);
		assertNull(component.getProperty("typesReject"));

		// arrays are wrapped in a smart array that can wrap and convert/handle changes automatically
		Object[] array = new Object[0];
		assertTrue(component.setProperty("typesReject", array));

		ChangeAwareList<Object, Object> typesArray = (ChangeAwareList)component.getProperty("typesReject"); // ChangeAwareList
		PropertyDescription elementMapPD = ((CustomJSONArrayType)component.getPropertyDescription("typesReject").getType()).getCustomJSONTypeDefinition();

		// just add some elements that won't be affected by splice
		Map<String, Object> map = new HashMap<>();
		map.put("name", "zero");
		typesArray.add(map);

		map = new HashMap<>();
		map.put("name", "one");
		typesArray.add(map);

		map = new HashMap<>();
		map.put("name", "two");
		TestChangeAwareMap< ? , ? > testMap2 = new TestChangeAwareMap<>(map, elementMapPD);
		assertEquals(0, testMap2.attachCalls);
		assertEquals(0, testMap2.detachCalls);
		typesArray.add(testMap2);
		assertEquals(1, testMap2.attachCalls);
		testMap2.attachCalls = 0;
		assertEquals(0, testMap2.detachCalls);

		map = new HashMap<>();
		map.put("name", "three");
		TestChangeAwareMap< ? , ? > testMap3 = new TestChangeAwareMap<>(map, elementMapPD);
		assertEquals(0, testMap3.attachCalls);
		assertEquals(0, testMap3.detachCalls);
		typesArray.add(testMap3);
		assertEquals(1, testMap3.attachCalls);
		testMap3.attachCalls = 0;
		assertEquals(0, testMap3.detachCalls);

		map = new HashMap<>();
		map.put("name", "four");
		TestChangeAwareMap<Object, ? > testMap4 = new TestChangeAwareMap<>(map, elementMapPD);
		assertEquals(0, testMap4.attachCalls);
		assertEquals(0, testMap4.detachCalls);
		typesArray.add(testMap4);
		assertEquals(1, testMap4.attachCalls);
		testMap4.attachCalls = 0;
		assertEquals(0, testMap4.detachCalls);

		// ok now simulate a JS splice operation that removes "two", see that it gets detached, but the others don't get detached
		typesArray.set(2, typesArray.get(3));
		typesArray.set(3, typesArray.get(4));
		typesArray.remove(4);
		ChangeAwareList<Object, Object>.Changes ch = typesArray.getChangesImmutableAndPrepareForReset();

		ArrayOperation[] opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertFalse(ch.mustSendAll());
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertEquals(1, opSeq.length);
		ListTest.assertGranularOpIs(0, 3, ArrayOperation.INSERT, null, opSeq[0]);
		ch.doneHandling();

		assertEquals(0, testMap2.attachCalls);
		assertEquals(1, testMap2.detachCalls);

		assertEquals(0, testMap3.attachCalls);
		assertEquals(0, testMap3.detachCalls);

		assertEquals(0, testMap4.attachCalls);
		assertEquals(0, testMap4.detachCalls);

		// ok, now if we alter what used to be at idx 4 (it is now at idx 3) see that the CAL reports correctly that idx 3 has changed
		testMap4.put("text", "I changed");
		assertTrue(!ch.mustSendAll());
		opSeq = ch.getGranularUpdatesKeeper().getEquivalentSequenceOfOperations();
		assertEquals(1, opSeq.length);
		ListTest.assertGranularOpIs(3, 3, ArrayOperation.CHANGE, ChangeAwareList.GRANULAR_UPDATE_OP, opSeq[0]);
		ch.doneHandling();
	}

	private class TestChangeAwareMap<ET, WT> extends ChangeAwareMap<ET, WT>
	{
		public TestChangeAwareMap(Map<String, ET> baseMap, PropertyDescription customObjectPD)
		{
			super(baseMap, null, customObjectPD);
		}

		private int attachCalls = 0;
		private int detachCalls = 0;

		@Override
		public void attachToBaseObject(IChangeListener changeMntr, IWebObjectContext webObjectCntxt)
		{
			attachCalls++;
			super.attachToBaseObject(changeMntr, webObjectCntxt);
		}

		@Override
		public void detach()
		{
			detachCalls++;
			super.detach();
		}

	};

}
