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

package org.sablo.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sablo.WebComponent;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.types.DatePropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for handling a websocket session.
 * @author rgansevles
 */
public abstract class BaseWebsocketSession implements IWebsocketSession
{
	private static final Logger log = LoggerFactory.getLogger(WebsocketEndpoint.class.getCanonicalName());
	private final Map<String, IService> services = new HashMap<>();
	private final List<IWebsocketEndpoint> registeredEnpoints = Collections.synchronizedList(new ArrayList<IWebsocketEndpoint>());

	private final String uuid;
	private volatile IEventDispatcher executor;

	public BaseWebsocketSession(String uuid)
	{
		this.uuid = uuid;
	}

	public void registerEndpoint(IWebsocketEndpoint endpoint)
	{
		registeredEnpoints.add(endpoint);
	}

	public void deregisterEndpoint(IWebsocketEndpoint endpoint)
	{
		registeredEnpoints.remove(endpoint);
	}

	/**
	 * @return the registeredEnpoints
	 */
	public List<IWebsocketEndpoint> getRegisteredEnpoints()
	{
		return Collections.unmodifiableList(registeredEnpoints);
	}

	public final IEventDispatcher getEventDispatcher()
	{
		if (executor == null) {
			synchronized (this) {
				if (executor == null) {
					Thread thread = new Thread(executor = createDispatcher(),
							"Executor,uuid:" + uuid);
					thread.setDaemon(true);
					thread.start();
				}
			}
		}
		return executor;
	}

	/**
	 * Method to create the {@link IEventDispatcher} runnable
	 */
	protected IEventDispatcher createDispatcher()
	{
		return new EventDispatcher(this);
	}

	public void onOpen(String argument)
	{
	}

	@Override
	public void closeSession()
	{
		for (IWebsocketEndpoint endpoint : registeredEnpoints.toArray(new IWebsocketEndpoint[registeredEnpoints.size()]))
		{
			endpoint.closeSession();
		}
		if (executor != null) executor.destroy();
	}

	/**
	 * @return the uuid
	 */
	public String getUuid()
	{
		return uuid;
	}

	public void registerService(String name, IService service)
	{
		services.put(name, service);
	}

	public IService getService(String name)
	{
		return services.get(name);
	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments) throws IOException
	{
		return WebsocketEndpoint.get().executeServiceCall(serviceName, functionName, arguments);
	}

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments)
	{
		WebsocketEndpoint.get().executeAsyncServiceCall(serviceName, functionName, arguments);
	}

	public Object invokeApi(WebComponent receiver, WebComponentApiDefinition apiFunction, Object[] arguments)
	{
		return invokeApi(receiver, apiFunction, arguments, null);
	}
	
	protected Object invokeApi(WebComponent receiver, WebComponentApiDefinition apiFunction, Object[] arguments, Map<String, Object> callContributions)
	{
		// {"call":{"form":"product","bean":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}}
		try
		{
			Map<String, Map<String, Map<String, Object>>> changes = WebsocketEndpoint.get().getAllComponentsChanges();
			Map<String, Object> data = new HashMap<>();
			data.put("forms", changes);

			Map<String, Object> call = new HashMap<>();
			if (callContributions != null) call.putAll(callContributions);
			call.put("form", receiver.getParent().getName());
			call.put("bean", receiver.getName());
			call.put("api", apiFunction.getName());
			if (arguments != null && arguments.length > 0)
			{
				call.put("args", arguments);
			}
			data.put("call", call);

			Object ret = WebsocketEndpoint.get().sendMessage(data, false, getForJsonConverter());
			// convert dates back
			if (ret instanceof Long && apiFunction.getReturnType().getType() instanceof DatePropertyType)
			{
				return new Date(((Long)ret).longValue());
			}
			return ret;
		}
		catch (IOException e)
		{
			log.error("IOException occurred",e);
		}

		return null;
	}

	@Override
	public IForJsonConverter getForJsonConverter()
	{
		// by default no conversion, only support basic types
		return null;
	}
}
