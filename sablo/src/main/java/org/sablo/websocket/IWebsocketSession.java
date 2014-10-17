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

import java.util.List;

import org.json.JSONObject;
import org.sablo.WebComponent;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;

/**
 * Interface for classes handling a websocket user session.
 * @author rgansevles
 */
public interface IWebsocketSession
{
	/**
	 * Called when an endpoint is created for this session.
	 * @param endpoint
	 */
	public void registerEndpoint(IWebsocketEndpoint endpoint);

	/**
	 * Called when an endpoint is closed for this session.
	 * @param endpoint
	 */
	public void deregisterEndpoint(IWebsocketEndpoint endpoint);

	/**
	 * get all the current registered endpoints for this session.
	 *
	 * @return
	 */
	public List<IWebsocketEndpoint> getRegisteredEnpoints();

	/**
	 * Returns the event dispatcher, that should be a separate thread that processes all the events.
	 *
	 * @return
	 */
	public IEventDispatcher getEventDispatcher();

	/**
	 * Can it still be used?
	 */
	public boolean isValid();

	/**
	 * Called when a new connection is started (also on reconnect)
	 * @param argument
	 */
	public void onOpen(String argument);

	public String getUuid();

	void startHandlingEvent();

	void stopHandlingEvent();

	/**
	 * Request to close the websocket session.
	 */
	public void closeSession();

	/**
	 * Handle an incoming message.
	 * @param obj
	 */
	public void handleMessage(JSONObject obj);

	/**
	 * Register server side service
	 * @param name
	 * @param service handler
	 */
	public void registerServerService(String name, IServerService service);

	/**
	 * Returns a server side service for that name.
	 * @param name
	 * @return
	 */
	public IServerService getServerService(String name);

	public IClientService getService(String name);

	/**
	 * Invoke an function on the webcomponent
	 * @param receiver the webcomponent to invoke on
	 * @param apiFunction the function to invoke
	 * @param arguments
	 */
	public Object invokeApi(WebComponent receiver, WebComponentApiDefinition apiFunction, Object[] arguments, PropertyDescription argumentTypes);
}
