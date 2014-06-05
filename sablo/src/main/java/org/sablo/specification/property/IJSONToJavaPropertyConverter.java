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
 * Used to parse a JSON value (comming from client-side) representing the property and to update or create a new server-side Java Object based on it.<br>
 * The Java Object can be used later to sync this property with the browser component as needed in a similar fashion (see linked interfaces).<br><br>
 * 
 * For example "{ x: 10, y: 10 }" to a Java Point object.
 * @author acostescu
 * @see {@link IComplexPropertyValue}
 * @see {@link IDesignJSONToJavaPropertyConverter}
 * @see {@link IServerObjToJavaPropertyConverter}
 */
public interface IJSONToJavaPropertyConverter<CT, T extends IComplexPropertyValue>
{
	// TODO if we reach the conclusion that oldJavaObject can never be null for a complex type when an update comes from browser
	// we can delete this interface and move the method to IComplexPropertyObject

	/**
	 * Parses a JSON value (comming from client-side) representing the property and uses it to update or create a new server-side Java Object.<br>
	 * The Java Object can be used later to sync this property with the browser component as needed in a similar fashion.<br><br>
	 * 
	 * For example "{ x: 10, y: 10 }" to a Java Point object.
	 * @param jsonValue can be a JSONObject, JSONArray or primitive type.
	 * @return the new or updated Java object representing this property.
	 */
	T jsonToJava(Object jsonValue, T oldJavaObject, CT config);

}
