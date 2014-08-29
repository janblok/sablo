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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.WebComponentPackage.IPackageReader;
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

	private final Map<String, WebComponentSpecification> cachedDescriptions = new HashMap<>();
	private final Map<String, List<String>> packagesToComponentNames = new HashMap<>();

	private final IPackageReader[] packageReaders;

	WebSpecReader(IPackageReader[] packageReaders)
	{
		this.packageReaders = packageReaders;
		load();
	}

	synchronized void load()
	{
		cachedDescriptions.clear();
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
	}

	protected synchronized void readGloballyDefinedTypes(List<WebComponentPackage> packages)
	{
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
				TypesRegistry.addTypes(WebComponentSpecification.getTypes(typeContainer).values());
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

	protected synchronized void cacheComponentSpecs(List<WebComponentPackage> packages)
	{
		for (WebComponentPackage p : packages)
		{
			try
			{
				cache(p.getPackageName(), p.getWebComponentDescriptions());
			}
			catch (IOException e)
			{
				log.error("Cannot read web component specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
		}
	}

	private void cache(String packageName, List<WebComponentSpecification> webComponentDescriptions)
	{
		if (packagesToComponentNames.get(packageName) == null) packagesToComponentNames.put(packageName, new ArrayList<String>());
		List<String> currentPackageComponents = packagesToComponentNames.get(packageName);
		for (WebComponentSpecification desc : webComponentDescriptions)
		{
			WebComponentSpecification old = cachedDescriptions.put(desc.getName(), desc);
			if (old != null) log.error("Conflict found! Duplicate web component definition name: " + old.getName());
			else currentPackageComponents.add(desc.getName());
		}
	}

	public synchronized WebComponentSpecification getWebComponentSpecification(String componentTypeName)
	{
		return cachedDescriptions.get(componentTypeName);
	}

	public synchronized WebComponentSpecification[] getWebComponentSpecifications()
	{
		return cachedDescriptions.values().toArray(new WebComponentSpecification[cachedDescriptions.size()]);
	}

	public synchronized Map<String, List<String>> getPackagesToComponents()
	{
		return packagesToComponentNames;
	}
}
