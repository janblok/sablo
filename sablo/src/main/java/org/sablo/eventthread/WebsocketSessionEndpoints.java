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

package org.sablo.eventthread;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.sablo.Container;
import org.sablo.websocket.ConversionLocation;
import org.sablo.websocket.IForJsonConverter;
import org.sablo.websocket.IWebsocketEndpoint;
import org.sablo.websocket.IWebsocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link IWebsocketEndpoint} implementation that redirects all the calls on it to the current registered,
 * {@link IWebsocketSession#getRegisteredEnpoints()}, endpoints.
 *
 * @author jcompagner
 *
 */
public class WebsocketSessionEndpoints implements IWebsocketEndpoint
{

	private static final Logger log = LoggerFactory.getLogger(WebsocketSessionEndpoints.class.getCanonicalName());


	private final IWebsocketSession session;

	/**
	 * @param session
	 */
	public WebsocketSessionEndpoints(IWebsocketSession session)
	{
		this.session = session;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sablo.websocket.IWebsocketEndpoint#getWindowId()
	 */
	@Override
	public String getWindowId()
	{
		// just ignore, this is a special class, not really an endpoint
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sablo.websocket.IWebsocketEndpoint#setWindowId(java.lang.String)
	 */
	@Override
	public void setWindowId(String windowId)
	{
		// just ignore, this is a special class, not really an endpoint
	}

	@Override
	public boolean hasSession()
	{
		return true;
	}

	@Override
	public void closeSession()
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.closeSession();
		}
	}

	@Override
	public void cancelSession(String reason)
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.cancelSession(reason);
		}
	}

	@Override
	public Object sendMessage(Map<String, ? > data, boolean async, IForJsonConverter forJsonConverter) throws IOException
	{
		// TODO should this throw an illegal call exception? Because this kind of call shouildn't be used in this class?
		// returns the first none null value.
		return sendMessage(data, async, forJsonConverter, ConversionLocation.BROWSER_UPDATE);
	}

	@Override
	public Object sendMessage(Map<String, ? > data, boolean async, IForJsonConverter forJsonConverter, ConversionLocation conversionLocation)
		throws IOException
	{
		Object retValue = null;
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			Object reply = endpoint.sendMessage(data, async, forJsonConverter, conversionLocation);
			retValue = retValue == null ? reply : retValue;
		}
		return retValue;
	}

	@Override
	public void sendMessage(String txt) throws IOException
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.sendMessage(txt);
		}
	}

	@Override
	public void sendResponse(Object msgId, Object object, boolean success, IForJsonConverter forJsonConverter) throws IOException
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.sendResponse(msgId, object, success, forJsonConverter);
		}
	}

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments)
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.executeAsyncServiceCall(serviceName, functionName, arguments);
			try
			{
				// Call is initiated not from client, flush call otherwise it will only be sent after next client call
				endpoint.sendMessage(null, true, null);
			}
			catch (IOException e)
			{
				log.error("Error flushing service call to endpoint " + endpoint, e);
			}
		}

	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments, Map<String, ? > changes) throws IOException
	{
		// TODO should this throw an illegal call exception? Because this kind of call shouildn't be used in this class?
		// returns the first none null value.
		Object retValue = null;
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			Object reply = endpoint.executeServiceCall(serviceName, functionName, arguments, changes);
			retValue = retValue == null ? reply : retValue;
		}
		return retValue;
	}

	@Override
	public void regisiterContainer(Container container)
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.regisiterContainer(container);
		}
	}

	@Override
	public Map<String, Map<String, Map<String, Object>>> getAllComponentsChanges()
	{
		Map<String, Map<String, Map<String, Object>>> changes = new HashMap<>(8);
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			changes.putAll(endpoint.getAllComponentsChanges());
		}
		return changes;
	}

	@Override
	public IWebsocketSession getWebsocketSession()
	{
		return session;
	}
}
