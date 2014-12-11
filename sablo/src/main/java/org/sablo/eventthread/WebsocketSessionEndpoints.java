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
import java.util.Map;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.Container;
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.IToJSONWriter;
import org.sablo.websocket.IWebsocketEndpoint;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
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
	public Object sendMessage(Map<String, ? > data, PropertyDescription dataType, boolean async, IToJSONConverter converter) throws IOException
	{
		Object retValue = null;
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			Object reply = endpoint.sendMessage(data, dataType, async, converter);
			retValue = retValue == null ? reply : retValue;
		}
		return retValue;
	}

	@Override
	public Object sendMessage(IToJSONWriter dataWriter, boolean async, IToJSONConverter converter) throws IOException
	{
		// TODO here we could write the data only once and send it to all endpoints
		Object retValue = null;
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			Object reply = endpoint.sendMessage(dataWriter, async, converter);
			retValue = retValue == null ? reply : retValue;
		}
		return retValue;
	}

	@Override
	public void flush() throws IOException
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.flush();
		}
	}

	@Override
	public void sendResponse(Object msgId, Object object, PropertyDescription objectType, IToJSONConverter converter, boolean success) throws IOException
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.sendResponse(msgId, object, objectType, converter, success);
		}
	}

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.executeAsyncServiceCall(serviceName, functionName, arguments, argumentTypes);
			try
			{
				// Call is initiated not from client, flush call otherwise it will only be sent after next client call
				endpoint.flush();
			}
			catch (IOException e)
			{
				log.error("Error flushing service call to endpoint " + endpoint, e);
			}
		}

	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes, Map<String, ? > changes,
		PropertyDescription changesTypes) throws IOException
	{
		// TODO should this throw an illegal call exception? Because this kind of call shouildn't be used in this class?
		// returns the first none null value.
		Object retValue = null;
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			Object reply = endpoint.executeServiceCall(serviceName, functionName, arguments, argumentTypes, changes, changesTypes);
			retValue = retValue == null ? reply : retValue;
		}
		return retValue;
	}

	@Override
	public void registerContainer(Container container)
	{
		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			endpoint.registerContainer(container);
		}
	}

	@Override
	public boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		boolean contentHasBeenWritten = false;
		String keyToAddIfChanges = keyInParent;

		for (IWebsocketEndpoint endpoint : session.getRegisteredEnpoints())
		{
			boolean endpointChangesFound = endpoint.writeAllComponentsChanges(w, keyToAddIfChanges, converter, clientDataConversions);
			if (endpointChangesFound)
			{
				keyToAddIfChanges = null;
				contentHasBeenWritten = true;
			}
		}

		return contentHasBeenWritten;
	}

	@Override
	public IWebsocketSession getWebsocketSession()
	{
		return session;
	}
}
