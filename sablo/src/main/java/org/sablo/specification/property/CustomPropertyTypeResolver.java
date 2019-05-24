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
 * Class responsible for creating {@link IPropertyType} instances and attaching custom behavior to them.<BR>
 * A property type can be for example defined in JSON spec files only or could have 'complex' behavior - behaving differently
 * in different stages (server-side/client-side/...).
 *
 * The so called "smart custom types" are types with custom implementation registered using the {@link TypesRegistry} class, but which also have
 * a custom JSON definition defined in a spec file (most of the time defining only the design JSON structure that should be used by GUI editor
 * - so not used at runtime). So the types from the TypeRegistry that match a custom JSON type definition in a spec file and implement {@link ICustomType}
 * will get the custom JSON information added to them.
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
	 * This method resolves based on typeName the defined custom types - be it pure JSON defined types or complex types, allowing
	 * complex and custom types to be contributed to the system.
	 * @param typeName the type name as used in .spec files; if spec name is available type name will be prefixed by spec name to avoid conflicts
	 * @return the appropriate property type (handler).
	 */
	public ICustomType< ? > resolveCustomPropertyType(String typeName)
	{
		CustomJSONPropertyType< ? > propertyType = cache.get(typeName);
		if (propertyType == null)
		{
			// currently typeName can resolve to a pure JSON handled all-over-the-place property type or
			// a special type that has JSON defined spec, but also it behaves differently in different stages - see 'components' type
			IPropertyType< ? > smartCustomType = TypesRegistry.getType(typeName, false);

			if (smartCustomType instanceof CustomJSONPropertyType< ? >)
			{
				propertyType = ((CustomJSONPropertyType< ? >)smartCustomType);
				propertyType.setCustomJSONDefinition(new PropertyDescriptionBuilder().withName(typeName).withType(propertyType).build());
			}
			else if (smartCustomType == null)
			{
				propertyType = (CustomJSONObjectType)TypesRegistry.createNewType(CustomJSONObjectType.TYPE_NAME, typeName);
				propertyType.setCustomJSONDefinition(new PropertyDescriptionBuilder().withName(typeName).withType(propertyType).build());
			}
			else
			{
				log.error("Type '" + typeName +
					"' is defined in a spec file, but also has a special java implementation that ignores the spec declaration. Is this a type naming conflict? Using spec declaration.");
				return null;
			}

			cache.put(typeName, propertyType);
		}
		return propertyType;
	}

}
