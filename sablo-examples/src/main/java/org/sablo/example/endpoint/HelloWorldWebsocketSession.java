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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.sablo.Container;
import org.sablo.example.HelloWorldWindow;
import org.sablo.example.forms.AnotherForm;
import org.sablo.example.forms.MainForm;
import org.sablo.websocket.BaseWebsocketSession;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IWindow;

/**
 * Web socket session for a sablo application.
 * 
 * Keeps component state for the active application.
 * 
 * @author rgansevles
 *
 */
public class HelloWorldWebsocketSession extends BaseWebsocketSession
{

	public HelloWorldWebsocketSession(String uuid)
	{
		super(uuid);
	}
	
	@Override
	public IWindow createWindow(String windowName) {
		
		IWindow window = new HelloWorldWindow(windowName);
		window.setCurrentFormUrl("forms/" + (windowName==null?"mainForm":windowName) + ".html");
		return window;
	}
	
	@Override
	public void onOpen(String argument) {
		if (CurrentWindow.get().getCurrentFormUrl()==null) {CurrentWindow.get().setCurrentFormUrl("forms/mainForm.html");}
	}

}
