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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;

import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.specification.WebComponentPackage.JarServletContextReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for loading a set of web component packages and specs.
 * @author acostescu
 */
public class WebComponentSpecProvider
{
	private static final Logger log = LoggerFactory.getLogger(WebComponentSpecProvider.class.getCanonicalName());

	private static volatile WebComponentSpecProvider instance;

	public static WebComponentSpecProvider getInstance()
	{
		return instance;
	}

	public static void disposeInstance()
	{
		instance = null;
	}

	/**
	 * @param array
	 */
	public static synchronized void init(IPackageReader[] locations)
	{
		instance = new WebComponentSpecProvider(new WebSpecReader(locations, "Web-Component"));
	}

	/**
	 * @param servletContext
	 * @param webComponentBundleNames
	 * @return the provider
	 */
	public static WebComponentSpecProvider init(ServletContext servletContext, String[] webComponentBundleNames)
	{
		if (instance == null)
		{
			synchronized (WebComponentSpecProvider.class)
			{
				if (instance == null)
				{
					try
					{
						List<IPackageReader> readers = new ArrayList<IPackageReader>();
						for (String location : webComponentBundleNames)
						{
							readers.add(new WebComponentPackage.WarURLPackageReader(servletContext, location));
						}

						// scan all jars for components
						for (String resourcePath : servletContext.getResourcePaths("/WEB-INF/lib"))
						{
							if (resourcePath.toLowerCase().endsWith(".jar") || resourcePath.toLowerCase().endsWith(".zip"))
							{
								readers.add(new JarServletContextReader(servletContext, resourcePath));
							}
						}

						instance = new WebComponentSpecProvider(new WebSpecReader(readers.toArray(new IPackageReader[readers.size()]), "Web-Component"));
					}
					catch (Exception e)
					{
						log.error("Exception during init", e);
					}
				}
			}
		}
		return instance;
	}

	public static void reload()
	{
		synchronized (WebComponentSpecProvider.class)
		{
			instance.reader.load();
		}
	}

	private final WebSpecReader reader;

	private WebComponentSpecProvider(WebSpecReader reader)
	{
		this.reader = reader;
	}

	/**
	 * Get the specification for the given component type.
	 *
	 * @param componentType
	 * @return the components specification, null if not found.
	 */
	public WebComponentSpecification getWebComponentSpecification(String componentType)
	{
		return reader.getWebComponentSpecification(componentType);
	}

	/**
	 * get all registered web component specifications.
	 *
	 * @return an array of all the specifications
	 */
	public WebComponentSpecification[] getWebComponentSpecifications()
	{
		return reader.getWebComponentSpecifications();
	}

	/**
	 * get all registered layout component specifications.
	 *
	 * @return an array of all the specifications
	 */
	public Map<String, Map<String, WebLayoutSpecification>> getLayoutSpecifications()
	{
		return reader.getLayoutSpecifications();
	}

	/**
	 * Get a list of all packages that contain web component specification
	 */
	public Set<String> getPackageNames()
	{
		return reader.getPackagesToComponents().keySet();
	}

	/**
	 * Get the map of component package names to package URLs.
	 */
	public Map<String, URL> getPackagesToURLs()
	{
		return reader.getPackagesToURLs();
	}

	/**
	 * Get a list of all components contained by provided package name
	 */
	public List<String> getComponentsInPackage(String packageName)
	{
		return reader.getPackagesToComponents().get(packageName);
	}
}
