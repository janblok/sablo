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
package org.sablo.websocket.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.json.JSONException;
import org.sablo.BaseWebObject;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * implementation of {@link IClientService}
 *
 * @author jcompagner
 */
public class ClientService extends BaseWebObject implements IClientService
{

	private static final Logger log = LoggerFactory.getLogger(ClientService.class.getCanonicalName());

	public ClientService(String serviceName, WebComponentSpecification spec)
	{
		super(serviceName, spec);
	}

	@Override
	public Object executeServiceCall(String functionName, Object[] arguments) throws IOException
	{
		WebComponentSpecification spec = WebServiceSpecProvider.getInstance().getWebServiceSpecification(name);
		WebComponentApiDefinition apiFunction = null;
		if (spec != null)
		{
			apiFunction = spec.getApiFunction(functionName);
		}

		TypedData<Map<String, Object>> serviceChanges = getAndClearChanges();
		Object retValue = CurrentWindow.get().executeServiceCall(name, functionName, arguments, getParameterTypes(functionName),
			serviceChanges.content.isEmpty() ? null : Collections.singletonMap("services", Collections.singletonMap(getName(), serviceChanges.content)),
			serviceChanges.contentType, apiFunction != null ? apiFunction.getBlockEventProcessing() : true);
		if (retValue != null)
		{
			if (spec != null)
			{
				if (apiFunction != null && apiFunction.getReturnType() != null)
				{
					try
					{
						return JSONUtils.fromJSONUnwrapped(null, retValue, new DataConverterContext(apiFunction.getReturnType(), this));
					}
					catch (JSONException e)
					{
						log.error("Error interpreting return value (wrong type ?):", e); //$NON-NLS-1$
						return null;
					}
				}
			}
		}
		return retValue;
	}

	@Override
	public void executeAsyncServiceCall(String functionName, Object[] arguments)
	{
		CurrentWindow.get().executeAsyncServiceCall(name, functionName, arguments, getParameterTypes(functionName));
	}

	protected PropertyDescription getParameterTypes(String functionName)
	{
		// we have a list of properties available; create a suitable wrapper PropertyDescription that has the
		// param indexes as child PropertyDescriptions
		PropertyDescription parameterTypes = null;
		WebComponentApiDefinition apiFunc = specification.getApiFunction(functionName);
		if (apiFunc != null)
		{
			parameterTypes = WebComponent.getParameterTypes(apiFunc);
		}
		return parameterTypes;
	}

}
