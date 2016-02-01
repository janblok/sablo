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

package org.sablo.specification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;


/**
 * @author rgansevles
 */
public class NGPackageSpecification<T extends WebObjectSpecification>
{

	private static final String CSS_DESIGN_LIBS = "CSS-DesignLibs";
	private static final String CSS_CLIENT_LIBS = "CSS-ClientLibs";
	private static final String JS_DESIGN_LIBS = "JS-DesignLibs";
	private static final String JS_CLIENT_LIBS = "JS-ClientLibs";

	private final String packageName;
	private final String packageDisplayname;
	private final List<String> cssClientLibrary;
	private final List<String> cssDesignLibrary;
	private final List<String> jsClientLibrary;
	private final List<String> jsDesignLibrary;
	private final Map<String, T> specifications;
	private final Manifest mf;

	public NGPackageSpecification(String packageName, String packageDisplayname, Map<String, T> specifications, Manifest mf)
	{
		this.packageName = packageName;
		this.packageDisplayname = packageDisplayname;
		this.mf = mf;

		this.specifications = specifications;

		this.cssClientLibrary = getAttributeValue(mf, CSS_CLIENT_LIBS);
		this.cssDesignLibrary = getAttributeValue(mf, CSS_DESIGN_LIBS);
		this.jsClientLibrary = getAttributeValue(mf, JS_CLIENT_LIBS);
		this.jsDesignLibrary = getAttributeValue(mf, JS_DESIGN_LIBS);

	}

	private List<String> getAttributeValue(Manifest mf, String attributeName)
	{
		if (mf != null)
		{
			Attributes mainAttrs = mf.getMainAttributes();
			if (mainAttrs != null)
			{
				String value = mainAttrs.getValue(attributeName);
				if (value != null)
				{
					return Arrays.asList(value.split(","));
				}
			}
		}
		return null;
	}

	/**
	 * @return the packageName
	 */
	public String getPackageName()
	{
		return packageName;
	}

	/**
	 * @return the packageDisplayname
	 */
	public String getPackageDisplayname()
	{
		return packageDisplayname == null ? packageName : packageDisplayname;
	}

	public List<String> getCssClientLibrary()
	{
		return cssClientLibrary;
	}

	public List<String> getJsClientLibrary()
	{
		return jsClientLibrary;
	}

	public List<String> getCssDesignLibrary()
	{
		return cssDesignLibrary;
	}

	public List<String> getJsDesignLibrary()
	{
		return jsDesignLibrary;
	}

	/**
	 * @return the specifications
	 */
	public Map<String, T> getSpecifications()
	{
		return Collections.unmodifiableMap(specifications);
	}

	public T getSpecification(String name)
	{
		return specifications.get(name);
	}

	public Manifest getManifest()
	{
		return mf;
	}
}
