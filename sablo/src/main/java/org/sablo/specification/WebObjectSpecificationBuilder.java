/*
 * Copyright (C) 2019 Servoy BV
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

import java.util.Map;

import org.json.JSONArray;

/**
 * @author lvostinar
 *
 */
public class WebObjectSpecificationBuilder extends PropertyDescriptionBuilder
{
	protected String packageName = "";
	protected String packageType;
	protected String displayName;
	protected String categoryName;
	protected String icon;
	protected String preview;
	protected String definition = "";
	protected JSONArray libraries;

	public WebObjectSpecificationBuilder()
	{
	}

	public WebObjectSpecificationBuilder withPackageName(String packageName)
	{
		this.packageName = packageName;
		return this;
	}

	public WebObjectSpecificationBuilder withPackageType(String packageType)
	{
		this.packageType = packageType;
		return this;
	}

	public WebObjectSpecificationBuilder withDisplayName(String displayName)
	{
		this.displayName = displayName;
		return this;
	}

	public WebObjectSpecificationBuilder withCategoryName(String categoryName)
	{
		this.categoryName = categoryName;
		return this;
	}

	public WebObjectSpecificationBuilder withIcon(String icon)
	{
		this.icon = icon;
		return this;
	}

	public WebObjectSpecificationBuilder withPreview(String preview)
	{
		this.preview = preview;
		return this;
	}

	public WebObjectSpecificationBuilder withDefinition(String definition)
	{
		this.definition = definition;
		return this;
	}

	public WebObjectSpecificationBuilder withLibraries(JSONArray libraries)
	{
		this.libraries = libraries;
		return this;
	}

	@Override
	public WebObjectSpecificationBuilder withName(String name)
	{
		if (this.displayName == null)
		{
			this.displayName = name;
		}
		return (WebObjectSpecificationBuilder)super.withName(name);
	}

	@Override
	public WebObjectSpecificationBuilder withProperties(Map<String, PropertyDescription> propertiesList)
	{
		return (WebObjectSpecificationBuilder)super.withProperties(propertiesList);
	}

	@Override
	public WebObjectSpecificationBuilder withDeprecated(String deprecated)
	{
		return (WebObjectSpecificationBuilder)super.withDeprecated(deprecated);
	}

	@Override
	public WebObjectSpecification build()
	{
		return new WebObjectSpecification(name, packageName, packageType, displayName, categoryName, icon, preview, definition, libraries, config, properties,
			deprecated);
	}
}
