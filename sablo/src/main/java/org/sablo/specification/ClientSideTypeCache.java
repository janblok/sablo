/*
 * Copyright (C) 2019 Servoy BV
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

package org.sablo.specification;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.sablo.specification.property.CustomJSONObjectType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.IPropertyWithClientSideConversions;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;

/**
 * In order to not re-compute all types used by a component spec client-side each time a component is sent to client we keep this cache.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class ClientSideTypeCache
{

	private static final String PROPERTIES_KEY = "p";
	private static final String FACTORY_TYPE_DETAILS = "ftd"; // currently only factory type "custom object" will send to client specific subproperty types for each custom object type; on client, a type factory will be aware of those and will be able to create then specific custom object types as defined in spec files
	private static final String HANDLERS_KEY = "h";
	private static final String APIS_KEY = "a";
	private static final String RETURN_VAL_KEY = "r";

	private final HashMap<String, EmbeddableJSONWriter> webObjectClientTypeCache = new HashMap<>();

	public void clear()
	{
		// clear all currently cached client side types (probably one or more of the component specs have changed)
		webObjectClientTypeCache.clear();
	}

	public EmbeddableJSONWriter getClientSideTypesFor(WebObjectSpecification webObjectSpec)
	{
		EmbeddableJSONWriter cachedClSideTs;
		String webObjectName = webObjectSpec.getName();
		if (!webObjectClientTypeCache.containsKey(webObjectName))
		{
			cachedClSideTs = buildClientSideTypesFor(webObjectSpec);
			webObjectClientTypeCache.put(webObjectName, cachedClSideTs);
		}
		else cachedClSideTs = webObjectClientTypeCache.get(webObjectName);

		return cachedClSideTs;
	}

	/**
	 * Make sure to use {@link #getClientSideTypesFor(WebObjectSpecification)} where possible - if you are not going to cache it yourself - to take it from cache instead of building it each time.
	 *
	 * Will build something like this for components and services (no handlers for services of course)
	 * <pre>
	 * {
	 *     p: { p1: ["JSON_obj", "ct1"], // custom object 'ct1'
	 *          p2: "date",
	 *          p3: ["JSON_arr", "component"], ... },                  // so any properties that have client side conversions (by name or in case of factory types via an array of 2: factory name and factory param)
	 *   ftd: { "JSON_obj":
	 *             { ct1: { p1: ..., p2: ..., ... },                       // ftd = factory type details (types that need to create specific type instances for usage); currently only custom object types need to send details about subproperties to client for each specific custom object type
	 *               ct2: { p1: ..., p2: ..., ... }, ... } },              // any custom object types defined in the component spec (by name, each containing the sub-properties defined in spec. for it)
	 *    ha: { handler1: { r: "foundsetRef",                                        // return value of handler if it's a converting client side type
	 *                      0: "date", 3: ["JSON_obj", "ct2"], ...}, ... },          // any handler arguments with client side conversion types (by arg no.)
	 *     a: { api1: { r: "foundsetRef",                              // return value of api call if it's a converting client side type
	 *                  1: "ct1", 5: "date", ... }, ... }              // any api call arguments with client side conversion types (by arg no.)
	 * }
	 * </pre>
	 *
	 * NOTE: currently for child 'component' types we just send 'component' - and the type itself will say at runtime in toJSON which kind of child component it is (because components can't be created client side anyway and sent to server - and we avoid some nesting complications in this code).
	 * The same is true for any other type that cannot be created client-side but has some nested types and has client side conversions (nested types are not sent here).
	 */
	public static EmbeddableJSONWriter buildClientSideTypesFor(WebObjectSpecification webObjectSpec)
	{
		Map<String, WebObjectFunctionDefinition> handlers = webObjectSpec.getHandlers();
		Map<String, WebObjectFunctionDefinition> apis = new HashMap<>(webObjectSpec.getApiFunctions());
		apis.putAll(webObjectSpec.getInternalApiFunctions());
		HashSet<String> modelProperties = new HashSet<>(webObjectSpec.getAllPropertiesNames());
		modelProperties.removeAll(handlers.keySet()); // for some reason WebObjectSpecification also returns handlers as property names

		EmbeddableJSONWriter clientSideTypesJSON = new EmbeddableJSONWriter();
		clientSideTypesJSON.object();
		boolean somethingWasWritten = false;

		// check model properties
		somethingWasWritten = writePropertiesWithClientSideConversions(webObjectSpec, modelProperties, clientSideTypesJSON, PROPERTIES_KEY);

		// check for/write any custom object types
		Map<String, PropertyDescription> customObjectTypes = webObjectSpec.getCustomJSONProperties();
		if (customObjectTypes.size() > 0)
		{
			somethingWasWritten = true;
			clientSideTypesJSON.key(FACTORY_TYPE_DETAILS).object().key(CustomJSONObjectType.TYPE_NAME).object();
			for (Entry<String, PropertyDescription> cot : customObjectTypes.entrySet())
			{
				clientSideTypesJSON.key(cot.getKey()).object();
				writePropertiesWithClientSideConversions(cot.getValue(), cot.getValue().getAllPropertiesNames(), clientSideTypesJSON, null);
				clientSideTypesJSON.endObject();
			}
			clientSideTypesJSON.endObject().endObject();
		}

		// check apis
		boolean anyApisWereWritten = false;
		for (Entry<String, WebObjectFunctionDefinition> api : apis.entrySet())
		{
			anyApisWereWritten = writeClientSideConversionsForFunction(clientSideTypesJSON, api.getValue(), api.getKey(), APIS_KEY, anyApisWereWritten);
		}
		if (anyApisWereWritten) clientSideTypesJSON.endObject();

		// check handlers
		boolean anyHandlersWereWritten = false;
		for (Entry<String, WebObjectFunctionDefinition> handler : handlers.entrySet())
		{
			anyHandlersWereWritten = writeClientSideConversionsForFunction(clientSideTypesJSON, handler.getValue(), handler.getKey(), HANDLERS_KEY,
				anyHandlersWereWritten);
		}
		if (anyHandlersWereWritten) clientSideTypesJSON.endObject();

		if (anyApisWereWritten || anyHandlersWereWritten) somethingWasWritten = true;

		clientSideTypesJSON.endObject();

		if (somethingWasWritten) return clientSideTypesJSON;
		else return null;
	}

	private static boolean writeClientSideConversionsForFunction(EmbeddableJSONWriter clientSideTypesJSON, WebObjectFunctionDefinition function,
		String functionName, String addAsObjectWithKey, boolean parentObjectStartWasAlreadyWritten)
	{
		boolean somethingFromFuncWasWritten = false;
		PropertyDescription funcRetType = function.getReturnType();
		if (funcRetType != null && funcRetType.getType() instanceof IPropertyWithClientSideConversions< ? >)
		{
			if (!somethingFromFuncWasWritten)
			{
				if (!parentObjectStartWasAlreadyWritten) clientSideTypesJSON.key(addAsObjectWithKey).object();
				somethingFromFuncWasWritten = true;
				clientSideTypesJSON.key(functionName).object();
			}
			((IPropertyWithClientSideConversions< ? >)funcRetType.getType()).writeClientSideTypeName(clientSideTypesJSON, RETURN_VAL_KEY, funcRetType);
		}

		List<PropertyDescription> parameters = function.getParameters();
		for (int i = 0; i < parameters.size(); i++)
		{
			PropertyDescription paramType = parameters.get(i);
			if (paramType != null && paramType.getType() instanceof IPropertyWithClientSideConversions< ? >)
			{
				if (!somethingFromFuncWasWritten)
				{
					if (!parentObjectStartWasAlreadyWritten) clientSideTypesJSON.key(addAsObjectWithKey).object();
					somethingFromFuncWasWritten = true;
					clientSideTypesJSON.key(functionName).object();
				}
				((IPropertyWithClientSideConversions< ? >)paramType.getType()).writeClientSideTypeName(clientSideTypesJSON, String.valueOf(i), paramType);
			}
		}
		if (somethingFromFuncWasWritten) clientSideTypesJSON.endObject();

		return somethingFromFuncWasWritten || parentObjectStartWasAlreadyWritten;
	}

	private static boolean writePropertiesWithClientSideConversions(PropertyDescription parentPD, Collection<String> modelProperties,
		EmbeddableJSONWriter clientSideTypesJSON, String addAsObjectWithKey)
	{
		boolean anyPropWritten = false;
		for (String prop : modelProperties)
		{
			PropertyDescription propPD = parentPD.getProperty(prop);
			IPropertyType< ? > propType = propPD.getType();
			if (propType instanceof IPropertyWithClientSideConversions< ? >)
			{
				if (!anyPropWritten)
				{
					anyPropWritten = true;
					if (addAsObjectWithKey != null) clientSideTypesJSON.key(addAsObjectWithKey).object();
				}
				((IPropertyWithClientSideConversions< ? >)propType).writeClientSideTypeName(clientSideTypesJSON, prop, propPD); // return value false can't be handled easily here as an undo of .key(addAsObjectWithKey).object() is not easy to do - but there's no harm sending even an empty {} there to client - if no properties need that
			}
		}
		if (anyPropWritten && addAsObjectWithKey != null)
		{
			clientSideTypesJSON.endObject();
		}
		return anyPropWritten;
	}

}
