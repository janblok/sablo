/*
 * Copyright (C) 2015 Servoy BV
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

package org.sablo.services.client;

import java.io.IOException;

import org.sablo.websocket.IClientService;

/**
 * RAGTEST doc
 * 
 * @author rgansevles
 *
 */
public class SabloService
{
	public static final String SABLO_SERVICE = "$sabloService";

	private final IClientService clientService;

	public SabloService(IClientService clientService)
	{
		this.clientService = clientService;
	}

	/**
	 * @param currentFormUrl
	 */
	public void setCurrentFormUrl(String currentFormUrl)
	{
		clientService.executeAsyncServiceCall("setCurrentFormUrl", new Object[] { currentFormUrl });
	}

	public boolean openWindowInClient(String url, String winname, String specs, String replace) throws IOException
	{
		return Boolean.TRUE.equals(clientService.executeServiceCall("windowOpen", new Object[] { url, winname, specs, replace }));
	}
}
