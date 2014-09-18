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

package org.sablo.websocket.utils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.DataConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IConvertedPropertyType;
import org.sablo.specification.property.IDataConverterContext;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.TypedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility methods for JSON usage.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class JSONUtils
{

	private static final Logger log = LoggerFactory.getLogger(JSONUtils.class.getCanonicalName());

	@SuppressWarnings("unchecked")
	public static void writeConversions(JSONWriter object, Map<String, Object> map) throws JSONException
	{
		for (Entry<String, Object> entry : map.entrySet())
		{
			if (entry.getValue() instanceof Map)
			{
				writeConversions(object.key(entry.getKey()).object(), (Map<String, Object>)entry.getValue());
				object.endObject();
			}
			else
			{
				object.key(entry.getKey()).value(entry.getValue());
			}
		}
	}

	public static JSONWriter writeDataWithConversions(JSONWriter writer, Map<String, ? > data, PropertyDescription dataTypes) throws JSONException
	{
		return writeDataWithConversions(ToJSONConverter.INSTANCE, writer, data, dataTypes);
	}

	public static JSONWriter writeDataWithConversions(IToJSONConverter converter, JSONWriter writer, Map<String, ? > data, PropertyDescription dataTypes)
		throws JSONException
	{
		DataConversion dataConversion = new DataConversion();
		for (Entry<String, ? > entry : data.entrySet())
		{
			dataConversion.pushNode(entry.getKey());
			converter.toJSONValue(writer, entry.getKey(), entry.getValue(), dataTypes != null ? dataTypes.getProperty(entry.getKey()) : null, dataConversion);
			dataConversion.popNode();
		}

		if (dataConversion.getConversions().size() > 0)
		{
			writer.key("conversions").object();
			writeConversions(writer, dataConversion.getConversions());
			writer.endObject();
		}

		return writer;
	}

	public static String writeDataWithConversions(Map<String, ? > data, PropertyDescription dataTypes) throws JSONException
	{
		return writeDataWithConversions(ToJSONConverter.INSTANCE, data, dataTypes);
	}

	public static String writeDataWithConversions(IToJSONConverter converter, Map<String, ? > data, PropertyDescription dataTypes) throws JSONException
	{
		JSONWriter writer = new JSONStringer().object();
		writeDataWithConversions(converter, writer, data, dataTypes);
		return writer.endObject().toString();
	}

//
//	/**
//	 * Writes the given object as design-time JSON into the JSONWriter.
//	 * @param writer the JSONWriter.
//	 * @param value the value to be written to the writer.
//	 * @return the writer object to continue writing JSON.
//	 * @throws JSONException
//	 * @throws IllegalArgumentException if the given object could not be written to JSON for some reason.
//	 */
//	public static JSONWriter toDesignJSONValue(JSONWriter writer, Object value, PropertyDescription valueType) throws JSONException, IllegalArgumentException
//	{
//		return toJSONValue(writer, value, valueType, null, ConversionLocation.DESIGN);
//	}

	/**
	 * Shortcut for using {@link ToJSONConverter} directly.
	 *
	 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 */
	public static JSONWriter toBrowserJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, DataConversion clientConversion)
		throws JSONException, IllegalArgumentException
	{
		return JSONUtils.ToJSONConverter.INSTANCE.toJSONValue(writer, key, value, valueType, clientConversion);
	}

	public static JSONWriter addKeyIfPresent(JSONWriter writer, String key) throws JSONException
	{
		if (key != null) writer.key(key);
		return writer;
	}

	/**
	 * Writes the given object into the JSONWriter. (it is meant to be used for transforming the basic types that can be sent by beans/components)
	 *
	 * @param toJSONConverter
	 * @param writer the JSONWriter.
	 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 * @param value the value to be written to the writer.
	 * @param valueType the types of the value; can be null in which case a 'best-effort' to JSON conversion will take place.
	 * @param clientConversion the object where the type (like Date) of the conversion that should happen on the client.
	 * @return true if the given value could be written using default logic and false otherwise.
	 * @throws IllegalArgumentException if the given object could not be written to JSON for some reason.
	 */
	public static boolean defaultToJSONValue(IToJSONConverter toJSONConverter, JSONWriter w, String key, Object value, PropertyDescription valueType,
		DataConversion clientConversion) throws JSONException, IllegalArgumentException
	{
		// there is no clear conversion; see if we find a primitive/default or Class based conversion
		Object converted = value;

		if (converted == null || converted == JSONObject.NULL)
		{
			addKeyIfPresent(w, key);
			w = w.value(null); // null is allowed
		}
		else if (converted instanceof JSONArray) // TODO are we using JSON object or Map and Lists? ( as internal representation of properties)
		{
			addKeyIfPresent(w, key);
			w = w.value(converted);
		}
		else if (converted instanceof JSONObject)
		{
			addKeyIfPresent(w, key);
			w = w.value(converted);
		}
		else if (converted instanceof List)
		{
			List< ? > lst = (List< ? >)converted;
			addKeyIfPresent(w, key);
			w.array();
			for (int i = 0; i < lst.size(); i++)
			{
				if (clientConversion != null) clientConversion.pushNode(String.valueOf(i));
				toJSONConverter.toJSONValue(w, null, lst.get(i), getArrayElementType(valueType, i), clientConversion);
				if (clientConversion != null) clientConversion.popNode();
			}
			w.endArray();
		}
		else if (converted instanceof Object[])
		{
			Object[] array = (Object[])converted;
			addKeyIfPresent(w, key);
			w.array();
			for (int i = 0; i < array.length; i++)
			{
				if (clientConversion != null) clientConversion.pushNode(String.valueOf(i));
				toJSONConverter.toJSONValue(w, null, array[i], getArrayElementType(valueType, i), clientConversion);
				if (clientConversion != null) clientConversion.popNode();
			}
			w.endArray();
		}
		else if (converted instanceof Map)
		{
			addKeyIfPresent(w, key);
			w = w.object();
			Map<String, ? > map = (Map<String, ? >)converted;
			for (Entry<String, ? > entry : map.entrySet())
			{
				if (clientConversion != null) clientConversion.pushNode(entry.getKey());
				//TODO remove the need for this when going to full tree recursion for sendChanges()
				String[] keys = entry.getKey().split("\\.");
				if (keys.length > 1)
				{
					//LIMITATION of JSONWriter because it can't add a property to an already written object
					// currently for 2 properties like complexmodel.firstNameDataprovider
					//								   size
					//								   complexmodel.lastNameDataprovider
					// it creates 2 json entries with the same key ('complexmodel') and on the client side it only takes one of them
					w.key(keys[0]);
					w.object();
					toJSONConverter.toJSONValue(w, keys[1], entry.getValue(), valueType != null ? valueType.getProperty(entry.getKey()) : null,
						clientConversion);
					w.endObject();
				}// END TODO REMOVE
				else
				{
					toJSONConverter.toJSONValue(w, entry.getKey(), entry.getValue(), valueType != null ? valueType.getProperty(entry.getKey()) : null,
						clientConversion);
				}
				if (clientConversion != null) clientConversion.popNode();
			}
			w = w.endObject();
		}
		else if (converted instanceof JSONWritable)
		{
			TypedData<Map<String, Object>> dm = ((JSONWritable)converted).toMap();
			toJSONConverter.toJSONValue(w, key, dm.content, dm.contentType, clientConversion);
		}
		// best-effort to still find a way to write data and convert if needed follows
		else if (converted instanceof Integer || converted instanceof Long)
		{
			addKeyIfPresent(w, key);
			w = w.value(((Number)converted).longValue());
		}
		else if (converted instanceof Boolean)
		{
			addKeyIfPresent(w, key);
			w = w.value(((Boolean)converted).booleanValue());
		}
		else if (converted instanceof Number)
		{
			addKeyIfPresent(w, key);
			w = w.value(((Number)converted).doubleValue());
		}
		else if (converted instanceof String)
		{
			addKeyIfPresent(w, key);
			w = w.value(converted);
		}
		else if (converted instanceof CharSequence)
		{
			addKeyIfPresent(w, key);
			w = w.value(converted.toString());
		}
		else if (converted instanceof Date)
		{
			addKeyIfPresent(w, key);
			if (clientConversion != null) clientConversion.convert("Date");
			w = w.value(((Date)converted).getTime());
		}
		else
		{
			return false;
		}

		return true;
	}

	public static Object fromJSONUnwrapped(Object previousComponentValue, Object newJSONValue, PropertyDescription propDesc,
		DataConverterContext dataConversionContext) throws JSONException
	{
		Object value = fromJSON(previousComponentValue, newJSONValue, propDesc, dataConversionContext);
		if (propDesc != null && propDesc.getType() instanceof IWrapperType< ? , ? >)
		{
			// will probably never happen as all this fromJSON thing was only meant for Dates (at least currently)
			IWrapperType wType = ((IWrapperType< ? , ? >)propDesc.getType());
			value = wType.unwrap(wType.fromJSON(value, null, null));
		}
		return value;
	}

	/**
	 * Returns the object to be set directly in a BaseWebObject properties map. For wrapper types this means a wrapped value directly.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object fromJSON(Object oldValue, Object newValue, PropertyDescription desc, IDataConverterContext dataConversionContext) throws JSONException
	{
		if (newValue == null || newValue == JSONObject.NULL) return null;
		if (desc != null)
		{
			IPropertyType< ? > type = desc.getType();
			if (type instanceof IConvertedPropertyType< ? >)
			{
				return ((IConvertedPropertyType)type).fromJSON(newValue, oldValue, dataConversionContext);
			}
			else if (type instanceof IWrapperType< ? , ? >)
			{
				return ((IWrapperType)type).fromJSON(newValue, oldValue, dataConversionContext);
			}
		}
		return newValue;
	}

	protected static PropertyDescription getArrayElementType(PropertyDescription valueType, int i)
	{
		PropertyDescription elValueType = null;
		if (valueType != null)
		{
			elValueType = valueType.getProperty(String.valueOf(i));
		}
		return elValueType;
	}

	/**
	 * Validates a String to be valid JSON content and normalizes it.
	 * @param json the json content to check.
	 * @return the given JSON normalized.
	 * @throws JSONException if the given JSON is not valid
	 */
	public static String validateAndTrimJSON(String json) throws JSONException
	{
		if (json == null) return null;

		return new JSONObject(json).toString(); // just to validate - can we do this nicer with available lib (we might not need the "normalize" part)?
	}

//	/**
//	 * Adds all properties of the given object as key-value pairs in the writer.
//	 * @param propertyWriter the writer.
//	 * @param objectToMerge the object contents to be merged into the writer prepared object.
//	 * @throws JSONException if the writer is not prepared (to write object contents) or other json exception occurs.
//	 */
//	public static void addObjectPropertiesToWriter(JSONWriter propertyWriter, JSONObject objectToMerge) throws JSONException
//	{
//		Iterator< ? > it = objectToMerge.keys();
//		while (it.hasNext())
//		{
//			String key = (String)it.next();
//			propertyWriter.key(key).value(objectToMerge.get(key));
//		}
//	}

	public static interface JSONWritable
	{
		TypedData<Map<String, Object>> toMap();
	}

	public static interface IToJSONConverter
	{
		/**
		 * Converts from a value to JSON form (that can be sent to the browser) and writes to "writer".
		 * @param writer the JSON writer to write to
		 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
		 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
		 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
		 * @param value the value to be converted and written.
		 * @param valueType the type of the property as described in the spec file
		 * @param clientConversion client conversion markers that can be set and if set will be used client side to interpret the data properly.
		 * @param toDestinationType TODO remove this
		 * @return the JSON writer for easily continuing the write process in the caller.
		 */
		JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, DataConversion clientConversion)
			throws JSONException, IllegalArgumentException;

	}

	public static class ToJSONConverter implements IToJSONConverter
	{

		public static final ToJSONConverter INSTANCE = new ToJSONConverter();

		@Override
		public JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, DataConversion browserConversionMarkers)
			throws JSONException, IllegalArgumentException
		{
			if (value != null && valueType != null)
			{
				IPropertyType< ? > type = valueType.getType();
				if (type instanceof IConvertedPropertyType)
				{
					// good, we now know that it needs special conversion
					try
					{
						return ((IConvertedPropertyType)type).toJSON(writer, key, value, browserConversionMarkers);
					}
					catch (Exception ex)
					{
						log.error("Error while converting value: " + value + " to type: " + type, ex);
						return writer;
					}
				}
				else if (type instanceof IWrapperType< ? , ? >)
				{
					// good, we now know that it needs special conversion
					try
					{
						return ((IWrapperType)type).toJSON(writer, key, value, browserConversionMarkers);
					}
					catch (Exception ex)
					{
						log.error("Error while converting value: " + value + " to type: " + type, ex);
						return writer;
					}
				}
			}

			// best-effort to still find a way to write data and convert if needed follows
			IClassPropertyType<Object> classType = (IClassPropertyType<Object>)(value == null ? null : TypesRegistry.getType(value.getClass()));
			if (classType != null)
			{
				return classType.toJSON(writer, key, value, browserConversionMarkers);
			}

			if (!defaultToJSONValue(this, writer, key, value, valueType, browserConversionMarkers))
			{
				// addKeyIfPresent(w, key);
				// w = w.value(new JSONObject("{}"));
				// write nothing here, neither key nor value as we know not how to do the conversion...
				log.error("unsupported value type:" + valueType + " for value: " + value, new IllegalArgumentException("unsupported value type for value: " +
					value));
			}

			return writer;
		}
	}

}
