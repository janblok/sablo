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

import org.sablo.specification.Package.DuplicateEntityException;
import org.sablo.specification.Package.IPackageReader;
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

	private final ClientSideTypeCache clientSideTypeCache;

	private final List<IPackageReader> activePackageReaders;

	private final String attributeName;

	private final SpecReloadSubject specReloadSubject;

	private long lastLoadTimestamp;

	private SpecProviderState specProviderState;


	WebSpecReader(IPackageReader[] packageReaders, String attributeName, SpecReloadSubject specReloadSubject,
		IDefaultComponentPropertiesProvider defaultComponentPropertiesProvider)
	{
		this.activePackageReaders = new ArrayList<>(Arrays.asList(packageReaders));
		this.attributeName = attributeName;
		this.specReloadSubject = specReloadSubject;
		this.defaultComponentPropertiesProvider = defaultComponentPropertiesProvider;
		this.clientSideTypeCache = new ClientSideTypeCache();

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

			clientSideTypeCache.clear();
			cachedComponentOrServiceDescriptions.clear();
			cachedLayoutDescriptions.clear();
			allWebObjectSpecifications.clear();
			allLayoutSpecifications.clear();
			allPackages.clear();
			packagesWithGloballyDefinedTypes.clear();
			List<Package> packages = new ArrayList<>();
			for (IPackageReader packageReader : activePackageReaders)
			{
				packages.add(new Package(packageReader));
			}
			try
			{
				cacheWebObjectSpecs(packages, null);
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
					// unload package
					shouldReloadAllDueToGlobalTypeChanges = removePackageFromPackageCaches(packageNameToRemove, removedOrReloadedSpecs);

					if (shouldReloadAllDueToGlobalTypeChanges) break; // we will reload all in this case anyway

					List<IPackageReader> oldPackageReader = allPackages.remove(packageNameToRemove);
					if (oldPackageReader != null)
					{
						// first remove all of them from the package readers
						activePackageReaders.removeAll(oldPackageReader);
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
				activePackageReaders.addAll(packagesToAdd);

				List<Package> packages = new ArrayList<>();
				for (IPackageReader packageReader : packagesToAdd)
				{
					packages.add(new Package(packageReader));
				}
				try
				{
					if (cacheWebObjectSpecs(packages, removedOrReloadedSpecs)) // cache web objects
					{
						load(); // reload all again because a package project replaced a zip project and it had global types
						shouldStillFireReloadListeners = false;
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

	private boolean removePackageFromPackageCaches(String packageNameToRemove, List<String> removedOrReloadedSpecs)
	{
		if (packagesWithGloballyDefinedTypes.remove(packageNameToRemove)) return true; // a full reload will happen

		PackageSpecification<WebLayoutSpecification> removedLayoutPackageSpecs = cachedLayoutDescriptions.remove(packageNameToRemove); // layouts
		if (removedLayoutPackageSpecs != null)
			for (String specName : removedLayoutPackageSpecs.getSpecifications().keySet())
		{
			allLayoutSpecifications.remove(specName);
			if (removedOrReloadedSpecs != null) removedOrReloadedSpecs.add(specName);
		}
		PackageSpecification< ? extends WebObjectSpecification> removedWebObjectPackageSpecs = cachedComponentOrServiceDescriptions
			.remove(packageNameToRemove); // components/services
		if (removedWebObjectPackageSpecs != null)
			for (String specName : removedWebObjectPackageSpecs.getSpecifications().keySet())
		{
			allWebObjectSpecifications.remove(specName);
			clientSideTypeCache.clear(specName);
			if (removedOrReloadedSpecs != null) removedOrReloadedSpecs.add(specName);
		}

		return false;
	}

	private boolean cacheWebObjectSpecs(List<Package> packages, List<String> removedOrReloadedSpecs)
	{
		for (Package p : packages)
		{
			try
			{
				if (cache(p, removedOrReloadedSpecs)) return true;
			}
			catch (Exception e)
			{
				log.error("Cannot read web component/service or layout specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
		}

		return false;
	}

	private boolean cache(Package p, List<String> removedOrReloadedSpecs) throws IOException
	{
		List<IPackageReader> list = allPackages.get(p.getPackageName());
		if (list == null)
		{
			list = new ArrayList<>(3);
			allPackages.put(p.getPackageName(), list);
		}
		else if (list.size() > 0)
		{
			IPackageReader oldPackageReader = list.get(0);
			if (oldPackageReader != null)
			{
				File pResource = p.getReader().getResource();
				File oldPackageResource = oldPackageReader.getResource();

				String packageBundleVersion = p.getReader().getVersion();
				String oldPackageBundleVersion = oldPackageReader.getVersion();

				// if we have duplicate packages, and one is a project (its resource is a folder)
				// and the other one is a file (zip), then just use the project, and skip the error
				boolean isPackageConflict = true;
				if (pResource != null && oldPackageResource != null)
				{
					if (oldPackageResource.isFile() && pResource.isDirectory())
					{
						activePackageReaders.remove(oldPackageReader);
						String packageNameToRemove = oldPackageReader.getName();

						if (removePackageFromPackageCaches(packageNameToRemove, removedOrReloadedSpecs)) return true; // a reload will happen
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
					log.error("Conflict found! Duplicate web component / web service / web layout package name: " + oldPackageReader.getPackageName());
					log.error("Location 1 : " + oldPackageReader.getPackageURL());
					log.error("Location 2 : " + p.getReader().getPackageURL());
					log.error("Will discard location 1 and load location 2... But this should be adressed by the solution.");
					p.getReader().reportError("", new DuplicateEntityException("Duplicate package found: " + oldPackageReader.getPackageName() + " (" +
						oldPackageReader.getPackageURL() + ")"));
				}
			}
		}
		list.add(p.getReader());

		// cache component or service specs if available
		if (!cachedComponentOrServiceDescriptions.containsKey(p.getPackageName()))
		{
			PackageSpecification<WebObjectSpecification> webObjectPackageSpecification = p.getWebObjectDescriptions(attributeName,
				defaultComponentPropertiesProvider);
			if (webObjectPackageSpecification.getSpecifications().size() > 0)
			{
				cachedComponentOrServiceDescriptions.put(webObjectPackageSpecification.getPackageName(), webObjectPackageSpecification);
				Map<String, WebObjectSpecification> webComponentDescriptions = webObjectPackageSpecification.getSpecifications();
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
		}

		// cache layout specs if available
		if (!cachedLayoutDescriptions.containsKey(p.getPackageName()))
		{
			PackageSpecification<WebLayoutSpecification> layoutPackageSpecification = p.getLayoutDescriptions();
			if (layoutPackageSpecification.getSpecifications().size() > 0)
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

		return false;
	}

	public ClientSideTypeCache getClientSideTypeCache()
	{
		return clientSideTypeCache; // TODO do we need to create a copy similar to what getSpecProviderState() does?
	}

	/**
	 * Get the current state of spec providers, returns an immutable state.
	 */
	public synchronized SpecProviderState getSpecProviderState()
	{
		if (specProviderState == null)
		{
			specProviderState = new SpecProviderState(cachedComponentOrServiceDescriptions, cachedLayoutDescriptions, allWebObjectSpecifications,
				activePackageReaders);
		}
		return specProviderState;
	}

}
