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


/**
 * The websocket endpoint interface.
 * @author rgansevles
 */
public interface IWebsocketEndpoint
{

	/**
	 * Sync service calls to client (that wait for a response on the event dispatch thread) will continue dipatching events of event level
	 * minimum {@link #EVENT_LEVEL_SYNC_API_CALL} while waiting. This is to avoid deadlocks in case the sync api call to client needs to wait
	 * for some initialization call to be executed on server (that initialization call can use a higher event level through {@link IEventDispatchAwareServerService#getMethodEventThreadLevel(String, org.json.JSONObject)}).
	 */
	public static final int EVENT_LEVEL_SYNC_API_CALL = 500;

	/**
	 * It there an active session to the browser?
	 */
	boolean hasSession();

	/**
	 * Close the browser session.
	 */
	void closeSession();

	/**
	 * Close the browser session with a cancel reason.
	 */
	void cancelSession(String reason);

	/**
	 * @return
	 */
	int getNextMessageId();

	/**
	 * @param text
	 */
	void sendText(String txt) throws IOException;

	/**
	 * @param messageId
	 * @param text
	 * @return
	 */
	Object waitResponse(Integer messageId, String text) throws IOException;
}
