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

import javax.websocket.CloseReason;
import javax.websocket.Session;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The websocket endpoint for communication between the WebSocketWindow instance on the server and the browser.
 * This class handles:
 * <ul>
 * <li>creating of websocket sessions and rebinding after reconnect
 * <li>messages protocol with request/response
 * <li>messages protocol with data conversion (currently only date)
 * <li>service calls (both server to client and client to server)
 * </ul>
 *
 * @author jcompagner, rgansevles
/*/
public abstract class WebsocketEndpoint implements IWebsocketEndpoint
{
	public static final Logger log = LoggerFactory.getLogger(WebsocketEndpoint.class.getCanonicalName());

	/*
	 * connection with browser
	 */

	private final String endpointType;

	private Session session;

	private IWindow window;

	private final Map<Integer, List<Object>> pendingMessages = new HashMap<>();

	public WebsocketEndpoint(String endpointType)
	{
		this.endpointType = endpointType;
	}

	public void start(Session newSession, String sessionid, String winname, final String winid) throws Exception
	{
		this.session = newSession;

		String uuid = "null".equalsIgnoreCase(sessionid) ? null : sessionid;
		String windowId = "null".equalsIgnoreCase(winid) ? null : winid;
		String windowName = "null".equalsIgnoreCase(winname) ? null : winname;

		final IWebsocketSession wsSession = WebsocketSessionManager.getOrCreateSession(endpointType, uuid, true);

		CurrentWindow.set(window = wsSession.getOrCreateWindow(windowId, windowName));
		try
		{
			window.setEndpoint(this);

			// send initial setup to client in separate thread in order to release current connection
			wsSession.getEventDispatcher().addEvent(new Runnable()
			{
				@Override
				public void run()
				{
					window.onOpen();
					wsSession.onOpen(session.getRequestParameterMap());
				}
			});
		}
		finally
		{
			CurrentWindow.set(null);
		}

		WebsocketSessionManager.closeInactiveSessions();
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
		window = null;
	}

	public void onClose()
	{
		if (window != null)
		{
			IWindow win = window;
			window = null;
			win.setEndpoint(null);
		}
		session = null;

		WebsocketSessionManager.closeInactiveSessions();
	}

	private final StringBuilder incomingPartialMessage = new StringBuilder();

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

		CurrentWindow.set(window);
		try
		{
			final JSONObject obj = new JSONObject(message);

			if (obj.has("smsgid"))
			{
				window.getSession().getEventDispatcher().addEvent(new Runnable()
				{

					@Override
					public void run()
					{
						// response message
						Integer suspendID = new Integer(obj.optInt("smsgid"));
						List<Object> ret = pendingMessages.remove(suspendID);
						if (ret != null) ret.add(obj.opt("ret"));

						window.getSession().getEventDispatcher().resume(suspendID);
					}

				}, IWebsocketEndpoint.EVENT_LEVEL_SYNC_API_CALL);
			}

			else if (obj.has("service"))
			{
				// service call
				final String serviceName = obj.optString("service");
				final IServerService service = window.getSession().getServerService(serviceName);

				if (service != null)
				{
					final String methodName = obj.optString("methodname");
					final JSONObject arguments = obj.optJSONObject("args");
					int eventLevel = (service instanceof IEventDispatchAwareServerService)
						? ((IEventDispatchAwareServerService)service).getMethodEventThreadLevel(methodName, arguments) : IEventDispatcher.EVENT_LEVEL_DEFAULT;

					window.getSession().getEventDispatcher().addEvent(new Runnable()
					{
						@Override
						public void run()
						{
							Object result = null;
							String error = null;
							try
							{
								result = service.executeMethod(methodName, arguments);
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
									if (error == null)
									{
										Object resultObject = result;
										PropertyDescription objectType = null;
										if (result instanceof TypedData)
										{
											resultObject = ((TypedData< ? >)result).content;
											objectType = ((TypedData< ? >)result).contentType;
										}
										sendResponse(msgId, resultObject, objectType, true);
									}
									else
									{
										sendResponse(msgId, error, null, false);
									}
								}
								catch (IOException e)
								{
									log.error(e.getMessage(), e);
								}
							}
						}
					}, eventLevel);
				}
				else
				{
					log.info("unknown service called from the client: " + serviceName);
				}
			}

			else if (obj.has("servicedatapush"))
			{
				String servicename = obj.optString("servicedatapush");
				IClientService service = window.getSession().getClientService(servicename);
				JSONObject changes = obj.optJSONObject("changes");
				Iterator keys = changes.keys();
				while (keys.hasNext())
				{
					String key = (String)keys.next();
					service.putBrowserProperty(key, changes.opt(key));
				}
			}

			else
			{
				window.getSession().handleMessage(obj);
			}
		}
		catch (JSONException e)
		{
			log.error("JSONException", e);
			return;
		}
		finally
		{
			CurrentWindow.set(null);
		}

	}

	protected void sendResponse(Object msgId, Object object, PropertyDescription objectType, boolean success) throws IOException
	{
		Map<String, Object> data = new HashMap<>();
		String key = success ? "ret" : "exception";
		data.put(key, object);
		data.put("cmsgid", msgId);
		PropertyDescription dataTypes = null;
		if (objectType != null)
		{
			dataTypes = AggregatedPropertyType.newAggregatedProperty();
			dataTypes.putProperty(key, objectType);
		}

		try
		{
			sendText(JSONUtils.writeDataWithConversions(FullValueToJSONConverter.INSTANCE, data, dataTypes, null));
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	public synchronized void sendText(String txt) throws IOException
	{
		if (session == null)
		{
			throw new IOException("No session");
		}
		session.getBasicRemote().sendText(txt);
	}


	/** Wait for a response message with given messsageId.
	 * @param text
	 * @throws IOException
	 */
	public Object waitResponse(Integer messageId, String text) throws IOException
	{
		List<Object> ret = new ArrayList<>(1);
		pendingMessages.put(messageId, ret);
		window.getSession().getEventDispatcher().suspend(messageId, EVENT_LEVEL_SYNC_API_CALL); // TODO are fail-safes/timeouts needed here in case client browser gets closed or confused?

		if (ret.size() == 0)
		{
			log.warn("No response from client for message '" + text + "'");
			// Or throw an exception here?
			return null;
		}
		return ret.get(0);
	}

	public boolean hasSession()
	{
		return session != null;
	}

}
