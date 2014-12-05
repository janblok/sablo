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
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.Container;
import org.sablo.IChangeListener;
import org.sablo.WebComponent;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.services.FormServiceHandler;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.specification.property.types.DatePropertyType;
import org.sablo.websocket.impl.ClientService;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.ChangesToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Base class for handling a websocket session.
 * @author rgansevles
 */
public abstract class BaseWebsocketSession implements IWebsocketSession, IChangeListener
{
	private static final Logger log = LoggerFactory.getLogger(WebsocketEndpoint.class.getCanonicalName());
	private final Map<String, IServerService> serverServices = new HashMap<>();
	private final Map<String, IClientService> services = new HashMap<>();
	private final List<IWebsocketEndpoint> registeredEnpoints = Collections.synchronizedList(new ArrayList<IWebsocketEndpoint>());

	private final String uuid;
	private volatile IEventDispatcher executor;

	private final AtomicInteger handlingEvent = new AtomicInteger(0);

	private boolean proccessChanges;


	public BaseWebsocketSession(String uuid)
	{
		this.uuid = uuid;
		registerServerService("formService", createFormService());
	}

	/**
	 * @return
	 */
	protected IServerService createFormService()
	{
		return new FormServiceHandler(this);
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
		if (executor == null)
		{
			synchronized (this)
			{
				if (executor == null)
				{
					Thread thread = new Thread(executor = createDispatcher(), "Executor,uuid:" + uuid);
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
		// send all the service data to the browser.
		Map<String, Object> data = new HashMap<>(3);
		Map<String, Map<String, Object>> serviceData = new HashMap<>();
		PropertyDescription serviceDataTypes = AggregatedPropertyType.newAggregatedProperty();

		for (Entry<String, IClientService> entry : services.entrySet())
		{
			TypedData<Map<String, Object>> sd = entry.getValue().getProperties();
			if (!sd.content.isEmpty())
			{
				serviceData.put(entry.getKey(), sd.content);
			}
			if (sd.contentType != null) serviceDataTypes.putProperty(entry.getKey(), sd.contentType);
		}
		if (!serviceDataTypes.hasChildProperties()) serviceDataTypes = null;
		if (serviceData.size() > 0)
		{
			data.put("services", serviceData);
		}
		PropertyDescription dataTypes = null;
		if (serviceDataTypes != null)
		{
			dataTypes = AggregatedPropertyType.newAggregatedProperty();
			dataTypes.putProperty("services", serviceDataTypes);
		}
		try
		{
			if (data.size() > 0)
			{
				WebsocketEndpoint.get().sendMessage(data, dataTypes, true, FullValueToJSONConverter.INSTANCE);
			}
		}
		catch (IOException e)
		{
			log.error(e.getLocalizedMessage(), e);
		}
	}

	@Override
	public void closeSession()
	{
		for (IWebsocketEndpoint endpoint : registeredEnpoints.toArray(new IWebsocketEndpoint[registeredEnpoints.size()]))
		{
			endpoint.closeSession();
		}
		if (executor != null)
		{
			synchronized (this)
			{
				if (executor != null)
				{
					executor.destroy();
					executor = null;
				}
			}
		}
	}

	/**
	 * @return the uuid
	 */
	public String getUuid()
	{
		return uuid;
	}

	public void registerServerService(String name, IServerService service)
	{
		if (service != null)
		{
			serverServices.put(name, service);
		}
	}

	public IServerService getServerService(String name)
	{
		return serverServices.get(name);
	}

	@Override
	public IClientService getService(String name)
	{

		IClientService clientService = services.get(name);
		if (clientService == null)
		{
			clientService = createClientService(name);
			services.put(name, clientService);
		}
		return clientService;
	}

	/**
	 * @param name
	 * @return
	 */
	protected IClientService createClientService(String name)
	{
		return new ClientService(name, WebServiceSpecProvider.getInstance().getWebServiceSpecification(name));
	}

	public boolean writeAllServicesChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		boolean contentHasBeenWritten = false;
		for (IClientService service : services.values())
		{
			TypedData<Map<String, Object>> changes = service.getChanges();
			if (changes.content.size() > 0)
			{
				if (!contentHasBeenWritten)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();
					contentHasBeenWritten = true;
				}
				String childName = service.getName();
				w.key(childName);
				clientDataConversions.pushNode(childName);
				JSONUtils.writeData(converter, w, changes.content, changes.contentType, clientDataConversions, (BaseWebObject)service);
				clientDataConversions.popNode();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
	}

	public void startHandlingEvent()
	{
		handlingEvent.incrementAndGet();
	}

	public void stopHandlingEvent()
	{
		handlingEvent.decrementAndGet();
		valueChanged();
	}

	@Override
	public void valueChanged()
	{
		// if there is an incoming message or an Event running on event thread, postpone sending until it's done; else push it.
		if (!proccessChanges && WebsocketEndpoint.exists() && WebsocketEndpoint.get().hasSession() && handlingEvent.get() == 0)
		{
			sendChanges();
		}
	}

	protected void sendChanges()
	{
		try
		{
			proccessChanges = true;

			// TODO this should not send to the currently active end-point, but to each of all end-points their own changes...
			// so that any change from 1 end-point request ends up in all the end points.
			WebsocketEndpoint.get().sendMessage(new IToJSONWriter()
			{
				@Override
				public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
					throws JSONException
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();

					clientDataConversions.pushNode("forms");
					boolean changesFound = WebsocketEndpoint.get().writeAllComponentsChanges(w, "forms", ChangesToJSONConverter.INSTANCE, clientDataConversions);
					clientDataConversions.popNode();

					clientDataConversions.pushNode("services");
					changesFound = writeAllServicesChanges(w, "services", ChangesToJSONConverter.INSTANCE, clientDataConversions) || changesFound;
					clientDataConversions.popNode();

					w.endObject();

					return changesFound;
				}
			}, true, ChangesToJSONConverter.INSTANCE);
		}
		catch (IOException e)
		{
			log.error("sendChanges", e);
		}
		finally
		{
			proccessChanges = false;
		}
	}

	public Object invokeApi(WebComponent receiver, WebComponentApiDefinition apiFunction, Object[] arguments, PropertyDescription argumentTypes)
	{
		return invokeApi(receiver, apiFunction, arguments, argumentTypes, null);
	}

	protected Object invokeApi(final WebComponent receiver, final WebComponentApiDefinition apiFunction, final Object[] arguments,
		final PropertyDescription argumentTypes, final Map<String, Object> callContributions)
	{
		// {"call":{"form":"product","bean":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}}
		try
		{
			Object ret = WebsocketEndpoint.get().sendMessage(new IToJSONWriter()
			{
				@Override
				public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
					throws JSONException
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();

					clientDataConversions.pushNode("forms");
					WebsocketEndpoint.get().writeAllComponentsChanges(w, "forms", converter, clientDataConversions);
					clientDataConversions.popNode();

					Map<String, Object> call = new HashMap<>();
					PropertyDescription callTypes = AggregatedPropertyType.newAggregatedProperty();
					if (callContributions != null) call.putAll(callContributions);
					Container topContainer = receiver.getParent();
					while (topContainer != null && topContainer.getParent() != null)
					{
						topContainer = topContainer.getParent();
					}
					call.put("form", topContainer.getName());
					call.put("bean", receiver.getName());
					call.put("api", apiFunction.getName());
					if (arguments != null && arguments.length > 0)
					{
						call.put("args", arguments);
						if (argumentTypes != null) callTypes.putProperty("args", argumentTypes);
					}

					w.key("call").object();
					clientDataConversions.pushNode("call");
					JSONUtils.writeData(converter, w, call, callTypes, clientDataConversions, receiver);
					clientDataConversions.popNode();
					w.endObject();

					return false;
				}
			}, false, FullValueToJSONConverter.INSTANCE);


			// convert dates back; TODO should this if be removed?; the JSONUtils.fromJSON below should do this anyway
			if (ret instanceof Long && apiFunction.getReturnType().getType() instanceof DatePropertyType)
			{
				return new Date(((Long)ret).longValue());
			}
			if (apiFunction.getReturnType() != null)
			{
				try
				{
					return JSONUtils.fromJSON(null, ret, apiFunction.getReturnType(), new DataConverterContext(apiFunction.getReturnType(), receiver));
				}
				catch (Exception e)
				{
					log.error("Cannot parse api call return value JSON for: " + ret + " for api call: " + apiFunction, e);
				}
			}
		}
		catch (IOException e)
		{
			log.error("IOException occurred", e);
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sablo.websocket.IWebsocketSession#handleMessage(org.json.JSONObject)
	 */
	@Override
	public void handleMessage(JSONObject obj)
	{
	}
}
