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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;

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
			"\n   \"abool\": {\"type\": \"boolean\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"aint\": {\"type\": \"int\", \"pushToServer\": \"allow\" }" + //
			"\n  ,\"tagged\": {\"type\": \"int\", \"tags\": {\"special\": 42}}" + //
			"\n  ,\"p2sreject\": {\"type\": \"object\", \"pushToServer\": \"reject\" }" + //
			"\n  ,\"p2sdefault1\": {\"type\": \"object\" }" + //
			"\n  ,\"p2sdefault2\": \"object\"" + //
			"\n  ,\"p2sdeep\": {\"type\": \"object\", \"pushToServer\": \"deep\" }" + //
			"\n  ,\"p2sshallow\": {\"type\": \"double\", \"pushToServer\": \"shallow\" }" + //
			"\n}" + //
			"\n}"; //

		Map<String, String> components = new HashMap<>();
		components.put("testcomponent.spec", testcomponentspec);

		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(manifest, components) }, null);
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

	@Test
	public void testPropertyTags()
	{
		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		assertFalse(testcomponent.getSpecification().getProperty("aint").hasTag(null));
		assertNull(testcomponent.getSpecification().getProperty("aint").getTag(null));

		assertFalse(testcomponent.getSpecification().getProperty("aint").hasTag("bladiebla"));
		assertNull(testcomponent.getSpecification().getProperty("aint").getTag("bladiebla"));

		assertFalse(testcomponent.getSpecification().getProperty("aint").hasTag("special"));
		assertNull(testcomponent.getSpecification().getProperty("aint").getTag("special"));

		assertFalse(testcomponent.getSpecification().getProperty("tagged").hasTag("bladiebla"));
		assertNull(testcomponent.getSpecification().getProperty("tagged").getTag("bladiebla"));

		assertTrue(testcomponent.getSpecification().getProperty("tagged").hasTag("special"));
		assertEquals(new Integer(42), testcomponent.getSpecification().getProperty("tagged").getTag("special"));
	}

	@Test
	public void testTaggedProperty()
	{
		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		assertTrue(testcomponent.getSpecification().getTaggedProperties(null).isEmpty());
		assertTrue(testcomponent.getSpecification().getTaggedProperties("bladiebla").isEmpty());

		Collection<PropertyDescription> specialComponents = testcomponent.getSpecification().getTaggedProperties("special");
		assertNotNull(specialComponents);
		assertEquals(1, specialComponents.size());
		assertEquals("tagged", specialComponents.iterator().next().getName());
		assertEquals(new Integer(42), specialComponents.iterator().next().getTag("special"));
	}

	@Test
	public void testPushToServer()
	{
		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		assertSame(PushToServerEnum.reject, testcomponent.getSpecification().getProperty("p2sreject").getPushToServer());
		assertSame(PushToServerEnum.reject, testcomponent.getSpecification().getProperty("p2sdefault1").getPushToServer());
		assertSame(PushToServerEnum.reject, testcomponent.getSpecification().getProperty("p2sdefault2").getPushToServer());
		assertSame(PushToServerEnum.deep, testcomponent.getSpecification().getProperty("p2sdeep").getPushToServer());
		assertSame(PushToServerEnum.shallow, testcomponent.getSpecification().getProperty("p2sshallow").getPushToServer());
	}
}
