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

import java.util.Collections;
import java.util.Map;


/**
 * @author rgansevles
 */
public class WebComponentPackageSpecification<T extends WebComponentSpecification>
{

	private final String packageName;
	private final String packageDisplayname;
	private final String cssLibrary;
	private final String jsLibrary;
	private final Map<String, T> specifications;

	public WebComponentPackageSpecification(String packageName, String packageDisplayname, Map<String, T> specifications, String cssLibrary, String jsLibrary)
	{
		this.packageName = packageName;
		this.packageDisplayname = packageDisplayname;
		this.specifications = specifications;
		this.cssLibrary = cssLibrary;
		this.jsLibrary = jsLibrary;
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

	/**
	 * @return the cssLibrary
	 */
	public String getCssLibrary()
	{
		return cssLibrary;
	}

	/**
	 * @return the jsLibrary
	 */
	public String getJsLibrary()
	{
		return jsLibrary;
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
}
