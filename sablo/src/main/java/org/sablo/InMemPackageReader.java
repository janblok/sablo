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
package org.sablo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.jar.Manifest;

import org.sablo.specification.WebComponentPackage.IPackageReader;

/**
 * @author jcompagner
 *
 */
public class InMemPackageReader implements IPackageReader
{

	private final String manifest;
	private final Map<String, String> files;

	public InMemPackageReader(String manifest, Map<String, String> files)
	{
		this.manifest = manifest;
		this.files = files;
	}

	@Override
	public String getName()
	{
		return "inmem";
	}

	@Override
	public String getPackageName()
	{
		return "inmem";
	}

	@Override
	public Manifest getManifest() throws IOException
	{
		return new Manifest(new ByteArrayInputStream(manifest.getBytes()));
	}

	@Override
	public String readTextFile(String path, Charset charset) throws IOException
	{
		return files.get(path);
	}

	@Override
	public URL getUrlForPath(String path)
	{
		return null;
	}

	@Override
	public void reportError(String specpath, Exception e)
	{
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

}
