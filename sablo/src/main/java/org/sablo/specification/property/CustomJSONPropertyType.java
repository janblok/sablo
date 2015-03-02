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

import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.DefaultPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Property types that are defined in JSON spec files.
 * @author acostescu
 */
public abstract class CustomJSONPropertyType<T> extends DefaultPropertyType<T> implements ICustomType<T>
{

	protected static final Logger log = LoggerFactory.getLogger(CustomJSONPropertyType.class.getCanonicalName());
	private PropertyDescription definition;
	private final String name;

	/**
	 * Creates a new property types that is defined in JSON spec files.
	 * @param typeName the name of this type as used in spec files.
	 * @param definition the parsed JSON definition of this type. If null, it must be set later via {@link #setCustomJSONDefinition(PropertyDescription)}.
	 */
	public CustomJSONPropertyType(String typeName, PropertyDescription definition)
	{
		this.name = typeName;
		setCustomJSONDefinition(definition);
	}

	@Override
	public String getName()
	{
		return name;
	}

	public void setCustomJSONDefinition(PropertyDescription definition)
	{
		this.definition = definition;
	}

	/**
	 * Returns the parsed JSON definition of this type.
	 * @return the parsed JSON definition of this type.
	 */
	@Override
	public PropertyDescription getCustomJSONTypeDefinition()
	{
		return definition;
	}

	@Override
	public String toString()
	{
		return super.toString() + "\nDefinition JSON:\n  " + (definition == null ? "null" : definition.toStringWholeTree()); //$NON-NLS-1$//$NON-NLS-2$
	}

	@Override
	public Object parseConfig(JSONObject config)
	{
		return config;
	}

}
