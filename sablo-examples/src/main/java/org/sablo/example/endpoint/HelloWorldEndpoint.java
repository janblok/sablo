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


import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.sablo.websocket.WebsocketEndpoint;

/**
 * Websocket endpoint for sample sablo application.
 * 
 * @author rgansevles
 *
 */

@ServerEndpoint(value = "/websocket/{sessionid}")
public class HelloWorldEndpoint extends WebsocketEndpoint
{
	public static final String HELLO_WORLD_ENDPOINT = "helloworld";

	public HelloWorldEndpoint()
	{
		super(HELLO_WORLD_ENDPOINT);
	}

	@OnOpen
	public void start(Session newSession, @PathParam("sessionid")
	String sessionid) throws Exception
	{
		start(newSession, sessionid, null, null);
	}

	@Override
	@OnMessage
	public void incoming(String msg, boolean lastPart)
	{
		super.incoming(msg, lastPart);
	}

	@Override
	@OnClose
	public void onClose()
	{
		super.onClose();
	}

	@OnError
	public void onError(Throwable t)
	{
		log.error("IOException happened", t);
	}
}
