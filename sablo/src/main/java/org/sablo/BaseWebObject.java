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
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.ISmartPropertyValue;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.specification.property.types.EnablePropertyType;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a base web object that hold properties and records changes for the specific {@link WebComponentSpecProvider}
 *
 * @author jcompagner
 */
public abstract class BaseWebObject
{
	private static final Logger log = LoggerFactory.getLogger(BaseWebObject.class.getCanonicalName());


	protected final WebComponentSpecification specification;

	/**
	 * model properties to interact with webcomponent values, maps name to value
	 */
	protected final Map<String, Object> properties = new HashMap<>();

	/**
	 * default model properties that are not send to the browser.
	 */
	protected final Map<String, Object> defaultPropertiesUnwrapped = new HashMap<>();

	/**
	 * the changed properties
	 */
	private final Set<String> changedProperties = new HashSet<>(3);
	protected IDirtyPropertyListener dirtyPropertyListener;

	/**
	 * the event handlers
	 */
	private final ConcurrentMap<String, IEventHandler> eventHandlers = new ConcurrentHashMap<String, IEventHandler>();

	protected final String name;

	public BaseWebObject(String name, WebComponentSpecification specification)
	{
		this.name = name;
		this.specification = specification;
		if (specification == null) throw new IllegalStateException("Cannot work without specification");
	}

	/**
	 * Registers a listener that is interested to know when this component has changes to be sent to browser.
	 */
	public void setDirtyPropertyListener(IDirtyPropertyListener listener)
	{
		this.dirtyPropertyListener = listener;
	}

	/**
	 * Returns the component name
	 *
	 * @return the name
	 */
	public final String getName()
	{
		return name;
	}

	/**
	 * @return the specification
	 */
	public WebComponentSpecification getSpecification()
	{
		return specification;
	}

	/**
	 * Execute incoming event
	 *
	 * @param eventType
	 * @param args
	 * @return
	 */
	public Object executeEvent(String eventType, Object[] args)
	{
		if (!isEnabled(eventType))
		{
			throw new IllegalComponentAccessException("Disabled", getName(), eventType);
		}

		IEventHandler handler = getEventHandler(eventType);
		if (handler == null)
		{
			log.warn("Unknown event '" + eventType + "' for component " + this);
			return null;
		}
		return handler.executeEvent(args);
	}

	/**
	 * RAGTEST doc
	 * @param eventType
	 * @return
	 */
	protected boolean isEnabled(String eventType)
	{
		// RAGTEST met for.....
		for (PropertyDescription prop : specification.getProperties(EnablePropertyType.INSTANCE))
		{
			if (!Boolean.TRUE.equals(getProperty(prop.getName())))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Get a property
	 *
	 * @param propertyName
	 * @return the value or null
	 */
	public Object getProperty(String propertyName)
	{
		return unwrapValue(propertyName, getCurrentValue(propertyName));
	}

	protected Object unwrapValue(String propertyName, Object object)
	{
		if (object != null)
		{
			PropertyDescription propDesc = specification.getProperty(propertyName);
			if (propDesc != null)
			{
				IPropertyType< ? > type = propDesc.getType();
				if (type instanceof IWrapperType)
				{
					return ((IWrapperType)type).unwrap(object);
				}
			}
		}
		return object;
	}

	public boolean hasChanges()
	{
		return !changedProperties.isEmpty();
	}

	public TypedData<Map<String, Object>> getChanges()
	{
		if (changedProperties.size() > 0)
		{
			Map<String, Object> changes = new HashMap<>();
			PropertyDescription changeTypes = AggregatedPropertyType.newAggregatedProperty();
			for (String propertyName : changedProperties)
			{
				changes.put(propertyName, properties.get(propertyName));
				PropertyDescription t = specification.getProperty(propertyName);
				if (t != null) changeTypes.putProperty(propertyName, t);
			}
			if (!changeTypes.hasChildProperties()) changeTypes = null;
			changedProperties.clear();
			return new TypedData<Map<String, Object>>(changes, changeTypes);
		}
		Map<String, Object> em = Collections.emptyMap();
		return new TypedData<>(em, null);
	}

	/**
	 * DO NOT USE THIS METHOD; when possible please use {@link #getProperty(String)}, {@link #getProperties()} or {@link #getAllPropertyNames(boolean)} instead.
	 */
	public Map<String, Object> getRawProperties()
	{
		return properties;
	}

	public TypedData<Map<String, Object>> getProperties()
	{
		PropertyDescription propertyTypes = AggregatedPropertyType.newAggregatedProperty();
		for (Entry<String, Object> p : properties.entrySet())
		{
			PropertyDescription t = specification.getProperty(p.getKey());
			if (t != null) propertyTypes.putProperty(p.getKey(), t);
		}
		if (!propertyTypes.hasChildProperties()) propertyTypes = null;

		return new TypedData<Map<String, Object>>(Collections.unmodifiableMap(properties), propertyTypes);
	}

	/**
	 * Don't call this method unless all changes are already sent to client!
	 */
	public void clearChanges()
	{
		changedProperties.clear();
	}

	/**
	 * Set the defaults property value that is not send to the browser.
	 * This should/can reflect the values that are set in the template as default values.
	 *
	 * @param propertyName
	 * @param propertyValue
	 */
	public void setDefaultProperty(String propertyName, Object propertyValue)
	{
		Object oldUnwrappedV = getProperty(propertyName);
		defaultPropertiesUnwrapped.put(propertyName, propertyValue);
		Object newUnwrappedV = unwrapValue(propertyName, getCurrentValue(propertyName)); // a default value wrap/unwrap might result in a different value
		if (newUnwrappedV != propertyValue) defaultPropertiesUnwrapped.put(propertyName, newUnwrappedV);

		if (newUnwrappedV != oldUnwrappedV) onPropertyChange(propertyName, oldUnwrappedV, newUnwrappedV);
	}

	/**
	 * Setting new data and recording this as change.
	 *
	 * @param propertyName
	 * @param propertyValue
	 * @return true is was change
	 */
	public boolean setProperty(String propertyName, Object propertyValue)
	{
		Object canBeWrapped = propertyValue;

		Map<String, Object> map = properties;
		String firstPropertyPart = propertyName;
		String lastPropertyPart = propertyName;
		String[] parts = propertyName.split("\\.");
		if (parts.length > 1)
		{
			firstPropertyPart = parts[0];
			String path = "";
			for (int i = 0; i < parts.length - 1; i++)
			{
				path += parts[i];
				Map<String, Object> propertyMap = (Map<String, Object>)getCurrentValue(path);
				if (propertyMap == null)
				{
					propertyMap = new HashMap<>();
					map.put(parts[i], wrapPropertyValue(parts[i], null, propertyMap));
				}
				path += ".";
				map = propertyMap;
			}
			lastPropertyPart = parts[parts.length - 1];
		}
		else
		{
			try
			{
				Object oldValue = getCurrentValue(propertyName);
				canBeWrapped = wrapPropertyValue(propertyName, oldValue, propertyValue);
			}
			catch (Exception e)
			{
				// TODO change this as part of SVY-6337
				throw new RuntimeException(e);
			}
		}

		if (map.containsKey(lastPropertyPart))
		{
			// existing property
			Object oldValue = getProperty(propertyName); // this unwraps it
			map.put(lastPropertyPart, canBeWrapped);
			propertyValue = getProperty(propertyName); // this is required as a wrap + unwrap might result in a different object then the initial one

			// TODO I think this could be wrapped values in onPropertyChange (would need less unwrapping)
			// TODO if this is a sub property then we fire here the onproperty change for the top level property with the values of a subproperty..
			onPropertyChange(firstPropertyPart, oldValue, propertyValue);

			if ((oldValue != null && !oldValue.equals(propertyValue)) || (propertyValue != null && !propertyValue.equals(oldValue)))
			{
				flagPropertyAsDirty(firstPropertyPart);
				return true;
			}
		}
		else
		{
			// new property
			map.put(lastPropertyPart, canBeWrapped);
			propertyValue = getProperty(propertyName); // this is required as a wrap + unwrap might result in a different object then the initial one

			// TODO I think this could be wrapped values in onPropertyChange (would need less unwrapping)
			onPropertyChange(firstPropertyPart, null, propertyValue);

			flagPropertyAsDirty(firstPropertyPart);
			return true;
		}
		return false;
	}

	/**
	 * Gets the current value from the properties, if not set then it fallbacks to the default properties (which it then wraps)
	 *
	 * @param propertyName
	 * @return
	 * @throws JSONException
	 */
	private Object getCurrentValue(String propertyName)
	{
		String[] parts = propertyName.split("\\.");
		String firstProperty = parts[0];
		Object oldValue = properties.get(firstProperty);
		if (oldValue == null && !properties.containsKey(firstProperty))
		{
			Object defaultProperty = defaultPropertiesUnwrapped.get(firstProperty);
			if (defaultProperty == null && !defaultPropertiesUnwrapped.containsKey(firstProperty))
			{
				// default value based o 
				PropertyDescription propertyDesc = specification.getProperty(firstProperty);
				if (propertyDesc != null)
				{
					defaultProperty = propertyDesc.getDefaultValue();
					if (defaultProperty == null && propertyDesc.getType() != null)
					{
						defaultProperty = propertyDesc.getType().defaultValue();
					}
				}
			}

			if (defaultProperty != null)
			{
				// quickly wrap this value so that it can be used as the oldValue later on.
				oldValue = wrapPropertyValue(firstProperty, null, defaultProperty);
			}
		}
		if (parts.length > 1)
		{
			for (int i = 1; i < parts.length; i++)
			{
				if (oldValue instanceof Map)
				{
					oldValue = ((Map)oldValue).get(parts[i]);
				}
			}
			// this value comes from internal maps, should be wrapped again (current value should always return a wrapped value)
			oldValue = wrapPropertyValue(propertyName, null, oldValue);
		}
		return oldValue;
	}

	/**
	 * put property from the outside world, not recording changes. converting to
	 * the right type.
	 *
	 * @param propertyName
	 * @param propertyValue
	 *            can be a JSONObject or array or primitive.
	 */
	public void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		Object oldWrappedValue = getCurrentValue(propertyName);
		Object newWrappedValue = convertValueFromJSON(propertyName, oldWrappedValue, propertyValue);
		properties.put(propertyName, newWrappedValue);

		// TODO I think this could be wrapped values in onPropertyChange (would need less unwrapping)
		if (oldWrappedValue != newWrappedValue) onPropertyChange(propertyName, unwrapValue(propertyName, oldWrappedValue),
			unwrapValue(propertyName, newWrappedValue));
	}

	/**
	 * Allow for subclasses to act on property changes
	 *
	 * @param propertyName
	 *            the property name
	 * @param oldValue
	 *            the old val
	 * @param newValue
	 *            the new val
	 */
	protected void onPropertyChange(String propertyName, final Object oldValue, final Object newValue)
	{
		if (newValue instanceof ISmartPropertyValue && newValue != oldValue)
		{
			final String complexPropertyRoot = propertyName;

			// NOTE here newValue and oldValue are the unwrapped values in case of wrapper types; TODO maybe we should use wrapped values here

			if (oldValue instanceof ISmartPropertyValue)
			{
				((ISmartPropertyValue)oldValue).detach();
			}

			// in case the 'smart' value completely changed by ref., no use keeping it in default values as it is too smart and it might want to notify changes later, although it wouldn't make sense cause the value is different now
			Object defaultSmartValue = defaultPropertiesUnwrapped.get(complexPropertyRoot);
			if (defaultSmartValue instanceof ISmartPropertyValue && defaultSmartValue != newValue)
			{
				defaultPropertiesUnwrapped.remove(complexPropertyRoot);
				((ISmartPropertyValue)defaultSmartValue).detach();
			}

			// a new complex property is linked to this component; initialize it
			((ISmartPropertyValue)newValue).attachToBaseObject(new IChangeListener()
			{
				@Override
				public void valueChanged()
				{
					flagPropertyAsDirty(complexPropertyRoot);

					if (defaultPropertiesUnwrapped.containsKey(complexPropertyRoot))
					{
						// something changed in this 'smart' property - so it no longer represents the default value; remove
						// it from default values (as the value reference is the same but the content changed) and put it in properties map
						properties.put(complexPropertyRoot, newValue);
						defaultPropertiesUnwrapped.remove(complexPropertyRoot);
					}
				}
			}, this);
		}
	}

	public void flagPropertyAsDirty(String key)
	{
		changedProperties.add(key);
		if (dirtyPropertyListener != null) dirtyPropertyListener.propertyFlaggedAsDirty(key);
		// else this is probably a direct form child and when the request is done the form will ask anyway all components for changes
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object wrapPropertyValue(String propertyName, Object oldValue, Object newValue)
	{
		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		IPropertyType<Object> type = propertyDesc != null ? (IPropertyType<Object>)propertyDesc.getType() : null;
		Object object = (type instanceof IWrapperType) ? ((IWrapperType)type).wrap(newValue, oldValue, new DataConverterContext(propertyDesc, this)) : newValue;
		if (type instanceof IClassPropertyType && object != null)
		{
			if (!((IClassPropertyType< ? >)type).getTypeClass().isAssignableFrom(object.getClass()))
			{
				log.info("property: " + propertyName + " of component " + getName() + " set with value: " + newValue + " which is not of type: " +
					((IClassPropertyType< ? >)type).getTypeClass());
				return null;
			}
		}
		return object;
	}

	/**
	 * Allow for subclasses to do conversions, by default it just ask for the
	 * type to do the conversion to Java
	 *
	 * @param propertyName
	 *            the property name
	 * @param previousComponentValue
	 *            the old val
	 * @param newJSONValue
	 *            the new val
	 * @param sourceOfValue
	 * @return the converted value
	 * @throws JSONException
	 */
	private Object convertValueFromJSON(String propertyName, Object previousComponentValue, Object newJSONValue) throws JSONException
	{
		if (newJSONValue == null || newJSONValue == JSONObject.NULL) return null;

		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		Object value = propertyDesc != null ? JSONUtils.fromJSON(previousComponentValue, newJSONValue, propertyDesc, new DataConverterContext(propertyDesc,
			this)) : null;
		return value != null && value != newJSONValue ? value : convertPropertyValue(propertyName, previousComponentValue, newJSONValue);
	}

	/**
	 * Allow for subclasses to do conversions, by default it just ask for the
	 * type to do the conversion to Java
	 *
	 * @param propertyName
	 *            the property name
	 * @param oldValue
	 *            the old val
	 * @param newValue
	 *            the new val
	 * @param sourceOfValue
	 * @return the converted value
	 * @throws JSONException
	 */
	protected Object convertPropertyValue(String propertyName, Object oldValue, Object newValue) throws JSONException
	{
		return newValue;
	}

	/**
	 * Use the returned set only for reading, not modifying.
	 */
	public Set<String> getAllPropertyNames(boolean includeDefaultValueKeys)
	{
		Set<String> allValKeys;
		if (includeDefaultValueKeys)
		{
			allValKeys = new HashSet<String>();
			allValKeys.addAll(properties.keySet());
			allValKeys.addAll(defaultPropertiesUnwrapped.keySet());
		}
		else allValKeys = properties.keySet();

		return allValKeys;
	}

	public void addEventHandler(String event, IEventHandler handler)
	{
		eventHandlers.put(event, handler);
	}

	public IEventHandler getEventHandler(String event)
	{
		return eventHandlers.get(event);
	}

	public IEventHandler removeEventHandler(String event)
	{
		return eventHandlers.remove(event);
	}

	public boolean hasEvent(String event)
	{
		return eventHandlers.containsKey(event);
	}

	/**
	 * Called when this object will not longer be used - to release any held resources/remove listeners.
	 */
	public void dispose()
	{
		for (String pN : getAllPropertyNames(true))
		{
			Object pUnwrapped = getProperty(pN);
			if (pUnwrapped instanceof ISmartPropertyValue) ((ISmartPropertyValue)pUnwrapped).detach(); // clear any listeners/held resources
		}
	}

}
