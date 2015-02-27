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

import java.util.Collection;
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
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.ISmartPropertyValue;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.specification.property.types.ProtectedConfig;
import org.sablo.specification.property.types.VisiblePropertyType;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a base web object that hold properties and records changes for the specific {@link WebComponentSpecProvider}
 *
 * @author jcompagner
 */
public abstract class BaseWebObject
{
	static final TypedData<Map<String, Object>> EMPTY_PROPERTIES = new TypedData<Map<String, Object>>(Collections.<String, Object> emptyMap(), null);


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
	 * @throws Exception 
	 */
	public final Object executeEvent(String eventType, Object[] args) throws Exception
	{
		checkProtection(eventType);

		return doExecuteEvent(eventType, args);
	}

	protected Object doExecuteEvent(String eventType, Object[] args) throws Exception
	{
		IEventHandler handler = getEventHandler(eventType);
		if (handler == null)
		{
			log.warn("Unknown event '" + eventType + "' for component " + this);
			return null;
		}
		return handler.executeEvent(args);
	}

	public final boolean isVisible()
	{
		return isVisible(null);
	}

	/**
	 * Determine visibility on properties of type VisiblePropertyType.INSTANCE.
	 *
	 * @param property check properties that have for defined for this  when null, check for component-level visibility.
	 */
	public final boolean isVisible(String property)
	{
		for (PropertyDescription prop : specification.getProperties(VisiblePropertyType.INSTANCE))
		{
			if (Boolean.FALSE.equals(getProperty(prop.getName())))
			{
				Object config = prop.getConfig();
				if (config instanceof ProtectedConfig && ((ProtectedConfig)config).getForEntries() != null)
				{
					Collection<String> forEntries = (((ProtectedConfig)config).getForEntries()).getEntries();
					if (forEntries != null && forEntries.size() > 0 && (property == null || !forEntries.contains(property)))
					{
						// specific visibility-property, not for this property
						continue;
					}
				}

				// general visibility-property or specific for this property
				return false;
			}
		}

		return true;
	}

	public final void setVisible(boolean visible)
	{
		boolean set = false;
		for (PropertyDescription prop : specification.getProperties(VisiblePropertyType.INSTANCE))
		{
			Object config = prop.getConfig();
			if (config instanceof ProtectedConfig && ((ProtectedConfig)config).getForEntries() != null)
			{
				Collection<String> forEntries = (((ProtectedConfig)config).getForEntries()).getEntries();
				if (forEntries != null && forEntries.size() > 0)
				{
					// specific enable-property, skip
					continue;
				}
			}

			setProperty(prop.getName(), Boolean.valueOf(visible));
			set = true;
		}

		if (!set)
		{
			log.warn("Could not set component '" + getName() + "' visibility to " + visible + ", no visibility property found");
		}
	}

	public boolean isVisibilityProperty(String propertyName)
	{
		PropertyDescription description = specification.getProperty(propertyName);
		return description != null && description.getType() == VisiblePropertyType.INSTANCE;
	}

	/**
	 * Check protection of property.
	 * Validate if component or not visible or protected by another property.
	 *
	 * @throws IllegalComponentAccessException when property is protected
	 */
	protected void checkProtection(String property)
	{
		for (PropertyDescription prop : specification.getProperties().values())
		{
			if (prop.getType().isProtecting())
			{
				Object config = prop.getConfig();

				// visible default true, so block on false by default
				// protected default false, so block on true by default
				boolean blockingOn = Boolean.FALSE.equals(prop.getType().defaultValue(prop));
				if (config instanceof ProtectedConfig)
				{
					blockingOn = ((ProtectedConfig)config).getBlockingOn();
				}

				if (Boolean.valueOf(blockingOn).equals(getProperty(prop.getName())))
				{
					if (config instanceof ProtectedConfig && ((ProtectedConfig)config).getForEntries() != null)
					{
						Collection<String> forEntries = (((ProtectedConfig)config).getForEntries()).getEntries();
						if (forEntries != null && forEntries.size() > 0 && (property == null || !forEntries.contains(property)))
						{
							// specific enable-property, not for this property
							continue;
						}
					}

					// general protected property or specific for this property
					throw new IllegalComponentAccessException(prop.getType().getName(), getName(), property);
				}
			}
		}

		// ok
	}

	/**
	 * Check if the property is protected, i.e. it cannot be set from the client.
	 *
	 * @param propName
	 * @throws IllegalComponentAccessException when property is protected
	 */
	protected void checkForProtectedProperty(String propName)
	{
		PropertyDescription property = specification.getProperty(propName);
		if (property != null && property.getType().isProtecting())
		{
			throw new IllegalComponentAccessException("protecting", getName(), propName);
		}

		// ok
	}

	/**
	 * Get a property
	 *
	 * @param propertyName
	 * @return the value or null
	 */
	public Object getProperty(String propertyName)
	{
		return unwrapValue(propertyName, getRawPropertyValue(propertyName));
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
		for (String propertyName : changedProperties)
		{
			if (isVisible(propertyName) || isVisibilityProperty(propertyName))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Get the changes of this component, clear changes.
	 * When the component is not visible, only the visibility-properties are returned and cleared from the changes.
	 *
	 */
	public TypedData<Map<String, Object>> getAndClearChanges()
	{
		if (changedProperties.isEmpty())
		{
			return EMPTY_PROPERTIES;
		}

		Map<String, Object> changes = null;
		PropertyDescription changeTypes = null;
		for (String propertyName : changedProperties.toArray(new String[changedProperties.size()]))
		{
			if (isVisible(propertyName) || isVisibilityProperty(propertyName))
			{
				flagPropertyAsDirty(propertyName, false);
				if (changes == null)
				{
					changes = new HashMap<>();
				}
				changes.put(propertyName, properties.get(propertyName));
				PropertyDescription t = specification.getProperty(propertyName);
				if (t != null)
				{
					if (changeTypes == null)
					{
						changeTypes = AggregatedPropertyType.newAggregatedProperty();
					}
					changeTypes.putProperty(propertyName, t);
				}
			}
		}

		if (changes == null)
		{
			return EMPTY_PROPERTIES;
		}

		return new TypedData<Map<String, Object>>(changes, changeTypes);
	}

	/**
	 * For testing only.
	 *
	 * DO NOT USE THIS METHOD; when possible please use {@link #getProperty(String)}, {@link #getProperties()} or {@link #getAllPropertyNames(boolean)} instead.
	 */
	Map<String, Object> getRawPropertiesWithoutDefaults()
	{
		return properties;
	}

	public TypedData<Map<String, Object>> getProperties()
	{
		if (properties.isEmpty())
		{
			return EMPTY_PROPERTIES;
		}

		PropertyDescription propertyTypes = AggregatedPropertyType.newAggregatedProperty();
		for (Entry<String, Object> p : properties.entrySet())
		{
			PropertyDescription t = specification.getProperty(p.getKey());
			if (t != null) propertyTypes.putProperty(p.getKey(), t);
		}

		return new TypedData<Map<String, Object>>(Collections.unmodifiableMap(properties), propertyTypes.hasChildProperties() ? propertyTypes : null);
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
		Object newUnwrappedV = unwrapValue(propertyName, getRawPropertyValue(propertyName)); // a default value wrap/unwrap might result in a different value
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
	@SuppressWarnings("nls")
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
				Map<String, Object> propertyMap = (Map<String, Object>)getRawPropertyValue(path);
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
				Object oldValue = getRawPropertyValue(propertyName);
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
				flagPropertyAsDirty(firstPropertyPart, true);
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

			flagPropertyAsDirty(firstPropertyPart, true);
			return true;
		}
		return false;
	}

	/**
	 * Gets the current value from the properties, if not set then it fallbacks to the default properties (which it then wraps)
	 * DO NOT USE THIS METHOD; when possible please use {@link #getProperty(String)}, {@link #getProperties()} or {@link #getAllPropertyNames(boolean)} instead.
	 */
	@SuppressWarnings("nls")
	public Object getRawPropertyValue(String propertyName)
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
						defaultProperty = propertyDesc.getType().defaultValue(propertyDesc);
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
	public final void putBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		checkProtection(propertyName);

		checkForProtectedProperty(propertyName);

		doPutBrowserProperty(propertyName, propertyValue);
	}

	protected void doPutBrowserProperty(String propertyName, Object propertyValue) throws JSONException
	{
		Object oldWrappedValue = getRawPropertyValue(propertyName);
		Object newWrappedValue = convertValueFromJSON(propertyName, oldWrappedValue, propertyValue);
		properties.put(propertyName, newWrappedValue);

		// TODO I think this could be wrapped values in onPropertyChange (would need less unwrapping)
		if (oldWrappedValue != newWrappedValue)
		{
			onPropertyChange(propertyName, unwrapValue(propertyName, oldWrappedValue), unwrapValue(propertyName, newWrappedValue));
		}
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
		if ((newValue instanceof ISmartPropertyValue || oldValue instanceof ISmartPropertyValue) && newValue != oldValue)
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
			if (newValue instanceof ISmartPropertyValue)
			{
				((ISmartPropertyValue)newValue).attachToBaseObject(new IChangeListener()
				{
					@Override
					public void valueChanged()
					{
						flagPropertyAsDirty(complexPropertyRoot, true);

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
	}

	public boolean flagPropertyAsDirty(String key, boolean dirty)
	{
		return dirty ? changedProperties.add(key) : changedProperties.remove(key);
	}

	protected boolean writeComponentProperties(JSONWriter w, IToJSONConverter converter, String nodeName, DataConversion clientDataConversions)
		throws JSONException
	{
		TypedData<Map<String, Object>> typedProperties = getProperties();
		if (typedProperties.content.isEmpty())
		{
			return false;
		}

		w.key(nodeName).object();
		clientDataConversions.pushNode(nodeName);

		// only write properties that are visible, always write visibility properties
		Map<String, Object> data = new HashMap<>();
		for (Entry<String, Object> entry : typedProperties.content.entrySet())
		{
			String propertyName = entry.getKey();
			if (isVisibilityProperty(propertyName) || isVisible(propertyName))
			{
				data.put(propertyName, entry.getValue());
				flagPropertyAsDirty(propertyName, false);
			}
			else
			{
				// will be sent as changed when component becomes visible
				flagPropertyAsDirty(propertyName, true);
			}
		}

		JSONUtils.writeData(converter, w, data, typedProperties.contentType, clientDataConversions, this);
		clientDataConversions.popNode();
		w.endObject();

		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object wrapPropertyValue(String propertyName, Object oldValue, Object newValue)
	{
		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		IPropertyType<Object> type = propertyDesc != null ? (IPropertyType<Object>)propertyDesc.getType() : null;
		Object object = (type instanceof IWrapperType) ? ((IWrapperType)type).wrap(newValue, oldValue, new DataConverterContext(propertyDesc, this)) : newValue;
		if (type instanceof IClassPropertyType && object != null && !((IClassPropertyType< ? >)type).getTypeClass().isAssignableFrom(object.getClass()))
		{
			log.info("property: " + propertyName + " of component " + getName() + " set with value: " + newValue + " which is not of type: " +
				((IClassPropertyType< ? >)type).getTypeClass());
			return null;
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
		if (newJSONValue == JSONObject.NULL) newJSONValue = null;

		PropertyDescription propertyDesc = specification.getProperty(propertyName);
		Object value = propertyDesc != null ? JSONUtils.fromJSON(previousComponentValue, newJSONValue, new DataConverterContext(propertyDesc, this)) : null;
		return value;
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

	public void addEventHandler(String handlerName, IEventHandler handler)
	{
		if (specification.getHandler(handlerName) == null)
		{
			throw new IllegalArgumentException("Handler for component '" + getName() + "' not found in component specification '" + specification.getName() +
				"' : handler '" + handlerName + "'");
		}
		eventHandlers.put(handlerName, handler);
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
