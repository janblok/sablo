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

package org.sablo.specification.property.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Configure for-entries in properties
 * 
 * TODO: validate if for-entries refer to existing properties
 * 
 * @author rgansevles
 *
 */
public class ForentriesConfig
{
	public static final ForentriesConfig DEFAULT = null;

	private final Collection<String> entries;

	private ForentriesConfig(Collection<String> entries)
	{
		this.entries = Collections.unmodifiableCollection(entries);
	}

	/**
	 * @return the entries
	 */
	public Collection<String> getEntries()
	{
		return entries;
	}

	/**
	 * @param json
	 * @return
	 */
	@SuppressWarnings("nls")
	public static ForentriesConfig parse(JSONObject json)
	{
		if (json == null)
		{
			return DEFAULT;
		}

		Object forObject = json.opt("for");

		List<String> entries = null;
		if (forObject instanceof String && forObject.toString().trim().length() > 0)
		{
			entries = Arrays.asList(forObject.toString().trim());
		}
		else if (forObject instanceof JSONArray)
		{
			JSONArray array = (JSONArray)forObject;
			entries = new ArrayList<>(array.length());
			for (int i = 0; i < array.length(); i++)
			{
				Object entry = array.opt(i);
				if (entry != null && entry.toString().trim().length() > 0)
				{
					entries.add(entry.toString().trim());
				}
			}
		}

		if (entries == null || entries.size() == 0)
		{
			return DEFAULT;
		}

		return new ForentriesConfig(entries);
	}
}
