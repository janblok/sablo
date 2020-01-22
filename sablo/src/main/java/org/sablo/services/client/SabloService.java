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

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.PropertyDescriptionBuilder;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.specification.property.types.BooleanPropertyType;
import org.sablo.specification.property.types.IntPropertyType;
import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;

/**
 * Class to access sablo builtin server-service methods.
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

	public void setCurrentFormUrl(String currentFormUrl)
	{
		clientService.executeAsyncServiceCall("setCurrentFormUrl", new Object[] { currentFormUrl });
	}

	public void openWindowInClient(String url, String winname, String specs, String replace) throws IOException
	{
		clientService.executeServiceCall("windowOpen", new Object[] { url, winname, specs, replace });
	}

	public void resolveDeferedEvent(int defid, boolean success, Object argument, PropertyDescription argumentPD)
	{
		PropertyDescription pd = null;
		if (argumentPD != null)
		{
			PropertyDescriptionBuilder pdBuilder = AggregatedPropertyType.newAggregatedPropertyBuilder();
			pdBuilder.withProperty("0", new PropertyDescriptionBuilder().withName("defid").withType(IntPropertyType.INSTANCE).build());
			pdBuilder.withProperty("1", argumentPD);
			pdBuilder.withProperty("2", new PropertyDescriptionBuilder().withName("success").withType(BooleanPropertyType.INSTANCE).build());
			pd = pdBuilder.build();
		}
		CurrentWindow.get().executeAsyncServiceCall(clientService, "resolveDeferedEvent",
			new Object[] { Integer.valueOf(defid), argument, Boolean.valueOf(success) }, pd);
	}

	public void addComponentClientSideConversionTypes(EmbeddableJSONWriter toBeSent)
	{
		clientService.executeAsyncServiceCall("addComponentClientSideConversionTypes", new Object[] { toBeSent });
	}

	public void setServiceClientSideConversionTypes(EmbeddableJSONWriter toBeSent)
	{
		clientService.executeAsyncServiceCall("setServiceClientSideConversionTypes", new Object[] { toBeSent });
	}

}
