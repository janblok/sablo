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

package org.sablo.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.json.JSONException;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.specification.property.types.DatePropertyType;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.ChangesToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RAGTEST doc
 * The websocket endpoint for communication between the WebSocketSession instance on the server and the browser.
 * This class handles:
 * <ul>
 * <li>creating of websocket sessions and rebinding after reconnect
 * <li>messages protocol with request/response
 * <li>messages protocol with data conversion (currently only date)
 * <li>service calls (both server to client and client to server)
 * </ul>
 *
 * @author jcompagner, rgansevles
 */

public class BaseWindow implements IWindow
{
	private static final Logger log = LoggerFactory.getLogger(BaseWindow.class.getCanonicalName());

	private IWebsocketEndpoint endpoint;
	private IWebsocketSession session;
	private String uuid;
	private final String name;


	private final List<Map<String, ? >> serviceCalls = new ArrayList<>();
	private final PropertyDescription serviceCallTypes = AggregatedPropertyType.newAggregatedProperty();

	private final WeakHashMap<Container, Object> usedContainers = new WeakHashMap<>(3); // set of used container in order to collect all changes

	private String currentFormUrl;

	public BaseWindow(String name)
	{
		this.name = name;
	}

	@Override
	public void setUuid(String uuid)
	{
		this.uuid = uuid;
	}

	@Override
	public String getUuid()
	{
		return uuid;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public IWebsocketSession getSession()
	{
		return session;
	}

	/**
	 * @param session the session to set
	 */
	@Override
	public void setSession(IWebsocketSession session)
	{
		this.session = session;
	}

	@Override
	public void setEndpoint(IWebsocketEndpoint endpoint)
	{
		if (this.endpoint != endpoint)
		{
			this.endpoint = endpoint;
			if (endpoint == null)
			{
				// endpoint was closed
				session.invalidateWindow(this);
			}
		}
	}

	@Override
	public void onOpen()
	{
		// window was connected to new endpoint
		try
		{
			sendServices();
			sendWindowId();
			sendCurrentFormUrl();
		}
		catch (IOException e)
		{
			log.error("Error sending services/windowid to new endpoint", e);
		}
	}

	protected void sendServices() throws IOException
	{
		// send all the service data to the browser.
		Map<String, Object> data = new HashMap<>(3);
		Map<String, Map<String, Object>> serviceData = new HashMap<>();
		PropertyDescription serviceDataTypes = AggregatedPropertyType.newAggregatedProperty();

		for (IClientService service : getSession().getServices())
		{
			TypedData<Map<String, Object>> sd = service.getProperties();
			if (!sd.content.isEmpty())
			{
				serviceData.put(service.getName(), sd.content);
			}
			if (sd.contentType != null) serviceDataTypes.putProperty(service.getName(), sd.contentType);
		}
		if (serviceData.size() > 0)
		{
			data.put("services", serviceData);
		}
		PropertyDescription dataTypes = null;
		if (serviceDataTypes.hasChildProperties())
		{
			dataTypes = AggregatedPropertyType.newAggregatedProperty();
			dataTypes.putProperty("services", serviceDataTypes);
		}
		if (data.size() > 0)
		{
			sendMessage(data, dataTypes, true);
		}
	}

	protected void sendWindowId() throws IOException
	{
		if (uuid == null)
		{
			throw new IllegalStateException("window uuid not set");
		}

		Map<String, String> msg = new HashMap<>();
		msg.put("sessionid", getSession().getUuid());
		msg.put("windowid", uuid);
		sendMessage(msg, null, true);
	}

	@Override
	public void destroy()
	{
	}

	@Override
	public Container getForm(String formName)
	{
		return null;
	}

	/**
	 * @return the currentFormUrl
	 */
	public String getCurrentFormUrl()
	{
		return currentFormUrl;
	}

	/**
	 * @param currentFormUrl the currentFormUrl to set
	 */
	public void setCurrentFormUrl(String currentFormUrl)
	{
		this.currentFormUrl = currentFormUrl;
		// RAGTEST altijd hier?
		sendCurrentFormUrl();
	}

	public void sendCurrentFormUrl()
	{
		if (currentFormUrl != null && getSession() != null)
		{
			getSession().getService(BaseWebsocketSession.SABLO_SERVICE).executeAsyncServiceCall("setCurrentFormUrl", new Object[] { currentFormUrl });
		}
	}

	@Override
	public void cancelSession(String reason)
	{
		if (endpoint != null)
		{
			endpoint.cancelSession(reason);
		}
	}

	@Override
	public void closeSession()
	{
		if (endpoint != null)
		{
			endpoint.closeSession();
		}
	}

	@Override
	public void registerContainer(Container container)
	{
		usedContainers.put(container, new Object());
	}

	protected <ContextT> Object sendMessage(final Map<String, ? > data, final PropertyDescription dataTypes, boolean async) throws IOException
	{
		return sendMessage((data == null || data.size() == 0) ? null : new IToJSONWriter<ContextT>()
		{
			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<ContextT> converter, DataConversion clientDataConversions)
				throws JSONException
			{
				if (data != null && data.size() > 0)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					converter.toJSONValue(w, null, data, dataTypes, clientDataConversions, null);
					return true;
				}
				return false;
			}

		}, async, FullValueToJSONConverter.INSTANCE);
	}

	protected Object sendMessage(IToJSONWriter dataWriter, boolean async, IToJSONConverter converter) throws IOException
	{
		if (dataWriter == null && serviceCalls.size() == 0) return null;

		if (endpoint == null)
		{
			throw new IOException("Endpoint was closed");
		}

		try
		{
			boolean hasContentToSend = false;
			JSONStringer w = new JSONStringer();
			w.object();
			DataConversion clientDataConversions = new DataConversion();

			if (dataWriter != null)
			{
				clientDataConversions.pushNode("msg");
				hasContentToSend = dataWriter.writeJSONContent(w, "msg", converter, clientDataConversions) || hasContentToSend;
				clientDataConversions.popNode();
			}
			if (serviceCalls.size() > 0)
			{
				hasContentToSend = true;
				clientDataConversions.pushNode("services");
				converter.toJSONValue(w, "services", serviceCalls, serviceCallTypes, clientDataConversions, null);
				clientDataConversions.popNode();
			}

			Integer messageId = null;
			String text = null;

			if (hasContentToSend)
			{
				if (!async)
				{
					w.key("smsgid").value(messageId = new Integer(endpoint.getNextMessageId()));
				}
				JSONUtils.writeClientConversions(w, clientDataConversions);
				w.endObject();

				text = w.toString();
				endpoint.sendText(text);
				serviceCalls.clear();
			}

			return messageId == null ? null : endpoint.waitResponse(messageId, text);
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}


	public void sendChanges() throws IOException
	{
		// TODO this should not send to the currently active end-point, but to each of all end-points their own changes...
		// so that any change from 1 end-point request ends up in all the end points.
		sendMessage(new IToJSONWriter<BaseWebObject>()
		{
			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<BaseWebObject> converter, DataConversion clientDataConversions)
				throws JSONException
			{
				JSONUtils.addKeyIfPresent(w, keyInParent);
				w.object();

				clientDataConversions.pushNode("forms");
				boolean changesFound = writeAllComponentsChanges(w, "forms", ChangesToJSONConverter.INSTANCE, clientDataConversions);
				clientDataConversions.popNode();

				clientDataConversions.pushNode("services");
				changesFound = writeAllServicesChanges(w, "services", ChangesToJSONConverter.INSTANCE, clientDataConversions) || changesFound;
				clientDataConversions.popNode();

				w.endObject();

				return changesFound;
			}
		}, true, ChangesToJSONConverter.INSTANCE);
	}


	public void flush() throws IOException
	{
		sendMessage(null, null, true);
	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes, Map<String, ? > changes,
		PropertyDescription changesTypes) throws IOException
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
		return sendMessage(changes, changesTypes, false); // will return response from last service call
	}

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
	}

	private void addServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		// {"services":[{name:serviceName,call:functionName,args:argumentsArray}]}
		Map<String, Object> serviceCall = new HashMap<>();
		PropertyDescription typesOfThisCall = AggregatedPropertyType.newAggregatedProperty();
		serviceCall.put("name", serviceName);
		serviceCall.put("call", functionName);
		serviceCall.put("args", arguments);
		if (argumentTypes != null) typesOfThisCall.putProperty("args", argumentTypes);
		serviceCalls.add(serviceCall);
		serviceCallTypes.putProperty(String.valueOf(serviceCalls.size() - 1), typesOfThisCall);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sablo.websocket.IWindow#hasSession()
	 */
	@Override
	public boolean hasEndpoint()
	{
		return endpoint != null;
	}

	@Override
	public synchronized boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		boolean contentHasBeenWritten = false;

		for (Container fc : usedContainers.keySet())
		{
			if (fc.isVisible() && fc.isChanged())
			{
				if (!contentHasBeenWritten)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();
					contentHasBeenWritten = true;
				}
				String containerName = fc.getName();
				clientDataConversions.pushNode(containerName);
				fc.writeAllComponentsChanges(w, containerName, converter, clientDataConversions);
				clientDataConversions.popNode();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
	}

	public boolean writeAllServicesChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		boolean contentHasBeenWritten = false;
		for (IClientService service : getSession().getServices())
		{
			TypedData<Map<String, Object>> changes = service.getAndClearChanges();
			if (changes.content.size() > 0)
			{
				if (!contentHasBeenWritten)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();
					contentHasBeenWritten = true;
				}
				String childName = service.getName();
				w.key(childName).object();
				clientDataConversions.pushNode(childName);
				JSONUtils.writeData(converter, w, changes.content, changes.contentType, clientDataConversions, (BaseWebObject)service);
				clientDataConversions.popNode();
				w.endObject();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
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
			Object ret = sendMessage(new IToJSONWriter<BaseWebObject>()
			{
				@Override
				public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<BaseWebObject> converter,
					DataConversion clientDataConversions) throws JSONException
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();

					clientDataConversions.pushNode("forms");
					writeAllComponentsChanges(w, "forms", converter, clientDataConversions);
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

					w.endObject();
					return true;
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


}
