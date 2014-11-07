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

package org.sablo.services;

import java.util.Map;

import org.json.JSONObject;
import org.sablo.Container;
import org.sablo.websocket.IServerService;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.TypedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * formService implementation to handle methods at form level.
 * 
 * @author rgansevles
 *
 */
public class FormServiceHandler implements IServerService
{
	public static final Logger log = LoggerFactory.getLogger(FormServiceHandler.class.getCanonicalName());

	private final IWebsocketSession websocketSession;

	/**
	 * @param baseWebsocketSession
	 */
	public FormServiceHandler(IWebsocketSession websocketSession)
	{
		this.websocketSession = websocketSession;
	}

	/**
	 * @return the websocketSession
	 */
	public IWebsocketSession getWebsocketSession()
	{
		return websocketSession;
	}

	@Override
	public Object executeMethod(String methodName, JSONObject args) throws Exception
	{
		switch (methodName)
		{
			case "requestData" :
			{
				return requestData(args.optString("formname"));
			}
		}

		log.warn("Method not implemented: '" + methodName + "'");
		return null;
	}


	protected TypedData<Map<String, Map<String, Object>>> requestData(String formName)
	{
		Container form = getWebsocketSession().getForm(formName);
		if (form == null)
		{
			log.warn("Data requested from unknown form '" + formName + "'");
			return null;
		}

		return form.getAllComponentsProperties();
	}

}
