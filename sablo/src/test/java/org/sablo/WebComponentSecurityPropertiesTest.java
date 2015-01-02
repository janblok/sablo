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

import org.junit.After;
import org.junit.Test;
import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.specification.WebComponentSpecProvider;

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

		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

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
			"\n   \"astr\": \"string\"" + //
			"\n  ,\"vis\": \"visible\"" + //
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
			"\n   \"astr\": \"string\"" + //
			"\n  ,\"vis\": { \"type\": \"visible\" }" + // slightly different spec from previous test, still uses defaults
			"\n}" + //
			"\n}"; // 

		doTestComponentVisibileWithDefaults(testcomponentspec);
	}

	private void doTestComponentVisibileWithDefaults(String testcomponentspec) throws Exception
	{
		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		// default visible
		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));

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
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("vis", e.getProperty());
		}

		assertTrue(testcomponent.isVisible());
		assertEquals(Boolean.TRUE, testcomponent.getProperty("vis"));

		// check protection of properties when component is invisible
		testcomponent = new WebComponent("testcomponent", "test");

		testcomponent.putBrowserProperty("astr", "aap");
		assertEquals("aap", testcomponent.getProperty("astr"));

		testcomponent.setVisible(false);

		try
		{
			testcomponent.putBrowserProperty("astr", "noot");
			fail("can set protected property from client!");
		}
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("astr", e.getProperty());
		}

		assertEquals("aap", testcomponent.getProperty("astr"));

		testcomponent.setVisible(true);

		testcomponent.putBrowserProperty("astr", "mies");
		assertEquals("mies", testcomponent.getProperty("astr"));
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
			"\n   \"astr1\": \"string\"" + //
			"\n  ,\"astr2\": \"string\"" + //
			"\n  ,\"vis\": \"visible\"" + //
			"\n  ,\"visforastr2\": { \"type\": \"visible\", \"for\": \"astr2\" }" + //	
			"\n}" + //
			"\n}"; // 

		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

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
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("visforastr2", e.getProperty());
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
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("astr2", e.getProperty());
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
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("astr2", e.getProperty());
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
			"\n   \"aint\": \"int\"" + //
			"\n  ,\"prot\": \"protected\"" + //
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
			"\n   \"aint\": \"int\"" + //
			"\n  ,\"prot\": { \"type\": \"protected\" }" + // slightly different spec from previous test, still uses defaults
			"\n}" + //
			"\n}"; // 

		doTestComponentProtectedPropertyDefaults(testcomponentspec);
	}

	private void doTestComponentProtectedPropertyDefaults(String testcomponentspec) throws Exception
	{
		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

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
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("prot", e.getProperty());
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
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("aint", e.getProperty());
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
			"\n   \"aint\": \"int\"" + //
			"\n  ,\"prot\": { \"type\": \"protected\", \"default\": true, \"blockingOn\": false  }" + //
			"\n}" + //
			"\n}"; // 

		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

		WebComponent testcomponent = new WebComponent("testcomponent", "test");

		// default true
		assertEquals(Boolean.TRUE, testcomponent.getProperty("prot"));

		testcomponent.setProperty("prot", Boolean.FALSE);
		assertEquals(Boolean.FALSE, testcomponent.getProperty("prot"));

		testcomponent.setProperty("prot", Boolean.TRUE);
		assertEquals(Boolean.TRUE, testcomponent.getProperty("prot"));

		// cannot set via putBrowserProperty
		testcomponent = new WebComponent("testcomponent", "test");
		try
		{
			testcomponent.putBrowserProperty("prot", Boolean.FALSE);
			fail("can set protected property from client!");
		}
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("prot", e.getProperty());
		}

		assertEquals(Boolean.TRUE, testcomponent.getProperty("prot"));

		// check protection, blocking on false
		testcomponent = new WebComponent("testcomponent", "test");

		testcomponent.putBrowserProperty("aint", new Integer(1));
		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		testcomponent.setProperty("prot", Boolean.FALSE);

		try
		{
			testcomponent.putBrowserProperty("aint", new Integer(2));
			fail("can set protected property from client!");
		}
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("aint", e.getProperty());
		}

		assertEquals(new Integer(1), testcomponent.getProperty("aint"));

		testcomponent.setProperty("aint", new Integer(3));
		assertEquals(new Integer(3), testcomponent.getProperty("aint"));

		testcomponent.setProperty("prot", Boolean.TRUE);
		testcomponent.putBrowserProperty("aint", new Integer(4));
		assertEquals(new Integer(4), testcomponent.getProperty("aint"));
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
			"\n   \"aint1\": \"int\"" + //
			"\n  ,\"aint2\": \"int\"" + //
			"\n  ,\"aint3\": \"int\"" + //
			"\n  ,\"prot\": { \"type\": \"protected\", \"default\": true, \"blockingOn\": false, \"for\": \"aint1, aint3\" }" + //
			"\n}" + //
			"\n}"; // 

		WebComponentSpecProvider.init(new IPackageReader[] { new InMemPackageReader(MANIFEST, Collections.singletonMap(TESTCOMPONENT_SPEC, testcomponentspec)) });

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
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("aint1", e.getProperty());
		}

		// can still set other properties
		testcomponent.putBrowserProperty("aint2", new Integer(22));

		try
		{
			testcomponent.putBrowserProperty("aint3", new Integer(32));
			fail("can set protected property from client!");
		}
		catch (IllegalComponentAccessException e)
		{
			// expected
			assertEquals("aint3", e.getProperty());
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

}
