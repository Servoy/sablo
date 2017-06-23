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

import java.io.InputStream;
import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebServiceSpecProvider;

/**
 * @author jcompagner
 *
 */
public class WebComponentLibTest
{

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUp() throws Exception
	{
		InputStream is = WebComponentLibTest.class.getResourceAsStream("lib.manifest");
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		String manifest = new String(bytes);
		is.close();

		HashMap<String, String> components = new HashMap<>();
		is = WebComponentLibTest.class.getResourceAsStream("lib1.spec");
		bytes = new byte[is.available()];
		is.read(bytes);
		String comp = new String(bytes);
		is.close();

		components.put("lib1.spec", comp);

		is = WebComponentLibTest.class.getResourceAsStream("lib2.spec");
		bytes = new byte[is.available()];
		is.read(bytes);
		comp = new String(bytes);
		is.close();

		components.put("lib2.spec", comp);

		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(manifest, components) });

		WebServiceSpecProvider.init(new IPackageReader[0]);
	}

	@AfterClass
	public static void tearDown()
	{
		WebComponentSpecProvider.disposeInstance();
	}

	@Test
	public void testLibVersion() throws Exception
	{
		String allContributions = IndexPageEnhancer.getAllContributions(null, null, null, null, null);
		Assert.assertTrue(allContributions.contains("lib1.js"));
		Assert.assertTrue(allContributions.contains("lib2.js"));
		Assert.assertTrue(allContributions.contains("url2.js"));
		Assert.assertTrue(allContributions.contains("url2.css"));
		Assert.assertFalse(allContributions.contains("url1.js"));
		Assert.assertTrue(allContributions.indexOf("b.js") < allContributions.indexOf("url2.js"));
		Assert.assertTrue(allContributions.indexOf("url2.js") < allContributions.indexOf("a.js"));
	}
}
