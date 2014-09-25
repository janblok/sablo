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

package org.sablo.specification.property;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

/**
 * Type for what in spec files you see defined in the types section. (custom javascript object types)
 * It should be a kind of proxy for all possible conversion types to it's child types.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
// TODO these ET and WT are improper - as for object type they can represent multiple types (a different set for each child key), but they help to avoid some bugs at compile-time
public class CustomJSONObjectType<ET, WT> extends CustomJSONPropertyType<Map<String, ET>> implements IWrapperType<Map<String, ET>, ChangeAwareMap<ET, WT>>
{

	public static final String TYPE_NAME = "JSON_obj";

	protected static final String CONTENT_VERSION = "vEr";
	protected static final String UPDATES = "u";
	protected static final String KEY = "k";
	protected static final String VALUE = "v";
	protected static final String INITIALIZE = "in";
	protected static final String NO_OP = "n";

	protected static Set<String> angularAutoAddedKeysToIgnore = new HashSet<>();
	{
		angularAutoAddedKeysToIgnore.add("$$hashKey");
	}

	protected Map<String, IWrapperType<ET, WT>> wrapperChildProps;


	/**
	 * Creates a new type that handles objects of the given key types (with their own set of default value, config object)
	 *
	 * @param definition the defined types of the object's values. (per key)
	 */
	public CustomJSONObjectType(String customTypeName, PropertyDescription definition)
	{
		super(customTypeName, definition);
	}

	@Override
	public Map<String, ET> unwrap(ChangeAwareMap<ET, WT> value)
	{
		// this type will wrap an [] or List into a list; unwrap will simply return that list that will further wrap/unwrap elements as needed on any operation
		// look at this wrapped list as the external portal of the list property as far as BaseWebObjects are concerned
		return value;
	}

	@Override
	public ChangeAwareMap<ET, WT> wrap(Map<String, ET> value, ChangeAwareMap<ET, WT> previousValue, IDataConverterContext dataConverterContext)
	{
		if (value instanceof ChangeAwareMap< ? , ? >) return (ChangeAwareMap<ET, WT>)value;

		Map<String, ET> wrappedMap = wrapMap(value, dataConverterContext);
		if (wrappedMap != null)
		{
			// ok now we have the map or wrap map (depending on if child types are IWrapperType or not)
			// wrap this further into a change-aware map; this is used to be able to track changes and perform server to browser full or granular updates
			return new ChangeAwareMap<ET, WT>(wrappedMap, dataConverterContext, previousValue != null ? previousValue.getListContentVersion() + 1 : 1);
		}
		return null;
	}

	protected IPropertyType<ET> getElementType(String childPropertyName)
	{
		return (IPropertyType<ET>)getCustomJSONTypeDefinition().getProperty(childPropertyName).getType();
	}

	private Map<String, ET> wrapMap(Map<String, ET> value, IDataConverterContext dataConverterContext)
	{
		// this type will wrap (if needed; that means it will end up as a normal list if element type is not wrapped type
		// or a WrapperList otherwise) an [] or List into a list; unwrap will simply return that list that will further
		// wrap/unwrap elements as needed on any operation
		// look at this wrapped list as the external portal of the list property as far as BaseWebObjects are concerned
		if (value != null)
		{
			if (value instanceof IWrappedBaseMapProvider)
			{
				// it's already what we want; return it
				return value;
			}
			Map<String, IWrapperType<ET, WT>> wrappingChildren = getChildPropsThatNeedWrapping();

			if (wrappingChildren == null || wrappingChildren.isEmpty())
			{
				// it's already what we want; return it
				return value;
			}

			return new WrapperMap<ET, WT>(value, wrappingChildren, dataConverterContext, true);
		}
		return null;
	}

	protected Map<String, IWrapperType<ET, WT>> getChildPropsThatNeedWrapping()
	{
		if (wrapperChildProps == null)
		{
			wrapperChildProps = new HashMap<>();

			for (Entry<String, PropertyDescription> entry : getCustomJSONTypeDefinition().getProperties().entrySet())
			{
				Object type = entry.getValue().getType();
				if (type instanceof IWrapperType< ? , ? >) wrapperChildProps.put(entry.getKey(), (IWrapperType<ET, WT>)type);
			}
		}
		return wrapperChildProps;
	}

	@Override
	public ChangeAwareMap<ET, WT> fromJSON(Object newJSONValue, ChangeAwareMap<ET, WT> previousChangeAwareMap, IDataConverterContext dataConverterContext)
	{
		JSONObject clientReceivedJSON;
		if (newJSONValue instanceof JSONObject && (clientReceivedJSON = (JSONObject)newJSONValue).has(CONTENT_VERSION) &&
			(clientReceivedJSON.has(VALUE) || clientReceivedJSON.has(UPDATES)))
		{
			if (clientReceivedJSON.has(NO_OP)) return previousChangeAwareMap;

			try
			{
				if (previousChangeAwareMap == null || clientReceivedJSON.getInt(CONTENT_VERSION) == previousChangeAwareMap.getListContentVersion() + 1)
				{
					if (clientReceivedJSON.has(UPDATES))
					{
						if (previousChangeAwareMap == null)
						{
							log.warn("property " + dataConverterContext.getPropertyDescription().getName() +
								" is typed as json object; it got browser updates but server-side it is null; ignoring browser update.");
						}
						else
						{
							// here we operate directly on (wrapper) base map as this change doesn't need to be sent back to browser
							// as browser initiated it; also JSON conversions work on wrapped values
							Map<String, WT> wrappedBaseMap = previousChangeAwareMap.getWrappedBaseMapForReadOnly();

							JSONArray updatedRows = clientReceivedJSON.getJSONArray(UPDATES);
							for (int i = updatedRows.length() - 1; i >= 0; i--)
							{
								JSONObject row = updatedRows.getJSONObject(i);
								String key = row.getString(KEY);
								Object val = row.get(VALUE);

								PropertyDescription keyPD = getCustomJSONTypeDefinition().getProperty(key);
								if (keyPD != null)
								{
									WT newWrappedEl = (WT)JSONUtils.fromJSON(wrappedBaseMap.get(key), val, keyPD, dataConverterContext);
									previousChangeAwareMap.putInWrappedBaseList(key, newWrappedEl, false);
								}
								else
								{
									if (!angularAutoAddedKeysToIgnore.contains(key)) log.warn("Cannot set property '" + key +
										"' of custom JSON Object as it's type is undefined.");
								}
							}
							previousChangeAwareMap.increaseContentVersion();
						}
						return previousChangeAwareMap;
					}
					else
					{
						// full replace
						return fullValueReplaceFromBrowser(previousChangeAwareMap, dataConverterContext, clientReceivedJSON.getJSONObject(VALUE));
					}
				}
				else
				{
					log.warn("property " + dataConverterContext.getPropertyDescription().getName() + " is typed as JSON object; it got browser updates (" +
						clientReceivedJSON.getInt(CONTENT_VERSION) + ") but expected server version (" + (previousChangeAwareMap.getListContentVersion() + 1) +
						") - so server changed meanwhile; ignoring browser update.");

					// dropped browser update because server object changed meanwhile;
					// will send a full update to have the correct value browser-side as well again (currently server side is leading / has more prio because not all server side values might support being recreated from client values)
					previousChangeAwareMap.markAllChanged();

					return previousChangeAwareMap;
				}
			}
			catch (JSONException e)
			{
				log.error("Cannot correctly parse custom JSON object property updates/values from browser.", e);
				return previousChangeAwareMap;
			}
		}
		else if (newJSONValue == null)
		{
			return null;
		}
		else if (newJSONValue instanceof JSONObject)
		{
			// this can happen if the property was undefined before (so not even aware of type client side) and it was assigned a complete object value client side;
			// in this case we must update server value and send a request back to client containing the type and letting it know that it must start watching the new value (for granular updates)
			ChangeAwareMap<ET, WT> newChangeAwareMap = fullValueReplaceFromBrowser(previousChangeAwareMap, dataConverterContext, (JSONObject)newJSONValue);
			newChangeAwareMap.markMustSendTypeToClient();
			return newChangeAwareMap;
		}
		else
		{
			log.error("property " + dataConverterContext.getPropertyDescription().getName() +
				" is typed as JSON object, but the value is not an JSONObject or supported update value: " + newJSONValue);
			return previousChangeAwareMap;
		}
	}

	protected ChangeAwareMap<ET, WT> fullValueReplaceFromBrowser(ChangeAwareMap<ET, WT> previousChangeAwareMap, IDataConverterContext dataConverterContext,
		JSONObject clientReceivedJSON)
	{
		Map<String, WT> map = new HashMap<String, WT>();
		Map<String, WT> previousWrappedBaseMap = (previousChangeAwareMap != null ? previousChangeAwareMap.getWrappedBaseMapForReadOnly() : null);

		Iterator<String> it = clientReceivedJSON.keys();
		while (it.hasNext())
		{
			String key = it.next();
			WT oldVal = null;
			PropertyDescription keyPD = getCustomJSONTypeDefinition().getProperty(key);
			if (keyPD != null)
			{
				if (previousWrappedBaseMap != null)
				{
					oldVal = previousWrappedBaseMap.get(key);
				}
				try
				{
					map.put(key,
						(WT)JSONUtils.fromJSON(oldVal, clientReceivedJSON.opt(key), getCustomJSONTypeDefinition().getProperty(key), dataConverterContext));
				}
				catch (JSONException e)
				{
					log.error("Cannot parse JSON object element browser JSON.", e);
				}
			}
			else
			{
				if (!angularAutoAddedKeysToIgnore.contains(key)) log.warn("Cannot set property '" + key + "' of custom JSON Object as it's type is undefined.");
			}
		}

		Map<String, ET> newBaseMap;
		Map<String, IWrapperType<ET, WT>> wrappingChildren = getChildPropsThatNeedWrapping();
		if (wrappingChildren != null)
		{
			newBaseMap = new WrapperMap<ET, WT>(map, wrappingChildren, dataConverterContext);
		}
		else
		{
			newBaseMap = (Map<String, ET>)map; // in this case ET == WT
		}

		// TODO how to handle previous null value here; do we need to re-send to client or not (for example initially both client and server had values, at the same time server==null client sends full update); how do we kno case server version is unknown then
		return new ChangeAwareMap<ET, WT>(newBaseMap, dataConverterContext, previousChangeAwareMap != null ? previousChangeAwareMap.increaseContentVersion()
			: 1);
	}

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, ChangeAwareMap<ET, WT> changeAwareMap, DataConversion conversionMarkers) throws JSONException
	{
		JSONUtils.addKeyIfPresent(writer, key);
		if (changeAwareMap != null)
		{
			if (conversionMarkers != null) conversionMarkers.convert(CustomJSONObjectType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets

			Set<String> changes = changeAwareMap.getChangedKeys();
			Map<String, WT> wrappedBaseMap = changeAwareMap.getWrappedBaseMapForReadOnly();
			writer.object();
			// TODO send all for now also if we know of no changes - when the separate tagging interface for granular updates vs full updates is added we can send NO_OP again
			if (changeAwareMap.mustSendAll() || (changes.size() == 0 && !changeAwareMap.mustSendTypeToClient()))
			{
				// send all (currently we don't support granular updates for remove but we could in the future)
				DataConversion objConversionMarkers = new DataConversion();
				writer.key(CONTENT_VERSION).value(changeAwareMap.increaseContentVersion()).key(VALUE).object();
				for (Entry<String, WT> e : wrappedBaseMap.entrySet())
				{
					objConversionMarkers.pushNode(e.getKey());
					JSONUtils.toBrowserJSONValue(writer, e.getKey(), wrappedBaseMap.get(e.getKey()), getCustomJSONTypeDefinition().getProperty(e.getKey()),
						objConversionMarkers);
					objConversionMarkers.popNode();
				}
				writer.endObject();
				if (objConversionMarkers.getConversions().size() > 0)
				{
					writer.key("conversions").object();
					JSONUtils.writeConversions(writer, objConversionMarkers.getConversions());
					writer.endObject();
				}
			}
			else if (changes.size() > 0)
			{
				// else write changed indexes / granular update:
				writer.key(CONTENT_VERSION).value(changeAwareMap.increaseContentVersion());
				if (changeAwareMap.mustSendTypeToClient())
				{
					// updates + mustSendTypeToClient can happen if child elements are also similar - and need to instrument their values client-side when set by reference/completely from browser
					writer.key(INITIALIZE).value(true);
				}

				writer.key(UPDATES).array();
				DataConversion objConversionMarkers = new DataConversion();
				int i = 0;
				for (String k : changes)
				{
					objConversionMarkers.pushNode(String.valueOf(i++));
					writer.object().key(KEY).value(k);
					objConversionMarkers.pushNode(VALUE);
					JSONUtils.toBrowserJSONValue(writer, VALUE, wrappedBaseMap.get(k), getCustomJSONTypeDefinition().getProperty(k), objConversionMarkers);
					objConversionMarkers.popNode();
					writer.endObject();
					objConversionMarkers.popNode();
				}
				writer.endArray();
				if (objConversionMarkers.getConversions().size() > 0)
				{
					writer.key("conversions").object();
					JSONUtils.writeConversions(writer, objConversionMarkers.getConversions());
					writer.endObject();
				}
			}
			else
			// changeAwareMap.mustSendTypeToClient() is true then
			{
				writer.key(CONTENT_VERSION).value(changeAwareMap.getListContentVersion());
				writer.key(INITIALIZE).value(true);
			}
			writer.endObject();
			changeAwareMap.clearChanges();
		}
		else
		{
			if (conversionMarkers != null) conversionMarkers.convert(CustomJSONObjectType.TYPE_NAME); // so that the client knows it must use the custom client side JS for what JSON it gets
			writer.value(JSONObject.NULL); // TODO how to handle null values which have no version info (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?
		}
		return writer;
	}

}
