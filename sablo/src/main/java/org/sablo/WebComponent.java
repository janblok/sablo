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

package org.sablo;

import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.websocket.WebsocketEndpoint;

/**
 * Server side representation of an angular webcomponent in the browser. It is
 * defined by a strong specification api,event and property-model wise
 * 
 * @author jblok
 */
public class WebComponent extends BaseWebObject
{
	Container parent;

	public WebComponent(String componentType, String name) {
		super(name, WebComponentSpecProvider.getInstance()
				.getWebComponentSpecification(componentType));
		if (componentType != null) {
			if (specification == null)
				throw new IllegalStateException(
						"Cannot work without specification");
		} 
		properties.put("name", name);
	}

	WebComponent(String name) {
		this(null, name);
	}

	/**
	 * Returns the parent container
	 * 
	 * @return the parent container
	 */
	public final Container getParent() {
		return parent;
	}
	
	/**
	 * @return
	 */
	public boolean isVisible() {
		Boolean v = (Boolean) properties.get("visible");
		return (v == null ? false : v.booleanValue());
	}

	/**
	 * Register as visible
	 * 
	 * @return
	 */
	public void setVisible(boolean v) {
		properties.put("visible", v);
	}
	
	/**
	 * Invoke apiFunction by name, fails silently if not found
	 * 
	 * @param apiFunctionName
	 *            the function name
	 * @param args
	 *            the args
	 * @return the value if any
	 */
	public Object invokeApi(String apiFunctionName, Object[] args) {
		WebComponentApiDefinition apiFunction = specification
				.getApiFunction(apiFunctionName);
		if (apiFunction != null) {
			return invokeApi(apiFunction, args);
		}
		return null;
	}

	/**
	 * Invoke apiFunction
	 * 
	 * @param apiFunction
	 *            the function
	 * @param args
	 *            the args
	 * @return the value if any
	 */
	public Object invokeApi(WebComponentApiDefinition apiFunction, Object[] args) {
		return WebsocketEndpoint.get().getWebsocketSession()
				.invokeApi(this, apiFunction, args);
	}


}