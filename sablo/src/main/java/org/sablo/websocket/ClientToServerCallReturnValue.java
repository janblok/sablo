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

package org.sablo.websocket;

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.IBrowserConverterContext;

/**
 * @author acostescu
 */
public class ClientToServerCallReturnValue
{

	public final Object retValOrErrorMessage;
	public final PropertyDescription returnType;
	public IBrowserConverterContext converterContextForReturnValue;
	public final boolean success;
	public Object cmsgid;

	public ClientToServerCallReturnValue(Object retValOrErrorMessage, PropertyDescription returnType, BrowserConverterContext converterContextForReturnValue,
		boolean success)
	{
		this.retValOrErrorMessage = retValOrErrorMessage;
		this.returnType = returnType;
		this.converterContextForReturnValue = converterContextForReturnValue;
		this.success = success;
	}


}
