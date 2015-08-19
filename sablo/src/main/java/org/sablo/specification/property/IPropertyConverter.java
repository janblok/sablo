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
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.utils.DataConversion;

/**
 * A property type that needs special (JSON) conversion (for example for web-socket traffic).<br/>
 *
 * @author acostescu
 * @param JT java class type to and from which JSON conversions take place.
 */
public interface IPropertyConverter<JT, ContextT>
{

	/**
	 * Updates or sets the component property value based on JSON data received from the browser.<br/><br/>
	 *
	 * Simple types will probably always send the whole value.<br/>
	 * Advanced/complex types might write their own JSON based protocol for sending granular updates.
	 *
	 * @param newJSONValue the JSON received from browser (can be whole value or just updates).
	 * @param previousValue the previous value of this property as available in the component.
	 * @param propertyDescription the description of the property that is being converted.
	 * @param context runtime context
	 * @return the new sablo value for this property to be put in component.
	 */
	JT fromJSON(Object newJSONValue, JT previousSabloValue, PropertyDescription propertyDescription, ContextT context);

	/**
	 * It generates (full value) JSON that will be sent to the browser for the given property value. So this sends the entire value
	 * - as if nothing was previously sent to the client for that property, this is not used for sending granular updates (see {@link IGranularUpdatesType).
	 *
	 * @param writer the JSON writer to write JSON converted data to.
	 * @param key if this value will be part of a JSONObject then key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 * @param sabloValue the value to convert to JSON.
	 * @param propertyDescription the description of the property that is being converted.
	 * @param clientConversion can be use to mark needed client/browser side conversion types.
	 * @param fullValue if true, a full value is requested from the property, as if no other value was previously available client side; if false, then
	 * only changes are requested. For most types this flag can be ignored and the whole value sent. It is only useful for types that can send granular
	 * updates and have their own JSON protocol (for example object and array types are implemented to support this).
	 * @param context runtime context
	 * @return the writer for cascaded usage.
	 * @throws JSONException if a JSON exception happens.
	 */
	JSONWriter toJSON(JSONWriter writer, String key, JT sabloValue, PropertyDescription propertyDescription, DataConversion clientConversion,
		ContextT dataConverterContext) throws JSONException;

}