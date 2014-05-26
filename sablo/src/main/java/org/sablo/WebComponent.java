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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.websocket.ConversionLocation;
import org.sablo.websocket.WebsocketEndpoint;

/**
 * Server side representation of an angular webcomponent in the browser.
 * It is defined by a strong specification api,event and property-model wise
 * @author jblok
 */
public abstract class WebComponent
{
	private final WebComponentSpecification specification;
	
	/**
	 * model properties to interact with webcomponent values, maps name to value
	 */
	protected final Map<String, Object> properties = new HashMap<>();
	
	/**
	 * the changed properties
	 */
	private final Set<String> changedProperties = new HashSet<>(3);

	private final String name;
	Container parent;

	public WebComponent(String componentType,String name)
	{
		if (componentType != null)
		{
			specification = WebComponentSpecProvider.getInstance().getWebComponentSpecification(componentType);
			if (specification == null) throw new IllegalStateException("Cannot work without specification");
		}
		else
		{
			specification = null;
		}
		this.name = name;
		properties.put("name", name);
	}
	
	protected WebComponent(String name)
	{
		this(null,name);
	}

	/**
	 * Returns the component name
	 * @return the name
	 */
	public final String getName()
	{
		return name;
	}

	/**
	 * Returns the parent container
	 * @return the parent container
	 */
	public final Container getParent()
	{
		return parent;
	}

	/**
	 * Execute incoming event
	 * @param eventType
	 * @param args
	 * @return
	 */
	public abstract Object executeEvent(String eventType, Object[] args);

	/**
	 * Invoke apiFunction by name, fails silently if not found
	 * @param apiFunctionName the function name
	 * @param args the args 
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
	 * @param apiFunction the function
	 * @param args the args 
	 * @return the value if any
	 */
	public Object invokeApi(WebComponentApiDefinition apiFunction, Object[] args)
	{
		return WebsocketEndpoint.get().getWebsocketSession().invokeApi(this, apiFunction, args);
	}
	
	/**
	 * Get a property
	 * @param propertyName
	 * @return the value or null
	 */
	public Object getProperty(String propertyName)
	{
		return properties.get(propertyName);
	}
	
	
	public Map<String, Object> getChanges()
	{
		if (changedProperties.size() > 0)
		{
			Map<String, Object> changes = new HashMap<>();
			for (String propertyName : changedProperties)
			{
				changes.put(propertyName, properties.get(propertyName));
			}
			changedProperties.clear();
			return changes;
		}
		return Collections.emptyMap();
	}

	public Map<String, Object> getProperties()
	{
		return properties;
	}
	
	public void clearChanges() 
	{
		changedProperties.clear();
	}
	
	/**
	 * Setting new data and recording this as change.
	 * @param propertyName
	 * @param propertyValue
	 * @return true is was change
	 */
	public boolean setProperty(String propertyName, Object propertyValue, ConversionLocation sourceOfValue)
	{
		Map<String, Object> map = properties;
		try
		{
			propertyValue = convertPropertyValue(propertyName, map.get(propertyName), propertyValue, sourceOfValue); // the propertyName can contain dots but that is supported by convertValue 
		}
		catch (Exception e)
		{
			//TODO change this as part of SVY-6337
			throw new RuntimeException(e);
		}

		String firstPropertyPart = propertyName;
		String lastPropertyPart = propertyName;
		String[] parts = propertyName.split("\\.");
		if (parts.length > 1)
		{
			firstPropertyPart = parts[0];
			for (int i = 0; i < parts.length - 1; i++)
			{
				Map<String, Object> propertyMap = (Map<String, Object>)map.get(parts[i]);
				if (propertyMap == null)
				{
					propertyMap = new HashMap<>();
					map.put(parts[i], propertyMap);
				}
				map = propertyMap;
			}
			lastPropertyPart = parts[parts.length - 1];
		}

		if (map.containsKey(lastPropertyPart))
		{
			//existing property
			Object oldValue = map.put(lastPropertyPart, propertyValue);
			onPropertyChange(firstPropertyPart, oldValue, propertyValue);

			if ((oldValue != null && !oldValue.equals(propertyValue)) || (propertyValue != null && !propertyValue.equals(oldValue)))
			{
				changedProperties.add(firstPropertyPart);
				return true;
			}
		}
		else
		{
			//new property
			map.put(lastPropertyPart, propertyValue);
			onPropertyChange(firstPropertyPart, null ,propertyValue);
			
			changedProperties.add(firstPropertyPart);
			return true;
		}
		return false;
	}
	
	/**
	 * put property from the outside world, not recording changes.
	 * converting to the right type.
	 * @param propertyName
	 * @param propertyValue can be a JSONObject or array or primitive.
	 */
	public void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		// currently we keep Java objects in here; we could switch to having only json objects in here is it make things quicker
		// (then whenever a server-side value is put in the map, convert it via JSONUtils.toJSONValue())
		//TODO remove this when hierarchical tree structure comes into play (only needed for )
		if (propertyValue instanceof JSONObject)
		{
			Iterator<String> it = ((JSONObject)propertyValue).keys();
			while (it.hasNext())
			{
				String key = it.next();
				properties.put(propertyName + '.' + key, ((JSONObject)propertyValue).get(key));
			}
		}// end TODO REMOVE
		properties.put(propertyName, convertPropertyValue(propertyName, properties.get(propertyName), propertyValue, ConversionLocation.BROWSER_UPDATE));
	}

	/**
	 * Allow for subclasses to act on property changes
	 * @param propertyName the property name
	 * @param oldValue the old val
	 * @param newValue the new val
	 */
	protected void onPropertyChange(String propertyName, Object oldValue, Object newValue) 
	{
	}

	/**
	 * @param key
	 */
	protected void flagPropertyChanged(String key)
	{
		changedProperties.add(key);
	}
	
	/**
	 * @return
	 */
	public boolean isVisible() 
	{
		Boolean v = (Boolean) properties.get("visible");
		return (v == null ? false : v.booleanValue());
	}

	/**
	 * Register as visible
	 * @return
	 */
	public void setVisible(boolean v) 
	{
		properties.put("visible",v);
	}
	
	/**
	 * Allow for subclasses to do conversions
	 * @param propertyName the property name
	 * @param oldValue the old val
	 * @param newValue the new val
	 * @param sourceOfValue
	 * @return the converted value
	 * @throws JSONException
	 */
	protected Object convertPropertyValue(String propertyName, Object oldValue, Object newValue, ConversionLocation sourceOfValue) throws JSONException
	{
		return newValue;
	}
}
