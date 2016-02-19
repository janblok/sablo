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

package org.sablo.example.endpoint;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.sablo.example.HelloWorldWindow;
import org.sablo.websocket.BaseWebsocketSession;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.IWindow;

/**
 * Web socket session for a sablo application.
 * 
 * Keeps component state for the active application.
 * 
 * @author rgansevles
 *
 */
public class HelloWorldWebsocketSession extends BaseWebsocketSession {

	private String clientState = "!";

	public HelloWorldWebsocketSession(String uuid) {
		super(uuid);
	}

	public String getClientState() {
		return clientState;
	}

	public void setClientState(String clientState) {
		this.clientState = clientState;
	}

	@Override
	protected IWindow createWindow(String windowUuid, String windowName) {

		HelloWorldWindow window = new HelloWorldWindow(this, UUID.randomUUID().toString(), windowName);
		window.setCurrentFormUrl("forms/" + (windowName == null ? "mainForm" : windowName) + ".html");
		return window;
	}
	
	@Override
	public void onOpen(Map<String, List<String>> requestParams) {
		if (CurrentWindow.get().getCurrentFormUrl() == null) {
			CurrentWindow.get().setCurrentFormUrl("forms/mainForm.html");
		}
	}

	@Override
	public Locale getLocale() {
		return Locale.getDefault();
	}
}
