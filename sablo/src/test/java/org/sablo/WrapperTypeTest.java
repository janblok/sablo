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

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.junit.Before;
import org.junit.Test;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.ConversionLocation;
import org.sablo.websocket.IForJsonConverter;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
public class WrapperTypeTest {

	/**
	 * @author jcompagner
	 *
	 */
	public class MyWrapperType implements IWrapperType<String,MyWrapper> {

		@Override
		public String getName() {
			return "mywrapper";
		}

		@Override
		public Object parseConfig(JSONObject config) {
			return config;
		}

		@Override
		public MyWrapper defaultValue() {
			return null;
		}

		@Override
		public String unwrap(MyWrapper value) {
			return value.string;
		}

		@Override
		public MyWrapper wrap(String value, MyWrapper previousValue, IDataConverterContext dataConverterContext) {
			if (previousValue == null) previousValue = new MyWrapper();
			previousValue.string = value;
			previousValue.counter++;
			return previousValue;
		}

		@Override
		public Class<MyWrapper> getTypeClass() {
			return MyWrapper.class;
		}

		@Override
		public String fromJSON(Object newValue, MyWrapper previousValue) {
			if (newValue instanceof JSONObject) {
				return ((JSONObject) newValue).optString("string"); 
			}
			else if (newValue instanceof String){
				return (String) newValue;
			}
			return null;
		}

		@Override
		public void toJSON(JSONWriter writer, MyWrapper object, DataConversion clientConversion, IForJsonConverter forJsonConverter)
				throws JSONException {
			writer.object();
			writer.key("string").value(object.string);
			writer.key("counter").value(object.counter);
			writer.endObject();
		}

	}
	
	public class MyWrapper {
		private String string;
		private int counter;
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		
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
		WebComponentSpecProvider.init(new IPackageReader[] {new InMemPackageReader(manifest, components)});
	}

	@Test
	public void test() throws JSONException {
		WebComponent component = new WebComponent("mycomponent","test");
		component.setProperty("somepropp", "test", null);
		
		assertEquals("test", component.getProperty("somepropp"));
		
		HashMap<String, Object> data = new HashMap<>();
		data.put("msg", component.getProperties());
		
		String msg = JSONUtils.writeDataWithConversions(data, null, ConversionLocation.BROWSER_UPDATE);
		assertEquals("{\"msg\":{\"somepropp\":{\"string\":\"test\",\"counter\":1},\"name\":\"test\"}}", msg);
		
		component.putBrowserProperty("somepropp", "tester");
		
		assertTrue(component.getProperties().get("somepropp") instanceof MyWrapper);
		
		assertEquals("tester", component.getProperty("somepropp"));
		
		component.putBrowserProperty("somepropp", new JSONObject("{\"string\":\"test\"}"));
		assertTrue(component.getProperties().get("somepropp") instanceof MyWrapper);
		
		assertEquals("test", component.getProperty("somepropp"));
	}

}
