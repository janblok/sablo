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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.websocket.ConversionLocation;
import org.sablo.websocket.WebsocketEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side representation of an angular webcomponent in the browser. It is
 * defined by a strong specification api,event and property-model wise
 * 
 * @author jblok
 */
public class WebComponent {
	private static final Logger log = LoggerFactory
			.getLogger(WebComponent.class.getCanonicalName());

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

	public WebComponent(String componentType, String name) {
		if (componentType != null) {
			specification = WebComponentSpecProvider.getInstance()
					.getWebComponentSpecification(componentType);
			if (specification == null)
				throw new IllegalStateException(
						"Cannot work without specification");
		} else {
			specification = null;
		}
		this.name = name;
		properties.put("name", name);
	}

	protected WebComponent(String name) {
		this(null, name);
	}

	/**
	 * Returns the component name
	 * 
	 * @return the name
	 */
	public final String getName() {
		return name;
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
	 * Execute incoming event
	 * 
	 * @param eventType
	 * @param args
	 * @return
	 */
	public Object executeEvent(String eventType, Object[] args) {
		return null;
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

	/**
	 * Get a property
	 * 
	 * @param propertyName
	 * @return the value or null
	 */
	public Object getProperty(String propertyName) {
		Object object = properties.get(propertyName);
		if (object != null) {
			PropertyDescription propDesc = specification
					.getProperty(propertyName);
			if (propDesc != null) {
				IPropertyType<?> type = propDesc.getType();
				if (type instanceof IWrapperType) {
					return ((IWrapperType) type).unwrap(object);
				}
			}
		}
		return object;
	}

	public Map<String, Object> getChanges() {
		if (changedProperties.size() > 0) {
			Map<String, Object> changes = new HashMap<>();
			for (String propertyName : changedProperties) {
				changes.put(propertyName, properties.get(propertyName));
			}
			changedProperties.clear();
			return changes;
		}
		return Collections.emptyMap();
	}

	public Map<String, Object> getProperties() {
		return properties;
	}

	public void clearChanges() {
		changedProperties.clear();
	}

	/**
	 * Setting new data and recording this as change.
	 * 
	 * @param propertyName
	 * @param propertyValue
	 * @return true is was change
	 */
	public boolean setProperty(String propertyName, Object propertyValue,
			ConversionLocation sourceOfValue) {
		Map<String, Object> map = properties;
		try {
			// TODO can the propertyName can contain dots? Or should this be
			// handled by the type??
			propertyValue = wrapPropertyValue(propertyName,
					map.get(propertyName), propertyValue);
		} catch (Exception e) {
			// TODO change this as part of SVY-6337
			throw new RuntimeException(e);
		}

		// TODO can the propertyName can contain dots? Or should this be handled
		// by the type?? Remove this code below.
		String firstPropertyPart = propertyName;
		String lastPropertyPart = propertyName;
		String[] parts = propertyName.split("\\.");
		if (parts.length > 1) {
			firstPropertyPart = parts[0];
			for (int i = 0; i < parts.length - 1; i++) {
				Map<String, Object> propertyMap = (Map<String, Object>) map
						.get(parts[i]);
				if (propertyMap == null) {
					propertyMap = new HashMap<>();
					map.put(parts[i], propertyMap);
				}
				map = propertyMap;
			}
			lastPropertyPart = parts[parts.length - 1];
		}

		if (map.containsKey(lastPropertyPart)) {
			// existing property
			Object oldValue = map.put(lastPropertyPart, propertyValue);
			onPropertyChange(firstPropertyPart, oldValue, propertyValue);

			if ((oldValue != null && !oldValue.equals(propertyValue))
					|| (propertyValue != null && !propertyValue
							.equals(oldValue))) {
				changedProperties.add(firstPropertyPart);
				return true;
			}
		} else {
			// new property
			map.put(lastPropertyPart, propertyValue);
			onPropertyChange(firstPropertyPart, null, propertyValue);

			changedProperties.add(firstPropertyPart);
			return true;
		}
		return false;
	}

	/**
	 * put property from the outside world, not recording changes. converting to
	 * the right type.
	 * 
	 * @param propertyName
	 * @param propertyValue
	 *            can be a JSONObject or array or primitive.
	 */
	public void putBrowserProperty(String propertyName, Object propertyValue)
			throws JSONException {
		// currently we keep Java objects in here; we could switch to having
		// only json objects in here is it make things quicker
		// (then whenever a server-side value is put in the map, convert it via
		// JSONUtils.toJSONValue())
		// TODO remove this when hierarchical tree structure comes into play
		// (only needed for )
		// if (propertyValue instanceof JSONObject)
		// {
		// Iterator<String> it = ((JSONObject)propertyValue).keys();
		// while (it.hasNext())
		// {
		// String key = it.next();
		// properties.put(propertyName + '.' + key,
		// ((JSONObject)propertyValue).get(key));
		// }
		// }// end TODO REMOVE
		Object oldValue = properties.get(propertyName);
		properties.put(propertyName,convertValue(propertyName, oldValue, propertyValue,
								ConversionLocation.BROWSER_UPDATE));
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
	protected void onPropertyChange(String propertyName, Object oldValue,
			Object newValue) {
	}

	/**
	 * @param key
	 */
	protected void flagPropertyChanged(String key) {
		changedProperties.add(key);
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object wrapPropertyValue(String propertyName, Object oldValue,
			Object newValue) throws JSONException {
		PropertyDescription propertyDesc = specification
				.getProperty(propertyName);
		IPropertyType<Object> type = propertyDesc != null ? (IPropertyType<Object>) propertyDesc
				.getType() : null;
		Object object = (type instanceof IWrapperType) ? ((IWrapperType) type)
				.wrap(newValue, oldValue) : newValue;
		if (type instanceof IClassPropertyType && object != null) {
			if (((IClassPropertyType<?>) type).getTypeClass() != object
					.getClass()) {
				log.info("property: " + propertyName + " of component "
						+ getName() + " set with value: " + newValue
						+ " which is not of type: "
						+ ((IClassPropertyType<?>) type).getTypeClass());
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
	 * @param oldValue
	 *            the old val
	 * @param newValue
	 *            the new val
	 * @param sourceOfValue
	 * @return the converted value
	 * @throws JSONException
	 */
	private Object convertValue(String propertyName, Object oldValue,
			Object newValue, ConversionLocation sourceOfValue)
			throws JSONException {
		if (newValue == null || newValue == JSONObject.NULL)
			return null;

		PropertyDescription propertyDesc = specification
				.getProperty(propertyName);
		Object value = propertyDesc != null ? convertValue(oldValue, newValue, propertyDesc)
				: null;
		return value != null && value != newValue ? value : convertPropertyValue(propertyName,
				oldValue, newValue, sourceOfValue);
	}

	/**
	 * @param oldValue
	 * @param newValue
	 * @param type
	 * @throws JSONException
	 */
	private Object convertValue(Object oldValue, Object newValue,
			PropertyDescription propDesc) throws JSONException {

		IPropertyType<?> type = propDesc.getType();
		if (propDesc.isArray()) {
			if (newValue instanceof JSONArray) {
				JSONArray array = (JSONArray) newValue;
				Object[] objectArray = new Object[array.length()];
				for(int i=0;i<array.length();i++) {
					Object obj = array.opt(i);
					objectArray[i] = obj == null?null:convertValue(null,obj, type);
				}
				return objectArray;
			}
			else {
				throw new RuntimeException("property "+ propDesc +" is types as array, but the value is not an JSONArray: " + newValue );
			}

		} else {
			return convertValue(oldValue, newValue, type);
		}
	}

	/**
	 * @param oldValue
	 * @param newValue
	 * @param type
	 * @throws JSONException
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object convertValue(Object oldValue, Object newValue,
			IPropertyType<?> type) throws JSONException {
		if (newValue == null || newValue == JSONObject.NULL) return null;
		if (type instanceof IClassPropertyType) {
			return ((IClassPropertyType) type).toJava(newValue, oldValue);
		} else if (type instanceof ICustomType) {
			// custom type, convert json to map with values.
			if (newValue instanceof JSONObject) {
				Map<String, Object> retValue = new HashMap<>();
				Map<String, Object> oldValues = (Map<String, Object>) (oldValue instanceof Map ? oldValue
						: Collections.emptyMap());
				PropertyDescription customTypeDesc = ((ICustomType) type)
						.getCustomJSONTypeDefinition();
				Iterator<String> keys = ((JSONObject) newValue).keys();
				while (keys.hasNext()) {
					String key = keys.next();
					Object propValue = ((JSONObject) newValue).get(key);
					Object oldPropValue = oldValues.get(key);
					PropertyDescription property = customTypeDesc
							.getProperty(key);
					if (property == null)
						continue; // ignore properties that are not spec'ed
									// for
									// this type..
					Object value = convertValue(oldPropValue, propValue,
							property);
					retValue.put(key, value);
				}
				return retValue;
			}
		}
		return newValue;
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
	protected Object convertPropertyValue(String propertyName, Object oldValue,
			Object newValue, ConversionLocation sourceOfValue)
			throws JSONException {
		return newValue;
	}
}
