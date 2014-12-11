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

package org.sablo.websocket;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Interface that helps with writing to a JSON writer in a nested way.
 *
 * @author acostescu
 */
public interface IToJSONWriter
{

	/**
	 * Writes as JSON changes from all components of all registered Containers.
	 * @param keyInParent a key (can be null in which case it should be ignored) that must be appended to 'w' initially if this method call writes content to it. If the method returns false, nothing should be written to the writer...
	 */
	boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions) throws JSONException;

}
