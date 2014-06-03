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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
public class WebComponentTest {

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		
		
		InputStream is = getClass().getResourceAsStream("WebComponentTest-manifest.spec");
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		String manifest = new String(bytes);
		is.close();
		
		is = getClass().getResourceAsStream("WebComponentTest-mycomponent.spec");
		bytes = new byte[is.available()];
		is.read(bytes);
		String comp = new String(bytes);
		is.close();
		
		HashMap<String, String> components = new HashMap<>();
		components.put("mycomponent.spec", comp);
		WebComponentSpecProvider.init(new IPackageReader[] {new InMemPackageReader(manifest, components)});
	}
	
	@Test
	public void testNotExistingProperty() {
		WebComponent component = new WebComponent("mycomponent","test");
		// TODO should this be null or the default value???
		assertNull(component.getProperty("doesnotexisits"));
		
		component.setProperty("doesnotexisits", "test", null);
		
		assertEquals("test", component.getProperty("doesnotexisits"));
	}

	@Test
	public void testDimension() throws JSONException {
		WebComponent component = new WebComponent("mycomponent","test");
		// TODO should this be null or the default value???
		assertNull(component.getProperty("size"));
		
		// put in something illegal
		component.setProperty("size", Color.black, null);
		assertNull(component.getProperty("size"));
		
		assertTrue(component.setProperty("size", new Dimension(10, 10), null));
		
		Map<String, Object> properties = component.getProperties();
		assertNotNull(properties.get("size"));
		
		assertEquals(new Dimension(10, 10), properties.get("size"));
		assertEquals(new Dimension(10, 10), component.getProperty("size"));
		
		HashMap<String, Object> data = new HashMap<>();
		data.put("msg", properties);
		
		String msg = JSONUtils.writeDataWithConversions(data, null);
		assertEquals("{\"msg\":{\"name\":\"test\",\"size\":{\"width\":10,\"height\":10}}}", msg);
		
		component.putBrowserProperty("size", new JSONObject("{\"width\":20,\"height\":20}"));
		properties = component.getProperties();
		assertNotNull(properties.get("size"));
		
		assertEquals(new Dimension(20, 20), properties.get("size"));
		assertEquals(new Dimension(20, 20), component.getProperty("size"));
	}
	
	@Test
	public void testColor() throws JSONException {
		WebComponent component = new WebComponent("mycomponent","test");
		assertNull(component.getProperty("background"));
		
		// put in something illegal
		component.setProperty("background", new Dimension(), null);
		assertNull(component.getProperty("background"));
		
		assertTrue(component.setProperty("background", Color.black, null));
		
		Map<String, Object> properties = component.getProperties();
		assertNotNull(properties.get("background"));
		
		assertEquals(Color.black, properties.get("background"));
		assertEquals(Color.black, component.getProperty("background"));
		
		HashMap<String, Object> data = new HashMap<>();
		data.put("msg", properties);
		
		String msg = JSONUtils.writeDataWithConversions(data, null);
		assertEquals("{\"msg\":{\"background\":\"#000000\",\"name\":\"test\"}}", msg);
		
		component.putBrowserProperty("background", "#ff0000");
		properties = component.getProperties();
		assertNotNull(properties.get("background"));
		
		assertEquals(Color.red, properties.get("background"));
		assertEquals(Color.red, component.getProperty("background"));
	}
	
	@Test
	public void testFont() throws JSONException {
		WebComponent component = new WebComponent("mycomponent","test");
		assertNull(component.getProperty("font"));
		
		// put in something illegal
		component.setProperty("font", new Dimension(), null);
		assertNull(component.getProperty("font"));
		
		assertTrue(component.setProperty("font", new Font("Arial", Font.BOLD, 12), null));
		
		Map<String, Object> properties = component.getProperties();
		assertNotNull(properties.get("font"));
		
		assertEquals(new Font("Arial", Font.BOLD, 12), properties.get("font"));
		assertEquals(new Font("Arial", Font.BOLD, 12), component.getProperty("font"));
		
		HashMap<String, Object> data = new HashMap<>();
		data.put("msg", properties);
		
		String msg = JSONUtils.writeDataWithConversions(data, null);
		assertEquals("{\"msg\":{\"font\":{\"fontWeight\":\"bold\",\"fontSize\":\"12px\",\"fontFamily\":\"Arial, Verdana, Arial\"},\"name\":\"test\"}}", msg);
		
		component.putBrowserProperty("font", new JSONObject("{\"italic\":\"italic\",\"fontSize\":\"10px\",\"fontFamily\":\"Arial, Verdana, Arial\"}"));
		properties = component.getProperties();
		assertNotNull(properties.get("font"));
		
		Font font = (Font) component.getProperty("font");
		assertEquals("Arial", font.getName());
		assertFalse(font.isBold());
		assertTrue(font.isItalic());
		assertEquals(10, font.getSize());
	}
	
	@Test
	public void testPoint() throws JSONException {
		WebComponent component = new WebComponent("mycomponent","test");
		// TODO should this be null or the default value???
		assertNull(component.getProperty("location"));
		
		// put in something illegal
		component.setProperty("location", Color.black, null);
		assertNull(component.getProperty("location"));
		
		assertTrue(component.setProperty("location", new Point(10, 10), null));
		
		Map<String, Object> properties = component.getProperties();
		assertNotNull(properties.get("location"));
		
		assertEquals(new Point(10, 10), properties.get("location"));
		assertEquals(new Point(10, 10), component.getProperty("location"));
		
		HashMap<String, Object> data = new HashMap<>();
		data.put("msg", properties);
		
		String msg = JSONUtils.writeDataWithConversions(data, null);
		assertEquals("{\"msg\":{\"location\":{\"x\":10,\"y\":10},\"name\":\"test\"}}", msg);
		
		component.putBrowserProperty("location", new JSONObject("{\"x\":20,\"y\":20}"));
		properties = component.getProperties();
		assertNotNull(properties.get("location"));
		
		assertEquals(new Point(20, 20), properties.get("location"));
		assertEquals(new Point(20, 20), component.getProperty("location"));
	}
	
	@Test
	public void testCustomType() throws JSONException {
		WebComponent component = new WebComponent("mycomponent","test");
		assertNull(component.getProperty("atype"));
		
		// custom types are always a Map of values..
		Map<String,Object> customType= new HashMap<>();
		customType.put("name", "myname");
		customType.put("active", Boolean.TRUE);
		customType.put("foreground", Color.black);
		
		
		assertTrue(component.setProperty("atype",customType, null));
		
		Map<String, Object> properties = component.getProperties();
		assertNotNull(properties.get("atype"));
		
		assertEquals(customType, properties.get("atype"));
		assertEquals(customType, component.getProperty("atype"));
		
		HashMap<String, Object> data = new HashMap<>();
		data.put("msg", properties);
		
		String msg = JSONUtils.writeDataWithConversions(data, null);
		assertEquals("{\"msg\":{\"atype\":{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"},\"name\":\"test\"}}", msg);
		
		component.putBrowserProperty("atype", new JSONObject("{\"name\":\"myname\",\"active\":false,\"text\":\"test\",\"size\":{\"width\":10,\"height\":10}}"));
		properties = component.getProperties();
		assertNotNull(properties.get("atype"));
		assertSame(properties.get("atype"), component.getProperty("atype"));
		
		customType = (Map<String, Object>) component.getProperty("atype");
		assertEquals("myname", customType.get("name"));
		assertEquals(Boolean.FALSE, customType.get("active"));
		assertEquals(new Dimension(10, 10), customType.get("size"));
		
		// TODO also for custom types none existing properties should just be added? Like in the component itself?
		// for now we dont allow it..
		component.putBrowserProperty("atype", new JSONObject("{\"name\":\"myname\",\"notintype\":false,\"text\":\"test\",\"size\":{\"width\":10,\"height\":10}}"));
		properties = component.getProperties();
		assertNotNull(properties.get("atype"));
		assertSame(properties.get("atype"), component.getProperty("atype"));
		
		customType = (Map<String, Object>) component.getProperty("atype");
		assertEquals("myname", customType.get("name"));
		assertNull(customType.get("active"));
		assertNull(customType.get("notintype"));
		
		// TODO add partial updates for custom types?
		// but how do we know that? that the previous value must be taken as a base, and updates should overwrite, and somehow properties marked as deletes should be deleted?
		
	}
	
	@Test
	public void testCustomTypeAsArray() throws JSONException {
		WebComponent component = new WebComponent("mycomponent","test");
		assertNull(component.getProperty("types"));
		
		// custom types are always a Map of values..
		Map<String,Object> customType1 = new HashMap<>();
		customType1.put("name", "myname");
		customType1.put("active", Boolean.TRUE);
		customType1.put("foreground", Color.black);
		
		Map<String,Object> customType2 = new HashMap<>();
		customType2.put("name", "myname2");
		customType2.put("active", Boolean.FALSE);
		customType2.put("foreground", Color.white);
		
		Object[] array = new Object[] {customType1,customType2};
		assertTrue(component.setProperty("types", array, null));
		
		Object[] array2 = (Object[]) component.getProperty("types");
		
		assertSame(array, array2);
		
		HashMap<String, Object> data = new HashMap<>();
		data.put("msg", component.getProperties());
		
		String msg = JSONUtils.writeDataWithConversions(data, null);
		assertEquals("{\"msg\":{\"name\":\"test\",\"types\":[{\"name\":\"myname\",\"active\":true,\"foreground\":\"#000000\"},{\"name\":\"myname2\",\"active\":false,\"foreground\":\"#ffffff\"}]}}", msg);
		
		component.putBrowserProperty("types", new JSONArray("[{\"name\":\"myname\",\"active\":false,\"foreground\":\"#ff0000\"},{\"name\":\"myname2\",\"active\":true,\"foreground\":\"#ff0000\"},{\"name\":\"myname3\",\"active\":true,\"foreground\":null}]"));
		
		Object[] array3 = (Object[]) component.getProperty("types");
		
		assertEquals(3, array3.length);
		
		assertEquals("myname", ((Map<?,?>)array3[0]).get("name"));
		assertEquals("myname2", ((Map<?,?>)array3[1]).get("name"));
		assertEquals("myname3", ((Map<?,?>)array3[2]).get("name"));
		assertEquals(Color.red, ((Map<?,?>)array3[1]).get("foreground"));
		assertNull(((Map<?,?>)array3[2]).get("foreground"));
		
	}

}
