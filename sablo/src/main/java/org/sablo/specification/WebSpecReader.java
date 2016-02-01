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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.NGPackage.DuplicatePackageException;
import org.sablo.specification.NGPackage.IPackageReader;
import org.sablo.specification.property.types.TypesRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jcompagner
 *
 */
class WebSpecReader
{
	private static final Logger log = LoggerFactory.getLogger(WebSpecReader.class.getCanonicalName());

	private final Map<String, NGPackageSpecification<WebObjectSpecification>> cachedDescriptions = new HashMap<>();
	private final Map<String, NGPackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions = new TreeMap<>();
	private final Map<String, WebObjectSpecification> allComponentSpecifications = new HashMap<>();
	private final Map<String, NGPackage> allPackages = new HashMap<String, NGPackage>();

	private final IPackageReader[] packageReaders;

	private final String attributeName;

	private long lastLoadTimestamp;

	WebSpecReader(IPackageReader[] packageReaders, String attributeName)
	{
		this.packageReaders = packageReaders;
		this.attributeName = attributeName;
		load();
	}

	public long getLastLoadTimestamp()
	{
		return lastLoadTimestamp;
	}

	synchronized void load()
	{
		lastLoadTimestamp = System.currentTimeMillis();

		cachedDescriptions.clear();
		cachedLayoutDescriptions.clear();
		allComponentSpecifications.clear();
		allPackages.clear();
		List<NGPackage> packages = new ArrayList<>();
		for (IPackageReader packageReader : packageReaders)
		{
			packages.add(new NGPackage(packageReader));
		}
		try
		{
			readGloballyDefinedTypes(packages);
			cacheComponentSpecs(packages);
		}
		finally
		{
			for (NGPackage p : packages)
			{
				p.dispose();
			}
		}
	}

	protected synchronized void readGloballyDefinedTypes(List<NGPackage> packages)
	{
		try
		{
			JSONObject typeContainer = new JSONObject();
			JSONObject allGlobalTypesFromAllPackages = new JSONObject();
			typeContainer.put(WebObjectSpecification.TYPES_KEY, allGlobalTypesFromAllPackages);

			for (NGPackage p : packages)
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
				TypesRegistry.addTypes(WebObjectSpecification.getTypes(typeContainer).values());
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

	protected synchronized void cacheComponentSpecs(List<NGPackage> packages)
	{
		for (NGPackage p : packages)
		{
			try
			{
				cache(p);
			}
			catch (Exception e)
			{
				log.error("Cannot read web component specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
			try
			{
				NGPackageSpecification<WebLayoutSpecification> layoutDescriptions = p.getLayoutDescriptions();
				if (layoutDescriptions.getSpecifications().size() > 0) cachedLayoutDescriptions.put(p.getPackageName(), layoutDescriptions);
			}
			catch (Exception e)
			{
				log.error("Cannot read web layout specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
		}
	}

	private void cache(NGPackage p) throws IOException
	{
		NGPackage oldPackage = allPackages.put(p.getPackageName(), p);
		if (oldPackage != null)
		{
			log.error("Conflict found! Duplicate web component package name: " + oldPackage.getPackageName());
			oldPackage.getReader().reportError("", new DuplicatePackageException("Duplicate package " + oldPackage.getPackageName()));
		}
		NGPackageSpecification<WebObjectSpecification> webComponentPackageSpecification = p.getWebComponentDescriptions(attributeName);
		Map<String, WebObjectSpecification> webComponentDescriptions = webComponentPackageSpecification.getSpecifications();
		Map<String, WebObjectSpecification> packageComponents = new HashMap<>(webComponentDescriptions.size());
		for (WebObjectSpecification desc : webComponentDescriptions.values())
		{
			WebObjectSpecification old = allComponentSpecifications.put(desc.getName(), desc);
			if (old != null) log.error("Conflict found! Duplicate web component definition name: " + old.getName());
			else
			{
				packageComponents.put(desc.getName(), desc);
			}
		}

		if (packageComponents.size() > 0)
		{
			cachedDescriptions.put(webComponentPackageSpecification.getPackageName(),
				new NGPackageSpecification<>(webComponentPackageSpecification.getPackageName(),
					webComponentPackageSpecification.getPackageDisplayname(), packageComponents, webComponentPackageSpecification.getManifest()));
		}
	}

	public synchronized WebObjectSpecification getWebComponentSpecification(String componentTypeName)
	{
		return allComponentSpecifications.get(componentTypeName);
	}

	public synchronized Map<String, NGPackageSpecification<WebObjectSpecification>> getWebComponentSpecifications()
	{
		return Collections.unmodifiableMap(cachedDescriptions);
	}

	public synchronized WebObjectSpecification[] getAllWebComponentSpecifications()
	{
		return allComponentSpecifications.values().toArray(new WebObjectSpecification[allComponentSpecifications.size()]);
	}

	/**
	 * @return
	 */
	public Map<String, NGPackageSpecification<WebLayoutSpecification>> getLayoutSpecifications()
	{
		return Collections.unmodifiableMap(cachedLayoutDescriptions);
	}

	/**
	 * Get the map of packages and package URLs.
	 * @return
	 */
	public Map<String, URL> getPackagesToURLs()
	{
		Map<String, URL> result = new HashMap<String, URL>();
		for (IPackageReader reader : packageReaders)
		{
			result.put(reader.getPackageName(), reader.getPackageURL());
		}
		return result;
	}

	/**
	 * Get the map of packages and package display names.
	 * @return
	 */
	public Map<String, String> getPackagesToDisplayNames()
	{
		Map<String, String> result = new HashMap<String, String>();
		for (IPackageReader reader : packageReaders)
		{
			result.put(reader.getPackageName(), reader.getPackageDisplayname());
		}
		return result;
	}
}
