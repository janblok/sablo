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

import java.awt.Dimension;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.property.IClassPropertyType;

/**
 * @author jcompagner
 *
 */
public class DimensionPropertyType extends DefaultPropertyType<Dimension> implements IClassPropertyType<Dimension> {

	public static final DimensionPropertyType INSTANCE = new DimensionPropertyType();
	
	private DimensionPropertyType() {
	}
	
	@Override
	public String getName() {
		return "dimension";
	}

	@Override
	public Dimension toJava(Object newValue, Dimension previousValue) {
		if (newValue instanceof JSONObject) {
			JSONObject json = (JSONObject) newValue;
			return new Dimension(json.optInt("width"), json.optInt("height"));
		}
		return null;
	}
	
	@Override
	public void toJSON(JSONWriter writer, Dimension object) throws JSONException {
		writer.object().key("width").value(object.getWidth()).key("height").value(object.getHeight()).endObject();
	}
	
	@Override
	public Dimension defaultValue() {
		return new Dimension(0, 0);
	}
	
	@Override
	public Class<Dimension> getTypeClass() {
		return Dimension.class;
	}
}
