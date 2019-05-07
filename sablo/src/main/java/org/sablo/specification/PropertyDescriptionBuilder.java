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
	protected String name = "";
	private IPropertyType< ? > type;
	protected Object config;
	private boolean optional;
	private Object defaultValue;
	private Object initialValue;
	private List<Object> values;
	private PushToServerEnum pushToServer;
	private JSONObject tags;
	protected Map<String, PropertyDescription> properties;
	private boolean hasDefault;
	protected String deprecated;

	/**
	 *
	 */
	public PropertyDescriptionBuilder()
	{
	}

	public PropertyDescriptionBuilder withProperty(String propertyName, PropertyDescription pd)
	{
		if (properties == null)
		{
			properties = new HashMap<>();
		}
		properties.put(propertyName, pd);
		return this;
	}

	public PropertyDescriptionBuilder withProperties(Map<String, PropertyDescription> propertiesList)
	{
		if (properties == null)
		{
			properties = new HashMap<>();
		}
		properties.putAll(propertiesList);
		return this;
	}

	public PropertyDescriptionBuilder withName(String name)
	{
		this.name = name;
		return this;
	}

	public PropertyDescriptionBuilder withPushToServer(PushToServerEnum pushToServer)
	{
		this.pushToServer = pushToServer;
		return this;
	}

	public PropertyDescriptionBuilder withType(IPropertyType< ? > type)
	{
		this.type = type;
		return this;
	}

	public PropertyDescriptionBuilder withDefaultValue(Object defaultValue)
	{
		this.defaultValue = defaultValue;
		return this;
	}

	public PropertyDescriptionBuilder withInitialValue(Object initialValue)
	{
		this.initialValue = initialValue;
		return this;
	}

	public PropertyDescriptionBuilder withConfig(Object config)
	{
		this.config = config;
		return this;
	}

	public PropertyDescriptionBuilder withOptional(boolean optional)
	{
		this.optional = optional;
		return this;
	}

	public PropertyDescriptionBuilder withHasDefault(boolean hasDefault)
	{
		this.hasDefault = hasDefault;
		return this;
	}

	public PropertyDescriptionBuilder withValues(List<Object> values)
	{
		this.values = values;
		return this;
	}

	public PropertyDescriptionBuilder withTags(JSONObject tags)
	{
		this.tags = tags;
		return this;
	}

	public PropertyDescriptionBuilder withDeprecated(String deprecated)
	{
		this.deprecated = deprecated;
		return this;
	}

	public PropertyDescription build()
	{
		return new PropertyDescription(name, type, config, properties, defaultValue, initialValue, hasDefault, values, pushToServer, tags, optional,
			properties != null && properties.containsKey("deprecated") ? ((String)properties.get("deprecated").getConfig()) : deprecated);
	}
}
