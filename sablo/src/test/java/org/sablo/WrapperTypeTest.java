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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.junit.Before;
import org.junit.Test;
import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
public class WrapperTypeTest
{

	/**
	 * @author jcompagner
	 *
	 */
	public class MyWrapperType implements IWrapperType<String, MyWrapper>
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
		public String defaultValue()
		{
			return null;
		}

		@Override
		public String unwrap(MyWrapper value)
		{
			return value.string;
		}

		@Override
		public MyWrapper wrap(String value, MyWrapper previousValue, IDataConverterContext dataConverterContext)
		{
			if (previousValue == null) previousValue = new MyWrapper();
			previousValue.string = value;
			previousValue.counter++;
			return previousValue;
		}

		@Override
		public MyWrapper fromJSON(Object newValue, MyWrapper previousValue, IDataConverterContext dataConverterContext)
		{
			if (newValue instanceof JSONObject)
			{
				return wrap(((JSONObject)newValue).optString("string"), previousValue, dataConverterContext);
			}
			else if (newValue instanceof String)
			{
				return wrap((String)newValue, previousValue, dataConverterContext);
			}
			return null;
		}

		@Override
		public JSONWriter toJSON(JSONWriter writer, String key, MyWrapper object, DataConversion clientConversion) throws JSONException
		{
			JSONUtils.addKeyIfPresent(writer, key);
			writer.object();
			writer.key("string").value(object.string);
			writer.key("counter").value(object.counter);
			writer.endObject();
			return writer;
		}

	}

	public class MyWrapper
	{
		private String string;
		private int counter;
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception
	{

		TypesRegistry.addType(new MyWrapperType());
		InputStream is = getClass().getResourceAsStream("WebComponentTest-manifest.spec");
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		String manifest = new String(bytes);
		is.close();

		is = getClass().getResourceAsStream("WrapperTypeTest-mycomponent.spec");
		bytes = new byte[is.available()];
		is.read(bytes);
		String comp = new String(bytes);
		is.close();

		HashMap<String, String> components = new HashMap<>();
		components.put("mycomponent.spec", comp);
		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(manifest, components) });
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

		String msg = JSONUtils.writeDataWithConversions(data, AggregatedPropertyType.newAggregatedProperty().putProperty("msg", properties.contentType),
			ConversionLocation.BROWSER_UPDATE);
		assertEquals("{\"msg\":{\"somepropp\":{\"string\":\"test\",\"counter\":1},\"name\":\"test\"}}", msg);

		component.putBrowserProperty("somepropp", "tester");

		assertTrue(component.getRawProperties().get("somepropp") instanceof MyWrapper);

		assertEquals("tester", component.getProperty("somepropp"));

		component.putBrowserProperty("somepropp", new JSONObject("{\"string\":\"test\"}"));
		assertTrue(component.getRawProperties().get("somepropp") instanceof MyWrapper);

		assertEquals("test", component.getProperty("somepropp"));
	}

}
