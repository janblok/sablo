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
 * Used to parse a JSON value (comming from design-time) representing the property into a server-side Java Object representing the property value.<br>
 * The Java Object can be used later to sync this property with the browser component as needed (see linked interfaces).<br><br>
 * 
 * For example "{ x: 10, y: 10 }" to a Java Point object.
 * @author acostescu
 * @see {@link IComplexPropertyValue}
 * @see {@link IJSONToJavaPropertyConverter}
 * @see {@link IServerObjToJavaPropertyConverter}
 */
public interface IDesignJSONToJavaPropertyConverter<CT, T extends IComplexPropertyValue>
{

	/**
	 * Parses a JSON value (comming from design-time) representing the property into a server-side Java Object representing the property value.<br>
	 * The Java Object can be used later to sync this property with the browser component as needed.<br><br>
	 * 
	 * For example "{ x: 10, y: 10 }" to a Java Point object.
	 * @param jsonValue can be a JSONObject, JSONArray or primitive type.
	 * @param config the configuration of this property as defined in the .spec file. For example { type: 'valuelist', for: 'dataProviderID'}
	 * @return the Java object representing this property's value.
	 */
	T designJSONToJava(Object jsonValue, CT config);

}
