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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

/**
 * RAGTEST doc
 * @author rgansevles
 *
 */
public class ForentriesConfig
{

	private final Collection<String> entries;

	/**
	 * @param array
	 */
	public ForentriesConfig(Collection<String> entries)
	{
		this.entries = entries;
	}

	/**
	 * @return the entries
	 */
	public Collection<String> getEntries()
	{
		return Collections.unmodifiableCollection(entries);
	}

	/**
	 * @param json
	 * @return
	 */
	public static ForentriesConfig parse(JSONObject json)
	{
		if (json == null)
		{
			return null;
		}

		String forString = json.optString("for", null);
		if (forString == null)
		{
			return null;
		}

		List<String> entries = new ArrayList<>();
		for (String f : forString.split(","))
		{
			entries.add(f.trim());
		}

		return new ForentriesConfig(entries);
	}
}
