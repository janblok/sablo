/*
 * Copyright (C) 2015 Servoy BV
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
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.sablo.specification.Package.IPackageReader;

/**
 * @author lvostinar
 *
 */
public abstract class BaseSpecProvider
{
	protected final WebSpecReader reader;

	protected BaseSpecProvider(WebSpecReader reader)
	{
		this.reader = reader;
	}

	/**
	 * Get the set of all package names.
	 */
	public Set<String> getPackageNames()
	{
		return reader.getWebObjectSpecifications().keySet();
	}

	/**
	 * Get the map of names to package URLs.
	 */
	public Map<String, URL> getPackagesToURLs()
	{
		return reader.getPackagesToURLs();
	}

	/**
	 * Get the map of names to package versions.
	 * @throws IOException
	 */
	public Map<String, String> getPackagesToVersions() throws IOException
	{
		return reader.getPackagesToVersions();
	}

	public String getPackageDisplayName(String packageName)
	{
		return reader.getPackagesToDisplayNames().get(packageName);
	}

	public String getPackageName(String displayName)
	{
		if (displayName != null)
		{
			Map<String, String> names = reader.getPackagesToDisplayNames();
			for (String name : names.keySet())
			{
				if (displayName.equals(names.get(name)))
				{
					return name;
				}
			}
		}
		return displayName;
	}

	/**
	 * Updates available packages. "toRemove" provided contents will be removed, "toAdd" will be made available.
	 */
	public void updatePackages(Collection<String> toRemove, Collection<IPackageReader> toAdd)
	{
		reader.updatePackages(toRemove, toAdd);
	}

	/**
	 * Forwards addition of listener to underlying reader.
	 * @see WebSpecReader#addSpecReloadListener(String, ISpecReloadListener)
	 */
	public void addSpecReloadListener(String specName, ISpecReloadListener specReloadListener)
	{
		reader.addSpecReloadListener(specName, specReloadListener);
	}

	/**
	 * Forwards addition of listener to underlying reader.
	 * @see WebSpecReader#addSpecReloadListener(String, ISpecReloadListener)
	 */
	public void removeSpecReloadListener(String specName, ISpecReloadListener specReloadListener)
	{
		reader.removeSpecReloadListener(specName, specReloadListener);
	}

	public static interface ISpecReloadListener
	{

		/**
		 * If the component's or service's specification was reloaded or removed.
		 */
		void webObjectSpecificationReloaded();

	}

}
