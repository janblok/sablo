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
import org.sablo.websocket.utils.DataConversion;

/**
 * A property type that needs special (JSON) conversion for web-socket traffic.<br/>
 *
 * @author acostescu
 */
public interface IConvertedPropertyType<T> extends IPropertyType<T>
{

	/**
	 * Updates/sets the component property value based on JSON data received from the browser.
	 * @param newJSONValue the JSON received from browser.
	 * @param previousValue the previous value of this property as available in the component.
	 * @param dataConverterContext context
	 * @return the new sablo value for this property to be put in component.
	 */
	T fromJSON(Object newJSONValue, T previousSabloValue, IDataConverterContext dataConverterContext);

	/**
	 * Generates JSON that will be sent to the browser for the given property value.
	 *
	 * @param writer the JSON writer to write JSON converted data to.
	 * @param key if this value will be part of a JSONObject then key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 * @param object the value to convert to JSON.
	 * @param clientConversion can be use to mark needed client/browser side conversion types.
	 * @return the writer for cascaded usage.
	 * @throws JSONException if a JSON exception happens.
	 */
	JSONWriter toJSON(JSONWriter writer, String key, T sabloValue, DataConversion clientConversion) throws JSONException;

}