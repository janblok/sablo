/*
 * Copyright (C) 2022 Servoy BV
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

package org.sablo.specification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.sablo.specification.property.CustomVariableArgsType;

/**
 * Function parameters would normally be a List<PropertyDescription>, but as we support as last param stuff like "String...", it should be able to
 * allow both someFunction(a, b, c, [varargs]) (that is what CustomVariableArgsType does) and stuff like someFunction(a, b, c, vararg1, vararg2, vararg3).
 *
 * @author acostescu
 */
public class FunctionParameters implements IFunctionParameters
{

	private final List<PropertyDescription> parameters;

	public FunctionParameters()
	{
		parameters = new ArrayList<>();
	}

	public FunctionParameters(int initialSize)
	{
		parameters = new ArrayList<>(initialSize);
	}

	public PropertyDescription getParameterDefinition(int argIndex)
	{
		return parameters.get(argIndex);
	}

	public PropertyDescription getParameterDefinitionTreatVarArgs(int argIndex)
	{
		if (argIndex < parameters.size() - 1) return parameters.get(argIndex);
		else // argIndex >= parameters.size() - 1
		{
			PropertyDescription lastArg = parameters.size() > 0 ? parameters.get(parameters.size() - 1) : null;
			if (lastArg != null && lastArg.getType() instanceof CustomVariableArgsType)
				return ((CustomVariableArgsType)lastArg.getType()).getCustomJSONTypeDefinition();
			else if (argIndex == parameters.size() - 1) return parameters.get(argIndex);
			else return null;
		}
	}

	public int getDefinedArgsCount()
	{
		return parameters.size();
	}

	public boolean isVarArgs()
	{
		PropertyDescription lastArg = parameters.size() > 0 ? parameters.get(parameters.size() - 1) : null;
		return (lastArg != null && lastArg.getType() instanceof CustomVariableArgsType);
	}

	public void add(PropertyDescription parameter)
	{
		parameters.add(parameter);
	}

	@Override
	public Iterator<PropertyDescription> iterator()
	{
		return parameters.iterator();
	}

}
