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
import java.util.Map;

import org.sablo.Container;
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;


/**
 * The websocket endpoint interface.
 * @author rgansevles
 */
public interface IWebsocketEndpoint
{
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
	 * Sends a message to the client/browser, containing the given data (transformed into JSON based on give dataTypes).
	 * Uses ConversionLocation.BROWSER_UPDATE as conversion location.<br/>
	 *
	 * If there are any pending service calls those will be sent to the client/attached to the message as well.
	 *
	 * @param data the data to be sent to the client (converted to JSON format where needed).
	 * @param dataTypes description of the data structure; each key in "data" might have a corresponding child "dataTypes.getProperty(key)" who's type can be used for "to JSON" conversion.
	 * @param async specifies is the messages should be sent later or right away.
	 * @return if async it will return null; otherwise it will return whatever the client sends back as a response to this message.
	 * @throws IOException when such an exception occurs.
	 */
	Object sendMessage(Map<String, ? > data, PropertyDescription dataTypes, boolean async, IToJSONConverter converter) throws IOException;

	/**
	 * Just send this text as message, no conversion, no waiting for response.
	 * @param txt
	 * @throws IOException
	 */
	void sendMessage(String txt) throws IOException;

	/**
	 * Send a response for a previous request.
	 * @see IWebsocketSession#callService(String, String, org.json.JSONObject, Object).
	 *
	 * @param msgId id of previous request
	 * @param object value to respond
	 * @param the type of the object (can be used for 'to JSON' conversions; can be null.
	 * @param success is this a normal or an error response?
	 * @throws IOException
	 */
	void sendResponse(Object msgId, Object object, PropertyDescription objectType, boolean success) throws IOException;

	/**
	 * Execute a (client/browser) service call asynchronously.
	 *
	 * @param serviceName the name of the service to call client side.
	 * @param functionName the name of the service's function to call.
	 * @param arguments the arguments to be passed to the service's function call.
	 * @param argumentTypes the types of arguments passed; can be null (the types are used for correct 'to JSON' conversion for websocket traffic).
	 */
	void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes);

	/**
	 * Execute a (client/browser) service call asynchronously and returns the resulting value.
	 *
	 * @param serviceName the name of the service to call client side.
	 * @param functionName the name of the service's function to call.
	 * @param arguments the arguments to be passed to the service's function call.
	 * @param argumentTypes the types of arguments passed; can be null (the types are used for correct 'to JSON' conversion for web-socket traffic).
	 * @param changes TODO
	 * @param changesTypes TODO the types of changes passed; can be null (the types are used for correct 'to JSON' conversion for web-socket traffic).
	 * @return remote result.
	 * @throws IOException if such an exception happens.
	 */
	Object executeServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes, Map<String, ? > changes,
		PropertyDescription changesTypes) throws IOException;

	/**
	 * Gets the window id belonging to this endpoint (a tab or window in the browser)
	 *
	 * @return a window id
	 */
	public String getWindowId();

	/**
	 * Sets the window id for this endpoint.
	 * @param windowId
	 */
	public void setWindowId(String windowId);

	/**
	 * Register a container at the websocket for traversal of changes
	 * @param container
	 */
	void regisiterContainer(Container container);

	/**
	 * Get the component changes
	 * @return the changes for all registered Containers.
	 */
	TypedData<Map<String, Map<String, Map<String, Object>>>> getAllComponentsChanges();

	/**
	 * Get the websocket session
	 * @return
	 */
	public IWebsocketSession getWebsocketSession();
}
