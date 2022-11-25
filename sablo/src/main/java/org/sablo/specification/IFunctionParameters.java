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

/**
 * Function parameters would normally be a List<PropertyDescription>, but as we support as last param stuff like "String...", it should be able to
 * allow both someFunction(a, b, c, [varargs]) (that is what CustomVariableArgsType does) and stuff like someFunction(a, b, c, vararg1, vararg2, vararg3).
 *
 * @author acostescu
 */
public interface IFunctionParameters extends Iterable<PropertyDescription>
{

	PropertyDescription getParameterDefinition(int argIndex);

	PropertyDescription getParameterDefinitionTreatVarArgs(int argIndex);

	int getDefinedArgsCount();

	boolean isVarArgs();

}