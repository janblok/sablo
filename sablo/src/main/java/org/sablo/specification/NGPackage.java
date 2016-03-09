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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.servlet.ServletContext;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstraction of package that contains Servoy web-components.
 * @author acostescu
 */
public class NGPackage
{
	private static final Logger log = LoggerFactory.getLogger(NGPackage.class.getCanonicalName());
	private static final String GLOBAL_TYPES_MANIFEST_ATTR = "Global-Types";
	private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName"; // for package name
	private static final String BUNDLE_NAME = "Bundle-Name"; // for package display name

	public interface IPackageReader
	{
		String getName();

		String getPackageName();

		String getPackageDisplayname();

		Manifest getManifest() throws IOException;

		String readTextFile(String path, Charset charset) throws IOException;

		URL getUrlForPath(String path) throws MalformedURLException;

		URL getPackageURL();

		/**
		 * @param specpath
		 * @param e
		 */
		void reportError(String specpath, Exception e);

	}
	public interface ISpecificationFilter
	{
		boolean filter(WebObjectSpecification spec);
	}

	private IPackageReader reader;

	public NGPackage(IPackageReader reader)
	{
		if (reader == null) throw new NullPointerException();
		this.reader = reader;
	}

	public String getName()
	{
		return reader.getName();
	}

	public String getPackageName()
	{
		return reader.getPackageName();
	}

	IPackageReader getReader()
	{
		return reader;
	}

	public void appendGlobalTypesJSON(JSONObject allGlobalTypesFromAllPackages) throws IOException
	{
		Manifest mf = reader.getManifest();

		if (mf != null)
		{
			Attributes mainAttrs = mf.getMainAttributes();
			if (mainAttrs != null)
			{
				String globalTypesSpecPath = mainAttrs.getValue(GLOBAL_TYPES_MANIFEST_ATTR);
				if (globalTypesSpecPath != null)
				{
					try
					{
						String specfileContent = reader.readTextFile(globalTypesSpecPath, Charset.forName("UTF8")); // TODO: check encoding
						if (specfileContent != null)
						{
							JSONObject json = new JSONObject(specfileContent);
							Object types = json.get(WebObjectSpecification.TYPES_KEY);
							if (types instanceof JSONObject)
							{
								Iterator<String> typesIt = ((JSONObject)types).keys();
								while (typesIt.hasNext())
								{
									String key = typesIt.next();
									allGlobalTypesFromAllPackages.put(key, ((JSONObject)types).get(key));
								}
							}
						}
					}
					catch (Exception e)
					{
						reader.reportError(globalTypesSpecPath, e);
					}
				}
			}
		}
	}

	public NGPackageSpecification<WebObjectSpecification> getWebComponentDescriptions(String attributeName) throws IOException
	{
		String packageName = null;
		String packageDisplayname = null;
		Map<String, WebObjectSpecification> descriptions = new HashMap<>();
		Manifest mf = reader.getManifest();
		if (mf != null)
		{
			packageName = reader.getPackageName();
			packageDisplayname = reader.getPackageDisplayname();

			for (String specpath : getWebEntrySpecNames(mf, attributeName))
			{
				String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
				if (specfileContent != null)
				{
					try
					{
						WebObjectSpecification parsed = WebObjectSpecification.parseSpec(specfileContent, reader.getPackageName(), reader);
						if (reader instanceof ISpecificationFilter && ((ISpecificationFilter)reader).filter(parsed)) continue;
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						if (parsed.getDefinition() != null)
						{
							String definition;
							if (packageName != null && parsed.getDefinition().startsWith(packageName + '/'))
							{
								definition = parsed.getDefinition().substring(packageName.length() + 1);
							}
							else if (packageName != null && parsed.getDefinition().startsWith("/"))
							{
								definition = parsed.getDefinition();
							}
							else
							{
								log.warn("Definition file for spec file " + specpath + " does not start with package name '" + packageName + "'");
								definition = parsed.getDefinition().substring(parsed.getDefinition().indexOf("/") + 1);
							}
							parsed.setDefinitionFileURL(reader.getUrlForPath(definition));
						}
						descriptions.put(parsed.getName(), parsed);
					}
					catch (Exception e)
					{
						reader.reportError(specpath, e);
					}
				}
				else
				{
					log.warn("could not read specification files content of " + specpath + ", url is not resolved, casing problem?");
				}
			}
		}

		return new NGPackageSpecification<>(packageName, packageDisplayname, descriptions, mf);
	}

	public NGPackageSpecification<WebLayoutSpecification> getLayoutDescriptions() throws IOException
	{
		String packageName = null;
		String packageDisplayname = null;
		Map<String, WebLayoutSpecification> descriptions = new HashMap<>();
		Manifest mf = reader.getManifest();
		if (mf != null)
		{
			packageName = reader.getPackageName();
			packageDisplayname = reader.getPackageDisplayname();
			for (String specpath : getWebEntrySpecNames(mf, "Web-Layout"))
			{
				String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
				if (specfileContent != null)
				{
					try
					{
						WebLayoutSpecification parsed = WebLayoutSpecification.parseLayoutSpec(specfileContent, packageName, reader);
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						if (parsed.getDefinition() != null)
							parsed.setDefinitionFileURL(reader.getUrlForPath(parsed.getDefinition().substring(parsed.getDefinition().indexOf("/") + 1)));
						descriptions.put(parsed.getName(), parsed);
					}
					catch (Exception e)
					{
						reader.reportError(specpath, e);
					}
				}
			}
			for (String specpath : getWebEntrySpecNames(mf, "Web-Composite"))
			{
				String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
				if (specfileContent != null)
				{
					try
					{
						WebLayoutSpecification parsed = WebLayoutSpecification.parseLayoutSpec(specfileContent, packageName, reader);
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						if (parsed.getDefinition() != null)
							parsed.setDefinitionFileURL(reader.getUrlForPath(parsed.getDefinition().substring(parsed.getDefinition().indexOf("/") + 1)));
						descriptions.put(parsed.getName(), parsed);
					}
					catch (Exception e)
					{
						reader.reportError(specpath, e);
					}
				}
			}
		}
		return new NGPackageSpecification<>(packageName, packageDisplayname, descriptions, mf);
	}

	private static List<String> getWebEntrySpecNames(Manifest mf, String attributeName)
	{
		List<String> names = new ArrayList<String>();
		for (Entry<String, Attributes> entry : mf.getEntries().entrySet())
		{
			if ("true".equalsIgnoreCase((String)entry.getValue().get(new Attributes.Name(attributeName))))
			{
				names.add(entry.getKey());
			}
		}

		return names;
	}

	public void dispose()
	{
		reader = null;
	}

	public static class JarServletContextReader implements IPackageReader
	{
		private final ServletContext servletContext;
		private final String resourcePath;

		private Manifest manifest = null;

		public JarServletContextReader(ServletContext servletContext, String resourcePath)
		{
			this.servletContext = servletContext;
			this.resourcePath = resourcePath;
		}

		@Override
		public String getName()
		{
			return resourcePath;
		}

		@Override
		public String getPackageName()
		{
			try
			{
				String packageName = NGPackage.getPackageName(getManifest());
				if (packageName != null) return packageName;
			}
			catch (Exception e)
			{
				log.error("Error getting package name." + getName(), e);
			}
			return FilenameUtils.getBaseName(resourcePath);
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = NGPackage.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (IOException e)
			{
				log.error("Error getting package display name." + getName(), e);
			}

			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			if (manifest == null)
			{
				try (JarInputStream jarInputStream = new JarInputStream(servletContext.getResourceAsStream(resourcePath)))
				{
					manifest = jarInputStream.getManifest();
				}
			}
			return manifest;
		}

		@Override
		public URL getUrlForPath(String path) throws MalformedURLException
		{
			return servletContext.getResource(path.charAt(0) == '/' ? path : '/' + getPackageName() + '/' + path);
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			String pathWithSlashPrefix = path.charAt(0) == '/' ? path : '/' + path;
			try (InputStream inputStream = servletContext.getResourceAsStream(pathWithSlashPrefix))
			{
				if (inputStream != null)
				{
					return IOUtils.toString(inputStream, charset);
				}
			}
			return null;
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package '" + toString() + "'. ", e);
		}

		@Override
		public String toString()
		{
			return "JarPackage: " + getName();
		}

		@Override
		public URL getPackageURL()
		{
			try
			{
				return servletContext.getResource(resourcePath);
			}
			catch (MalformedURLException e)
			{
				log.error("MalformedURL", e);
			}
			return null;
		}
	}

	public static class JarPackageReader implements IPackageReader
	{

		private final File jarFile;

		public JarPackageReader(File jarFile)
		{
			this.jarFile = jarFile;
		}

		@Override
		public String getName()
		{
			return jarFile.getAbsolutePath();
		}

		@Override
		public String getPackageName()
		{
			try
			{
				String packageName = NGPackage.getPackageName(getManifest());
				if (packageName != null) return packageName;
			}
			catch (Exception e)
			{
				log.error("Error getting package name " + jarFile.getAbsolutePath(), e);
			}
			return FilenameUtils.getBaseName(jarFile.getAbsolutePath());
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = NGPackage.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (IOException e)
			{
				log.error("Error getting package display name " + jarFile.getAbsolutePath(), e);
			}

			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			try (JarFile jar = new JarFile(jarFile))
			{
				return jar.getManifest();
			}
		}

		@Override
		public URL getUrlForPath(String path)
		{
			String pathWithSlashPrefix = path.startsWith("/") ? path : "/" + path;
			try (JarFile jar = new JarFile(jarFile))
			{
				JarEntry entry = jar.getJarEntry(pathWithSlashPrefix.substring(1)); // strip /
				if (entry != null)
				{
					return new URL("jar:" + jarFile.toURI().toURL() + "!" + pathWithSlashPrefix);
				}
			}
			catch (IOException e)
			{
				log.error("Exception in getUrlForPath", e);
			}

			return null;
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			try (JarFile jar = new JarFile(jarFile))
			{
				JarEntry entry = jar.getJarEntry(path);
				if (entry != null)
				{
					return IOUtils.toString(jar.getInputStream(entry), charset);
				}
			}

			return null;
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package '" + toString() + "'. ", e);
		}

		@Override
		public String toString()
		{
			return "JarPackage: " + jarFile.getAbsolutePath();
		}

		@Override
		public URL getPackageURL()
		{
			try
			{
				return jarFile.toURI().toURL();
			}
			catch (MalformedURLException e)
			{
				log.error("MalformedURL", e);
			}
			return null;
		}

	}

	public static class DirPackageReader implements IPackageReader
	{

		private final File dir;

		public DirPackageReader(File dir)
		{
			if (!dir.isDirectory()) throw new IllegalArgumentException("Non-directory package cannot be read by directory reader: " + dir.getAbsolutePath());
			this.dir = dir;
		}

		@Override
		public String getName()
		{
			return dir.getAbsolutePath();
		}

		@Override
		public String getPackageName()
		{
			try
			{
				String packageName = NGPackage.getPackageName(getManifest());
				if (packageName != null) return packageName;
			}
			catch (IOException e)
			{
				log.error("Error getting package name", e);
			}
			return dir.getName();
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = NGPackage.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (IOException e)
			{
				log.error("Error getting package display name", e);
			}

			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			try (InputStream is = new BufferedInputStream(new FileInputStream(new File(dir, "META-INF/MANIFEST.MF"))))
			{
				return new Manifest(is);
			}
		}

		@Override
		public URL getUrlForPath(String path)
		{
			File file = new File(dir, path);
			if (file.exists())
			{
				try
				{
					return file.toURI().toURL();
				}
				catch (MalformedURLException e)
				{
					log.error("MalformedURLException", e);
				}
			}
			return null;
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			try (InputStream is = new BufferedInputStream(new FileInputStream(new File(dir, path))))
			{
				return IOUtils.toString(is, charset);
			}
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package '" + toString() + "'. ", e);
		}

		@Override
		public String toString()
		{
			return "DirPackage: " + dir.getAbsolutePath();
		}

		@Override
		public URL getPackageURL()
		{
			try
			{
				return dir.toURI().toURL();
			}
			catch (MalformedURLException e)
			{
				log.error("MalformedURLException", e);
			}
			return null;
		}
	}

	public static class WarURLPackageReader implements NGPackage.IPackageReader, NGPackage.ISpecificationFilter
	{
		private final URL urlOfManifest;
		private final String packageName;
		private final ServletContext servletContext;
		private HashSet<String> exportedComponents;

		public WarURLPackageReader(ServletContext servletContext, String packageName) throws MalformedURLException
		{
			this.packageName = packageName.endsWith("/") ? packageName : packageName + "/";
			this.urlOfManifest = servletContext.getResource(this.packageName + "META-INF/MANIFEST.MF");
			this.servletContext = servletContext;
			if (urlOfManifest == null)
			{
				throw new IllegalArgumentException("Package " + this.packageName + "META-INF/MANIFEST.MF not found in this context");
			}
			try
			{
				if (servletContext.getResource("/WEB-INF/exported_components.properties") != null)
				{
					InputStream is = servletContext.getResourceAsStream("/WEB-INF/exported_components.properties");
					Properties properties = new Properties();
					properties.load(is);
					exportedComponents = new HashSet<String>(Arrays.asList(properties.getProperty("components").split(",")));
				}
			}
			catch (Exception e)
			{
				throw new IllegalArgumentException("Exception during init exported_components.properties reading", e);
			}
		}

		@Override
		public String getName()
		{
			return urlOfManifest.toExternalForm();
		}

		@Override
		public String getPackageName()
		{
			return packageName.replaceAll("/", "");
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = NGPackage.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (IOException e)
			{
				log.error("getting package display name." + getName(), e);
			}

			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			try (InputStream is = urlOfManifest.openStream())
			{
				return new Manifest(is);
			}
		}

		@Override
		public URL getUrlForPath(String path)
		{
			try
			{
				return servletContext.getResource(packageName + path);// path includes /
			}
			catch (MalformedURLException e)
			{
				log.error("MalformedURLException", e);
				return null;
			}
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			URL url = getUrlForPath(path);
			if (url == null) return null;

			try (InputStream is = url.openStream())
			{
				return IOUtils.toString(is, charset);
			}
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package 'WarReeader[ " + urlOfManifest + " ]'. ", e);
		}

		@Override
		public URL getPackageURL()
		{
			return null;
		}

		/**
		 * @param spec
		 * @return true if the component is not in the list of the exported components
		 */
		@Override
		public boolean filter(WebObjectSpecification spec)
		{
			return exportedComponents != null && !exportedComponents.contains(spec.getName());
		}
	}

	@Override
	public String toString()
	{
		return "NGpackage: " + getPackageName();
	}

	/**
	 * @param manifest
	 * @return
	 */
	public static String getPackageName(Manifest manifest)
	{
		String bundleName = null;
		if (manifest != null)
		{
			bundleName = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME);

			if (bundleName != null && bundleName.indexOf(';') > 0)
			{
				return bundleName.substring(0, bundleName.indexOf(';')).trim();
			}
		}
		return bundleName;
	}

	public static String getPackageDisplayname(Manifest manifest)
	{
		if (manifest == null) return null;
		return manifest.getMainAttributes().getValue(BUNDLE_NAME);
	}

	public static class DuplicatePackageException extends Exception
	{
		public DuplicatePackageException(String message)
		{
			super(message);
		}
	}
}
