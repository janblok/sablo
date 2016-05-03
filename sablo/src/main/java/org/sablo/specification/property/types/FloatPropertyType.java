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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author jcompagner
 *
 */
public class FloatPropertyType extends DefaultPropertyType<Float> implements IPropertyConverterForBrowser<Number>
{
	protected static final Logger log = LoggerFactory.getLogger(FloatPropertyType.class.getCanonicalName());
	public static final FloatPropertyType INSTANCE = new FloatPropertyType();
	public static final String TYPE_NAME = "float";

	private FloatPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Float defaultValue(PropertyDescription pd)
	{
		return Float.valueOf(0);
	}

	@Override
	public Number fromJSON(Object newJSONValue, Number previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		if (newJSONValue == null || newJSONValue instanceof Float) return (Float)newJSONValue;
		if (newJSONValue instanceof Number) return Float.valueOf(((Number)newJSONValue).floatValue());
		if (newJSONValue instanceof String)
		{
			if (((String)newJSONValue).trim().length() == 0) return null;

			Locale locale = CurrentWindow.get().getSession().getLocale();
			Number parsedValue;
			try
			{
				parsedValue = NumberFormat.getNumberInstance(locale).parse((String)newJSONValue);
				return parsedValue instanceof Float ? (Float)parsedValue : Float.valueOf(parsedValue.floatValue());
			}
			catch (ParseException e)
			{
				log.warn("Parse exception while processing " + newJSONValue + " as a float", e);
				if (returnValueAdjustedIncommingValue != null) returnValueAdjustedIncommingValue.value = Boolean.TRUE;
				return null;
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
