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


/**
 * Implementors of this interface are able to provide complex property type behavior (special client-side/server-side handling) for JSON custom property types.
 * @author acostescu
 */
public interface IComplexTypeImpl<CT, T extends IComplexPropertyValue> extends IPropertyType<T>
{
	
	public static final String ARRAY = "[]"; 

	// TODO ac document this
	IJSONToJavaPropertyConverter<CT, T> getJSONToJavaPropertyConverter(boolean isArray);

	// TODO ac document this
	IDesignJSONToJavaPropertyConverter<CT, T> getDesignJSONToJavaPropertyConverter(boolean isArray);

	// TODO ac document this
	IServerObjToJavaPropertyConverter<CT, T> getServerObjectToJavaPropertyConverter(boolean isArray);

}