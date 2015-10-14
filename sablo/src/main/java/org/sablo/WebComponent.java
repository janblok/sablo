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

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.CurrentWindow;

/**
 * Server side representation of an angular webcomponent in the browser. It is
 * defined by a strong specification api,event and property-model wise
 *
 * @author jblok
 */
public class WebComponent extends BaseWebObject
{
	Container parent;

	public WebComponent(String componentType, String name)
	{
		super(name, WebComponentSpecProvider.getInstance().getWebComponentSpecification(componentType));
		properties.put("name", name);
	}

	public WebComponent(String name, WebComponentSpecification spec)
	{
		super(name, spec);
	}

	/**
	 * Returns the parent container
	 *
	 * @return the parent container
	 */
	public final Container getParent()
	{
		return parent;
	}

	@Override
	public boolean flagPropertyAsDirty(String key, boolean dirty)
	{
		boolean modified = super.flagPropertyAsDirty(key, dirty);
		if (dirty && modified && parent != null) parent.markAsChanged();
		return modified;
	}

	/**
	 * Finds the first container parent of this component of the given class.
	 *
	 * @param <Z> type of parent
	 * @param c class to search for
	 * @return First container parent that is an instance of the given class, or null if none can be
	 *         found
	 */
	public final <Z> Z findParent(final Class<Z> c)
	{
		Container current = getParent();
		while (current != null)
		{
			if (c.isInstance(current))
			{
				return c.cast(current);
			}
			current = current.getParent();
		}
		return null;
	}

	@Override
	protected void checkProtection(String eventType)
	{
		super.checkProtection(eventType);

		// Check if container is not protected or invisible
		if (parent != null)
		{
			parent.checkProtection(null);
		}
	}

	@Override
	public void dispose()
	{
		super.dispose();
		if (parent != null)
		{
			parent.remove(this);
		}
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
	public Object invokeApi(String apiFunctionName, Object[] args)
	{
		WebComponentApiDefinition apiFunction = specification.getApiFunction(apiFunctionName);
		if (apiFunction != null)
		{
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
	public Object invokeApi(WebComponentApiDefinition apiFunction, Object[] args)
	{
		return CurrentWindow.get().invokeApi(this, apiFunction, args, getParameterTypes(apiFunction));
	}

	/**
	 * Execute a service function defined in the client.
	 * Synchronous call, blocks until the result is returned.
	 *
	 * @param service
	 *            the service name
	 * @param functionName
	 *            the functionName name
	 * @param arguments
	 *            the arguments
	 * @return the value if any
	 * @throws IOException
	 */
	public Object executeServiceCall(String service, String functionName, Object[] arguments) throws IOException
	{
		return CurrentWindow.get().getSession().getClientService(service).executeServiceCall(functionName, arguments);
	}

	/**
	 * Execute a service function defined in the client.
	 * Asynchronous non-blocking call.
	 *
	 * @param service
	 *            the service name
	 * @param functionName
	 *            the functionName name
	 * @param arguments
	 *            the arguments
	 * @throws IOException
	 */
	public void executeAsyncServiceCall(String service, String functionName, Object[] arguments)
	{
		CurrentWindow.get().getSession().getClientService(service).executeAsyncServiceCall(functionName, arguments);
	}

	public static PropertyDescription getParameterTypes(WebComponentApiDefinition apiFunc)
	{
		PropertyDescription parameterTypes = null;
		final List<PropertyDescription> types = apiFunc.getParameters();
		if (types.size() > 0)
		{
			parameterTypes = new PropertyDescription("", AggregatedPropertyType.INSTANCE)
			{
				@Override
				public Map<String, PropertyDescription> getProperties()
				{
					Map<String, PropertyDescription> map = new HashMap<String, PropertyDescription>();
					for (int i = 0; i < types.size(); i++)
					{
						map.put(String.valueOf(i), types.get(i));
					}
					return map;
				}

				@Override
				public PropertyDescription getProperty(String name)
				{
					try
					{
						int index = Integer.parseInt(name);
						if (index < types.size())
						{
							return types.get(index);
						}
						return null;
					}
					catch (NumberFormatException e)
					{
						return super.getProperty(name);
					}
				}

				@Override
				public Collection<String> getAllPropertiesNames()
				{
					Set<String> s = new HashSet<String>();
					for (int i = 0; i < types.size(); i++)
					{
						s.add(String.valueOf(i));
					}
					s.addAll(super.getAllPropertiesNames());
					return s;
				}
			};
		}
		return parameterTypes;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sablo.BaseWebObject#isEnabled()
	 */
	@Override
	public final boolean isEnabled()
	{
		return super.isEnabled() && (parent == null || parent.isEnabled());
	}
}
