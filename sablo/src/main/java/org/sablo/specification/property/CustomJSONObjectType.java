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

package org.sablo.specification.property;

import java.util.Map;

import org.sablo.specification.PropertyDescription;

/**
 * Type for what in spec files you see defined in the types section. (custom javascript object types)
 * It should be a kind of proxy for all possible conversion types to it's child types.
 *
 * @author acostescu
 */
public class CustomJSONObjectType extends CustomJSONPropertyType<Map<String, ? >>
{

	/**
	 * @param typeName
	 * @param definition
	 */
	public CustomJSONObjectType(String typeName, PropertyDescription definition)
	{
		super(typeName, definition);
		// TODO Auto-generated constructor stub
	}

}
