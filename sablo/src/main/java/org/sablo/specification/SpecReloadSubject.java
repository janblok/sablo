/*
 * Copyright (C) 2016 Servoy BV
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Class for Subjects to which {@link ISpecReloadListener} can (de)register for modifications.
 *
 * @author rgansevles
 *
 */
public class SpecReloadSubject
{
	private final Map<String, List<ISpecReloadListener>> specReloadListeners = new HashMap<>();

	public static interface ISpecReloadListener
	{
		/**
		 * If the component's or service's specification was reloaded or removed.
		 */
		void webObjectSpecificationReloaded();
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

	void fireWebObjectSpecificationReloaded()
	{
		fireWebObjectSpecificationReloaded(null);
	}

	void fireWebObjectSpecificationReloaded(Collection<String> specNames)
	{
		for (Entry<String, List<ISpecReloadListener>> listenerEntry : specReloadListeners.entrySet())
		{
			if (listenerEntry.getKey() == null // global listeners
				|| specNames == null || specNames.contains(listenerEntry.getKey()))
			{
				List<ISpecReloadListener> lst = listenerEntry.getValue();
				ISpecReloadListener[] array = lst.toArray(new ISpecReloadListener[lst.size()]);
				for (ISpecReloadListener l : array)
				{
					l.webObjectSpecificationReloaded();
				}
			}
		}
	}

	void removeOtherSpecReloadListeners(Collection<String> specNamesToKeep)
	{
		Iterator<String> it = specReloadListeners.keySet().iterator();
		while (it.hasNext())
		{
			String specName = it.next();
			if (specName != null && !specNamesToKeep.contains(specName))
			{
				it.remove();
			}
		}
	}
}
