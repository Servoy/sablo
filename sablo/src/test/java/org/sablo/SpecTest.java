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

import static org.mockito.Mockito.when;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.WebObjectSpecification;

/**
 * @author acostescu
 */
@SuppressWarnings("nls")
public class SpecTest
{

	@Mock
	private static IPackageReader packageReaderMock;
	@Rule
	public MockitoRule mockitoRule = MockitoJUnit.rule();

	@BeforeClass
	public static void setUp() throws Exception
	{
	}

	@AfterClass
	public static void tearDown()
	{
	}

	@Test
	public void testScriptingServiceNames()
	{
		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_COMPONENT);
		String property = "{name:'sample-testMe',definition:'/test.js'}";
		WebObjectSpecification spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-testMe", spec.getName());
		Assert.assertEquals("sample-testMe", spec.getScriptingName());
		Assert.assertEquals("sample-testMe", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());

		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_SERVICE);
		property = "{name:'sample-testMe',definition:'/test.js'}";
		spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-testMe", spec.getName());
		Assert.assertEquals("sampleTestMe", spec.getScriptingName());
		Assert.assertEquals("sample-testMe", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());

		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_COMPONENT);
		property = "{name:'sample-testMe',definition:'/test.js'}";
		spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-testMe", spec.getName());
		Assert.assertEquals("sample-testMe", spec.getScriptingName());
		Assert.assertEquals("sample-testMe", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());

		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_COMPONENT);
		property = "{name:'sample-TestMe',definition:'/test.js'}";
		spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-TestMe", spec.getName());
		Assert.assertEquals("sample-TestMe", spec.getScriptingName());
		Assert.assertEquals("sample-TestMe", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());

		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_SERVICE);
		property = "{name:'sample-testMe',definition:'/test.js'}";
		spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-testMe", spec.getName());
		Assert.assertEquals("sampleTestMe", spec.getScriptingName());
		Assert.assertEquals("sample-testMe", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());

		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_SERVICE);
		property = "{name:'sample-TestMe',definition:'/test.js'}";
		spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-TestMe", spec.getName());
		Assert.assertEquals("sampleTestMe", spec.getScriptingName());
		Assert.assertEquals("sample-TestMe", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());

		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_SERVICE);
		property = "{name:'sample-t',definition:'/test.js'}";
		spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-t", spec.getName());
		Assert.assertEquals("sampleT", spec.getScriptingName());
		Assert.assertEquals("sample-t", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());

		when(packageReaderMock.getPackageType()).thenReturn(IPackageReader.WEB_SERVICE);
		property = "{name:'sample-Ti-Ra-no',definition:'/test.js'}";
		spec = WebObjectSpecification.parseSpec(property, "sample", packageReaderMock);
		Assert.assertEquals("sample-Ti-Ra-no", spec.getName());
		Assert.assertEquals("sampleTiRaNo", spec.getScriptingName());
		Assert.assertEquals("sample-Ti-Ra-no", spec.getDisplayName());
		Assert.assertEquals("sample", spec.getPackageName());
	}

}
