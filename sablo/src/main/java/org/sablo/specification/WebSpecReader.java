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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.Package.DuplicateEntityException;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.property.types.TypesRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jcompagner
 */
class WebSpecReader
{
	private static final Logger log = LoggerFactory.getLogger(WebSpecReader.class.getCanonicalName());

	private final Map<String, PackageSpecification<WebObjectSpecification>> cachedComponentOrServiceDescriptions = new HashMap<>(); // component, services NOT layouts
	private final Map<String, PackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions = new TreeMap<>(); // only for layouts

	private final Map<String, WebObjectSpecification> allWebObjectSpecifications = new HashMap<>(); // this map does not includes layouts
	private final Map<String, WebLayoutSpecification> allLayoutSpecifications = new HashMap<>(); // this map does not includes layouts
	private final Map<String, List<IPackageReader>> allPackages = new HashMap<>();
	private final Set<String> packagesWithGloballyDefinedTypes = new HashSet<>(); // packageNames of packages that have globally defined types
	private final IDefaultComponentPropertiesProvider defaultComponentPropertiesProvider;

	private final List<IPackageReader> packageReaders;

	private final String attributeName;

	private final SpecReloadSubject specReloadSubject;

	private long lastLoadTimestamp;

	private SpecProviderState specProviderState;


	WebSpecReader(IPackageReader[] packageReaders, String attributeName, SpecReloadSubject specReloadSubject,
		IDefaultComponentPropertiesProvider defaultComponentPropertiesProvider)
	{
		this.packageReaders = new ArrayList<>(Arrays.asList(packageReaders));
		this.attributeName = attributeName;
		this.specReloadSubject = specReloadSubject;
		this.defaultComponentPropertiesProvider = defaultComponentPropertiesProvider;
		load();
	}

	public long getLastLoadTimestamp()
	{
		return lastLoadTimestamp;
	}

	void load()
	{
		synchronized (this)
		{
			lastLoadTimestamp = System.currentTimeMillis();

			cachedComponentOrServiceDescriptions.clear();
			cachedLayoutDescriptions.clear();
			allWebObjectSpecifications.clear();
			allLayoutSpecifications.clear();
			allPackages.clear();
			packagesWithGloballyDefinedTypes.clear();
			List<Package> packages = new ArrayList<>();
			for (IPackageReader packageReader : packageReaders)
			{
				packages.add(new Package(packageReader));
			}
			try
			{
				readGloballyDefinedTypes(packages);
				cacheWebObjectSpecs(packages);
			}
			finally
			{
				for (Package p : packages)
				{
					p.dispose();
				}
			}
		}

		specReloadSubject.fireWebObjectSpecificationReloaded();

		HashSet<String> allSpecs = new HashSet<>(allWebObjectSpecifications.keySet());
		allSpecs.addAll(allLayoutSpecifications.keySet());
		specReloadSubject.removeOtherSpecReloadListeners(allSpecs);

		specProviderState = null;
	}

	/**
	 * Updates available packages. "toRemove" provided contents will be removed, "toAdd" will be made available.
	 */
	public void updatePackages(Collection<IPackageReader> packagesToRemove, Collection<IPackageReader> packagesToAdd)
	{
		if (packagesToRemove.size() == 0 && packagesToAdd.size() == 0) return;
		boolean shouldStillFireReloadListeners = true;
		List<String> removedOrReloadedSpecs = new ArrayList<>(); // so not newly added

		synchronized (this)
		{
			boolean shouldReloadAllDueToGlobalTypeChanges = false;
			specProviderState = null;

			lastLoadTimestamp = System.currentTimeMillis();

			for (IPackageReader packageToRemove : packagesToRemove)
			{
				// find the package name from cache - cause the IPackageReader might already be invalid (contents deleted and such - so we can't rely on it's .getPackageName())
				String packageNameToRemove = null;
				for (Entry<String, List<IPackageReader>> e : allPackages.entrySet())
				{
					if (e.getValue().contains(packageToRemove))
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
					packagesWithGloballyDefinedTypes.remove(packageNameToRemove);

					PackageSpecification<WebLayoutSpecification> removedLayoutPackageSpecs = cachedLayoutDescriptions.remove(packageNameToRemove); // layouts
					if (removedLayoutPackageSpecs != null)
						for (String specName : removedLayoutPackageSpecs.getSpecifications().keySet())
					{
						allLayoutSpecifications.remove(specName);
						removedOrReloadedSpecs.add(specName);
					}
					PackageSpecification< ? extends WebObjectSpecification> removedWebObjectPackageSpecs = cachedComponentOrServiceDescriptions
						.remove(packageNameToRemove); // components/services
					if (removedWebObjectPackageSpecs != null)
						for (String specName : removedWebObjectPackageSpecs.getSpecifications().keySet())
					{
						allWebObjectSpecifications.remove(specName);
						removedOrReloadedSpecs.add(specName);
					}
					List<IPackageReader> oldPackageReader = allPackages.remove(packageNameToRemove);
					if (oldPackageReader != null)
					{
						// first remove all of them from the package readers
						packageReaders.removeAll(oldPackageReader);
						// remove the one we really want to remove from this list
						oldPackageReader.remove(packageToRemove);
						// then add the list back into the packagesToAdd so they are added
						if (oldPackageReader.size() > 0)
						{
							for (IPackageReader pr : oldPackageReader)
							{
								if (!packagesToAdd.contains(pr) && !packagesToRemove.contains(pr))
								{
									packagesToAdd.add(pr);
								}
								pr.clearError();
							}
						}
					}
				}
				else
				{
					log.warn("Cannot unload an ng package: " + packageToRemove);
				}
			}

			if (shouldReloadAllDueToGlobalTypeChanges)
			{
				load(); // reload all because removed packages had global types
				shouldStillFireReloadListeners = false;
			}
			else
			{
				// load new packages
				packageReaders.addAll(packagesToAdd);

				List<Package> packages = new ArrayList<>();
				for (IPackageReader packageReader : packagesToAdd)
				{
					packages.add(new Package(packageReader));
				}
				try
				{
					if (readGloballyDefinedTypes(packages))
					{
						load(); // reload all because added packages have global types
						shouldStillFireReloadListeners = false;
					}
					else
					{
						cacheWebObjectSpecs(packages);
					}
				}
				finally
				{
					for (Package p : packages)
					{
						p.dispose();
					}
				}
			}
		}

		if (shouldStillFireReloadListeners)
		{
			specReloadSubject.fireWebObjectSpecificationReloaded(removedOrReloadedSpecs);

			HashSet<String> allSpecs = new HashSet<>(allWebObjectSpecifications.keySet());
			allSpecs.addAll(allLayoutSpecifications.keySet());
			specReloadSubject.removeOtherSpecReloadListeners(allSpecs);
		}
	}

	protected synchronized boolean readGloballyDefinedTypes(List<Package> packages)
	{
		boolean globalTypesFound = false;
		try
		{
			JSONObject typeContainer = new JSONObject();
			JSONObject allGlobalTypesFromAllPackages = new JSONObject();
			typeContainer.put(WebObjectSpecification.TYPES_KEY, allGlobalTypesFromAllPackages);

			for (Package p : packages)
			{
				try
				{
					if (p.appendGlobalTypesJSON(allGlobalTypesFromAllPackages))
					{
						globalTypesFound = true;
						packagesWithGloballyDefinedTypes.add(p.getPackageName());
					}
				}
				catch (Exception e)
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
		return globalTypesFound;
	}

	private void cacheWebObjectSpecs(List<Package> packages)
	{
		for (Package p : packages)
		{
			try
			{
				cache(p);
			}
			catch (Exception e)
			{
				log.error("Cannot read web component/service or layout specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
		}
	}

	private void cache(Package p) throws IOException
	{
		List<IPackageReader> list = allPackages.get(p.getPackageName());
		if (list == null)
		{
			list = new ArrayList<>(3);
			allPackages.put(p.getPackageName(), list);
		}
		else if (list.size() > 0)
		{
			IPackageReader oldPackage = list.get(0);
			if (oldPackage != null)
			{
				File pResource = p.getReader().getResource();
				File oldPackageResource = oldPackage.getResource();

				String packageBundleVersion = p.getReader().getVersion();
				String oldPackageBundleVersion = oldPackage.getVersion();

				// if we have duplicate packages, and one is a project (its resource is a folder)
				// and the other one is a file (zip), then just use the project, and skip the error
				boolean isPackageConflict = true;
				if (pResource != null && oldPackageResource != null)
				{
					if (oldPackageResource.isFile() && pResource.isDirectory())
					{
						list.remove(oldPackage);
						isPackageConflict = false;
					}
					else if (oldPackageResource.isDirectory() && pResource.isFile())
					{
						isPackageConflict = false;
					}
					else if (packageBundleVersion.equalsIgnoreCase(oldPackageBundleVersion))
					{
						isPackageConflict = false;
					}
				}

				if (isPackageConflict)
				{
					log.error("Conflict found! Duplicate web component / web service / web layout package name: " + oldPackage.getPackageName());
					log.error("Location 1 : " + oldPackage.getPackageURL());
					log.error("Location 2 : " + p.getReader().getPackageURL());
					log.error("Will discard location 1 and load location 2... But this should be adressed by the solution.");
					p.getReader().reportError("", new DuplicateEntityException("Duplicate package found: " + oldPackage.getPackageName() + " (" +
						oldPackage.getPackageURL() + ")"));
				}
			}
		}
		list.add(p.getReader());

		// cache component or service specs if available
		PackageSpecification<WebObjectSpecification> webComponentPackageSpecification = p.getWebObjectDescriptions(attributeName,
			defaultComponentPropertiesProvider);
		if (webComponentPackageSpecification.getSpecifications().size() > 0 &&
			!cachedComponentOrServiceDescriptions.containsKey(webComponentPackageSpecification.getPackageName()))
		{
			cachedComponentOrServiceDescriptions.put(webComponentPackageSpecification.getPackageName(), webComponentPackageSpecification);
			Map<String, WebObjectSpecification> webComponentDescriptions = webComponentPackageSpecification.getSpecifications();
			for (WebObjectSpecification desc : webComponentDescriptions.values())
			{
				WebObjectSpecification old = allWebObjectSpecifications.put(desc.getName(), desc); // TODO should we check against allLayoutSpecifications as well?
				if (old != null)
				{
					String s = "Duplicate web object definition found; name: " + old.getName() + ". One is in package '" + old.getPackageName() +
						"' and another in package '" +
						desc.getPackageName() + "'.";
					log.error(s);
					p.getReader().reportError(desc.getSpecURL().toString(), new DuplicateEntityException(s));
				}
			}
		}

		// cache layout specs if available
		PackageSpecification<WebLayoutSpecification> layoutPackageSpecification = p.getLayoutDescriptions();
		if (layoutPackageSpecification.getSpecifications().size() > 0 &&
			!cachedLayoutDescriptions.containsKey(layoutPackageSpecification.getPackageName()))
		{
			cachedLayoutDescriptions.put(layoutPackageSpecification.getPackageName(), layoutPackageSpecification);
			Map<String, WebLayoutSpecification> layoutDescriptions = layoutPackageSpecification.getSpecifications();
			for (WebLayoutSpecification desc : layoutDescriptions.values())
			{
				WebObjectSpecification old = allLayoutSpecifications.put(desc.getName(), desc); // TODO should we check against allWebObjectSpecifications as well?
				if (old != null)
				{
					String s = "Duplicate layout definition found; name: " + old.getName() + ". One is in package '" + old.getPackageName() +
						"' and one in package '" + desc.getPackageName() + "'.";
					log.error(s);
					p.getReader().reportError(desc.getSpecURL().toString(), new DuplicateEntityException(s));
				}
			}
		}
	}

	/**
	 * Get the current state of spec providers, returns an immutable state.
	 */
	public synchronized SpecProviderState getSpecProviderState()
	{
		if (specProviderState == null)
		{
			specProviderState = new SpecProviderState(cachedComponentOrServiceDescriptions, cachedLayoutDescriptions, allWebObjectSpecifications,
				packageReaders);
		}
		return specProviderState;
	}


}
