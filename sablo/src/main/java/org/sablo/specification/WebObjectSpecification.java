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

package org.sablo.specification;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.CustomPropertyTypeResolver;
import org.sablo.specification.property.CustomVariableArgsType;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.types.BooleanPropertyType;
import org.sablo.specification.property.types.FunctionPropertyType;
import org.sablo.specification.property.types.ObjectPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.sablo.websocket.impl.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse .spec files for components/services (web objects).
 * @author rgansevles
 */
public class WebObjectSpecification extends PropertyDescription
{
	/**
	 * Property descriptions that are array element property descriptions will have this name.
	 */
	public static final String ARRAY_ELEMENT_PD_NAME = ""; //$NON-NLS-1$

	private static final Logger log = LoggerFactory.getLogger(WebObjectSpecification.class.getCanonicalName());

	public static final String TYPES_KEY = "types";

	public final static String PUSH_TO_SERVER_KEY = "pushToServer";

	public final static String ALLOW_ACCESS = "allowaccess";

	public enum PushToServerEnum
	{

		// keep them ordered (in code checks for < and > can be used; so for example allow.compareTo(pushToServer) <= 0  can be used to check that a property can change on server due to client change)

		reject, // default, throw exception when updates are pushed to server
		allow, // allow changes, not default implementation in sablo
		shallow, // allow changes, implementation in sablo by creating watcher on client with objectEquality = false
		deep // allow changes, implementation in sablo by creating watcher on client with objectEquality = true
		;

		public static PushToServerEnum fromString(String s)
		{
			return s == null ? null : valueOf(s);
		}

		/**
		 * .spec file can define "pushToServer" setting for each property. But for nested properties (can be multiple levels so that for example
		 * a custom object type can be in two completely different places with parents having different pushToServer levels) we want that setting to be
		 * enforced on all children of that property.
		 *
		 * So if the root property is defined as 'reject' then all nested properties will be reject as well.
		 * Children can only restrict parent's push to server level; if child doesn't specify a push to server it will inherit it from parent.
		 * If no push to server is specified on any of the parents and child the default will be "reject".
		 *
		 * For example ("rest" means rest of available combinations that are not explicitly specified in the table below, * means whatever value, set or not).
		 * Parent pushToServer is of course the computed value that takes into acount already all the parents of parent:
		 *
		 * <pre>
		 * PARENT      CHILD       RESULT ON CHILD
		 * ----------------------------------------
		 * unset       unset       reject (default)           this actually can't happen in this method as parent is the 'this' object
		 * unset       The_Rest    same as CHILD              this actually can't happen in this method as parent is the 'this' object
		 *
		 * reject      *           reject
		 *
		 * *           reject      reject
		 *
		 * allow       unset       allow
		 * allow       The_Rest    same as CHILD
		 *
		 * shallow     unset       shallow
		 * shallow     The_Rest    same as CHILD
		 *
		 * deep        unset       deep                                  "deep" is for random untyped JSON object values only (so "object" type); but if we get this called here
		 *                                                               it means that smart types (custom object/array) are marked as deep in spec; being smart types,
		 *                                                               they won't add deep angulat watches directly on themselves, only on their dumb child values maybe; so
		 *                                                               propagate the deep value to children anyway, even though it will not actually generate deep angulat
		 *                                                               watches for smart nested structures (which would behave similar to 'shallow' then except for dumb leafes)
		 * deep        The_Rest    same as CHILD
		 * </pre>
		 *
		 * Note: parent can NEVER be unset (null) in the table above. If a root property doesn't have a pushToServer values set in .spec file it is then considered 'reject' by default.
		 *
		 * We cannot store this in the PropertyDescription objects only and use it from there because the same custom/nested type (so PropertyDescription)
		 * can be used in multiple subtrees in properties in the spec file, sometimes with pushToServer 'reject' and sometimes with pushToServer 'shallow'
		 * for example somewhere in parents. So the same PropertyDescription's sub-properties can sometimes be 'reject' sometimes 'shallow' etc.
		 *
		 * @return the computed PushToServerEnum value for child sub-property, where this obj. is a parent computed pushToServer and "childDeclaredPushToServer" param is child declared pushToServer (as specified in the spec file).
		 */
		public PushToServerEnum combineWithChild(PushToServerEnum childDeclaredPushToServer)
		{
			PushToServerEnum computed;

			if (this == reject || childDeclaredPushToServer == reject) computed = reject;
			else
			{
				if (childDeclaredPushToServer == null) computed = this;
				else computed = childDeclaredPushToServer;

				if (this == deep) log.info(
					"One of your web component/service .spec files declares a parent property (that has child properties in spec, so an custom array, custom object...) as \"pushToServer\": \"deep\"; this will work but these smart types will not generate angular deep watches but will watch all child properties on each level like they do if shallow is used as well - in order to send granular updates; only dumb child values will actually be deep watched. \"deep\" is meant to be used with \"object\" type where there is just some random JSON content in it, nested or not...");
			}

			return computed;
		}

	}

	private final Map<String, WebObjectFunctionDefinition> handlers = new HashMap<>(); // second String is always a "function" for now, but in the future it will probably contain more (to specify sent args/types...)
	private final Map<String, WebObjectFunctionDefinition> apis = new HashMap<>();
	private final Map<String, WebObjectFunctionDefinition> internalApis = new HashMap<>();
	private final String definition;
	private final JSONArray libraries;
	private final String displayName;
	private final String categoryName;
	private final String icon;
	private final String packageName;
	private final JSONArray keywords;
	private final JSONObject dependencies;

	private Map<String, ICustomType< ? >> foundTypes;
	private static IPackageReader reader;

	/**
	 * Different then name only for services, not components/layouts.
	 */
	private final String scriptingName;

	private URL serverScript;

	private URL specURL;

	private URL definitionURL;

	private final String preview;

	private boolean supportsGrouping;

	private String replacement = null;

	/**
	 * @param packageType one of {@link IPackageReader#WEB_SERVICE}, {@link IPackageReader#WEB_COMPONENT} and {@link IPackageReader#WEB_LAYOUT}.
	 */

	protected WebObjectSpecification(String name, String packageName, String packageType, String displayName, String categoryName, String icon, String preview,
		String definition, JSONArray libs, JSONArray keywords, JSONObject dependencies)
	{
		this(name, packageName, packageType, displayName, categoryName, icon, preview, definition, libs, null, null, null, keywords, dependencies);
	}


	WebObjectSpecification(String name, String packageName, String packageType, String displayName, String categoryName, String icon, String preview,
		String definition, JSONArray libs, Object configObject, Map<String, PropertyDescription> properties, String deprecated, JSONArray keywords,
		JSONObject dependencies)
	{
		super(name, null, configObject, properties, null, null, false, null, null, null, false, deprecated);
		this.scriptingName = scriptifyNameIfNeeded(name, packageType);
		this.packageName = packageName;
		this.displayName = displayName;
		this.categoryName = categoryName;
		this.icon = icon;
		this.preview = preview;
		this.definition = definition;
		this.libraries = libs != null ? libs : new JSONArray();
		this.foundTypes = new HashMap<>();
		this.keywords = keywords != null ? keywords : new JSONArray();
		this.dependencies = dependencies != null ? dependencies : new JSONObject();
	}

	protected String scriptifyNameIfNeeded(String name, String packageType)
	{
		String scriptingN = name;
		if (scriptingN != null && IPackageReader.WEB_SERVICE.equals(packageType))
		{
			scriptingN = ClientService.convertToJSName(scriptingN);
		} // else other types (components/layouts don't get their scope on client in the same way and work directly with "name")
		return scriptingN;
	}

	/**
	 * @param serverScript the serverScript to set
	 */
	public void setServerScript(URL serverScript)
	{
		this.serverScript = serverScript;
	}

	/**
	 * @return
	 */
	public URL getServerScript(boolean fromDependencies)
	{
		if (fromDependencies && this.dependencies != null && !this.dependencies.isNull("serverscript")) try
		{
			return WebObjectSpecification.reader.getUrlForPath(dependencies.optString("serverscript").substring(packageName.length()));
		}
		catch (MalformedURLException e)
		{
			e.printStackTrace();
		}
		return serverScript;
	}


	protected final void addApiFunction(WebObjectFunctionDefinition apiFunction)
	{
		apis.put(apiFunction.getName(), apiFunction);
	}

	protected final void addInternalApiFunction(WebObjectFunctionDefinition apiFunction)
	{
		internalApis.put(apiFunction.getName(), apiFunction);
	}

	protected final void addHandler(WebObjectFunctionDefinition propertyDescription)
	{
		handlers.put(propertyDescription.getName(), propertyDescription);
	}

	/**
	 * @param hndlrs
	 */
	protected final void putAllHandlers(Map<String, WebObjectFunctionDefinition> hndlrs)
	{
		handlers.putAll(hndlrs);
	}

	/**
	 * You are not allowed to modify this map!
	 */
	public Map<String, WebObjectFunctionDefinition> getHandlers()
	{
		return Collections.unmodifiableMap(handlers);
	}

	public WebObjectFunctionDefinition getHandler(String handlerName)
	{
		return handlers.get(handlerName);
	}

	public WebObjectFunctionDefinition getApiFunction(String apiFunctionName)
	{
		return apis.get(apiFunctionName);
	}

	public WebObjectFunctionDefinition getInternalApiFunction(String apiFunctionName)
	{
		return internalApis.get(apiFunctionName);
	}

	public Map<String, WebObjectFunctionDefinition> getApiFunctions()
	{
		return Collections.unmodifiableMap(apis);
	}

	public Map<String, WebObjectFunctionDefinition> getInternalApiFunctions()
	{
		return Collections.unmodifiableMap(internalApis);
	}

	public String getDisplayName()
	{
		return displayName == null ? getName() : displayName;
	}

	public String getCategoryName()
	{
		return categoryName;
	}

	/**
	 * This is the name used in client side scripting (module name, for services service scope and factory name...).
	 */
	public String getScriptingName()
	{
		return scriptingName;
	}

	public String getIcon()
	{
		return icon;
	}

	public String getPreview()
	{
		return preview;
	}

	public String getPackageName()
	{
		return packageName;
	}

	public String getDefinition()
	{
		return definition;
	}

	@Override
	public Set<String> getAllPropertiesNames()
	{
		Set<String> names = new HashSet<>(super.getAllPropertiesNames());
		names.addAll(handlers.keySet());
		return names;
	}

	public JSONArray getLibraries()
	{
		return libraries;
	}

	private static ParsedProperty parsePropertyString(final String propertyString, Map<String, ? extends IPropertyType< ? >> foundTypes, String specName)
	{
		String property = propertyString.replaceAll("\\s", "");
		boolean isArray = false;
		boolean isVarArgs = false;
		if (property.endsWith("[]"))
		{
			isArray = true;
			property = property.substring(0, property.length() - 2);
		}
		else if (property.endsWith("..."))
		{
			isVarArgs = true;
			property = property.substring(0, property.length() - 3);
		}
		// first check the local ones.
		IPropertyType< ? > t = foundTypes != null ? foundTypes.get(property) : null;
		try
		{
			if (t == null) t = TypesRegistry.getType(property);
		}
		catch (RuntimeException e)
		{
			t = ObjectPropertyType.INSTANCE;
			if (!"${dataproviderType}".equals(property))
			{
				String message = "Unknown type name '" + property + "' encountered while parsing spec " + specName;
				log.warn(message);
				System.err.println(message);
			}
		}
		return new ParsedProperty(t, isArray, isVarArgs);
	}

	/**
	 * @return the types parsed from the "types" attribute.
	 */
	public Map<String, ICustomType< ? >> getDeclaredCustomObjectTypes()
	{
		return foundTypes;
	}

	public static Map<String, ICustomType< ? >> getTypes(JSONObject typesContainer) throws JSONException
	{
		return WebObjectSpecification.parseTypes(typesContainer);
	}

	@SuppressWarnings("unchecked")
	public static WebObjectSpecification parseSpec(String specfileContent, String packageName, IPackageReader reader,
		IDefaultComponentPropertiesProvider defaultComponentPropertiesProvider) throws JSONException
	{
		JSONObject json = new JSONObject(specfileContent);
		WebObjectSpecification.reader = reader;

		// first types, can be used in properties
		Map<String, ICustomType< ? >> types = WebObjectSpecification.parseTypes(json);

		// properties
		Map<String, PropertyDescription> properties = new HashMap<>();
		if (defaultComponentPropertiesProvider != null)
		{
			properties.putAll(defaultComponentPropertiesProvider.getDefaultComponentProperties());
		}
		properties.putAll(WebObjectSpecification.parseProperties("model", json, types, json.getString("name")));

		WebObjectSpecification spec = new WebObjectSpecificationBuilder().withPackageName(packageName).withPackageType(
			reader != null ? reader.getPackageType() : null).withDisplayName(json.optString("displayName", null)).withCategoryName(
				json.optString("categoryName", null))
			.withIcon(json.optString("icon", null)).withPreview(json.optString("preview", null)).withDefinition(
				json.getString("definition"))
			.withLibraries(json.optJSONArray("libraries")).withProperties(properties).withName(
				json.getString("name"))
			.withDeprecated(json.optString("deprecated", null)).withKeywords(json.optJSONArray("keywords")).withDependencies(json.optJSONObject("dependencies"))
			.build();
		spec.foundTypes = types;
		if (json.has("serverscript"))
		{
			try
			{
				spec.setServerScript(reader.getUrlForPath(json.getString("serverscript").substring(packageName.length())));
			}
			catch (MalformedURLException e)
			{
				log.error("Error getting serverscript", e);
			}
		}
		//handlers
		if (json.has("handlers"))
		{
			JSONObject api = json.getJSONObject("handlers");
			Iterator<String> itk = api.keys();
			while (itk.hasNext())
			{
				WebObjectFunctionDefinition def = parseFunctionDefinition(spec, api, itk.next());
				spec.addHandler(def);
			}
		}

		// api
		if (json.has("api"))
		{
			JSONObject api = json.getJSONObject("api");
			Iterator<String> itk = api.keys();
			while (itk.hasNext())
			{
				WebObjectFunctionDefinition def = parseFunctionDefinition(spec, api, itk.next());
				spec.addApiFunction(def);
			}
		}
		//internal api
		if (json.has("internalApi"))
		{
			JSONObject api = json.getJSONObject("internalApi");
			Iterator<String> itk = api.keys();
			while (itk.hasNext())
			{
				WebObjectFunctionDefinition def = parseFunctionDefinition(spec, api, itk.next());
				spec.addInternalApiFunction(def);
			}
		}
		spec.setSupportGrouping(json.has("group") ? json.optBoolean("group", true) : true);
		spec.setReplacement(json.optString("replacement", null));
		return spec;
	}

	private static WebObjectFunctionDefinition parseFunctionDefinition(WebObjectSpecification spec, JSONObject api, String func) throws JSONException
	{
		WebObjectFunctionDefinition def = new WebObjectFunctionDefinition(func);
		def.setPropertyDescription(
			new PropertyDescriptionBuilder().withName(func).withType(TypesRegistry.getType(FunctionPropertyType.TYPE_NAME)).withConfig(api.get(func)).build());
		if (api.get(func) instanceof JSONObject)
		{
			JSONObject jsonDef = api.getJSONObject(func);
			Iterator<String> it = jsonDef.keys();
			JSONObject customConfiguration = null;
			while (it.hasNext())
			{
				String key = it.next();
				if ("parameters".equals(key))
				{
					JSONArray params = jsonDef.getJSONArray("parameters");
					for (int p = 0; p < params.length(); p++)
					{
						JSONObject param = params.getJSONObject(p);

						IPropertyType< ? > propertyType;
						Object config;
						if (param.optJSONObject("type") != null)
						{
							JSONObject paramJSON = new JSONObject();
							paramJSON.put((String)param.get("name"), param.get("type"));
							JSONObject parseJSON = new JSONObject();
							parseJSON.put("", paramJSON);
							PropertyDescription propertyDescription = WebObjectSpecification.parseProperties("", parseJSON, spec.foundTypes,
								spec.getName()).get(param.get("name"));
							propertyType = propertyDescription.getType();
							config = propertyDescription.getConfig();
						}
						else
						{
							ParsedProperty pp = WebObjectSpecification.parsePropertyString(param.getString("type"), spec.foundTypes, spec.getName());
							propertyType = resolveArrayType(pp);
							config = propertyType.parseConfig(null);
						}
						def.addParameter(
							new PropertyDescriptionBuilder().withName((String)param.get("name")).withType(propertyType).withConfig(config).withOptional(
								Boolean.TRUE.equals(param.opt("optional"))).build());
					}
				}
				else if ("returns".equals(key))
				{
					if (jsonDef.get("returns") instanceof JSONObject)
					{
						JSONObject returnType = jsonDef.getJSONObject("returns");
						ParsedProperty pp = WebObjectSpecification.parsePropertyString(returnType.getString("type"), spec.foundTypes, spec.getName());
						PropertyDescription desc = new PropertyDescriptionBuilder().withName("return").withType(resolveArrayType(pp)).build();
						def.setReturnType(desc);
					}
					else
					{
						ParsedProperty pp = WebObjectSpecification.parsePropertyString(jsonDef.getString("returns"), spec.foundTypes, spec.getName());
						PropertyDescription desc = new PropertyDescriptionBuilder().withName("return").withType(resolveArrayType(pp)).build();
						def.setReturnType(desc);
					}
				}
				else if ("blockEventProcessing".equals(key))
				{
					def.setBlockEventProcessing(jsonDef.getBoolean("blockEventProcessing"));
				}
				else if ("delayUntilFormLoad".equals(key) || "delayUntilFormLoads".equals(key)) // first one is deprecated but still usable
				{
					def.setDelayUntilFormLoads(jsonDef.getBoolean(key));
				}
				else if ("async".equals(key))
				{
					def.setAsync(jsonDef.getBoolean("async"));
				}
				else if ("async-now".equals(key))
				{
					def.setAsyncNow(jsonDef.getBoolean("async-now"));
				}
				else if ("globalExclusive".equals(key) || "discardPreviouslyQueuedSimilarCalls".equals(key)) // first one is deprecated but still usable
				{
					def.setDiscardPreviouslyQueuedSimilarCalls(jsonDef.getBoolean(key));
				}
//				else if ("waitsForUserAction".equals(key))
//				{
//					def.setWaitsForUserAction(jsonDef.getBoolean("waitsForUserAction"));
//				}
				else if ("description".equals(key))
				{
					// deprecated, replaced by "doc" (DOCUMENTATION_TAG_FOR_PROP_OR_KEY_FOR_HANDLERS) below to match the properties "doc" tag
					def.setDocumentation(jsonDef.getString("description"));
				}
				else if (DOCUMENTATION_TAG_FOR_PROP_OR_KEY_FOR_HANDLERS.equals(key))
				{
					def.setDocumentation(jsonDef.getString(DOCUMENTATION_TAG_FOR_PROP_OR_KEY_FOR_HANDLERS));
				}
				else if ("private".equals(key))
				{
					def.setPrivate(jsonDef.getBoolean("private"));
				}
				else if ("deprecated".equals(key))
				{
					def.setDeprecated(jsonDef.optString(key));
				}
				else if (ALLOW_ACCESS.equals(key))
				{
					def.setAllowAccess(jsonDef.getString(key));
				}
				else if ("ignoreNGBlockDuplicateEvents".equals(key))
				{
					def.setIgnoreNGBlockDuplicateEvents(jsonDef.getBoolean(key));
				}
				else
				{
					if (customConfiguration == null) customConfiguration = new JSONObject();
					customConfiguration.put(key, jsonDef.get(key));
				}
			}
			if (customConfiguration != null) def.setCustomConfigOptions(customConfiguration);
		}
		return def;
	}

	private static IPropertyType< ? > resolveArrayType(ParsedProperty pp)
	{
		if (pp.array)
		{
			return TypesRegistry.createNewType(CustomJSONArrayType.TYPE_NAME,
				new PropertyDescriptionBuilder().withName(ARRAY_ELEMENT_PD_NAME).withType(pp.type).build());
		}
		if (pp.varArgs)
		{
			return TypesRegistry.createNewType(CustomVariableArgsType.TYPE_NAME,
				new PropertyDescriptionBuilder().withName(ARRAY_ELEMENT_PD_NAME).withType(pp.type).build());
		}
		return pp.type;
	}

	/**
	 * Parses json spec object for declared custom types; custom type will be stored prefixed by spec name (if available)
	 *
	 * @param json JSON to parse for custom types;
	 *
	 * @throws JSONException
	 */
	static Map<String, ICustomType< ? >> parseTypes(JSONObject json) throws JSONException
	{
		Map<String, ICustomType< ? >> foundTypes = new HashMap<>();
		String specName = json.optString("name", null);
		if (json.has(TYPES_KEY))
		{
			JSONObject jsonObject = json.getJSONObject(TYPES_KEY);
			// first create all types
			Iterator<String> types = jsonObject.keys();
			while (types.hasNext())
			{
				String name = types.next();
				ICustomType< ? > wct = CustomPropertyTypeResolver.getInstance().resolveCustomPropertyType(specName != null ? (specName + "." + name) : name);
				foundTypes.put(name, wct);
			}

			// then parse all the types (so that they can find each other)
			types = jsonObject.keys();
			while (types.hasNext())
			{
				String typeName = types.next();
				ICustomType< ? > type = foundTypes.get(typeName);
				JSONObject typeJSON = jsonObject.getJSONObject(typeName);
				PropertyDescription pd = new PropertyDescriptionBuilder().withName(specName != null ? (specName + "." + typeName) : typeName).withType(
					type).withProperties(
						typeJSON.has("model") ? parseProperties("model", typeJSON, foundTypes, specName)
							: parseProperties(typeName, jsonObject, foundTypes, specName))
					.build();
				type.setCustomJSONDefinition(pd);
				// TODO this is currently never true? See 5 lines above this, types are always just PropertyDescription?
				// is this really supported? or should we add it just to the properties? But how are these handlers then added and used
//				if (type instanceof WebObjectSpecification)
//				{
//					((WebObjectSpecification)type).putAllHandlers(parseProperties("handlers", jsonObject.getJSONObject(typeName)));
//				}
//				if (type instanceof WebObjectSpecification)
//				{
//					if (jsonObject.getJSONObject(typeName).has("handlers"))
//					{
//						JSONObject handlersJson = jsonObject.getJSONObject(typeName).getJSONObject("handlers");
//						Iterator<String> itk = handlersJson.keys();
//						while (itk.hasNext())
//						{
//							WebObjectFunctionDefinition def = parseFunctionDefinition(((WebObjectSpecification)type), handlersJson, itk.next());
//							((WebObjectSpecification)type).addHandler(def);
//						}
//					}
//				}
			}
		}
		return foundTypes;
	}

	private static class StandardTypeConfigSettings
	{
		public final Object defaultValue;
		public final Object initialValue;
		public final boolean hasDefault;
		public final PushToServerEnum pushToServer;
		public final JSONObject tags;
		public final List<Object> values;
		private final String deprecated;

		public StandardTypeConfigSettings(Object defaultValue, Object initialValue, boolean hasDefault, PushToServerEnum pushToServer, JSONObject tags,
			List<Object> values, String deprecated)
		{
			this.defaultValue = defaultValue;
			this.initialValue = initialValue;
			this.hasDefault = hasDefault;
			this.pushToServer = pushToServer;
			this.tags = tags;
			this.values = values;
			this.deprecated = deprecated;
		}

		public StandardTypeConfigSettings()
		{
			this(null, null, false, null, null, null, null);
		}

	}

	protected static Map<String, PropertyDescription> parseProperties(String propKey, JSONObject json, Map<String, ? extends IPropertyType< ? >> foundTypes,
		String specName) throws JSONException
	{
		Map<String, PropertyDescription> pds = new HashMap<>();
		if (json.has(propKey))
		{
			JSONObject jsonProps = json.getJSONObject(propKey);
			Iterator<String> itk = jsonProps.keys();
			while (itk.hasNext())
			{
				String key = itk.next();
				Object value = jsonProps.get(key);

				JSONObject configObject = null;
				ParsedProperty pp = null;
				StandardTypeConfigSettings standardConfigurationSettings = null;
				if (value instanceof String)
				{
					pp = parsePropertyString((String)value, foundTypes, specName);
					standardConfigurationSettings = new StandardTypeConfigSettings();
				}
				else if (value instanceof JSONObject && ((JSONObject)value).has("type"))
				{
					pp = parsePropertyString(((JSONObject)value).getString("type"), foundTypes, specName);
					configObject = ((JSONObject)value);
					standardConfigurationSettings = parseStandardConfigurationSettings(configObject);
				}
				else if (value instanceof JSONObject && "handlers".equals(propKey))
				{
					pds.put(key, new PropertyDescriptionBuilder().withName(key).withType(TypesRegistry.getType(FunctionPropertyType.TYPE_NAME)).withConfig(
						value).build());
				}
				else if (value instanceof Boolean && "deprecated".equals(key))
				{
					pds.put(key, new PropertyDescriptionBuilder().withName(key).withType(TypesRegistry.getType(BooleanPropertyType.TYPE_NAME)).withConfig(
						value).build());
				}

				if (pp != null && pp.type != null /* && standardConfigurationSettings != null -- is implied by pp != null -- */)
				{
					IPropertyType< ? > type = pp.type;
					if (pp.array || pp.varArgs)
					{
						// here we could have something like { type: 'myprop[]', a: ..., b: ... } so with a config object;
						// the config object will be used by the 'CustomJSONArray' type;
						// a config for the element type can be specified like this: { type: 'myprop[]', a: ..., b: ..., elementConfig: {...} } and we could give that to the elementDescription instead
						JSONObject elementConfig;
						StandardTypeConfigSettings elementStandardConfigurationSettings;
						if (configObject != null && configObject.optJSONObject(CustomJSONArrayType.ELEMENT_CONFIG_KEY) != null)
						{
							elementConfig = configObject.optJSONObject(CustomJSONArrayType.ELEMENT_CONFIG_KEY);
							elementStandardConfigurationSettings = parseStandardConfigurationSettings(elementConfig);
						}
						else
						{
							// use nothing/defaults
							elementConfig = new JSONObject();
							elementStandardConfigurationSettings = new StandardTypeConfigSettings();
						}

						PropertyDescription elementDescription = new PropertyDescriptionBuilder().withName(ARRAY_ELEMENT_PD_NAME).withType(type).withConfig(
							type.parseConfig(elementConfig)).withDefaultValue(elementStandardConfigurationSettings.defaultValue).withInitialValue(
								elementStandardConfigurationSettings.initialValue)
							.withHasDefault(elementStandardConfigurationSettings.hasDefault).withValues(
								elementStandardConfigurationSettings.values)
							.withPushToServer(elementStandardConfigurationSettings.pushToServer).withTags(
								elementStandardConfigurationSettings.tags)
							.build();
						if (pp.array)
						{
							type = TypesRegistry.createNewType(CustomJSONArrayType.TYPE_NAME, elementDescription);
						}
						else
						{
							type = TypesRegistry.createNewType(CustomVariableArgsType.TYPE_NAME, elementDescription);
						}
					}

					pds.put(key,
						new PropertyDescriptionBuilder().withName(key).withType(type).withConfig(type.parseConfig(configObject)).withDefaultValue(
							standardConfigurationSettings.defaultValue).withInitialValue(standardConfigurationSettings.initialValue).withHasDefault(
								standardConfigurationSettings.hasDefault)
							.withValues(standardConfigurationSettings.values).withPushToServer(
								standardConfigurationSettings.pushToServer)
							.withTags(standardConfigurationSettings.tags).withDeprecated(
								standardConfigurationSettings.deprecated)
							.build());
				}
			}
		}
		return pds;
	}

	private static StandardTypeConfigSettings parseStandardConfigurationSettings(JSONObject configObject)
	{
		Object defaultValue = null;
		Object initialValue = null;
		boolean hasDefault = false;
		PushToServerEnum pushToServer = null;
		JSONObject tags = null;
		List<Object> values = null;
		String deprecated = configObject.optString("deprecated", null);

		defaultValue = configObject.opt("default");
		initialValue = configObject.opt("initialValue");
		hasDefault = configObject.has("default");

		String pushToServerString = configObject.optString(PUSH_TO_SERVER_KEY, null);
		if (pushToServerString != null) pushToServer = PushToServerEnum.fromString(pushToServerString);
		tags = configObject.optJSONObject("tags");

		JSONArray valuesArray = configObject.optJSONArray("values");
		if (valuesArray != null)
		{
			values = new ArrayList<Object>(valuesArray.length());
			for (int i = 0; i < valuesArray.length(); i++)
			{
				values.add(valuesArray.get(i));
			}
		}

		return new StandardTypeConfigSettings(defaultValue, initialValue, hasDefault, pushToServer, tags, values, deprecated);
	}

	@Override
	public String toString()
	{
		return getName();
	}

	private static class ParsedProperty
	{
		private final IPropertyType type;
		private final boolean array;
		private final boolean varArgs;

		ParsedProperty(IPropertyType type, boolean array, boolean varArgs)
		{
			this.type = type;
			this.array = array;
			this.varArgs = varArgs;
		}
	}

	/**
	 * Get the location of the specFile inside.
	 * @return
	 */
	public URL getSpecURL()
	{
		return specURL;
	}

	public void setSpecURL(URL url)
	{
		specURL = url;
	}

	public void setDefinitionFileURL(URL url)
	{
		definitionURL = url;
	}

	public URL getDefinitionURL()
	{
		return definitionURL;
	}

	public void setSupportGrouping(boolean supportsGrouping)
	{
		this.supportsGrouping = supportsGrouping;
	}

	public boolean supportGrouping()
	{
		return supportsGrouping;
	}

	public String getReplacement()
	{
		return replacement;
	}

	public void setReplacement(String replacement)
	{
		this.replacement = replacement;
	}

	@Override
	public boolean isDeprecated()
	{
		return replacement != null && !"".equals("replacement") || super.isDeprecated();
	}

	@Override
	public String getDeprecatedMessage()
	{
		return (replacement != null && !"".equals("replacement") ? " Use '" + replacement + "'. " : "") + super.getDeprecatedMessage();
	}

	public JSONArray getKeywords()
	{
		return keywords;
	}

	public JSONObject getDependencies()
	{
		return dependencies;
	}
}
