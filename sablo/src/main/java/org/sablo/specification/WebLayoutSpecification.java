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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.WebComponentPackage.IPackageReader;


/**
 * @author jcompagner
 *
 */
@SuppressWarnings({ "nls" })
public class WebLayoutSpecification extends WebComponentSpecification
{
	public static WebLayoutSpecification parseLayoutSpec(String specfileContent, String packageName, IPackageReader reader) throws JSONException, IOException
	{
		JSONObject json = new JSONObject(specfileContent);
		List<String> parents = new ArrayList<>();
		List<String> children = new ArrayList<>();

		JSONArray mustBeParents = json.optJSONArray("parents");
		if (mustBeParents != null)
		{
			for (int i = 0; i < mustBeParents.length(); i++)
			{
				parents.add(mustBeParents.optString(i));
			}
		}

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
		WebLayoutSpecification spec = new WebLayoutSpecification(json.getString("name"), packageName, json.optString("displayName", null), json.optString(
			"categoryName", null), json.optString("icon", null), json.getString("definition"), jsonConfig, parents, children);

		// properties
		spec.putAll(spec.parseProperties("model", json));


		return spec;
	}

	private final List<String> allowedParents;
	private final List<String> allowedChildren;

	/**
	 * @param name
	 * @param packageName
	 * @param displayName
	 * @param categoryName
	 * @param icon
	 * @param definition
	 * @param configObject
	 * @param parents
	 * @param children
	 */
	public WebLayoutSpecification(String name, String packageName, String displayName, String categoryName, String icon, String definition,
		Object configObject, List<String> allowedParents, List<String> allowedChildren)
	{
		super(name, packageName, displayName, categoryName, icon, definition, null, configObject);
		this.allowedParents = allowedParents;
		this.allowedChildren = allowedChildren;
	}

	/**
	 * @return the parents
	 */
	public List<String> getAllowedParents()
	{
		return allowedParents;
	}

	/**
	 * @return the children
	 */
	public List<String> getAllowedChildren()
	{
		return allowedChildren;
	}

}
