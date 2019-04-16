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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;
import org.sablo.specification.property.IPropertyType;

/**
 * @author lvostinar
 *
 */
public class PropertyDescriptionBuilder
{
	private final String name;
	private final IPropertyType< ? > type;
	private Object config;
	private boolean optional;
	private Object defaultValue;
	private Object initialValue;
	private List<Object> values;
	private PushToServerEnum pushToServer;
	private JSONObject tags;
	private final Map<String, PropertyDescription> properties = new HashMap<>();
	private boolean hasDefault;

	/**
	 *
	 */
	public PropertyDescriptionBuilder(String name, IPropertyType< ? > type)
	{
		this.name = name;
		this.type = type;
	}

	public PropertyDescriptionBuilder putProperty(String propertyName, PropertyDescription pd)
	{
		properties.put(propertyName, pd);
		return this;
	}

	public PropertyDescriptionBuilder putAll(Map<String, PropertyDescription> propertiesList)
	{
		properties.putAll(propertiesList);
		return this;
	}

	public PropertyDescription create()
	{
		return new PropertyDescription(name, type, config, properties, defaultValue, initialValue, hasDefault, values, pushToServer, tags, optional);
	}
}
