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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.BaseSpecProvider.ISpecReloadListener;
import org.sablo.specification.WebComponentPackage.DuplicatePackageException;
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

	private final Map<String, WebComponentPackageSpecification<WebComponentSpecification>> cachedDescriptions = new HashMap<>();
	private final Map<String, WebComponentPackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions = new TreeMap<>();
	private final Map<String, WebComponentSpecification> allWebObjectSpecifications = new HashMap<>();
	private final Map<String, IPackageReader> allPackages = new HashMap<>();
	private final Set<String> packagesWithGloballyDefinedTypes = new HashSet<>(); // packageNames of packages that have globally defined types

	private final List<IPackageReader> packageReaders;

	private final String attributeName;

	private long lastLoadTimestamp;

	private final Map<String, List<ISpecReloadListener>> specReloadListeners = new HashMap<>();

	WebSpecReader(IPackageReader[] packageReaders, String attributeName)
	{
		this.packageReaders = new ArrayList<>(Arrays.asList(packageReaders));
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
		allWebObjectSpecifications.clear();
		allPackages.clear();
		packagesWithGloballyDefinedTypes.clear();
		List<WebComponentPackage> packages = new ArrayList<>();
		for (IPackageReader packageReader : packageReaders)
		{
			packages.add(new WebComponentPackage(packageReader));
		}
		try
		{
			readGloballyDefinedTypes(packages);
			cacheWebObjectSpecs(packages);
		}
		finally
		{
			for (WebComponentPackage p : packages)
			{
				p.dispose();
			}
		}

		Iterator<Entry<String, List<ISpecReloadListener>>> it = specReloadListeners.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<String, List<ISpecReloadListener>> listenerEntry = it.next();
			for (ISpecReloadListener l : listenerEntry.getValue())
				l.webObjectSpecificationReloaded();
			if (!allWebObjectSpecifications.containsKey(listenerEntry.getKey())) it.remove();
		}
	}

	/**
	 * Updates available packages. "toRemove" provided contents will be removed, "toAdd" will be made available.
	 */
	public synchronized void updatePackages(Collection<IPackageReader> packagesToRemove, Collection<IPackageReader> packagesToAdd)
	{
		if (packagesToRemove.size() == 0 && packagesToAdd.size() == 0) return;
		lastLoadTimestamp = System.currentTimeMillis();
		List<String> removedOrReloadedSpecs = new ArrayList<>();

		boolean shouldReloadAllDueToGlobalTypeChanges = false;
		for (IPackageReader packageToRemove : packagesToRemove)
		{
			// find the package name from cache - cause the IPackageReader might already be invalid (contents deleted and such - so we can't rely on it's .getPackageName())
			String packageNameToRemove = null;
			for (Entry<String, IPackageReader> e : allPackages.entrySet())
			{
				if (e.getValue() == packageToRemove)
				{
					packageNameToRemove = e.getKey();
					break;
				}
			}

			if (packageNameToRemove != null)
			{
				if (packagesWithGloballyDefinedTypes.contains(packageNameToRemove))
				{
					shouldReloadAllDueToGlobalTypeChanges = true;
					break; // we will reload all in this case anyway
				}

				// unload package
				cachedLayoutDescriptions.remove(packageNameToRemove);
				packagesWithGloballyDefinedTypes.remove(packageNameToRemove);
				WebComponentPackageSpecification<WebComponentSpecification> removedPackageSpecs = cachedDescriptions.remove(packageNameToRemove);
				if (removedPackageSpecs != null) for (String specName : removedPackageSpecs.getSpecifications().keySet())
				{
					allWebObjectSpecifications.remove(specName);
					removedOrReloadedSpecs.add(specName);
				}
				IPackageReader oldPackageReader = allPackages.remove(packageNameToRemove);
				if (oldPackageReader != null) packageReaders.remove(oldPackageReader);
			}
			else
			{
				log.warn("Cannot unload an ng package:");
				try
				{
					log.warn("Package name: :" + packageToRemove.getName());
				}
				catch (Exception e)
				{
					// nothing
				}
			}
		}

		if (shouldReloadAllDueToGlobalTypeChanges) load(); // reload all because removed packages had global types
		else
		{
			// load new packages
			packageReaders.addAll(packagesToAdd);

			List<WebComponentPackage> packages = new ArrayList<>();
			for (IPackageReader packageReader : packagesToAdd)
			{
				packages.add(new WebComponentPackage(packageReader));
			}
			try
			{
				if (readGloballyDefinedTypes(packages)) load(); // reload all because added packages have global types
				else
				{
					cacheWebObjectSpecs(packages);
				}
			}
			finally
			{
				for (WebComponentPackage p : packages)
				{
					p.dispose();
				}
			}
		}

		for (String removedOrReloadedSpec : removedOrReloadedSpecs)
		{
			List<ISpecReloadListener> ls = specReloadListeners.get(removedOrReloadedSpec);
			if (ls != null) for (ISpecReloadListener l : ls)
				l.webObjectSpecificationReloaded();
			if (!allWebObjectSpecifications.containsKey(removedOrReloadedSpec)) specReloadListeners.remove(removedOrReloadedSpec);
		}
	}

	protected synchronized boolean readGloballyDefinedTypes(List<WebComponentPackage> packages)
	{
		boolean globalTypesFound = false;
		try
		{
			JSONObject typeContainer = new JSONObject();
			JSONObject allGlobalTypesFromAllPackages = new JSONObject();
			typeContainer.put(WebComponentSpecification.TYPES_KEY, allGlobalTypesFromAllPackages);

			for (WebComponentPackage p : packages)
			{
				try
				{
					if (p.appendGlobalTypesJSON(allGlobalTypesFromAllPackages))
					{
						globalTypesFound = true;
						packagesWithGloballyDefinedTypes.add(p.getPackageName());
					}
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
		return globalTypesFound;
	}

	protected synchronized void cacheWebObjectSpecs(List<WebComponentPackage> packages)
	{
		for (WebComponentPackage p : packages)
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
				WebComponentPackageSpecification<WebLayoutSpecification> layoutDescriptions = p.getLayoutDescriptions();
				if (layoutDescriptions.getSpecifications().size() > 0) cachedLayoutDescriptions.put(p.getPackageName(), layoutDescriptions);
			}
			catch (Exception e)
			{
				log.error("Cannot read web layout specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
		}
	}

	private void cache(WebComponentPackage p) throws IOException
	{
		IPackageReader oldPackage = allPackages.put(p.getPackageName(), p.getReader());
		if (oldPackage != null)
		{
			log.error("Conflict found! Duplicate web component / web service package name: " + oldPackage.getPackageName());
			p.getReader().reportError("", new DuplicatePackageException("Duplicate package " + oldPackage.getPackageName()));
		}
		WebComponentPackageSpecification<WebComponentSpecification> webComponentPackageSpecification = p.getWebObjectDescriptions(attributeName);
		Map<String, WebComponentSpecification> webComponentDescriptions = webComponentPackageSpecification.getSpecifications();
		Map<String, WebComponentSpecification> packageComponents = new HashMap<>(webComponentDescriptions.size());
		for (WebComponentSpecification desc : webComponentDescriptions.values())
		{
			WebComponentSpecification old = allWebObjectSpecifications.put(desc.getName(), desc);
			if (old != null) log.error("Conflict found! Duplicate web component / web service definition name: " + old.getName());
			else
			{
				packageComponents.put(desc.getName(), desc);
			}
		}

		if (packageComponents.size() > 0)
		{
			cachedDescriptions.put(webComponentPackageSpecification.getPackageName(),
				new WebComponentPackageSpecification<>(webComponentPackageSpecification.getPackageName(), webComponentPackageSpecification.getPackageDisplayname(),
					packageComponents, webComponentPackageSpecification.getManifest()));
		}
	}

	public synchronized WebComponentSpecification getWebComponentSpecification(String componentTypeName)
	{
		return allWebObjectSpecifications.get(componentTypeName);
	}

	public synchronized Map<String, WebComponentPackageSpecification<WebComponentSpecification>> getWebComponentSpecifications()
	{
		return Collections.unmodifiableMap(cachedDescriptions);
	}

	public synchronized WebComponentSpecification[] getAllWebComponentSpecifications()
	{
		return allWebObjectSpecifications.values().toArray(new WebComponentSpecification[allWebObjectSpecifications.size()]);
	}

	public Map<String, WebComponentPackageSpecification<WebLayoutSpecification>> getLayoutSpecifications()
	{
		return Collections.unmodifiableMap(cachedLayoutDescriptions);
	}

	/**
	 * Get the map of packages and package URLs.
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

	/**
	 * Adds a listener that gets notified when a specific component or service specification gets reloaded or removed.
	 * If it gets removed, the listener will be cleared as well after begin triggered.
	 *
	 * @param specName the name of the component/service to listen to for reloads.
	 */
	public void addSpecReloadListener(String specName, ISpecReloadListener specReloadListener)
	{
		List<ISpecReloadListener> listeners = specReloadListeners.get(specName);
		if (listeners == null)
		{
			listeners = new ArrayList<>();
			specReloadListeners.put(specName, listeners);
		}
		listeners.add(specReloadListener);
	}

	/**
	 * Removes the given listener.
	 * @see #addSpecReloadListener(String, ISpecReloadListener)
	 */
	public void removeSpecReloadListener(String specName, ISpecReloadListener specReloadListener)
	{
		List<ISpecReloadListener> listeners = specReloadListeners.get(specName);
		if (listeners != null)
		{
			listeners.remove(specReloadListener);
			if (listeners.size() == 0) specReloadListeners.remove(specName);
		}
	}

}
