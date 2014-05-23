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
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.property.IComplexPropertyValue;
import org.sablo.websocket.ConversionLocation;

/**
 * Server side representation of an angular webcomponent in the browser.
 * It is defined by a strong specification api,event and property-model wise
 * @author jblok
 */
public abstract class WebComponent
{
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

	public WebComponent(String name)
	{
		this.name = name;
		properties.put("name", name);
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

	public abstract Object invokeApi(WebComponentApiDefinition apiDefinition, Object[] args);
	
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
	 * Allow for subclasses to act on property changes
	 * @param propertyName
	 * @param oldValue
	 * @param newValue
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
	 * Allow for conversion
	 * @param propertyName
	 * @param oldValue
	 * @param newValue
	 * @param sourceOfValue
	 * @return the converted value
	 * @throws JSONException
	 */
	protected Object convertPropertyValue(String propertyName, Object oldValue, Object newValue, ConversionLocation sourceOfValue) throws JSONException
	{
		return newValue;
	}
}
