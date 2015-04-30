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

import java.text.NumberFormat;
import java.text.ParseException;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.specification.property.IPropertyConverter;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;


/**
 * @author jcompagner
 *
 */
public class DoublePropertyType extends DefaultPropertyType<Double> implements IPropertyConverter<Number>
{
	public static final DoublePropertyType INSTANCE = new DoublePropertyType();
	public static final String TYPE_NAME = "double";

	private DoublePropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Double defaultValue(PropertyDescription pd)
	{
		return Double.valueOf(0);
	}

	@Override
	public Number fromJSON(Object newJSONValue, Number previousSabloValue, IDataConverterContext dataConverterContext)
	{
		if (newJSONValue == null || newJSONValue instanceof Double) return (Double)newJSONValue;
		if (newJSONValue instanceof Number) return Double.valueOf(((Number)newJSONValue).doubleValue());
		if (newJSONValue instanceof String)
		{
			if (((String)newJSONValue).trim().length() == 0) return null;
// TODO get the locale from the session?
// IWebsocketSession session = CurrentWindow.get().getSession();
			Number parsedValue;
			try
			{
				parsedValue = NumberFormat.getNumberInstance().parse((String)newJSONValue);
				return parsedValue instanceof Double ? (Double)parsedValue : Double.valueOf(parsedValue.doubleValue());
			}
			catch (ParseException e)
			{
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Number sabloValue, DataConversion clientConversion, IDataConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		writer.value(sabloValue);
		return writer;
	}
}
