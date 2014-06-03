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

import java.awt.Point;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.property.IClassPropertyType;

/**
 * @author jcompagner
 *
 */
public class PointPropertyType extends DefaultPropertyType<Point> implements IClassPropertyType<Point> {

	public static final PointPropertyType INSTANCE = new PointPropertyType();
	
	private PointPropertyType() {
	}
	
	@Override
	public String getName() {
		return "point";
	}

	@Override
	public Point toJava(Object newValue, Point previousValue) {
		if (newValue instanceof JSONObject) {
			JSONObject json = (JSONObject) newValue;
			return new Point(json.optInt("x"), json.optInt("y"));
		}
		return null;
	}
	
	@Override
	public void toJSON(JSONWriter writer, Point object) throws JSONException {
		writer.object().key("x").value(object.getX()).key("y").value(object.getY()).endObject();
	}
	
	@Override
	public Point defaultValue() {
		return new Point(0,0);
	}
	
	@Override
	public Class<Point> getTypeClass() {
		return Point.class;
	}
}
