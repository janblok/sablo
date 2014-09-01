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

import java.awt.Insets;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

/**
 * @author jcompagner
 *
 */
public class InsetsPropertyType extends DefaultPropertyType<Insets> implements IClassPropertyType<Insets>
{

	public static final InsetsPropertyType INSTANCE = new InsetsPropertyType();

	private InsetsPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return "insets";
	}

	@Override
	public Insets fromJSON(Object newValue, Insets previousValue, IDataConverterContext dataConverterContext)
	{
		// TODO
		return null;
	}


	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Insets i, DataConversion clientConversion) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		writer.object();
		writer.key("paddingTop").value(i.top + "px");
		writer.key("paddingBottom").value(i.bottom + "px");
		writer.key("paddingLeft").value(i.left + "px");
		writer.key("paddingRight").value(i.right + "px");
		return writer.endObject();
	}

	@Override
	public Class<Insets> getTypeClass()
	{
		return Insets.class;
	}

}
