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

package org.sablo.startup;

import java.net.URL;
import java.util.Enumeration;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator for Sablo osgi plugin.
 *
 * @author rgansevles
 */

public class Activator implements BundleActivator
{
	public static final String RESOURCES_PATH = "META-INF/resources/";

	private static Activator plugin;

	private BundleContext context;

	public void start(BundleContext bundleContext) throws Exception
	{
		plugin = this;
		this.context = bundleContext;
	}

	@Override
	public void stop(BundleContext bundleContext)
	{
	}

	public BundleContext getContext()
	{
		return context;
	}

	public Enumeration<String> getResourcePaths(String path)
	{
		final Enumeration<String> entryPaths = context.getBundle().getEntryPaths(RESOURCES_PATH + (path.startsWith("/") ? path.substring(1) : path));
		return new Enumeration<String>()
		{

			@Override
			public boolean hasMoreElements()
			{
				return entryPaths.hasMoreElements();
			}

			@Override
			public String nextElement()
			{
				String nextElement = entryPaths.nextElement();
				return nextElement.substring(RESOURCES_PATH.length());
			}
		};
	}

	public URL getResource(String path)
	{
		return context.getBundle().getEntry(RESOURCES_PATH + (path.startsWith("/") ? path.substring(1) : path));
	}

	public static Activator getDefault()
	{
		return plugin;
	}
}
