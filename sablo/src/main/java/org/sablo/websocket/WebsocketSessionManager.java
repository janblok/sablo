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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Websocket user session management
 * @author jblok, rgansevles
 */
public class WebsocketSessionManager
{
	private static final Logger log = LoggerFactory.getLogger(WebsocketSessionManager.class.getCanonicalName());

	private static Map<String, IWebsocketSessionFactory> websocketSessionFactories = new HashMap<>();

	//maps form uuid to session
	private static Map<String, IWebsocketSession> wsSessions = new HashMap<>();

	//maps sessionkey to time
	private static Map<String, Long> nonActiveWsSessions = new HashMap<>();

	private static final long SESSION_TIMEOUT = 1 * 60 * 1000;

	public static void addSession(IWebsocketSession wsSession)
	{
		synchronized (wsSessions)
		{
			wsSessions.put(wsSession.getUuid(), wsSession);
		}
	}

	public static IWebsocketSession removeSession(String uuid)
	{
		synchronized (wsSessions)
		{
			nonActiveWsSessions.remove(uuid);
			return wsSessions.remove(uuid);
		}
	}

	public static IWebsocketSession getSession(String endpointType, String prevUuid)
	{
		try
		{
			return getOrCreateSession(endpointType, prevUuid, false);
		}
		catch (Exception e)
		{
			log.error("exception calling getSession (not create) should not happen", e);
		}
		return null;
	}

	/**
	 * This method only throws an exception if the creation of the client fails (so the create boolean is true)
	 *
	 * @param endpointType
	 * @param prevUuid
	 * @param create
	 * @return
	 * @throws Exception
	 */
	static IWebsocketSession getOrCreateSession(String endpointType, String prevUuid, boolean create) throws Exception
	{
		String uuid = prevUuid;
		IWebsocketSession wsSession = null;
		synchronized (wsSessions)
		{
			if (uuid != null && uuid.length() > 0)
			{
				wsSession = wsSessions.get(uuid);
				nonActiveWsSessions.remove(uuid);
			}
			else
			{
				uuid = UUID.randomUUID().toString();
			}
			if (wsSession == null || !wsSession.isValid())
			{
				wsSessions.remove(uuid);
				wsSession = null;
				if (create && websocketSessionFactories.containsKey(endpointType))
				{
					wsSession = websocketSessionFactories.get(endpointType).createSession(uuid);
				}
				if (wsSession != null)
				{
					wsSessions.put(uuid, wsSession);
				}
			}
		}
		return wsSession;
	}

	/**
	 * @param endpointType
	 * @param uuid
	 */
	static void closeSession(String endpointType, String uuid)
	{
		synchronized (wsSessions)
		{
			//do global non active cleanup
			long currentTime = System.currentTimeMillis();
			Iterator<Entry<String, Long>> iterator = nonActiveWsSessions.entrySet().iterator();
			while (iterator.hasNext())
			{
				Entry<String, Long> entry = iterator.next();
				if (currentTime - entry.getValue().longValue() > SESSION_TIMEOUT)
				{
					wsSessions.remove(entry.getKey());
					iterator.remove();
				}
			}

			//mark current as non active
			if (uuid != null)
			{
				nonActiveWsSessions.put(uuid, new Long(currentTime));
			}
		}
	}

	public static void setWebsocketSessionFactory(String endpointType, IWebsocketSessionFactory factory)
	{
		websocketSessionFactories.put(endpointType, factory);
	}

	public static IWebsocketSessionFactory getWebsocketSessionFactory(String endpointType)
	{
		return websocketSessionFactories.get(endpointType);
	}
}
