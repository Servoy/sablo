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
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.property.types.DimensionPropertyType;
import org.sablo.specification.property.types.EnabledPropertyType;
import org.sablo.specification.property.types.ProtectedConfig;
import org.sablo.specification.property.types.ProtectedPropertyType;
import org.sablo.specification.property.types.VisiblePropertyType;

/**
 * Test protected and visibility properties.
 *
 * @author rgansevles
 *
 */
@SuppressWarnings("nls")
public class WebComponentSecurityPropertiesTest
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

	@Test
	public void testComponentWithoutVisibilityProperty()
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"abool\": \"boolean\"" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");
		assertTrue(testcomponent.isVisible());

		testcomponent.setVisible(false);
		// setting visible false has no effect if there is no visibility property
		assertTrue(testcomponent.isVisible());
	}

	/*
	 * Test visibility with defaults: (value: true, blockingOn: false)
	 */
	@Test
	public void testComponentVisibileWithDefaults() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"astr\": { \"type\": \"string\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"vis\": \"visible\"" + //
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		doTestComponentVisibileWithDefaults(testcomponentspec);
	}

	/*
	 * Test visibility with defaults: (value: true, blockingOn: false)
	 */
	@Test
	public void testComponentVisibileWithDefaults2() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"astr\": { \"type\": \"string\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"vis\": { \"type\": \"visible\" }" + // slightly different spec from previous test, still uses defaults
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		doTestComponentVisibileWithDefaults(testcomponentspec);
	}

	private void doTestComponentVisibileWithDefaults(String testcomponentspec) throws Exception
	{
		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		final AtomicInteger called = new AtomicInteger(0);

		testcomponent.addEventHandler("callme", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called.addAndGet(1);
				return null;
			}
		});

		// default visible
		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));

		// call function
		assertEquals(0, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(1, called.intValue());

		// set via setVisible
		testcomponent.setVisible(false);
		assertFalse(testcomponent.isVisible());
		assertEquals(Boolean.FALSE, testcomponent.getProperty("vis"));

		testcomponent.setVisible(true);
		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));

		// set via property
		testcomponent = new WebComponent("testcomponent", "test");
		testcomponent.setProperty("vis", Boolean.FALSE);
		assertFalse(testcomponent.isVisible());
		assertEquals(Boolean.FALSE, testcomponent.getProperty("vis"));

		testcomponent.setProperty("vis", Boolean.TRUE);
		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));

		// cannot set via putBrowserProperty
		testcomponent = new WebComponent("testcomponent", "test");
		try
		{
			testcomponent.putBrowserProperty("vis", Boolean.FALSE);
			fail("visibility property is not protected!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("vis", e.getBlockedProperty());
		}

		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));

		// check protection of properties when component is invisible
		testcomponent = new WebComponent("testcomponent", "test");
		testcomponent.addEventHandler("callme", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called.addAndGet(1);
				return null;
			}
		});

		testcomponent.putBrowserProperty("astr", "aap");
		assertEquals("aap", testcomponent.getProperty("astr"));

		testcomponent.setVisible(false);

		try
		{
			testcomponent.putBrowserProperty("astr", "noot");
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("astr", e.getBlockedProperty());
		}

		assertEquals("aap", testcomponent.getProperty("astr"));

		// cannot call function of invisible component
		assertEquals(1, called.intValue());
		try
		{
			testcomponent.executeEvent("callme", null);
			fail("can call function on invisible component from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("callme", e.getBlockedProperty());
		}
		assertEquals(1, called.intValue());


		testcomponent.setVisible(true);

		testcomponent.putBrowserProperty("astr", "mies");
		assertEquals("mies", testcomponent.getProperty("astr"));

		// call function
		assertEquals(1, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(2, called.intValue());
	}

	@Test
	public void testComponentVisibileWithFor() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"astr1\": { \"type\": \"string\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"astr2\": { \"type\": \"string\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"vis\": \"visible\"" + //
			"\n  ,\"visforastr2\": { \"type\": \"visible\", \"for\": \"astr2\" }" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		// default visible
		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));
		assertEquals(Boolean.TRUE, testcomponent.getProperty("visforastr2"));

		// set via setVisible
		testcomponent.setVisible(false);
		assertFalse(testcomponent.isVisible());
		assertEquals(Boolean.FALSE, testcomponent.getProperty("vis"));
		assertEquals(Boolean.TRUE, testcomponent.getProperty("visforastr2")); // not set because it is not a component-global prop (for is defined)

		testcomponent.setVisible(true);
		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));
		assertEquals(Boolean.TRUE, testcomponent.getProperty("visforastr2"));

		// set via property
		testcomponent = new WebComponent("testcomponent", "test");

		testcomponent.setProperty("visforastr2", Boolean.FALSE);
		assertTrue(testcomponent.isVisible()); // does not affect global visibility

		testcomponent.setProperty("vis", Boolean.FALSE);
		assertFalse(testcomponent.isVisible());
		assertEquals(Boolean.FALSE, testcomponent.getProperty("vis"));

		testcomponent.setProperty("vis", Boolean.TRUE);
		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));

		// check protection of selective visibility fields
		testcomponent = new WebComponent("testcomponent", "test");
		try
		{
			testcomponent.putBrowserProperty("visforastr2", Boolean.FALSE);
			fail("visibility property is not protected!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("visforastr2", e.getBlockedProperty());
		}

		testcomponent.putBrowserProperty("astr1", "the");
		testcomponent.putBrowserProperty("astr2", "quick");

		assertEquals("the", testcomponent.getProperty("astr1"));
		assertEquals("quick", testcomponent.getProperty("astr2"));

		// astr2 becomes invisible
		testcomponent.setProperty("visforastr2", Boolean.FALSE);

		testcomponent.putBrowserProperty("astr1", "brown");
		try
		{
			testcomponent.putBrowserProperty("astr2", "fox");
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("astr2", e.getBlockedProperty());
		}

		assertEquals("brown", testcomponent.getProperty("astr1")); // modified
		assertEquals("quick", testcomponent.getProperty("astr2")); // not modified

		testcomponent.setVisible(true); // does not affect subprops

		testcomponent.putBrowserProperty("astr1", "jumps");

		try
		{
			testcomponent.putBrowserProperty("astr2", "over");
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("astr2", e.getBlockedProperty());
		}

		assertEquals("jumps", testcomponent.getProperty("astr1")); // not modified
		assertEquals("quick", testcomponent.getProperty("astr2")); // modified

		// astr2 becomes visible again
		testcomponent.setProperty("visforastr2", Boolean.TRUE);

		testcomponent.putBrowserProperty("astr1", "lazy");
		testcomponent.putBrowserProperty("astr2", "dog");

		assertEquals("lazy", testcomponent.getProperty("astr1")); // modified
		assertEquals("dog", testcomponent.getProperty("astr2")); // modified
	}

	/*
	 * Test protected property with defaults: (value: false, blockingOn: true)
	 */
	@Test
	public void testComponentProtectedPropertyDefaults() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint\": { \"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"prot\": \"protected\"" + //
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		doTestComponentProtectedPropertyDefaults(testcomponentspec);
	}

	/*
	 * Test protected property with defaults: (value: false, blockingOn: true)
	 */
	@Test
	public void testComponentProtectedPropertyDefaults2() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint\": { \"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"prot\": { \"type\": \"protected\" }" + // slightly different spec from previous test, still uses defaults
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		doTestComponentProtectedPropertyDefaults(testcomponentspec);
	}

	private void doTestComponentProtectedPropertyDefaults(String testcomponentspec) throws Exception
	{
		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		// default false
		assertEquals(Boolean.FALSE, testcomponent.getProperty("prot"));

		testcomponent.setProperty("prot", Boolean.TRUE);
		assertEquals(Boolean.TRUE, testcomponent.getProperty("prot"));

		testcomponent.setProperty("prot", Boolean.FALSE);
		assertEquals(Boolean.FALSE, testcomponent.getProperty("prot"));

		// cannot set via putBrowserProperty
		testcomponent = new WebComponent("testcomponent", "test");
		try
		{
			testcomponent.putBrowserProperty("prot", Boolean.TRUE);
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("prot", e.getBlockedProperty());
		}

		assertEquals(Boolean.FALSE, testcomponent.getProperty("prot"));

		// check protection, blocking on true
		testcomponent = new WebComponent("testcomponent", "test");

		testcomponent.putBrowserProperty("aint", new Integer(1));
		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		testcomponent.setProperty("prot", Boolean.TRUE);

		try
		{
			testcomponent.putBrowserProperty("aint", new Integer(2));
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("aint", e.getBlockedProperty());
		}

		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		testcomponent.setProperty("aint", new Integer(3));
		assertEquals(new Integer(3), testcomponent.getProperty("aint"));

		testcomponent.setProperty("prot", Boolean.FALSE);
		testcomponent.putBrowserProperty("aint", new Integer(4));
		assertEquals(new Integer(4), testcomponent.getProperty("aint"));
	}

	/*
	 * Test protected property with specified behaviour: (value: true, blockingOn: false)
	 */
	@Test
	public void testComponentProtectedPropertyDefTrueBlockFalse() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint\": { \"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"prot\": { \"type\": \"protected\", \"default\": true, \"blockingOn\": false  }" + //
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		// default true
		assertEquals(Boolean.TRUE, testcomponent.getProperty("prot"));

		testcomponent.setProperty("prot", Boolean.FALSE);
		assertEquals(Boolean.FALSE, testcomponent.getProperty("prot"));

		testcomponent.setProperty("prot", Boolean.TRUE);
		assertEquals(Boolean.TRUE, testcomponent.getProperty("prot"));

		// cannot set via putBrowserProperty
		testcomponent = new WebComponent("testcomponent", "test");

		final AtomicInteger called = new AtomicInteger(0);

		testcomponent.addEventHandler("callme", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called.addAndGet(1);
				return null;
			}
		});

		try
		{
			testcomponent.putBrowserProperty("prot", Boolean.FALSE);
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("prot", e.getBlockedProperty());
		}

		// call function
		assertEquals(0, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(1, called.intValue());


		assertEquals(Boolean.TRUE, testcomponent.getProperty("prot"));

		// check protection, blocking on false
		testcomponent = new WebComponent("testcomponent", "test");
		testcomponent.addEventHandler("callme", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called.addAndGet(1);
				return null;
			}
		});


		testcomponent.putBrowserProperty("aint", new Integer(1));
		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		testcomponent.setProperty("prot", Boolean.FALSE);

		try
		{
			testcomponent.putBrowserProperty("aint", new Integer(2));
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("aint", e.getBlockedProperty());
		}

		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		testcomponent.setProperty("aint", new Integer(3));
		assertEquals(new Integer(3), testcomponent.getProperty("aint"));

		// cannot call function on protected element
		assertEquals(1, called.intValue());
		try
		{
			testcomponent.executeEvent("callme", null);
			fail("can call function on invisible component from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("callme", e.getBlockedProperty());
		}
		assertEquals(1, called.intValue());

		// unprotect
		testcomponent.setProperty("prot", Boolean.TRUE);
		testcomponent.putBrowserProperty("aint", new Integer(4));
		assertEquals(new Integer(4), testcomponent.getProperty("aint"));

		// call function
		assertEquals(1, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(2, called.intValue());
	}

	/*
	 * Test protected property for specific fields
	 */
	@Test
	public void testComponentProtectedPropertyWithFor() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint1\": { \"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"aint2\": { \"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"aint3\": { \"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"prot\": { \"type\": \"protected\", \"default\": true, \"blockingOn\": false, \"for\": [\"aint1\", \"aint3\"] }" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		// default true and not blocking
		testcomponent.putBrowserProperty("aint1", new Integer(11));
		testcomponent.putBrowserProperty("aint2", new Integer(21));
		testcomponent.putBrowserProperty("aint3", new Integer(31));

		// check protection, blocking on false, should protect only aint1 and aint3
		testcomponent.setProperty("prot", Boolean.FALSE);

		try
		{
			testcomponent.putBrowserProperty("aint1", new Integer(12));
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("aint1", e.getBlockedProperty());
		}

		// can still set other properties
		testcomponent.putBrowserProperty("aint2", new Integer(22));

		try
		{
			testcomponent.putBrowserProperty("aint3", new Integer(32));
			fail("can set protected property from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("aint3", e.getBlockedProperty());
		}

		assertEquals(new Integer(11), testcomponent.getProperty("aint1")); // not modified
		assertEquals(new Integer(22), testcomponent.getProperty("aint2")); // modified
		assertEquals(new Integer(31), testcomponent.getProperty("aint3")); // not modified

		// unprotect
		testcomponent.setProperty("prot", Boolean.TRUE);

		testcomponent.putBrowserProperty("aint1", new Integer(13));
		testcomponent.putBrowserProperty("aint2", new Integer(23));
		testcomponent.putBrowserProperty("aint3", new Integer(33));

		assertEquals(new Integer(13), testcomponent.getProperty("aint1"));
		assertEquals(new Integer(23), testcomponent.getProperty("aint2"));
		assertEquals(new Integer(33), testcomponent.getProperty("aint3"));
	}

	/*
	 * Test protected property for specific handler
	 */
	@Test
	public void testProtectedFunction() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint\": \"int\"" + //
			"\n  ,\"protcallme1\": { \"type\": \"protected\", \"for\": \"callme1\" }" + //
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme1\": \"function\"" + //
			"\n  ,\"callme2\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		doTestProtectedFunction(testcomponentspec, true);
	}

	@Test
	public void testInvisibleFunction() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint\": \"int\"" + //
			"\n  ,\"protcallme1\": { \"type\": \"visible\", \"for\": \"callme1\" }" + //
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme1\": \"function\"" + //
			"\n  ,\"callme2\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		doTestProtectedFunction(testcomponentspec, false);
	}

	private void doTestProtectedFunction(String testcomponentspec, boolean blockingOn) throws Exception
	{
		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		final AtomicInteger called1 = new AtomicInteger(0);
		testcomponent.addEventHandler("callme1", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called1.addAndGet(1);
				return null;
			}
		});

		final AtomicInteger called2 = new AtomicInteger(0);
		testcomponent.addEventHandler("callme2", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called2.addAndGet(1);
				return null;
			}
		});

		// call function
		assertEquals(0, called1.intValue());
		testcomponent.executeEvent("callme1", null);
		assertEquals(1, called1.intValue());

		assertEquals(0, called2.intValue());
		testcomponent.executeEvent("callme2", null);
		assertEquals(1, called2.intValue());

		// protect the method
		testcomponent.setProperty("protcallme1", Boolean.valueOf(blockingOn));

		// call function
		assertEquals(1, called1.intValue());
		try
		{
			testcomponent.executeEvent("callme1", null);
			fail("can call protected function on from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("callme1", e.getBlockedProperty());
		}
		assertEquals(1, called1.intValue());

		assertEquals(1, called2.intValue());
		testcomponent.executeEvent("callme2", null);
		assertEquals(2, called2.intValue());

		// unprotect the method
		testcomponent.setProperty("protcallme1", Boolean.valueOf(!blockingOn));

		// call function
		assertEquals(1, called1.intValue());
		testcomponent.executeEvent("callme1", null);
		assertEquals(2, called1.intValue());

		assertEquals(2, called2.intValue());
		testcomponent.executeEvent("callme2", null);
		assertEquals(3, called2.intValue());
	}

	@Test
	public void testProtectedContainer() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint\": { \"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebObjectSpecification formSpec = new WebObjectSpecification("form_spec", "", IPackageReader.WEB_COMPONENT, "", null, null, null, "", null);
		formSpec.putProperty("size", new PropertyDescription("size", DimensionPropertyType.INSTANCE));
		formSpec.putProperty("prot", new PropertyDescription("prot", ProtectedPropertyType.INSTANCE, ProtectedConfig.DEFAULTBLOCKING_TRUE));

		Container form = new Container("form", formSpec)
		{
		};

		WebComponent testcomponent = new WebComponent("testcomponent", "test");
		form.add(testcomponent);

		final AtomicInteger called = new AtomicInteger(0);
		testcomponent.addEventHandler("callme", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called.addAndGet(1);
				return null;
			}
		});

		// call function
		assertEquals(0, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(1, called.intValue());

		// set a property
		testcomponent.putBrowserProperty("aint", new Integer(1));
		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		// protect form
		form.setProperty("prot", Boolean.TRUE);

		// call function
		assertEquals(1, called.intValue());
		try
		{
			testcomponent.executeEvent("callme", null);
			fail("can call function on invisble form on from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("callme", e.getBlockedProperty());
		}
		assertEquals(1, called.intValue());

		// set a property
		try
		{
			testcomponent.putBrowserProperty("aint", new Integer(11));
			fail("can set property on invisble form on from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("aint", e.getBlockedProperty());
		}
		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		// make form visible again
		form.setProperty("prot", Boolean.FALSE);

		// call function
		assertEquals(1, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(2, called.intValue());

		// set a property
		testcomponent.putBrowserProperty("aint", new Integer(111));
		assertEquals(new Integer(111), testcomponent.getProperty("aint"));
	}

	@Test
	public void testInvisibleContainer() throws Exception
	{
		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"aint\": { \"type\": \"int\", \"pushToServer\": \"allow\", \"tags\":{\"" + WebObjectSpecification.ALLOW_ACCESS + "\":\"visible\"} }," + //
			"\n  \"dataProviderID\" : { \"type\":\"dataprovider\", \"pushToServer\": \"allow\"}" + //
			"\n}," + //
			"\n\"handlers\":" + //
			"\n{" + //
			"\n   \"callme\": \"function\"" + //
			"\n}" + //
			"\n}"; //

		WebComponentSpecProvider.init(
			new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		JSONObject tags = new JSONObject();
		tags.put(WebObjectSpecification.ALLOW_ACCESS, new JSONArray(new Object[] { "visible", "enabled" }));
		WebObjectSpecification formSpec = new WebObjectSpecification("form_spec", "", IPackageReader.WEB_COMPONENT, "", null, null, null, "", null);
		formSpec.putProperty("size", new PropertyDescription("size", DimensionPropertyType.INSTANCE, null, null, null, false, null, null, tags, false));
		formSpec.putProperty("visible", new PropertyDescription("visible", VisiblePropertyType.INSTANCE));
		formSpec.putProperty("enabled", new PropertyDescription("enabled", EnabledPropertyType.INSTANCE));

		Container form = new Container("form", formSpec)
		{
		};

		WebComponent testcomponent = new WebComponent("testcomponent", "test");
		form.add(testcomponent);

		final AtomicInteger called = new AtomicInteger(0);
		testcomponent.addEventHandler("callme", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				called.addAndGet(1);
				return null;
			}
		});

		// call function
		assertEquals(0, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(1, called.intValue());

		// set a property
		testcomponent.putBrowserProperty("aint", new Integer(1));
		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		// set form invisible
		form.setVisible(false);

		form.putBrowserProperty("size", new JSONObject("{\"width\":10,\"height\":10}"));

		// call function
		assertEquals(1, called.intValue());
		try
		{
			testcomponent.executeEvent("callme", null);
			fail("can call function on invisble form on from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("callme", e.getBlockedProperty());
		}

		try
		{
			testcomponent.putBrowserProperty("dataProviderID", "someillegavalue");
			fail("can call function on invisble form on from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("dataProviderID", e.getBlockedProperty());
		}
		assertEquals(1, called.intValue());

		// set a property that is allowed in visible
		testcomponent.putBrowserProperty("aint", new Integer(11));
		assertEquals(new Integer(11), testcomponent.getProperty("aint"));

		// make form visible again
		form.setVisible(true);

		// test enabled,
		form.setEnabled(false);
		try
		{
			// this property can only be set when visible is false not when enabled is false
			testcomponent.putBrowserProperty("aint", new Integer(12));
			fail("can call function on enabled  form on from client!");
		}
		catch (IllegalChangeFromClientException e)
		{
			// expected
			assertEquals("aint", e.getBlockedProperty());
		}
		assertEquals(new Integer(11), testcomponent.getProperty("aint"));
		form.setEnabled(true);

		// call function
		assertEquals(1, called.intValue());
		testcomponent.executeEvent("callme", null);
		assertEquals(2, called.intValue());

		// set a property
		testcomponent.putBrowserProperty("aint", new Integer(111));
		assertEquals(new Integer(111), testcomponent.getProperty("aint"));
	}

}
