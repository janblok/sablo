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

import java.util.Collection;

import org.json.JSONObject;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.services.client.SabloService;

/**
 * Interface for classes handling a websocket user session.
 * @author rgansevles
 */
public interface IWebsocketSession
{
	/**
	 * Returns the event dispatcher, that should be a separate thread that processes all the events.
	 *
	 * @return
	 */
	IEventDispatcher getEventDispatcher();

	/**
	 * Can it still be used?
	 */
	boolean isValid();

	/**
	 * Called when a new connection is started (also on reconnect)
	 * @param argument
	 */
	public void onOpen(String... argument);

	String getUuid();

	void startHandlingEvent();

	void stopHandlingEvent();

	/**
	 * Request to close the websocket session.
	 */
	void closeSession();

	/**
	 * Handle an incoming message.
	 * @param obj
	 */
	// TODO: remove this, all when messages are done via service calls
	void handleMessage(JSONObject obj);

	/**
	 * Register server side service
	 * @param name
	 * @param service handler
	 */
	void registerServerService(String name, IServerService service);

	/**
	 * Returns a server side service for that name.
	 * @param name
	 * @return
	 */
	IServerService getServerService(String name);

	IClientService getClientService(String name);

	Collection<IClientService> getServices();

	/**RAGTEST doc
	 * @param windowId
	 * @param windowName
	 * @return
	 */
	IWindow getOrCreateWindow(String windowId, String windowName);

	Collection<IWindow> getWindows();

	/**
	 * @param window
	 */
	void invalidateWindow(IWindow window);

	void activateWindow(IWindow window);

	boolean checkForWindowActivity();

	SabloService getSabloService();
}
