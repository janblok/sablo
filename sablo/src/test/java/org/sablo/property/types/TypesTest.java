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
package org.sablo.property.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Dimension;
import java.awt.Point;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.junit.Test;
import org.sablo.specification.property.types.DimensionPropertyType;
import org.sablo.specification.property.types.PointPropertyType;

/**
 * @author jcompagner
 *
 */
public class TypesTest
{

	@Test
	public void testDimensionType() throws Exception
	{
		DimensionPropertyType type = DimensionPropertyType.INSTANCE;

		Dimension dim = type.defaultValue();
		assertNotNull(dim);

		assertEquals(0, dim.height);
		assertEquals(0, dim.width);

		dim.height = 10;
		dim.width = 10;

		JSONStringer writer = new JSONStringer();
		type.toJSON(writer, null, dim, null, null);

		String json = writer.toString();

		assertEquals("{\"width\":10,\"height\":10}", json);

		JSONObject object = new JSONObject(json);

		Dimension result = type.fromJSON(object, dim, null);

		assertEquals(dim, result);
	}


	@Test
	public void testPointType() throws Exception
	{
		PointPropertyType type = PointPropertyType.INSTANCE;

		Point point = type.defaultValue();
		assertNotNull(point);

		assertEquals(0, point.x);
		assertEquals(0, point.y);

		point.x = 10;
		point.y = 10;

		JSONStringer writer = new JSONStringer();
		type.toJSON(writer, null, point, null, null);

		String json = writer.toString();

		assertEquals("{\"x\":10,\"y\":10}", json);

		JSONObject object = new JSONObject(json);

		Point result = type.fromJSON(object, point, null);

		assertEquals(point, result);
	}

}
