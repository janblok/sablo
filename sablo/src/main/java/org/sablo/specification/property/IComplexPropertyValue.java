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

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.IChangeListener;
import org.sablo.IWebComponentInitializer;
import org.sablo.WebComponent;
import org.sablo.websocket.utils.DataConversion;

/**
 * The representation of a property value for a complex type property - server side.
 * It can be used to generate a JSON value (to be sent client-side) representing the property when there is a change in the server-side Java Object of this property.<br>
 * The Java Object can get updates from the browser component as needed in a similar fashion (see linked interfaces).<br><br>
 * 
 * For example a Java Point object could be represented as "{ x: 10, y: 10 }".
 * @author acostescu
 * @see {@link IJSONToJavaPropertyConverter}
 * @see {@link IDesignJSONToJavaPropertyConverter}
 * @see {@link IServerObjToJavaPropertyConverter}
 */
public interface IComplexPropertyValue
{

	public static final Object NOT_AVAILABLE = new Object();

	/**
	 * Method that will get called when this property value is attached to a component.<br>
	 * NOTE: other methods of this interface might get called prior to attachToComponent - for initial values to be sent to browser in form templates.
	 * 
	 * @param changeMonitor an object that can be used to notify the system that something in this property has changed.
	 * @param component the component to which the complex property belongs.
	 * @param propertyName the name of the property that this value was assigned to. (can be nested with '.' if the value is inside a custom JSON property leaf)
	 */
	void attachToComponent(IChangeListener changeMonitor, WebComponent component);

	/**
	 * Initialize this property value - this is the first method called.
	 * After this method is called the property value should be ready to provide initial property JSON value.
	 * 
	 * @param fe the 'blueprint' of the component this property belongs to.
	 * @param propertyName the name of the property this value is assigned to.
	 * @param defaultValue the default value of this property as defined in the .spec file. Can be a primitive, a JSONArray or JSONObject.
	 */
	void initialize(IWebComponentInitializer fe, String propertyName, Object defaultValue);

	/**
	 * Transforms this property value object into a JSON to be sent to the client<br>
	 * 
	 * For example a Java Point object could be represented as "{ x: 10, y: 10 }".
	 * @return the new or updated Java object representing this property.
	 */
	JSONWriter toJSON(JSONWriter destinationJSON, DataConversion conversionMarkers) throws JSONException;

	/**
	 * Write changes of this property value object into a JSON to be sent to the client. It can be a custom JSON that will be interpreted by
	 * custom client side code.
	 * 
	 * For example a Java Point object change only on x axis could be represented as "{ x: 10 }".
	 * @return the new or updated Java object representing this property.
	 */
	JSONWriter changesToJSON(JSONWriter destinationJSON, DataConversion conversionMarkers) throws JSONException;

	/**
	 * Writes the design JSON that would be transformed into this property object.<br>
	 * This method must be able to run even if the change monitor 
	 * @param writer
	 * @return
	 */
	JSONWriter toDesignJSON(JSONWriter writer) throws JSONException;

	/**
	 * Transforms this property value representation into an implementation specific server side object.
	 * @return either the implementation specific object or {@link #NOT_AVAILABLE}, if this property doesn't provide such an object.
	 */
	Object toServerObj();

	/**
	 * Called when a property is detached from a component; cleanup can happen here.
	 */
	void detach();

}
