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
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.specification.WebComponentSpecProvider;

/**
 * @author rgansevles
 *
 */
@SuppressWarnings("nls")
public class WebComponentPropertiesTest
{
	@BeforeClass
	public static void setUpSpecs() throws Exception
	{
		String manifest = "Manifest-Version: 1.0" + //
			"\n" + //
			"\nName: testcomponent.spec" + //
			"\nWeb-Component: True" + //
			"\n"; //

		String testcomponentspec = "{" + //
			"\n\"name\": \"testcomponent\"," + //
			"\n\"displayName\": \"Test Component\"," + //
			"\n\"definition\": \"testcomponent.js\"," + //
			"\n\"libraries\": []," + //
			"\n\"model\":" + //
			"\n{" + //
			"\n   \"abool\": \"boolean\"" + //
			"\n  ,\"aint\": \"int\"" + //
			"\n}" + //
			"\n}"; // 

		Map<String, String> components = new HashMap<>();
		components.put("testcomponent.spec", testcomponentspec);

		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(manifest, components) });
	}

	@AfterClass
	public static void disposeSpecs()
	{
		WebComponentSpecProvider.disposeInstance();
	}

	@Test
	public void testUnknownProperty() throws Exception
	{
		WebComponent testcomponent = new WebComponent("testcomponent", "test");
		assertNull(testcomponent.getProperty("appeltaart"));

		testcomponent.setProperty("appeltaart", new Boolean(true));
		assertEquals(Boolean.TRUE, testcomponent.getProperty("appeltaart"));

		testcomponent.setProperty("appeltaart", new Boolean(false));
		assertEquals(Boolean.FALSE, testcomponent.getProperty("appeltaart"));

		testcomponent.putBrowserProperty("slagroom", new Boolean(true));
		assertNull(testcomponent.getProperty("slagroom"));
	}

	@Test
	public void testBoolean() throws Exception
	{
		WebComponent testcomponent = new WebComponent("testcomponent", "test");
		assertEquals(Boolean.FALSE, testcomponent.getProperty("abool"));

		testcomponent.setProperty("abool", new Boolean(true));
		assertEquals(Boolean.TRUE, testcomponent.getProperty("abool"));

		testcomponent.setProperty("abool", new Boolean(false));
		assertEquals(Boolean.FALSE, testcomponent.getProperty("abool"));

		testcomponent.putBrowserProperty("abool", new Boolean(true));
		assertEquals(Boolean.TRUE, testcomponent.getProperty("abool"));

		testcomponent.putBrowserProperty("abool", new Boolean(false));
		assertEquals(Boolean.FALSE, testcomponent.getProperty("abool"));
	}

	@Test
	public void testInt() throws Exception
	{
		WebComponent testcomponent = new WebComponent("testcomponent", "test");
		assertEquals(new Integer(0), testcomponent.getProperty("aint"));

		testcomponent.setProperty("aint", new Integer(42));
		assertEquals(new Integer(42), testcomponent.getProperty("aint"));

		testcomponent.putBrowserProperty("aint", new Integer(-42));
		assertEquals(new Integer(-42), testcomponent.getProperty("aint"));
	}

}
