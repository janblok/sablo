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
import java.util.jar.Manifest;

import javax.servlet.ServletContext;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.sablo.specification.property.types.DimensionPropertyType;
import org.sablo.specification.property.types.IntPropertyType;
import org.sablo.specification.property.types.PointPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstraction of package that contains Servoy web-components.
 * @author acostescu
 */
public class WebComponentPackage
{
	private static final Logger log = LoggerFactory.getLogger(WebComponentPackage.class.getCanonicalName());
	private static final String GLOBAL_TYPES_MANIFEST_ATTR = "Global-Types";
	private static final String BUNDLE_NAME = "Bundle-Name";

	public interface IPackageReader
	{
		String getName();

		String getPackageName();

		Manifest getManifest() throws IOException;

		String readTextFile(String path, Charset charset) throws IOException;

		URL getUrlForPath(String path);

		URL getPackageURL();

		/**
		 * @param specpath
		 * @param e
		 */
		void reportError(String specpath, Exception e);

	}
	public interface ISpecificationFilter
	{
		boolean filter(WebComponentSpecification spec);
	}

	private IPackageReader reader;

	public WebComponentPackage(IPackageReader reader)
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
							Object types = json.get(WebComponentSpecification.TYPES_KEY);
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

	public List<WebComponentSpecification> getWebComponentDescriptions() throws IOException
	{
		ArrayList<WebComponentSpecification> descriptions = new ArrayList<>();
		Manifest mf = reader.getManifest();

		if (mf != null)
		{
			for (String specpath : getWebEntrySpecNames(mf, "Web-Component"))
			{
				String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
				if (specfileContent != null)
				{
					try
					{
						WebComponentSpecification parsed = WebComponentSpecification.parseSpec(specfileContent, reader.getPackageName(), reader);
						if (reader instanceof ISpecificationFilter && ((ISpecificationFilter)reader).filter(parsed)) continue;
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						if (parsed.getDefinition() != null) parsed.setDefinitionFileURL(reader.getUrlForPath(parsed.getDefinition().substring(
							parsed.getDefinition().indexOf("/") + 1)));
						// add properties defined by us
						// TODO this is servoy specific so remove?
						if (parsed.getProperty("size") == null) parsed.putProperty("size",
							new PropertyDescription("size", TypesRegistry.getType(DimensionPropertyType.TYPE_NAME)));
						if (parsed.getProperty("location") == null) parsed.putProperty("location",
							new PropertyDescription("location", TypesRegistry.getType(PointPropertyType.TYPE_NAME)));
						if (parsed.getProperty("anchors") == null) parsed.putProperty("anchors",
							new PropertyDescription("anchors", TypesRegistry.getType(IntPropertyType.TYPE_NAME)));
						descriptions.add(parsed);
					}
					catch (Exception e)
					{
						reader.reportError(specpath, e);
					}
				}
			}

			for (String specpath : getWebEntrySpecNames(mf, "Web-Service"))
			{
				String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
				if (specfileContent != null)
				{
					try
					{
						WebComponentSpecification parsed = WebComponentSpecification.parseSpec(specfileContent, reader.getPackageName(), reader);
						if (reader instanceof ISpecificationFilter && ((ISpecificationFilter)reader).filter(parsed)) continue;
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						if (parsed.getDefinition() != null) parsed.setDefinitionFileURL(reader.getUrlForPath(parsed.getDefinition().substring(
							parsed.getDefinition().indexOf("/") + 1)));
						descriptions.add(parsed);
					}
					catch (Exception e)
					{
						reader.reportError(specpath, e);
					}
				}
			}
		}
		return descriptions;
	}

	/**
	 * @return
	 * @throws IOException
	 */
	public Map<String, WebLayoutSpecification> getLayoutDescriptions() throws IOException
	{
		Map<String, WebLayoutSpecification> descriptions = new HashMap<>();
		Manifest mf = reader.getManifest();
		for (String specpath : getWebEntrySpecNames(mf, "Web-Layout"))
		{
			String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
			if (specfileContent != null)
			{
				try
				{
					WebLayoutSpecification parsed = WebLayoutSpecification.parseLayoutSpec(specfileContent, reader.getPackageName(), reader);
					parsed.setSpecURL(reader.getUrlForPath(specpath));
					if (parsed.getDefinition() != null) parsed.setDefinitionFileURL(reader.getUrlForPath(parsed.getDefinition().substring(
						parsed.getDefinition().indexOf("/") + 1)));
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
					WebLayoutSpecification parsed = WebLayoutSpecification.parseLayoutSpec(specfileContent, reader.getPackageName(), reader);
					parsed.setSpecURL(reader.getUrlForPath(specpath));
					if (parsed.getDefinition() != null) parsed.setDefinitionFileURL(reader.getUrlForPath(parsed.getDefinition().substring(
						parsed.getDefinition().indexOf("/") + 1)));
					descriptions.put(parsed.getName(), parsed);
				}
				catch (Exception e)
				{
					reader.reportError(specpath, e);
				}
			}
		}
		return descriptions;
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

	public static class JarPackageReader implements IPackageReader
	{

		private final File jarFile;

		public JarPackageReader(File jarFile)
		{
			this.jarFile = jarFile;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getName()
		 */
		@Override
		public String getName()
		{
			return jarFile.getAbsolutePath();
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getPackageName()
		 */
		@Override
		public String getPackageName()
		{
			try
			{
				String bundleName = getManifest().getMainAttributes().getValue(BUNDLE_NAME);
				if (bundleName != null) return bundleName;
			}
			catch (IOException e)
			{
				log.error("Bundle Name attribute not found.", e);
			}
			return FilenameUtils.getBaseName(jarFile.getAbsolutePath());
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			JarFile jar = null;
			try
			{
				jar = new JarFile(jarFile);
				return jar.getManifest();
			}
			finally
			{
				if (jar != null) jar.close();
			}
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getUrlForPath(java.lang.String)
		 */
		@Override
		public URL getUrlForPath(String path)
		{
			JarFile jar = null;
			try
			{
				String pathWithSlashPrefix = path.startsWith("/") ? path : "/" + path;
				jar = new JarFile(jarFile);
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
			finally
			{
				if (jar != null) try
				{
					jar.close();
				}
				catch (IOException e)
				{
				}
			}
			return null;
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			JarFile jar = null;
			try
			{
				jar = new JarFile(jarFile);
				JarEntry entry = jar.getJarEntry(path);
				if (entry != null)
				{
					InputStream is = jar.getInputStream(entry);
					return IOUtils.toString(is, charset);
				}
			}
			finally
			{
				if (jar != null) jar.close();
			}
			return null;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.WebComponentPackage.IPackageReader#reportError(java.lang.String, java.lang.Exception)
		 */
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

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.sablo.specification.WebComponentPackage.IPackageReader#getPackageURL()
		 */
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

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getName()
		 */
		@Override
		public String getName()
		{
			return dir.getAbsolutePath();
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getPackageName()
		 */
		@Override
		public String getPackageName()
		{
			try
			{
				String bundleName = getManifest().getMainAttributes().getValue(BUNDLE_NAME);
				if (bundleName != null) return bundleName;
			}
			catch (IOException e)
			{
				log.error("Bundle Name attribute not found.", e);
			}
			return dir.getName();
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			InputStream is = null;
			try
			{
				is = new BufferedInputStream(new FileInputStream(new File(dir, "META-INF/MANIFEST.MF")));
				return new Manifest(is);
			}
			finally
			{
				if (is != null) is.close();
			}
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getUrlForPath(java.lang.String)
		 */
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
			InputStream is = null;
			try
			{
				is = new BufferedInputStream(new FileInputStream(new File(dir, path)));
				return IOUtils.toString(is, charset);
			}
			finally
			{
				if (is != null) is.close();
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

		/*
		 * (non-Javadoc)
		 *
		 * @see org.sablo.specification.WebComponentPackage.IPackageReader#getPackageURL()
		 */
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

	public static class WarURLPackageReader implements WebComponentPackage.IPackageReader, WebComponentPackage.ISpecificationFilter
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

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getName()
		 */
		@Override
		public String getName()
		{
			return urlOfManifest.toExternalForm();
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getPackageName()
		 */
		@Override
		public String getPackageName()
		{
			return packageName.replaceAll("/", "");
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			InputStream is = urlOfManifest.openStream();
			try
			{
				Manifest manifest = new Manifest();
				manifest.read(is);
				return manifest;
			}
			finally
			{
				is.close();
			}
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.j2db.server.ngclient.component.WebComponentPackage.IPackageReader#getUrlForPath(java.lang.String)
		 */
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
			InputStream is = null;
			try
			{
				is = url.openStream();
				return IOUtils.toString(is, charset);
			}
			finally
			{
				if (is != null) is.close();
			}
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package 'WarReeader[ " + urlOfManifest + " ]'. ", e);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.sablo.specification.WebComponentPackage.IPackageReader#getPackageURL()
		 */
		@Override
		public URL getPackageURL()
		{
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.sablo.specification.WebComponentPackage.ISpecificationFilter#filter(org.sablo.specification.WebComponentSpecification)
		 */
		/**
		 * @param spec
		 * @return true if the component is not in the list of the exported components
		 */
		@Override
		public boolean filter(WebComponentSpecification spec)
		{
			return exportedComponents != null && !exportedComponents.contains(spec.getName());
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return "WebComponnetPackage: " + getPackageName();
	}

}