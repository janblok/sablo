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
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IPropertyConverterForBrowser;
import org.sablo.util.ValueReference;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;


/**
 * @author jcompagner
 *
 */
public class LongPropertyType extends DefaultPropertyType<Long> implements IPropertyConverterForBrowser<Number>
{

	public static final LongPropertyType INSTANCE = new LongPropertyType();
	public static final String TYPE_NAME = "long";

	private LongPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Long defaultValue(PropertyDescription pd)
	{
		return Long.valueOf(0);
	}

	@Override
	public Number fromJSON(Object newJSONValue, Number previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newJSONValue == null || newJSONValue instanceof Long) return (Long)newJSONValue;
		if (newJSONValue instanceof Number)
		{
			Long val = Long.valueOf(((Number)newJSONValue).longValue());
			if (returnValueAdjustedIncommingValue != null && val.doubleValue() != ((Number)newJSONValue).doubleValue()) returnValueAdjustedIncommingValue.value = Boolean.TRUE;
			return val;
		}
		if (newJSONValue instanceof String)
		{
			if (((String)newJSONValue).trim().length() == 0) return null;

			Locale locale = CurrentWindow.get().getSession().getLocale();
			Number parsedValue;
			try
			{
				parsedValue = NumberFormat.getIntegerInstance(locale).parse((String)newJSONValue);
				if (parsedValue instanceof Long)
				{
					return parsedValue;
				}
				else
				{
					Long val = Long.valueOf(parsedValue.longValue());
					if (returnValueAdjustedIncommingValue != null && val.doubleValue() != parsedValue.doubleValue()) returnValueAdjustedIncommingValue.value = Boolean.TRUE;
					return val;
				}
			}
			catch (ParseException e)
			{
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Number sabloValue, PropertyDescription pd, DataConversion clientConversion,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		writer.value(sabloValue);
		return writer;
	}
}
