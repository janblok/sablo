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

import javax.swing.border.Border;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.property.IClassPropertyType;

/**
 * @author jcompagner
 *
 */
public class BorderPropertyType extends DefaultPropertyType<Border> implements IClassPropertyType<Border> {

	public static final BorderPropertyType INSTANCE = new BorderPropertyType();
	
	private BorderPropertyType() {
	}
	
	@Override
	public String getName() {
		return "border";
	}

	@Override
	public Border toJava(Object newValue, Border previousValue) {
		return null;
	}

	@Override
	public void toJSON(JSONWriter writer, Border object) throws JSONException {
		writer.object();
		// TODO impl our border type has special suppor for specific servoy borders.
		
		writer.endObject();
	}

	@Override
	public Object parseConfig(JSONObject json) {
		return Boolean.valueOf(json == null || !json.has("stringformat") || json.optBoolean("stringformat"));
	}
	
	@Override
	public Class<Border> getTypeClass() {
		return Border.class;
	}
}
