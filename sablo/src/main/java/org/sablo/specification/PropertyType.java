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

import org.json.JSONObject;
import org.sablo.specification.property.IComplexTypeImpl;
import org.sablo.specification.property.IDesignJSONToJavaPropertyConverter;
import org.sablo.specification.property.IJSONToJavaPropertyConverter;
import org.sablo.specification.property.IServerObjToJavaPropertyConverter;


/**
 * Base class for property types in web component spec files.
 * 
 * @author rgansevles
 * @author acostescu
 */
@SuppressWarnings("nls")
public class PropertyType implements IComplexTypeImpl
{

	private final String typeName;

	public PropertyType(String typeName)
	{
		this.typeName = typeName;
	}

	@Override
	public String getName()
	{
		return typeName;
	}

	@Override
	public String toString()
	{
		return "'" + typeName + "' type";
	}

	@Override
	public IJSONToJavaPropertyConverter< ? , ? > getJSONToJavaPropertyConverter(boolean isArray)
	{
		return null;
	}

	@Override
	public IDesignJSONToJavaPropertyConverter< ? , ? > getDesignJSONToJavaPropertyConverter(boolean isArray)
	{
		return null;
	}

	@Override
	public IServerObjToJavaPropertyConverter< ? , ? > getServerObjectToJavaPropertyConverter(boolean isArray)
	{
		return null;
	}

	public Object toJava(Object newValue, Object previousValue) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object parseConfig(JSONObject config) {
		return config;
	}
	
	@Override
	public Object defaultValue() {
		return null;
	}

}
