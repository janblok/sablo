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

import java.util.HashMap;
import java.util.Map;

import org.sablo.specification.PropertyDescriptionBuilder;
import org.sablo.specification.property.types.TypesRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for creating CustomJSONObjectType instances and attaching custom behavior to them.<br/>
 * A custom object property type can be for example defined in JSON spec files only (in the types section).<br/><br/>
 *
 * @author acostescu
 */
public class CustomPropertyTypeResolver
{

	private static final Logger log = LoggerFactory.getLogger(CustomPropertyTypeResolver.class.getCanonicalName());

	private static final CustomPropertyTypeResolver INSTANCE = new CustomPropertyTypeResolver();

	public static final CustomPropertyTypeResolver getInstance()
	{
		return INSTANCE;
	}

	private final Map<String, CustomJSONPropertyType< ? >> cache = new HashMap<>();

	/**
	 * Checks is a type is already registered.
	 */
	public boolean hasTypeName(String string)
	{
		return TypesRegistry.getType(string) != null;
	}

	/**
	 * This method resolves based on typeName the defined custom types - pure JSON defined custom object types, allowing
	 * custom types to be contributed to the system. If a custom object type was not yet defined it will create it with a blank subprop. description (to be populated by caller).
	 *
	 * @param typeName the type name as used in .spec files; if spec name is available type name will be prefixed by spec name to avoid conflicts
	 * @return the appropriate property type (handler).
	 *
	 * @throws RuntimeException if a type with this name was already registered to the TypesRegistry but not via this class...
	 */
	public ICustomType< ? > resolveCustomPropertyType(String typeName)
	{
		CustomJSONPropertyType< ? > propertyType = cache.get(typeName);
		if (propertyType == null)
		{
			// see if such a type already exists in the types registry - if it does (and it's not in the cache above) we have a naming conflict
			IPropertyType< ? > existingType = TypesRegistry.getType(typeName, false);

			if (existingType == null)
			{
				propertyType = (CustomJSONObjectType< ? , ? >)TypesRegistry.createNewType(CustomJSONObjectType.TYPE_NAME, typeName);
				propertyType.setCustomJSONDefinition(new PropertyDescriptionBuilder().withName(typeName).withType(propertyType).build());
				cache.put(typeName, propertyType);
			}
			else
			{
				throw new RuntimeException("Type naming conflict! Type '" + typeName +
					"' is defined in a .spec file's \"types\" section, but that type name it is already available in the TypesRegistry before being registered by that spec file.");
			}
		}
		return propertyType;
	}

}
