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
import org.sablo.specification.property.CustomJSONObjectType;
import org.sablo.specification.property.CustomJSONObjectTypeFactory;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jcompagner
 *
 */
@SuppressWarnings("nls")
public class TypesRegistry
{

	private static final Logger log = LoggerFactory.getLogger(TypesRegistry.class.getCanonicalName());

	private static Map<String, IPropertyType< ? >> types = new HashMap<>();
	private static Map<String, IPropertyTypeFactory< ? , ? >> typeFactories = new HashMap<>();
	private static Map<Class< ? >, IClassPropertyType< ? >> typesByClass = new HashMap<>();


	static
	{
		addType(IntPropertyType.INSTANCE);
		addType(LongPropertyType.INSTANCE);
		addType(FloatPropertyType.INSTANCE);
		addType(DoublePropertyType.INSTANCE);
		addType(BooleanPropertyType.INSTANCE);
		addType(ColorPropertyType.INSTANCE);
		addType(StringPropertyType.INSTANCE);
		addType(InsetsPropertyType.INSTANCE);
		addType(DatePropertyType.INSTANCE);
		addType(DimensionPropertyType.INSTANCE);
		addType(FontPropertyType.INSTANCE);
		addType(FunctionPropertyType.INSTANCE);
		addType(ObjectPropertyType.INSTANCE);
		addType(PointPropertyType.INSTANCE);
		addType(StyleClassPropertyType.INSTANCE);
		addType(TabSeqPropertyType.INSTANCE);
		addType(ValuesPropertyType.INSTANCE);
		addType(ComponentDefPropertyType.INSTANCE);
		addType(BytePropertyType.INSTANCE);

		addTypeFactory(CustomJSONArrayType.TYPE_NAME, new CustomJSONArrayTypeFactory());
		addTypeFactory(CustomJSONObjectType.TYPE_NAME, new CustomJSONObjectTypeFactory());
	}


	public static IPropertyType< ? > getType(String name, boolean failIfNull)
	{
		IPropertyType< ? > type = types.get(name);
		if (type == null && failIfNull) throw new RuntimeException("Type '" + name + "' not found in " + printTypes());
		return type;
	}

	public static IPropertyType< ? > getType(String name)
	{
		return getType(name, true);
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
