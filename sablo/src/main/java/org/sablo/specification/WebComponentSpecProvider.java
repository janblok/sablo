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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.specification.property.IComplexTypeImpl;
import org.sablo.specification.property.IPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for loading a set of web component packages and specs.
 * @author acostescu
 */
public class WebComponentSpecProvider
{
	private static final Logger log = LoggerFactory.getLogger(WebComponentSpecProvider.class.getCanonicalName());

	private final Map<String, WebComponentSpecification> cachedDescriptions = new HashMap<>();
	private final Map<String, IPropertyType> globalTypes = new HashMap<>();

	private final IPackageReader[] packageReaders;

	public WebComponentSpecProvider(File[] packages)
	{
		this(getReades(packages));
	}

	public WebComponentSpecProvider(IPackageReader[] packageReaders)
	{
		this.packageReaders = packageReaders;
		List<WebComponentPackage> packages = new ArrayList<>();
		for (IPackageReader packageReader : packageReaders)
		{
			packages.add(new WebComponentPackage(packageReader));
		}
		try
		{
			readGloballyDefinedTypes(packages);
			cacheComponentSpecs(packages);
		}
		finally
		{
			for (WebComponentPackage p : packages)
			{
				p.dispose();
			}
		}
		instance = this;
	}

	protected void cacheComponentSpecs(List<WebComponentPackage> packages)
	{
		for (WebComponentPackage p : packages)
		{
			try
			{
				cache(p.getWebComponentDescriptions(globalTypes));
			}
			catch (IOException e)
			{
				log.error("Cannot read web component specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
		}
	}
	
	public static Map<String, IPropertyType> readDefaultTypes()
	{
		Map<String, IPropertyType> map = new HashMap<>();
		populateDefaultTypes(map);
		return map;
	}

	public static void populateDefaultTypes(Map<String, IPropertyType> map)
	{
		
		for (IPropertyType.Default e : IPropertyType.Default.values())
		{
			IPropertyType type = e.getType();
			map.put(type.getName(), type);
		}
	}

	protected void readGloballyDefinedTypes(List<WebComponentPackage> packages)
	{
		// populate default types
		populateDefaultTypes(globalTypes);
		
		try
		{
			JSONObject typeContainer = new JSONObject();
			JSONObject allGlobalTypesFromAllPackages = new JSONObject();
			typeContainer.put(WebComponentSpecification.TYPES_KEY, allGlobalTypesFromAllPackages);

			for (WebComponentPackage p : packages)
			{
				try
				{
					p.appendGlobalTypesJSON(allGlobalTypesFromAllPackages);
				}
				catch (IOException e)
				{
					log.error("Cannot read globally defined types from package: " + p.getName(), e); //$NON-NLS-1$
				}
			}

			try
			{
				Map<String, IPropertyType> parsedTypes = WebComponentSpecification.parseTypes(typeContainer, globalTypes,
					"flattened global types - from all web component packages");
				globalTypes.putAll(parsedTypes);
			}
			catch (Exception e)
			{
				log.error("Cannot parse flattened global types - from all web component packages.", e);
			}
		}
		catch (JSONException e)
		{
			// should never happen
			log.error("Error Creating a simple JSON object hierarchy while reading globally defined types...");
		}
	}

	private static IPackageReader[] getReades(File[] packages)
	{
		ArrayList<IPackageReader> readers = new ArrayList<>();
		for (File f : packages)
		{
			if (f.exists())
			{
				if (f.isDirectory()) readers.add(new WebComponentPackage.DirPackageReader(f));
				else readers.add(new WebComponentPackage.JarPackageReader(f));
			}
			else
			{
				log.error("A web component package location does not exist: " + f.getAbsolutePath()); //$NON-NLS-1$
			}
		}
		return readers.toArray(new IPackageReader[readers.size()]);
	}

	private void cache(List<WebComponentSpecification> webComponentDescriptions)
	{
		for (WebComponentSpecification desc : webComponentDescriptions)
		{
			WebComponentSpecification old = cachedDescriptions.put(desc.getName(), desc);
			if (old != null) log.error("Conflict found! Duplicate web component definition name: " + old.getName());
		}
	}

	public IComplexTypeImpl getGlobalType(String typeName)
	{
		return globalTypes.get(typeName);
	}

	public WebComponentSpecification getWebComponentSpecification(String componentTypeName)
	{
		return cachedDescriptions.get(componentTypeName);
	}

	public WebComponentSpecification[] getWebComponentSpecifications()
	{
		return cachedDescriptions.values().toArray(new WebComponentSpecification[cachedDescriptions.size()]);
	}

	private static volatile WebComponentSpecProvider instance;

	public static WebComponentSpecProvider getInstance()
	{
		return instance;
	}

	/**
	 * @param array
	 */
	public static synchronized void init(IPackageReader[] locations)
	{
		instance = new WebComponentSpecProvider(locations);
	}


	public static WebComponentSpecProvider init(ServletContext servletContext)
	{
		try
		{
			InputStream is = servletContext.getResourceAsStream("/WEB-INF/components.properties");
			Properties properties = new Properties();
			properties.load(is);
			String[] locations = properties.getProperty("locations").split(";");
			return init(servletContext, locations);
		}
		catch (Exception e)
		{
			log.error("Exception during init components.properties reading",e);
		}
		return instance;
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
						instance = new WebComponentSpecProvider(readers.toArray(new IPackageReader[readers.size()]));
					}
					catch (Exception e)
					{
						log.error("Exception during init",e);
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
			instance = new WebComponentSpecProvider(instance.packageReaders);
		}
	}
}
