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

import java.util.List;
import java.util.Map;

/**
 * @author lvostinar
 *
 */
public class WebLayoutSpecificationBuilder extends WebObjectSpecificationBuilder
{
	private boolean topContainer;
	private String layout;
	private List<String> allowedChildren;
	private String designStyleClass;
	private List<String> excludedChildren;

	public WebLayoutSpecificationBuilder()
	{

	}

	public WebLayoutSpecificationBuilder withTopContainer(boolean topContainer)
	{
		this.topContainer = topContainer;
		return this;
	}

	public WebLayoutSpecificationBuilder withLayout(String layout)
	{
		this.layout = layout;
		return this;
	}

	public WebLayoutSpecificationBuilder withDesignStyleClass(String designStyleClass)
	{
		this.designStyleClass = designStyleClass;
		return this;
	}

	public WebLayoutSpecificationBuilder withAllowedChildren(List<String> allowedChildren)
	{
		this.allowedChildren = allowedChildren;
		return this;
	}

	public WebLayoutSpecificationBuilder withExcludedChildren(List<String> excludedChildren)
	{
		this.excludedChildren = excludedChildren;
		return this;
	}

	@Override
	public WebLayoutSpecificationBuilder withProperties(Map<String, PropertyDescription> propertiesList)
	{
		return (WebLayoutSpecificationBuilder)super.withProperties(propertiesList);
	}

	@Override
	public WebLayoutSpecificationBuilder withConfig(Object config)
	{
		return (WebLayoutSpecificationBuilder)super.withConfig(config);
	}

	@Override
	public WebLayoutSpecificationBuilder withName(String name)
	{
		return (WebLayoutSpecificationBuilder)super.withName(name);
	}

	@Override
	public WebLayoutSpecificationBuilder withPackageName(String packageName)
	{
		return (WebLayoutSpecificationBuilder)super.withPackageName(packageName);
	}

	@Override
	public WebLayoutSpecificationBuilder withPackageType(String packageType)
	{
		return (WebLayoutSpecificationBuilder)super.withPackageType(packageType);
	}

	@Override
	public WebLayoutSpecificationBuilder withCategoryName(String categoryName)
	{
		return (WebLayoutSpecificationBuilder)super.withCategoryName(categoryName);
	}

	@Override
	public WebLayoutSpecificationBuilder withDisplayName(String displayName)
	{
		return (WebLayoutSpecificationBuilder)super.withDisplayName(displayName);
	}

	@Override
	public WebLayoutSpecificationBuilder withIcon(String icon)
	{
		return (WebLayoutSpecificationBuilder)super.withIcon(icon);
	}

	@Override
	public WebLayoutSpecificationBuilder withPreview(String preview)
	{
		return (WebLayoutSpecificationBuilder)super.withPreview(preview);
	}

	@Override
	public WebLayoutSpecificationBuilder withDefinition(String definition)
	{
		return (WebLayoutSpecificationBuilder)super.withDefinition(definition);
	}

	@Override
	public WebLayoutSpecificationBuilder withDeprecated(String deprecated)
	{
		return (WebLayoutSpecificationBuilder)super.withDeprecated(deprecated);
	}

	@Override
	public WebLayoutSpecification build()
	{
		return new WebLayoutSpecification(name, packageName, displayName, categoryName, icon, preview, definition, config, topContainer, allowedChildren,
			excludedChildren, designStyleClass, layout, properties, deprecated);
	}
}
