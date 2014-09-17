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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.sablo.Container;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
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
@ServerEndpoint(value = "/websocket/{endpointType}/{sessionid}/{windowid}/{argument}")
public class WebsocketEndpoint implements IWebsocketEndpoint
{
	private static final Logger log = LoggerFactory.getLogger(WebsocketEndpoint.class.getCanonicalName());
	private static ThreadLocal<IWebsocketEndpoint> currentInstance = new ThreadLocal<>();

	private final WeakHashMap<Container, Object> usedContainers = new WeakHashMap<>(3); // set of used container in order to collect all changes

	public static IWebsocketEndpoint get()
	{
		IWebsocketEndpoint websocketEndpoint = currentInstance.get();
		if (websocketEndpoint == null)
		{
			throw new IllegalStateException("no current websocket endpoint set");
		}

		IWebsocketSession wsSession = websocketEndpoint.getWebsocketSession();
		if (wsSession != null)
		{
			List<IWebsocketEndpoint> registeredEndpoints = wsSession.getRegisteredEnpoints();
			if (registeredEndpoints.indexOf(websocketEndpoint) == -1)
			{
				String windowId = websocketEndpoint.getWindowId();
				if (windowId != null)
				{
					for (IWebsocketEndpoint ep : registeredEndpoints)
					{
						if (windowId.equals(ep.getWindowId()))
						{
							websocketEndpoint = ep;
							currentInstance.set(websocketEndpoint);
							break;
						}
					}
				}
			}
		}

		return websocketEndpoint;
	}

	public static boolean exists()
	{
		return currentInstance.get() != null;
	}

	public static IWebsocketEndpoint set(IWebsocketEndpoint endpoint)
	{
		IWebsocketEndpoint websocketEndpoint = currentInstance.get();
		currentInstance.set(endpoint);
		return websocketEndpoint;
	}

	/*
	 * connection with browser
	 */
	private Session session;

	private String windowId;

	/*
	 * user session alike http session space
	 */
	private IWebsocketSession wsSession;

	private final AtomicInteger nextMessageId = new AtomicInteger(0);
	private final Map<Integer, List<Object>> pendingMessages = new HashMap<>();
	private final List<Map<String, Object>> serviceCalls = new ArrayList<>();
	private final PropertyDescription serviceCallTypes = AggregatedPropertyType.newAggregatedProperty();

	public WebsocketEndpoint()
	{
	}

	@OnOpen
	public void start(Session newSession, @PathParam("endpointType")
	final String endpointType, @PathParam("sessionid") String sessionid, @PathParam("windowid")
	final String windowid, @PathParam("argument")
	final String arg) throws Exception
	{
		session = newSession;

		String uuid = "NULL".equals(sessionid) ? null : sessionid;
		windowId = "NULL".equals(windowid) ? null : windowid;
		String argument = "NULL".equals(arg) ? null : arg;

		currentInstance.set(this);
		try
		{
			wsSession = WebsocketSessionManager.getOrCreateSession(endpointType, uuid, true);

			if (!wsSession.getUuid().equals(uuid)) sendMessage(new JSONStringer().object().key("sessionid").value(wsSession.getUuid()).endObject().toString());
			wsSession.registerEndpoint(this);
			wsSession.onOpen(argument);
		}
		finally
		{
			currentInstance.remove();
		}
	}

	/**
	 * @return the windowId
	 */
	@Override
	public String getWindowId()
	{
		return windowId;
	}

	/**
	 * @param windowId the windowId to set
	 */
	public void setWindowId(String windowId)
	{
		this.windowId = windowId;
		try
		{
			sendMessage(new JSONStringer().object().key("windowid").value(windowId).endObject().toString());
		}
		catch (Exception e)
		{
			log.error("error sending the window id to the client", e);
		}
	}

	@OnError
	public void onError(Throwable t)
	{
		if (t instanceof IOException)
		{
			log.error("IOException happend", t.getMessage()); // TODO if it has no message but has a 'cause' it will not print anything useful
		}
		else
		{
			log.error("Exception happend", t);
		}
	}

	@Override
	public void closeSession()
	{
		closeSession(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Application Server shutdown!!!!!!!"));
	}

	@Override
	public void cancelSession(String reason)
	{
		closeSession(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason));
	}

	private void closeSession(CloseReason closeReason)
	{
		if (session != null)
		{
			try
			{
				session.close(closeReason);
			}
			catch (IOException e)
			{
			}
			session = null;
		}
		if (wsSession != null)
		{
			wsSession.deregisterEndpoint(this);
			wsSession = null;
		}
	}

	@OnClose
	public void onClose(@PathParam("endpointType")
	final String endpointType)
	{
		if (wsSession != null)
		{
			wsSession.deregisterEndpoint(this);
			WebsocketSessionManager.closeSession(endpointType, wsSession.getUuid());
		}
		session = null;
	}

	private final StringBuilder incomingPartialMessage = new StringBuilder();

	@OnMessage
	public void incoming(String msg, boolean lastPart)
	{
		String message = msg;
		if (!lastPart)
		{
			incomingPartialMessage.append(message);
			return;
		}
		if (incomingPartialMessage.length() > 0)
		{
			incomingPartialMessage.append(message);
			message = incomingPartialMessage.toString();
			incomingPartialMessage.setLength(0);
		}

		final JSONObject obj;
		try
		{
			currentInstance.set(this);
			obj = new JSONObject(message);
			if (obj.has("smsgid"))
			{
				// response message
				synchronized (pendingMessages)
				{
					List<Object> ret = pendingMessages.get(new Integer(obj.getInt("smsgid")));
					if (ret != null) ret.add(obj.opt("ret"));
					pendingMessages.notifyAll();
				}
				return;
			}

			if (obj.has("service"))
			{
				// service call
				final String serviceName = obj.optString("service");
				final IServerService service = wsSession.getServerService(serviceName);

				if (service != null)
				{
					wsSession.getEventDispatcher().addEvent(new Runnable()
					{

						@Override
						public void run()
						{
							Object result = null;
							String error = null;
							try
							{
								result = service.executeMethod(obj.optString("methodname"), obj.optJSONObject("args"));
							}
							catch (Exception e)
							{
								error = "Error: " + e.getMessage();
								log.error(error, e);
							}

							final Object msgId = obj.opt("cmsgid");
							if (msgId != null) // client wants response
							{
								try
								{
									WebsocketEndpoint.get().sendResponse(msgId, error == null ? result : error, null, error == null);
								}
								catch (IOException e)
								{
									log.error(e.getMessage(), e);
								}
							}
						}
					});
				}
				else
				{
					log.info("unknown service called from the client: " + serviceName);
				}
				return;
			}
			if (obj.has("servicedatapush"))
			{
				String servicename = obj.optString("servicedatapush");
				IClientService service = getWebsocketSession().getService(servicename);
				JSONObject changes = obj.optJSONObject("changes");
				Iterator keys = changes.keys();
				while (keys.hasNext())
				{
					String key = (String)keys.next();
					service.putBrowserProperty(key, changes.opt(key));
				}
				return;
			}
			wsSession.handleMessage(obj);
		}
		catch (JSONException e)
		{
			log.error("JSONException", e);
			return;
		}
		finally
		{
			currentInstance.remove();
		}

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

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes, Map<String, ? > changes,
		PropertyDescription changesTypes) throws IOException
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
		return sendMessage(changes, changesTypes, false); // will return response from last service call
	}

	public Object sendMessage(Map<String, ? > data, PropertyDescription dataTypes, boolean async) throws IOException
	{
		if ((data == null || data.size() == 0) && serviceCalls.size() == 0) return null;

		Map<String, Object> message = new HashMap<>();
		PropertyDescription messageTypes = AggregatedPropertyType.newAggregatedProperty();
		if (data != null && data.size() > 0)
		{
			message.put("msg", data);
			if (dataTypes != null) messageTypes.putProperty("msg", dataTypes);
		}
		if (serviceCalls.size() > 0)
		{
			message.put("services", serviceCalls);
			if (serviceCallTypes != null) messageTypes.putProperty("services", serviceCallTypes);
		}

		Integer messageId = null;
		if (!async)
		{
			message.put("smsgid", messageId = new Integer(nextMessageId.incrementAndGet()));
		}

		try
		{
			if (!messageTypes.hasChildProperties()) messageTypes = null;
			sendText(JSONUtils.writeDataWithConversions(message, messageTypes));
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}

		serviceCalls.clear();

		return (messageId == null) ? null : waitResponse(messageId);
	}

	public void sendMessage(String txt) throws IOException
	{
		sendText("{\"msg\":" + txt + '}');
	}

	@Override
	public void sendResponse(Object msgId, Object object, PropertyDescription objectType, boolean success) throws IOException
	{
		Map<String, Object> data = new HashMap<>();
		data.put("cmsgid", msgId);
		data.put(success ? "ret" : "exception", object);
		try
		{
			sendText(JSONUtils.writeDataWithConversions(data, objectType));
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	private synchronized void sendText(String txt) throws IOException
	{
		if (session == null)
		{
			throw new IOException("No session");
		}
		session.getBasicRemote().sendText(txt);
	}


	/** Wait for a response message with given messsageId.
	 * @throws IOException
	 */
	protected Object waitResponse(Integer messageId) throws IOException
	{
		List<Object> ret = new ArrayList<>(1);
		synchronized (pendingMessages)
		{
			pendingMessages.put(messageId, ret);
			while (ret.size() == 0) // TODO are fail-safes/timeouts needed here in case client browser gets closed or confused?
			{
				try
				{
					pendingMessages.wait();
				}
				catch (InterruptedException e)
				{
					// ignore
				}
			}
			pendingMessages.remove(messageId);
			return ret.get(0);
		}
	}

	public boolean hasSession()
	{
		return session != null;
	}

	@Override
	public void regisiterContainer(Container container)
	{
		usedContainers.put(container, new Object());
	}

	@Override
	public synchronized TypedData<Map<String, Map<String, Map<String, Object>>>> getAllComponentsChanges()
	{
		Map<String, Map<String, Map<String, Object>>> changes = new HashMap<>(8);
		PropertyDescription changeTypes = AggregatedPropertyType.newAggregatedProperty();

		for (Container fc : usedContainers.keySet())
		{
			if (fc.isVisible())
			{
				TypedData<Map<String, Map<String, Object>>> formChanges = fc.getAllComponentsChanges();
				if (formChanges.content.size() > 0)
				{
					changes.put(fc.getName(), formChanges.content);
					if (formChanges.contentType != null) changeTypes.putProperty(fc.getName(), formChanges.contentType);
				}
			}
		}
		if (!changeTypes.hasChildProperties()) changeTypes = null;

		return new TypedData<Map<String, Map<String, Map<String, Object>>>>(changes, changeTypes);
	}

	@Override
	public IWebsocketSession getWebsocketSession()
	{
		return wsSession;
	}
}
