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

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.Container;
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;


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
	 * Sends a message to the client/browser, containing the given data (transformed into JSON based on give dataTypes).
	 * Uses ConversionLocation.BROWSER_UPDATE as conversion location.<br/>
	 *
	 * If there are any pending service calls those will be sent to the client/attached to the message as well.
	 *
	 * @param data the data to be sent to the client (converted to JSON format where needed).
	 * @param dataTypes description of the data structure; each key in "data" might have a corresponding child "dataTypes.getProperty(key)" who's type can be used for "to JSON" conversion.
	 * @param async specifies is the messages should be sent later or right away.
	 * @param converter converter for values to json.
	 * @return if async it will return null; otherwise it will return whatever the client sends back as a response to this message.
	 * @throws IOException when such an exception occurs.
	 */
	<ContextT> Object sendMessage(Map<String, ? > data, PropertyDescription dataTypes, boolean async, IToJSONConverter<ContextT> converter) throws IOException;

	/**
	 * Same as {@link #sendMessage(Map, PropertyDescription, boolean, IToJSONConverter)}, but instead of giving data as java maps and property descriptions
	 * it simply uses a 'dataWriter' to write directly the data to JSON.
	 * @throws IOException
	 */
	<ContextT> Object sendMessage(IToJSONWriter<ContextT> dataWriter, boolean async, IToJSONConverter<ContextT> converter) throws IOException;

	/**
	 * Flush outstanding async service calls.
	 *
	 * @throws IOException
	 */
	void flush() throws IOException;

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
	void sendResponse(Object msgId, Object object, PropertyDescription objectType, IToJSONConverter converter, boolean success) throws IOException;

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
	void registerContainer(Container container);

	/**
	 * Writes as JSON changes from all components of all registered Containers.
	 * @param keyInParent a key (can be null in which case it should be ignored) that must be appended to 'w' initially if this method call writes content to it. If the method returns false, nothing should be written to the writer...
	 */
	boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions) throws JSONException;

	/**
	 * Get the websocket session
	 * @return
	 */
	public IWebsocketSession getWebsocketSession();
}
