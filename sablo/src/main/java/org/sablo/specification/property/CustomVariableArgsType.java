/*
 * Copyright (C) 2015 Servoy BV
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

import java.util.List;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.util.ValueReference;
import org.sablo.websocket.utils.JSONUtils;

/**
 *  Type for what in spec files you see like 'mytype...'.
 *
 * @author lvostinar
 *
 */
public class CustomVariableArgsType extends CustomJSONPropertyType<Object> implements IAdjustablePropertyType<Object>, IConvertedPropertyType<Object>
{
	public static final String TYPE_NAME = "var_args";

	public CustomVariableArgsType(PropertyDescription definition)
	{
		super(definition.getType().getName(), definition);
	}

	public String getGenericName()
	{
		return TYPE_NAME;
	}

	@Override
	public Object fromJSON(Object newJSONValue, Object previousSabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue)
	{
		//not needed for now, this type is for passing variable number of parameters
		return null;
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, Object sabloValue, PropertyDescription pd, IBrowserConverterContext dataConverterContext)
		throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		writer.array();
		if (sabloValue instanceof List)
		{
			for (int i = 0; i < ((List)sabloValue).size(); i++)
			{
				JSONUtils.FullValueToJSONConverter.INSTANCE.toJSONValue(writer, null, ((List)sabloValue).get(i), getCustomJSONTypeDefinition(),
					dataConverterContext);
			}
		}
		else
		{
			JSONUtils.FullValueToJSONConverter.INSTANCE.toJSONValue(writer, null, sabloValue, getCustomJSONTypeDefinition(), dataConverterContext);
		}
		writer.endArray();
		return writer;
	}
}
