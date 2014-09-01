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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.CustomJSONArrayTypeFactory;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jcompagner
 *
 */
public class TypesRegistry
{

	private static final Logger log = LoggerFactory.getLogger(TypesRegistry.class.getCanonicalName());

	private static Map<String, IPropertyType< ? >> types = new HashMap<>();
	private static Map<String, IPropertyTypeFactory< ? , ? >> typeFactories = new HashMap<>();
	private static Map<Class< ? >, IClassPropertyType< ? >> typesByClass = new HashMap<>();


	static
	{
		types.put("int", IntPropertyType.INSTANCE);
		types.put("long", LongPropertyType.INSTANCE);
		types.put("float", FloatPropertyType.INSTANCE);
		types.put("double", DoublePropertyType.INSTANCE);
		types.put("boolean", BooleanPropertyType.INSTANCE);
		types.put("color", ColorPropertyType.INSTANCE);
		types.put("string", StringPropertyType.INSTANCE);
		types.put("insets", InsetsPropertyType.INSTANCE);
		types.put("date", DatePropertyType.INSTANCE);
		types.put("dimension", DimensionPropertyType.INSTANCE);
		types.put("font", FontPropertyType.INSTANCE);
		types.put("function", FunctionPropertyType.INSTANCE);
		types.put("object", ObjectPropertyType.INSTANCE);
		types.put("point", PointPropertyType.INSTANCE);
		types.put("styleclass", StyleClassPropertyType.INSTANCE);
		types.put("tabseq", TabSeqPropertyType.INSTANCE);
		types.put("values", ValuesPropertyType.INSTANCE);
		types.put("componentDef", ComponentDefPropertyType.INSTANCE);
		types.put("byte", BytePropertyType.INSTANCE);

		for (IPropertyType< ? > type : types.values())
		{
			if (type instanceof IClassPropertyType)
			{
				IClassPropertyType< ? > previous = typesByClass.put(((IClassPropertyType< ? >)type).getTypeClass(), (IClassPropertyType< ? >)type);
				if (previous != null)
				{
					throw new RuntimeException("duplicate type found for class: " + ((IClassPropertyType< ? >)type).getTypeClass() + " of type: " +
						type.getName() + " replaced: " + ((IPropertyType< ? >)previous).getName());
				}
			}
		}

		typeFactories.put(CustomJSONArrayType.TYPE_ID, new CustomJSONArrayTypeFactory());
//		typeFactories.put(CustomJSONObjectType.TYPE_ID, new CustomJSONObjectTypeFactory());
	}


	public static IPropertyType< ? > getType(String name)
	{
		IPropertyType< ? > type = types.get(name);
		if (type == null) throw new RuntimeException("Type '" + name + "' not found in " + printTypes());
		return type;
	}

	public static <ParamT> IPropertyType< ? > createNewType(String name, ParamT params)
	{
		IPropertyTypeFactory<ParamT, ? > typeFactory = (IPropertyTypeFactory<ParamT, ? >)typeFactories.get(name);
		if (typeFactory == null) throw new RuntimeException("Type factory for type '" + name + "' not found in " + printTypeFactories());
		return typeFactory.createType(params);
	}

	public static IClassPropertyType< ? > getType(Class< ? > clz)
	{
		IClassPropertyType< ? > type = typesByClass.get(clz);
		//TODO if this is still null should we do a isAssignableFrom/instanceof check?
		// clz could be a concrete type, but the registered type is a interface class type.
//		if (type == null) throw new RuntimeException("Type for class: '" + clz + "' not found in " + printTypes());
		return type;
	}

	public static String printTypes()
	{
		StringBuilder sb = new StringBuilder();
		for (IPropertyType< ? > type : types.values())
		{
			sb.append(type.getName());
			if (type instanceof IClassPropertyType)
			{
				sb.append("[");
				sb.append(((IClassPropertyType< ? >)type).getTypeClass());
				sb.append("]");
			}
			sb.append(",");
		}
		return sb.toString();
	}

	public static String printTypeFactories()
	{
		StringBuilder sb = new StringBuilder();
		for (String typeName : typeFactories.keySet())
		{
			sb.append(typeName);
			sb.append(",");
		}
		return sb.toString();
	}

	/**
	 * @param parsedTypes
	 */
	public static void addTypes(Collection<IPropertyType< ? >> collection)
	{
		for (IPropertyType< ? > type : collection)
		{
			addType(type);
		}
	}

	public static void addType(IPropertyType< ? > type)
	{
		IPropertyType< ? > previous = types.put(type.getName(), type);
		if (previous != null)
		{
			log.info("there was already a type for typename " + type.getName() + ": " + previous + " replaced by: " + type);
		}
		if (type instanceof IClassPropertyType)
		{
			previous = typesByClass.put(((IClassPropertyType< ? >)type).getTypeClass(), (IClassPropertyType< ? >)type);
			if (previous != null)
			{
				log.info("there was already a type for type class " + ((IClassPropertyType< ? >)type).getTypeClass() + ": " + previous + " replaced by: " +
					type);
			}
		}
	}

	public static void addTypeFactory(String typeName, IPropertyTypeFactory< ? , ? > factory)
	{
		IPropertyTypeFactory< ? , ? > previous = typeFactories.put(typeName, factory);
		if (previous != null)
		{
			log.info("there was already a type factory for typename " + typeName + ": " + previous + " replaced by: " + factory);
		}
	}

}
