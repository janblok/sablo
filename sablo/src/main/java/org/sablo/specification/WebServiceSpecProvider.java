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

import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;

import javax.servlet.ServletContext;

import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.Package.JarServletContextReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for loading the service spec files.
 *
 * @author jcompagner
 */
public class WebServiceSpecProvider extends BaseSpecProvider
{
	private static final Logger log = LoggerFactory.getLogger(WebServiceSpecProvider.class.getCanonicalName());

	private static volatile WebServiceSpecProvider instance;

	private WebServiceSpecProvider(WebSpecReader reader)
	{
		super(reader);
	}

	public static WebServiceSpecProvider getInstance()
	{
		return instance;
	}

	public static synchronized void init(IPackageReader[] locations)
	{
		instance = new WebServiceSpecProvider(new WebSpecReader(locations, IPackageReader.WEB_SERVICE));
	}

	public static void disposeInstance()
	{
		instance = null;
	}


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
							readers.add(new Package.WarURLPackageReader(servletContext, location));
						}

						// scan all jars for services
						for (String resourcePath : servletContext.getResourcePaths("/WEB-INF/lib"))
						{
							if (resourcePath.toLowerCase().endsWith(".jar") || resourcePath.toLowerCase().endsWith(".zip"))
							{
								IPackageReader reader = new JarServletContextReader(servletContext, resourcePath);
								Manifest mf = reader.getManifest();
								if (mf != null && IPackageReader.WEB_SERVICE.equals(Package.getPackageType(mf))) readers.add(reader);
							}
						}

						instance = new WebServiceSpecProvider(
							new WebSpecReader(readers.toArray(new IPackageReader[readers.size()]), IPackageReader.WEB_SERVICE));
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

	/**
	 * Get the current state of spec providers, returns an immutable state.
	 */
	public static SpecProviderState getSpecProviderState()
	{
		return instance == null ? null : instance.reader.getSpecProviderState();
	}

	public static long getLastLoadTimestamp()
	{
		synchronized (WebServiceSpecProvider.class)
		{
			return instance.reader.getLastLoadTimestamp();
		}
	}

}
