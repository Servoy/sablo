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
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.IWrappingContext;
import org.sablo.specification.property.WrappingContext;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.util.ValueReference;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
@SuppressWarnings("nls")
public class WrapperTypeTest
{

	/**
	 * @author jcompagner
	 *
	 */
	public static class MyWrapperType implements IWrapperType<String, MyWrapper>
	{

		@Override
		public String getName()
		{
			return "mywrapper";
		}

		@Override
		public Object parseConfig(JSONObject config)
		{
			return config;
		}

		@Override
		public String defaultValue(PropertyDescription pd)
		{
			return null;
		}

		@Override
		public boolean isPrimitive()
		{
			return false;
		}

		@Override
		public String unwrap(MyWrapper value)
		{
			return value.string;
		}

		@Override
		public MyWrapper wrap(String value, MyWrapper previousValue, PropertyDescription pd, IWrappingContext dataConverterContext)
		{
			if (previousValue == null) previousValue = new MyWrapper();
			previousValue.string = value;
			previousValue.counter++;
			return previousValue;
		}

		@Override
		public MyWrapper fromJSON(Object newValue, MyWrapper previousValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
			ValueReference<Boolean> returnValueAdjustedIncommingValue)
		{
			if (newValue instanceof JSONObject)
			{
				if (dataConverterContext instanceof IWrappingContext)
					return wrap(((JSONObject)newValue).optString("string"), previousValue, pd, (IWrappingContext)dataConverterContext);
				else return wrap(((JSONObject)newValue).optString("string"), previousValue, pd,
					new WrappingContext(dataConverterContext.getWebObject(), pd.getName()));

			}
			else if (newValue instanceof String)
			{
				if (dataConverterContext instanceof IWrappingContext) return wrap((String)newValue, previousValue, pd, (IWrappingContext)dataConverterContext);
				else return wrap((String)newValue, previousValue, pd, new WrappingContext(dataConverterContext.getWebObject(), pd.getName()));
			}
			return null;
		}

		@Override
		public JSONWriter toJSON(JSONWriter writer, String key, MyWrapper object, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
			throws JSONException
		{
			JSONUtils.addKeyIfPresent(writer, key);
			writer.object();
			writer.key("string").value(object.string);
			writer.key("counter").value(object.counter);
			writer.endObject();
			return writer;
		}

		@Override
		public boolean isProtecting()
		{
			return false;
		}

	}

	public static class MyWrapper
	{
		private String string;
		private int counter;
	}

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUp() throws Exception
	{

		TypesRegistry.addType(new MyWrapperType());
		InputStream is = WrapperTypeTest.class.getResourceAsStream("WebComponentTest.manifest");
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		String manifest = new String(bytes);
		is.close();

		is = WrapperTypeTest.class.getResourceAsStream("WrapperTypeTest-mycomponent.spec");
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
	public void test() throws JSONException
	{
		WebComponent component = new WebComponent("mycomponent", "test");
		component.setProperty("somepropp", "test");

		assertEquals("test", component.getProperty("somepropp"));

		HashMap<String, Object> data = new HashMap<>();
		TypedData<Map<String, Object>> properties = component.getProperties();
		data.put("msg", properties.content);

		String msg = JSONUtils.writeDataAsFullToJSON(data,
			AggregatedPropertyType.newAggregatedPropertyBuilder().withProperty("msg", properties.contentType).build(), null);
		assertTrue(new JSONObject("{\"msg\":{\"somepropp\":{\"string\":\"test\",\"counter\":1},\"name\":\"test\"}}")
			.similar(new JSONObject(msg)));

		component.putBrowserProperty("somepropp", "tester");

		assertTrue(component.getRawPropertiesWithoutDefaults().get("somepropp") instanceof MyWrapper);

		assertEquals("tester", component.getProperty("somepropp"));

		component.putBrowserProperty("somepropp", new JSONObject("{\"string\":\"test\"}"));
		assertTrue(component.getRawPropertiesWithoutDefaults().get("somepropp") instanceof MyWrapper);

		assertEquals("test", component.getProperty("somepropp"));
	}

}
