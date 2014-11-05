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

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;

import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for loading the service spec files.
 *
 * @author jcompagner
 */
public class WebServiceSpecProvider
{
	private static final Logger log = LoggerFactory.getLogger(WebServiceSpecProvider.class.getCanonicalName());

	private static volatile WebServiceSpecProvider instance;

	public static WebServiceSpecProvider getInstance()
	{
		return instance;
	}

	/**
	 * @param array
	 */
	public static synchronized void init(IPackageReader[] locations)
	{
		instance = new WebServiceSpecProvider(new WebSpecReader(locations));
	}


	public static WebServiceSpecProvider init(ServletContext servletContext)
	{
		try
		{
			InputStream is = servletContext.getResourceAsStream("/WEB-INF/services.properties");
			Properties properties = new Properties();
			properties.load(is);
			String[] locations = properties.getProperty("locations").split(";");
			return init(servletContext, locations);
		}
		catch (Exception e)
		{
			log.error("Exception during init services.properties reading", e);
		}
		return instance;
	}

	/**
	 * @param servletContext
	 * @param webComponentBundleNames
	 * @return the provider
	 */
	public static WebServiceSpecProvider init(ServletContext servletContext, String[] webComponentBundleNames)
	{
		if (instance == null)
		{
			synchronized (WebServiceSpecProvider.class)
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
						instance = new WebServiceSpecProvider(new WebSpecReader(readers.toArray(new IPackageReader[readers.size()])));
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
		synchronized (WebServiceSpecProvider.class)
		{
			instance.reader.load();
		}
	}

	private WebSpecReader reader;

	private WebServiceSpecProvider(WebSpecReader reader)
	{
		this.reader = reader;
	}

	/**
	 * get all registered web service specifications.
	 *
	 * @return an array of all the specifications
	 */
	public WebComponentSpecification[] getWebServiceSpecifications()
	{
		return reader.getWebComponentSpecifications();
	}

	/**
	 * get a specification for a specific service.
	 *
	 * @param serviceName
	 */
	public WebComponentSpecification getWebServiceSpecification(String serviceName)
	{
		return reader.getWebComponentSpecification(serviceName);
	}

	/**
	 * Get a list of all services contained by provided package name
	 */
	public List<String> getServicesInPackage(String packageName)
	{
		return reader.getPackagesToComponents().get(packageName);
	}

	/**
	 * Get the set of all services package names.
	 * @return
	 */
	public Set<String> getPackageNames()
	{
		return reader.getPackagesToComponents().keySet();
	}

	/**
	 * Get the map of service names to package URLs.
	 * @return
	 */
	public Map<String, URL> getPackagesToURLs()
	{
		return reader.getPackagesToURLs();
	}

}
