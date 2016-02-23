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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.NGPackage.IPackageReader;


/**
 * @author jcompagner
 *
 */
@SuppressWarnings({ "nls" })
public class WebLayoutSpecification extends WebObjectSpecification
{
	public static WebLayoutSpecification parseLayoutSpec(String specfileContent, String packageName, IPackageReader reader) throws JSONException, IOException
	{
		JSONObject json = new JSONObject(specfileContent);
		List<String> children = new ArrayList<>();

		boolean topContainer = json.optBoolean("topContainer", false);

		JSONArray canContainChildren = json.optJSONArray("contains");
		if (canContainChildren != null)
		{
			for (int i = 0; i < canContainChildren.length(); i++)
			{
				children.add(canContainChildren.optString(i));
			}
		}
		String jsonConfig = null;
		if (json.getString("definition") != null)
		{
			jsonConfig = reader.readTextFile(json.getString("definition"), Charset.forName("UTF8"));
		}
		WebLayoutSpecification spec = new WebLayoutSpecification(json.getString("name"), packageName, json.optString("displayName", null),
			json.optString("categoryName", null), json.optString("icon", null), json.optString("preview", null), json.getString("definition"), jsonConfig,
			topContainer, children, json.optString("designStyleClass"));

		// properties
		spec.putAll(spec.parseProperties("model", json));

		if (json.has("attributes"))
		{
			String attributes = reader.readTextFile(json.getString("attributes"), Charset.forName("UTF8"));
			spec.putAllAttributes(spec.parseProperties("attributes", new JSONObject(attributes)));
		}

		return spec;
	}

	private Map<String, PropertyDescription> attributes = null;
	private void putAllAttributes(Map<String, PropertyDescription> parseProperties)
	{
		attributes = new TreeMap<String, PropertyDescription>(parseProperties);
	}

	public Map<String, PropertyDescription> getAttributes()
	{
		return attributes;
	}

	private final boolean topContainer;
	private final List<String> allowedChildren;
	private final String designStyleClass;

	public WebLayoutSpecification(String name, String packageName, String displayName, String categoryName, String icon, String preview, String definition,
		Object configObject, boolean topContainer, List<String> allowedChildren, String designStyleClass)
	{
		super(name, packageName, displayName, categoryName, icon, preview, definition, null, configObject);
		this.topContainer = topContainer;
		this.allowedChildren = allowedChildren;
		this.designStyleClass = designStyleClass;
	}

	/**
	 * @return the parents
	 */
	public boolean isTopContainer()
	{
		return topContainer;
	}

	/**
	 * @return the children
	 */
	public List<String> getAllowedChildren()
	{
		return allowedChildren;
	}

	/**
	 * @return the designStyleClass
	 */
	public String getDesignStyleClass()
	{
		return designStyleClass;
	}
}
