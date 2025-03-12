/*
 * Copyright (C) 2015 Servoy BV
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
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.junit.After;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.property.types.VisibleSabloValue;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;

/**
 * Test invisible data, should not be sent to client.
 *
 * @author rgansevles
 *
 */
@SuppressWarnings("nls")
public class WebComponentSecurityDataTest
{
	private static final String TESTCOMPONENT_SPEC = "testcomponent.spec";

	private static final String MANIFEST = "Manifest-Version: 1.0" + //
		"\n" + //
		"\nName: " + TESTCOMPONENT_SPEC + //
		"\nWeb-Component: True" + //
		"\n";

	@After
	public void disposeSpecs()
	{
		WebComponentSpecProvider.disposeInstance();
	}

	/*
	 * Test data blocked on invisible component.
	 */
	@Test
	public void testWritePropertiesWhenInvisible() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"astr\": \"string\"" + //
			"\n  ,\"abool\": \"boolean\"" + //
			"\n  ,\"aint\": \"int\"" + //
			"\n  ,\"vis\": \"visible\"" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) }, null);

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		JSONObject propsForClient = writeComponentProperties(testcomponent);

		// initial props, just the name
		assertEquals("test", propsForClient.get("name"));
		assertEquals(1, iteratorSize(propsForClient.keys()));

		// another property is set
		testcomponent.setProperty("abool", Boolean.TRUE);

		propsForClient = writeComponentProperties(testcomponent);
		assertEquals("test", propsForClient.get("name"));
		assertEquals(Boolean.TRUE, propsForClient.get("abool"));
		assertEquals(2, iteratorSize(propsForClient.keys()));

		// component is made invisible, only visibilityprop is shown
		testcomponent.setVisible(false);

		propsForClient = writeComponentProperties(testcomponent);
		assertEquals(Boolean.FALSE, propsForClient.get("vis"));
		assertEquals(1, iteratorSize(propsForClient.keys()));

		// set another property, not shown yet
		testcomponent.setProperty("aint", new Integer(42));

		propsForClient = writeComponentProperties(testcomponent);
		assertEquals(Boolean.FALSE, propsForClient.get("vis"));
		assertEquals(1, iteratorSize(propsForClient.keys()));

		// visible again, show all set properties
		testcomponent.setVisible(true);
		propsForClient = writeComponentProperties(testcomponent);
		assertEquals("test", propsForClient.get("name"));
		assertEquals(Boolean.TRUE, propsForClient.get("abool"));
		assertEquals(Boolean.TRUE, propsForClient.get("vis"));
		assertEquals(new Integer(42), propsForClient.get("aint"));
		assertEquals(4, iteratorSize(propsForClient.keys()));
	}

	/*
	 * Test that initial properties are saved as changes when component was initially invisible.
	 */
	@Test
	public void testWritePropertiesWhenInvisibleShowUpAsChanges() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"astr\": \"string\"" + //
			"\n  ,\"abool\": \"boolean\"" + //
			"\n  ,\"aint\": \"int\"" + //
			"\n  ,\"vis\": \"visible\"" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) }, null);

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		// setup: set some initial properties
		testcomponent.setProperty("abool", Boolean.TRUE);
		testcomponent.setProperty("astr", "fourty-two");

		// clear changes all changes
		testcomponent.getAndClearChanges();

		// set invisible
		testcomponent.setVisible(false);

		// clear the last visibility change
		testcomponent.getAndClearChanges();

		// setup done, now generate initial props for invisible component
		JSONObject propsForClient = writeComponentProperties(testcomponent);

		// initial props, just the visibility props
		assertEquals(Boolean.FALSE, propsForClient.get("vis"));
		assertEquals(1, iteratorSize(propsForClient.keys()));

		// no changes
		assertFalse(testcomponent.hasChanges());
		Map<String, Object> changes = testcomponent.getAndClearChanges().content;
		assertEquals(0, changes.size());

		// visible again, show all set properties
		testcomponent.setVisible(true);

		assertTrue(testcomponent.hasChanges());

		changes = testcomponent.getAndClearChanges().content;
		assertEquals(Boolean.TRUE, ((VisibleSabloValue)changes.get("vis")).getValue());
		assertEquals(Boolean.TRUE, changes.get("abool"));
		assertEquals("fourty-two", changes.get("astr"));
		assertEquals("test", changes.get("name"));
		assertEquals(4, changes.size());

		// changes are cleared
		assertFalse(testcomponent.hasChanges());
		changes = testcomponent.getAndClearChanges().content;
		assertEquals(0, changes.size());
	}

	/*
	 * Test data blocked on invisible component.
	 */
	@Test
	public void testWritePropertiesChangesWhenInvisible() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"astr\": \"string\"" + //
			"\n  ,\"abool\": \"boolean\"" + //
			"\n  ,\"aint\": \"int\"" + //
			"\n  ,\"vis\": \"visible\"" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) }, null);

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		Map<String, Object> changes = testcomponent.getAndClearChanges().content;

		// initially no changes
		assertEquals(0, changes.size());

		// a property is set
		testcomponent.setProperty("abool", Boolean.TRUE);

		changes = testcomponent.getAndClearChanges().content;
		assertEquals(Boolean.TRUE, changes.get("abool"));
		assertEquals(1, changes.size());

		// component is made invisible, only visibilityprop is shown

		testcomponent.setProperty("astr", "hello");
		testcomponent.setVisible(false);
		testcomponent.setProperty("aint", new Integer(42));

		changes = testcomponent.getAndClearChanges().content;
		assertEquals(Boolean.FALSE, ((VisibleSabloValue)changes.get("vis")).getValue());
		assertEquals(1, changes.size());

		// set another property, no changes when component still invisible
		testcomponent.setProperty("aint", new Integer(-42));

		changes = testcomponent.getAndClearChanges().content;
		assertEquals(0, changes.size());

		// make component visible, previous changes are now shown
		testcomponent.setVisible(true);

		changes = testcomponent.getAndClearChanges().content;
		assertEquals(Boolean.TRUE, ((VisibleSabloValue)changes.get("vis")).getValue());
		assertEquals("hello", changes.get("astr"));
		assertEquals(new Integer(-42), changes.get("aint"));
		assertEquals(3, changes.size());
	}

	private static int iteratorSize(Iterator< ? > it)
	{
		int n = 0;
		while (it.hasNext())
		{
			it.next();
			n++;
		}

		return n;
	}

	private static JSONObject writeComponentProperties(WebComponent wc) throws Exception
	{
		JSONWriter writer = new JSONStringer().object();
		wc.writeComponentProperties(writer, FullValueToJSONConverter.INSTANCE, "props");
		JSONObject json = new JSONObject(writer.endObject().toString());
		return json.getJSONObject("props");
	}

}
