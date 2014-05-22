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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;

/**
 * Base class for handling a websocket session.
 * @author rgansevles
 */
public abstract class BaseWebsocketSession implements IWebsocketSession
{
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

	@Override
	public IForJsonConverter getForJsonConverter()
	{
		// by default no conversion, only support basic types
		return null;
	}

}
