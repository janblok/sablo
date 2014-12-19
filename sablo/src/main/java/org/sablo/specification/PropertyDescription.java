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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.sablo.specification.property.CustomJSONPropertyType;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyType;

/**
 * Property description as parsed from web component spec file.
 * @author rgansevles
 */
public class PropertyDescription
{
	private final String name;
	private final IPropertyType< ? > type;
	private final Object config;
	private final boolean optional;
	private final Object defaultValue;
	private final List<Object> values;
	private String scope = null;

	//case of nested type
	// TODO: make properties final and remove put* calls so PropertyDescription is immutable
	private Map<String, PropertyDescription> properties = null;


	public PropertyDescription(String name, IPropertyType< ? > type)
	{
		this(name, type, null, null, null, null, false);
	}

	public PropertyDescription(String name, IPropertyType< ? > type, Object config)
	{
		this(name, type, null, config, null, null, false);
	}

	/**
	 * 
	 * @param name
	 * @param type
	 * @param scope
	 * @param config
	 * @param defaultValue
	 * @param values
	 * @param optional only used for api arguments
	 */
	public PropertyDescription(String name, IPropertyType< ? > type, String scope, Object config, Object defaultValue, List<Object> values, boolean optional)
	{
		this.name = name;
		this.type = type;
		this.config = config;
		this.defaultValue = defaultValue;
		this.values = values;
		this.scope = scope;
		this.optional = optional;
	}

	public Collection<PropertyDescription> getProperties(IPropertyType< ? > pt)
	{
		if (properties == null)
		{
			return Collections.emptyList();
		}

		List<PropertyDescription> filtered = new ArrayList<>(4);
		for (PropertyDescription pd : properties.values())
		{
			if (pd.getType() == pt)
			{
				filtered.add(pd);
			}
		}
		return filtered;
	}

	public boolean hasChildProperties()
	{
		return properties != null && !properties.isEmpty();
	}

	public Collection<String> getAllPropertiesNames()
	{
		if (properties != null)
		{
			return Collections.unmodifiableCollection(properties.keySet());
		}
		return Collections.emptySet();
	}


	public String getName()
	{
		return name;
	}

	public IPropertyType< ? > getType()
	{
		return type;
	}

	public String getScope()
	{
		return scope;
	}

	public Object getConfig()
	{
		return config;
	}

	public Object getDefaultValue()
	{
		return defaultValue;
	}

	public List<Object> getValues()
	{
		return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((config == null) ? 0 : config.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		PropertyDescription other = (PropertyDescription)obj;
		if (config == null)
		{
			if (other.config != null) return false;
		}
		else if (!config.equals(other.config)) return false;
		if (name == null)
		{
			if (other.name != null) return false;
		}
		else if (!name.equals(other.name)) return false;
		if (type != other.type) return false;

		if (defaultValue == null)
		{
			if (other.defaultValue != null) return false;
		}
		else if (!defaultValue.equals(other.defaultValue)) return false;

		return true;
	}

	public boolean isOptional()
	{
		return optional;
	}

	public PropertyDescription putProperty(String propname, PropertyDescription proptype)
	{
		if (properties == null) properties = new HashMap<>();
		if (proptype == null) properties.remove(propname);
		properties.put(propname, proptype);
		return this;
	}

	public PropertyDescription getProperty(String propname)
	{
		if (properties != null)
		{
			PropertyDescription propertyDescription = properties.get(propname);
			if (propertyDescription != null)
			{
				return propertyDescription;
			}
			int indexOfDot = propname.indexOf('.');
			if (indexOfDot >= 0)
			{
				// this must be a custom type then
				propertyDescription = properties.get(propname.substring(0, indexOfDot));
				PropertyDescription typeSpec = ((ICustomType< ? >)propertyDescription.getType()).getCustomJSONTypeDefinition();
				return typeSpec.getProperty(propname.substring(indexOfDot + 1));
			}
		}
		else if (type instanceof CustomJSONPropertyType)
		{
			return ((CustomJSONPropertyType< ? >)type).getCustomJSONTypeDefinition().getProperty(propname);
		}
		return null;
	}

	// TODO: move to constructor so PropertyDescription is immutable
	public Map<String, PropertyDescription> getProperties()
	{
		if (properties != null) return Collections.unmodifiableMap(properties);
		return Collections.emptyMap();
	}

	public void putAll(Map<String, PropertyDescription> map)
	{
		properties = new HashMap<>(map);
	}


	@Override
	public String toString()
	{
		return toString(true);
	}

	public String toString(boolean showFullType)
	{
		return "PropertyDescription[name: " + name + ", type: " + (showFullType ? type : "'" + type.getName() + "' type") + ", config: " + config +
			", default value: " + defaultValue + "]";
	}

	public String toStringWholeTree()
	{
		return toStringWholeTree(new StringBuilder(100), 2).toString();
	}

	public StringBuilder toStringWholeTree(StringBuilder b, int level)
	{
		b.append(toString(false));
		if (properties != null)
		{
			for (Entry<String, PropertyDescription> p : properties.entrySet())
			{
				b.append('\n');
				addSpaces(b, level + 1);
				b.append(p.getKey()).append(": ");
				p.getValue().toStringWholeTree(b, level + 1);
			}
		}
		else
		{
			b.append(" (no nested child properties)");
		}
		return b;
	}

	private static void addSpaces(StringBuilder b, int level)
	{
		for (int i = 0; i < level * 2; i++)
		{
			b.append(' ');
	}
	}

}
