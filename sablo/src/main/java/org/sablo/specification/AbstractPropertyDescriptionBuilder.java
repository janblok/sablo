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
abstract class AbstractPropertyDescriptionBuilder<B extends AbstractPropertyDescriptionBuilder<B, P>, P extends PropertyDescription>
{
	protected String name = "";
	protected IPropertyType< ? > type;
	protected Object config;
	protected boolean optional;
	protected Object defaultValue;
	protected Object initialValue;
	protected List<Object> values;
	protected PushToServerEnum pushToServer;
	protected JSONObject tags;
	protected Map<String, PropertyDescription> properties;
	protected boolean hasDefault;
	protected String deprecated;

	public B withProperty(String propertyName, PropertyDescription pd)
	{
		if (properties == null)
		{
			properties = new HashMap<>();
		}
		properties.put(propertyName, pd);
		return getThis();
	}

	public B withProperties(Map<String, PropertyDescription> propertiesList)
	{
		if (properties == null)
		{
			properties = new HashMap<>();
		}
		properties.putAll(propertiesList);
		return getThis();
	}

	public B withName(String name)
	{
		this.name = name;
		return getThis();
	}

	public B withPushToServer(PushToServerEnum pushToServer)
	{
		this.pushToServer = pushToServer;
		return getThis();
	}

	public B withType(IPropertyType< ? > type)
	{
		this.type = type;
		return getThis();
	}

	public B withDefaultValue(Object defaultValue)
	{
		this.defaultValue = defaultValue;
		return getThis();
	}

	public B withInitialValue(Object initialValue)
	{
		this.initialValue = initialValue;
		return getThis();
	}

	public B withConfig(Object config)
	{
		this.config = config;
		return getThis();
	}

	public B withOptional(boolean optional)
	{
		this.optional = optional;
		return getThis();
	}

	public B withHasDefault(boolean hasDefault)
	{
		this.hasDefault = hasDefault;
		return getThis();
	}

	public B withValues(List<Object> values)
	{
		this.values = values;
		return getThis();
	}

	public B withTags(JSONObject tags)
	{
		this.tags = tags;
		return getThis();
	}

	public B withDeprecated(String deprecated)
	{
		this.deprecated = deprecated;
		return getThis();
	}

	protected final B getThis()
	{
		return (B)this;
	}

	abstract public P build();
}
