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
import java.util.concurrent.Callable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.specification.property.IPropertyConverterForBrowser;
import org.sablo.specification.property.IPropertyConverterForBrowserWithDynamicClientType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.IPropertyWithClientSideConversions;
import org.sablo.specification.property.ISupportsGranularUpdates;
import org.sablo.specification.property.IWrapperType;
import org.sablo.specification.property.types.DatePropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.util.ValueReference;
import org.sablo.websocket.IToJSONWriter;
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

	public static final String CONVERSION_CL_SIDE_TYPE_KEY = "_T";
	public static final String VALUE_KEY = "_V";

	public static final String TYPES_KEY = "svy_types";
	private static final Logger log = LoggerFactory.getLogger(JSONUtils.class.getCanonicalName());


	public static JSONWriter writeDataAsFullToJSON(JSONWriter writer, Map<String, ? > data, PropertyDescription dataTypes, BrowserConverterContext context)
		throws JSONException
	{
		writeData(FullValueToJSONConverter.INSTANCE, writer, data, dataTypes, context);
		return writer;
	}

	public static <ContextT> void writeData(IToJSONConverter<ContextT> converter, JSONWriter writer, Map<String, ? > data, PropertyDescription dataTypes,
		ContextT contextObject) throws JSONException
	{
		for (Entry<String, ? > entry : data.entrySet())
		{
			converter.toJSONValue(writer, entry.getKey(), entry.getValue(), dataTypes != null ? dataTypes.getProperty(entry.getKey()) : null, contextObject);
		}
	}

	// DELETE THIS LINE previously writeDataWithConversions
	public static String writeDataAsFullToJSON(Map<String, ? > data, PropertyDescription dataTypes, BrowserConverterContext context) throws JSONException
	{
		return writeData(FullValueToJSONConverter.INSTANCE, data, dataTypes, context);
	}

	public static String writeChanges(Map<String, ? > data, PropertyDescription dataTypes, BrowserConverterContext context) throws JSONException
	{
		return writeData(ChangesToJSONConverter.INSTANCE, data, dataTypes, context);
	}

	public static <ContextT> String writeData(IToJSONConverter<ContextT> converter, Map<String, ? > data, PropertyDescription dataTypes, ContextT contextObject)
		throws JSONException
	{
		JSONWriter writer = new JSONStringer().object();
		writeData(converter, writer, data, dataTypes, contextObject);
		return writer.endObject().toString();
	}

	public static <ContextT> String writeComponentChanges(WebComponent component, ChangesToJSONConverter converter) throws JSONException
	{
		JSONWriter writer = new JSONStringer().object();
		if (component.writeOwnChanges(writer, "comp", component.getName(), converter)) writer.endObject();
		return writer.endObject().toString();
	}

	/**
	 * Shortcut for using {@link FullValueToJSONConverter} directly.
	 *
	 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 */
	public static JSONWriter toBrowserJSONFullValue(JSONWriter writer, String key, Object value, PropertyDescription valueType,
		IBrowserConverterContext context) throws JSONException, IllegalArgumentException
	{
		return JSONUtils.FullValueToJSONConverter.INSTANCE.toJSONValue(writer, key, value, valueType, context);
	}

	/**
	 * Shortcut for using {@link ChangesToJSONConverter} directly.
	 *
	 * @param key if this value will be part of a JSON object, key is non-null and you MUST do writer.key(...) before adding the converted value. This
	 * is useful for cases when you don't want the value written at all in resulting JSON in which case you don't write neither key or value. If
	 * key is null and you want to write the converted value write only the converted value to the writer, ignore the key.
	 */
	public static JSONWriter changesToBrowserJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType,
		IBrowserConverterContext context) throws JSONException, IllegalArgumentException
	{
		return JSONUtils.ChangesToJSONConverter.INSTANCE.toJSONValue(writer, key, value, valueType, context);
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
	public static <ContextObject> boolean defaultToJSONValue(IToJSONConverter<ContextObject> toJSONConverter, JSONWriter w, String key, Object value,
		PropertyDescription valueType, ContextObject contextObject) throws JSONException, IllegalArgumentException
	{
		// there is no clear conversion; see if we find a primitive/default or Class based conversion
		Object converted = value;

		if (converted == null || converted == JSONObject.NULL)
		{
			addKeyIfPresent(w, key);
			w = w.value(null); // null is allowed
		}
		else if (converted instanceof JSONArray || converted instanceof JSONObject || converted instanceof JSONString)
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
				toJSONConverter.toJSONValue(w, null, lst.get(i), getArrayElementType(valueType, i), contextObject);
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
				toJSONConverter.toJSONValue(w, null, array[i], getArrayElementType(valueType, i), contextObject);
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
					toJSONConverter.toJSONValue(w, keys[1], entry.getValue(), valueType != null ? valueType.getProperty(entry.getKey()) : null, contextObject);
					w.endObject();
				} // END TODO REMOVE
				else
				{
					toJSONConverter.toJSONValue(w, entry.getKey(), entry.getValue(), valueType != null ? valueType.getProperty(entry.getKey()) : null,
						contextObject);
				}
			}
			w = w.endObject();
		}
		else if (converted instanceof JSONWritable)
		{
			TypedData<Map<String, Object>> dm = ((JSONWritable)converted).toMap();
			toJSONConverter.toJSONValue(w, key, dm.content, dm.contentType, contextObject);
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
			double convertedDouble = ((Number)converted).doubleValue();
			if (Double.isNaN(convertedDouble) || Double.isInfinite(convertedDouble))
			{
				w = w.value(null);
			}
			else
			{
				w = w.value(convertedDouble);
			}
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
			final JSONWriter fw = w;
			writeConvertedValueWithClientType(w, key, () -> {
				return '"' + DatePropertyType.CLIENT_SIDE_TYPE_NAME + '"';
			}, () -> {
				IPropertyType< ? > dateType = TypesRegistry.getType(DatePropertyType.TYPE_NAME);

				if (dateType instanceof IToJSONConverter< ? >) ((IToJSONConverter< ? >)dateType).toJSONValue(fw, null, converted, null, null); // we rely here on the fact that the currently registered date type knows how to handle null PD or context
				else throw new RuntimeException(
					"Cannot find 'Date' type - or date type does not implement IToJSONConverter - in TypesRegistry when trying to perform a default date conversion: " +
						dateType);

				return null;
			});
		}
		else
		{
			return false;
		}

		return true;
	}

	/**
	 * This method returns a IJSONStringWithClientSideType that contains the output of writing a property's value to client side and separately the client side type for that value (if any).<br/>
	 * This is meant to be called by {@link IPropertyConverterForBrowserWithDynamicClientType} properties when computing the value and the type they actually have based on another type (that here are
	 * represented by "pd" arg.<br/><br/>
	 *
	 * For example a data driven type can decide at runtime if it's data is a number (no client side conversion) or a date ('date' client side conversion).<br/><br/>
	 *
	 * <ul>
	 * <li>if "pd" is itself a {@link IPropertyConverterForBrowserWithDynamicClientType} it will just ask it for what it needs;</li>
	 * <li>if "pd" is just of a{@link IPropertyWithClientSideConversions} type it will just use {@link FullValueToJSONConverter#toJSONValue(JSONWriter, String, Object, PropertyDescription, IBrowserConverterContext)}} to get the content and will get the type from {@link IPropertyWithClientSideConversions#writeClientSideTypeName(JSONWriter, String, PropertyDescription)};</li>
	 * <li>otherwise, it will just write the value to JSON using {@link FullValueToJSONConverter#toJSONValue(JSONWriter, String, Object, PropertyDescription, IBrowserConverterContext)}} and have a null client side type.
	 *
	 * @param propertyValue the value of a property
	 * @param pd the property description of that property.
	 * @param dataConverterContext context
	 *
	 * @return a IJSONStringWithClientSideType representing the value (that can be embedded in a larger JSON) and the client side conversion type (if any).
	 */
	public static IJSONStringWithClientSideType getConvertedValueWithClientType(Object propertyValue, PropertyDescription pd,
		IBrowserConverterContext dataConverterContext)
	{
		EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true); // that 'true' is a workaround for allowing directly a value instead of object or array
		IPropertyType< ? > type = (pd != null ? pd.getType() : null);
		JSONString clientSideConversionType;

		if (type instanceof IPropertyConverterForBrowserWithDynamicClientType)
			clientSideConversionType = ((IPropertyConverterForBrowserWithDynamicClientType)type).toJSONWithDynamicClientSideType(ejw, null, propertyValue, pd,
				dataConverterContext);
		else
		{
			FullValueToJSONConverter.INSTANCE.toJSONValue(ejw, null, propertyValue, pd, dataConverterContext);
			if (type instanceof IPropertyWithClientSideConversions)
			{
				clientSideConversionType = JSONUtils.getClientSideTypeJSONString(pd);
			}
			else clientSideConversionType = null;
		}

		return new JSONStringWithClientSideType(ejw.toJSONString(), clientSideConversionType);
	}

	/**
	 * This method handles sending typed default values to client; it will generate instead of a value an object with two keys:
	 * { __dt: "typeName", v: ... } that will be handled correctly by the appropriate default conversion code client-side (if found in a value with unknown client conversion type).<br/><br/>
	 *
	 * This is not that nice but helps remove a lot of code where types were kept in parallel to the written values. But now as all needed client side conversions are sent separately to each window
	 * when a container is shown in that window - the only thing that still needed to send client side conversion types when writing the values are default conversions => the need for this hackish method.
	 *
	 * @param w the json writer used to send content to browser.
	 * @param key the key that this value should be written to - if any value will be written.
	 * @param typeOfValue could be anything that a {@link IPropertyWithClientSideConversions#writeClientSideTypeName(JSONWriter, String, PropertyDescription)} returns. Usually just a client side type name.
	 * @param valueWriter the callable that writes the actual value to w.
	 */
	public static void writeConvertedValueWithClientType(JSONWriter w, String key, JSONString typeOfValue, Callable<Void> valueWriter) throws JSONException
	{
		addKeyIfPresent(w, key);
		w.object();
		w.key(CONVERSION_CL_SIDE_TYPE_KEY);
		w.value(typeOfValue).key(VALUE_KEY);
		try
		{
			valueWriter.call();
		}
		catch (Exception e)
		{
			if (e instanceof JSONException) throw (JSONException)e;
			else log.error("Error while trying to write default converted value with type: " + typeOfValue + " and key: " + key, e);

		}
		w.endObject();
	}

	public static EmbeddableJSONWriter getClientSideTypeJSONString(PropertyDescription pd)
	{
		if (!(pd.getType() instanceof IPropertyWithClientSideConversions)) return null;

		EmbeddableJSONWriter ejw = new EmbeddableJSONWriter(true);
		((IPropertyWithClientSideConversions< ? >)pd.getType()).writeClientSideTypeName(ejw, null, pd);
		return ejw;
	}


	public static Object fromJSONUnwrapped(Object previousComponentValue, Object newJSONValue, PropertyDescription pd,
		IBrowserConverterContext dataConversionContext, ValueReference<Boolean> returnValueAdjustedIncommingValue) throws JSONException
	{
		Object value = fromJSON(previousComponentValue, newJSONValue, pd, dataConversionContext, returnValueAdjustedIncommingValue);
		if (pd != null && pd.getType() instanceof IWrapperType< ? , ? >)
		{
			// will probably never happen as all this fromJSON thing was only meant for Dates (at least currently)
			IWrapperType wType = ((IWrapperType< ? , ? >)pd.getType());
			value = wType.unwrap(value);
		}
		return value;
	}

	/**
	 * Returns the object to be set directly in a BaseWebObject properties map. For wrapper types this means a wrapped value directly.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object fromJSON(Object oldValue, Object newValue, PropertyDescription pd, IBrowserConverterContext dataConversionContext,
		ValueReference<Boolean> returnValueAdjustedIncommingValue) throws JSONException
	{
		if (newValue == JSONObject.NULL) newValue = null;
		if (pd != null)
		{
			IPropertyType< ? > type = pd.getType();
			if (type instanceof IPropertyConverterForBrowser< ? >)
			{
				return ((IPropertyConverterForBrowser)type).fromJSON(newValue, oldValue, pd, dataConversionContext, returnValueAdjustedIncommingValue);
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
	 * @param value
	 * @return
	 */
	private static String safeToString(Object value)
	{
		try
		{
			return String.valueOf(value);
		}
		catch (Exception e)
		{
			return "toString failed for instanceof " + value.getClass();
		}
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

	public static interface IToJSONConverter<ContextType>
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
		 * @param context an object representing a state that the conversions of this type might need.
		 * @return the JSON writer for easily continuing the write process in the caller.
		 */
		JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, ContextType context)
			throws JSONException, IllegalArgumentException;

	}

	public static class FullValueToJSONConverter implements IToJSONConverter<IBrowserConverterContext>
	{

		public static final FullValueToJSONConverter INSTANCE = new FullValueToJSONConverter();

		@Override
		public JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, IBrowserConverterContext context)
			throws JSONException, IllegalArgumentException
		{
			if (value != null && valueType != null)
			{
				IPropertyType< ? > type = valueType.getType();
				if (type instanceof IPropertyConverterForBrowser)
				{
					// good, we now know that it needs special conversion
					try
					{
						return ((IPropertyConverterForBrowser)type).toJSON(writer, key, value, valueType, context);
					}
					catch (Exception ex)
					{
						log.error("Error while converting value (toJSON): " + safeToString(value) + " of key: " + key + " to type: " + type +
							" current json: " + writer.toString(), ex);
						return writer;
					}
				}
				else if (type instanceof IWrapperType< ? , ? >)
				{
					// good, we now know that it needs special conversion
					try
					{
						return ((IWrapperType)type).toJSON(writer, key, value, valueType, context);
					}
					catch (Exception ex)
					{
						log.error("Error while converting value (toJSON - wrapper): " + safeToString(value) + " of key: " + key + " to type: " + type +
							" current json: " + writer.toString(), ex);
						return writer;
					}
				}
			}

			// best-effort to still find a way to write data and convert if needed follows
			IClassPropertyType<Object> classType = (IClassPropertyType<Object>)(value == null ? null : TypesRegistry.getType(value.getClass()));
			if (classType != null)
			{
				return classType.toJSON(writer, key, value, valueType, context);
			}

			if (!defaultToJSONValue(this, writer, key, value, valueType, context))
			{
				// addKeyIfPresent(w, key);
				// w = w.value(new JSONObject("{}"));
				// write nothing here, neither key nor value as we know not how to do the conversion...
				log.error("unsupported value type:" + valueType + " for value: " + safeToString(value) + " current json: " + writer.toString(),
					new IllegalArgumentException("unsupported value type for value: " + safeToString(value)));
			}

			return writer;
		}

	}

	public static class ChangesToJSONConverter extends FullValueToJSONConverter
	{

		public static final ChangesToJSONConverter INSTANCE = new ChangesToJSONConverter();

		@Override
		public JSONWriter toJSONValue(JSONWriter writer, String key, Object value, PropertyDescription valueType, IBrowserConverterContext context)
			throws JSONException, IllegalArgumentException
		{
			if (value != null && valueType != null)
			{
				IPropertyType< ? > type = valueType.getType();
				if (type instanceof ISupportsGranularUpdates)
				{
					// good, we now know that it can send changes only
					try
					{
						return ((ISupportsGranularUpdates)type).changesToJSON(writer, key, value, valueType, context);
					}
					catch (Exception ex)
					{
						log.error("Error while writing changes for value (changesToJSON): " + safeToString(value) + " to type: " + type + " current json: " +
							writer.toString(), ex);
						return writer;
					}
				}
			}

			// for most values that don't support granular updates use full value to JSON
			super.toJSONValue(writer, key, value, valueType, context);

			return writer;
		}
	}

	/**
	 * A JSONStringer that is able to be appended to another JSONWriter directly - without re-parsing the value into a JSONObject for example.
	 * @author acostescu
	 */
	public static class EmbeddableJSONWriter extends JSONStringer implements JSONString
	{

		public EmbeddableJSONWriter()
		{
			this(false);
		}

		/**
		 * @param hackForValueOnly if you really need to write directly a value (not array or object) initially, this should be true. It simulates
		 * an open object initially without writing anything to the underlying StringBuffer.
		 */
		public EmbeddableJSONWriter(boolean hackForValueOnly)
		{
			super();
			if (hackForValueOnly) this.mode = 'o'; // simulate being in an object already without anything written to char buffer
		}

		/**
		 * If 'hackForValueOnly' is used this method should be called only once!
		 */
		@Override
		public String toJSONString()
		{
			return writer.toString();
		}

	}

	/**
	 * Interface for easy grouping of typed content to be written to JSON.
	 *
	 * @author acostescu
	 */
	public static interface IJSONStringWithClientSideType extends JSONString
	{

		JSONString getClientSideType();

	}

	public static class JSONStringWrapper implements JSONString
	{
		public String wrappedString;

		public JSONStringWrapper()
		{
		}

		public JSONStringWrapper(String wrappedString)
		{
			this.wrappedString = wrappedString;
		}

		@Override
		public String toJSONString()
		{
			return wrappedString != null ? wrappedString : "null";
		}
	}

	/**
	 * Class for easy grouping of typed content to be written to JSON.
	 * @author acostescu
	 */
	public static class JSONStringWithClientSideType implements IJSONStringWithClientSideType
	{

		protected final String jsonString;
		protected final JSONString clientSideType;

		public JSONStringWithClientSideType(String jsonString)
		{
			this.jsonString = jsonString;
			clientSideType = null;
		}

		public JSONStringWithClientSideType(String jsonString, JSONString clientSideType)
		{
			this.jsonString = jsonString;
			this.clientSideType = clientSideType;
		}

		@Override
		public String toJSONString()
		{
			return jsonString;
		}

		@Override
		public JSONString getClientSideType()
		{
			return clientSideType;
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ((clientSideType == null) ? 0 : clientSideType.hashCode());
			result = prime * result + ((jsonString == null) ? 0 : jsonString.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			JSONStringWithClientSideType other = (JSONStringWithClientSideType)obj;
			if (clientSideType == null)
			{
				if (other.clientSideType != null) return false;
			}
			else if (!clientSideType.equals(other.clientSideType)) return false;
			if (jsonString == null)
			{
				if (other.jsonString != null) return false;
			}
			else if (!jsonString.equals(other.jsonString)) return false;
			return true;
		}

		@Override
		public String toString()
		{
			return "JSONString:" + toJSONString();
		}

	}

	public static <X> String writeToJSONString(IToJSONWriter<X> toJSONWriter, IToJSONConverter<X> converter) throws JSONException
	{
		EmbeddableJSONWriter rowData = new EmbeddableJSONWriter();

		toJSONWriter.writeJSONContent(rowData, null, converter);
		return rowData.toJSONString();
	}

}
