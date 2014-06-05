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

/**
 * Represents a property type that is both defined in JSON spec file and has special server-side/client-side handling.
 * 
 * @author acostescu
 */
public class ComplexCustomPropertyType extends CustomPropertyType implements IComplexTypeImpl
{

	protected final IComplexTypeImpl< ? , ? > handlers;

	// just caches
	protected IJSONToJavaPropertyConverter< ? , ? >[] js2j = new IJSONToJavaPropertyConverter< ? , ? >[2];
	protected IDesignJSONToJavaPropertyConverter< ? , ? >[] d2j = new IDesignJSONToJavaPropertyConverter< ? , ? >[2];
	protected IServerObjToJavaPropertyConverter< ? , ? >[] s2j = new IServerObjToJavaPropertyConverter< ? , ? >[2];


	/**
	 * Creates a new property type the is both defined in JSON spec file and has special server-side/client-side handling.
	 * @param typeName see super.
	 * @param definition see super.
	 */
	public ComplexCustomPropertyType(String typeName, PropertyDescription definition, IComplexTypeImpl< ? , ? > handlers)
	{
		super(typeName, definition);
		this.handlers = handlers;
	}
	
	@Override
	public IJSONToJavaPropertyConverter< ? , ? > getJSONToJavaPropertyConverter(boolean isArray)
	{
		int tmp = isArray ? 0 : 1;
		return js2j[tmp] != null ? js2j[tmp] : (js2j[tmp] = handlers.getJSONToJavaPropertyConverter(isArray));
	}

	@Override
	public IDesignJSONToJavaPropertyConverter< ? , ? > getDesignJSONToJavaPropertyConverter(boolean isArray)
	{
		int tmp = isArray ? 0 : 1;
		return d2j[tmp] != null ? d2j[tmp] : (d2j[tmp] = handlers.getDesignJSONToJavaPropertyConverter(isArray));
	}

	@Override
	public IServerObjToJavaPropertyConverter< ? , ? > getServerObjectToJavaPropertyConverter(boolean isArray)
	{
		int tmp = isArray ? 0 : 1;
		return s2j[tmp] != null ? s2j[tmp] : (s2j[tmp] = handlers.getServerObjectToJavaPropertyConverter(isArray));
	}

	@Override
	public String toString()
	{
		return "(COMPLEX Type) " + super.toString(); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.sablo.specification.property.CustomPropertyType#parseConfig(org.json.JSONObject)
	 */
	@Override
	public Object parseConfig(JSONObject config) {
		return handlers.parseConfig(config);
	}
}
