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
		 * If the given value 'newLevel' is more 'restrictive' then current it will return the 'newLevel'; otherwise it will just return this.
		 * @param newLevel the newLevel to check for.
		 *
		 * Useful for example in nested properties where parent is for example 'deep' and child could be 'shallow' or 'restrict' (TODO do we want this in the future?).
		 *
		 * @return the most restrictive setting of the two.
		 */
		public PushToServerEnum restrictIfNeeded(PushToServerEnum newLevel)
		{
			return (this.compareTo(newLevel) > 0 ? newLevel : this);
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

	private Map<String, IPropertyType< ? >> foundTypes;

	/**
	 * Different then name only for services, not components/layouts.
	 */
	private final String scriptingName;

	private URL serverScript;

	private URL specURL;

	private URL definitionURL;

	private final String preview;

	private boolean supportsGrouping;

	/**
	 * @param packageType one of {@link IPackageReader#WEB_SERVICE}, {@link IPackageReader#WEB_COMPONENT} and {@link IPackageReader#WEB_LAYOUT}.
	 */
	public WebObjectSpecification(String name, String packageName, String packageType, String displayName, String categoryName, String icon, String preview,
		String definition, JSONArray libs)
	{
		this(name, packageName, packageType, displayName, categoryName, icon, preview, definition, libs, null, null);
	}

	public WebObjectSpecification(String name, String packageName, String packageType, String displayName, String categoryName, String icon, String preview,
		String definition, JSONArray libs, Object configObject, Map<String, PropertyDescription> properties)
	{
		super(name, null, configObject, properties, null, null, false, null, null, null, false);
		this.scriptingName = scriptifyNameIfNeeded(name, packageType);
		this.packageName = packageName;
		this.displayName = displayName;
		this.categoryName = categoryName;
		this.icon = icon;
		this.preview = preview;
		this.definition = definition;
		this.libraries = libs != null ? libs : new JSONArray();
		this.foundTypes = new HashMap<>();
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
	public URL getServerScript()
	{
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

	private static ParsedProperty parsePropertyString(final String propertyString, Map<String, IPropertyType< ? >> foundTypes, String specName)
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
	public Map<String, IPropertyType< ? >> getDeclaredCustomObjectTypes()
	{
		return foundTypes;
	}

	public static Map<String, IPropertyType< ? >> getTypes(JSONObject typesContainer) throws JSONException
	{
		return WebObjectSpecification.parseTypes(typesContainer);
	}

	@SuppressWarnings("unchecked")
	public static WebObjectSpecification parseSpec(String specfileContent, String packageName, IPackageReader reader,
		IDefaultComponentPropertiesProvider defaultComponentPropertiesProvider) throws JSONException
	{
		JSONObject json = new JSONObject(specfileContent);

		// first types, can be used in properties
		Map<String, IPropertyType< ? >> types = WebObjectSpecification.parseTypes(json);

		// properties
		Map<String, PropertyDescription> properties = new HashMap<>();
		if (defaultComponentPropertiesProvider != null)
		{
			properties.putAll(defaultComponentPropertiesProvider.getDefaultComponentProperties());
		}
		properties.putAll(WebObjectSpecification.parseProperties("model", json, types, json.getString("name")));

		WebObjectSpecification spec = new WebObjectSpecification(json.getString("name"), packageName, reader != null ? reader.getPackageType() : null,
			json.optString("displayName", null), json.optString("categoryName", null), json.optString("icon", null), json.optString("preview", null),
			json.getString("definition"), json.optJSONArray("libraries"), null, properties);
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
		return spec;
	}

	private static WebObjectFunctionDefinition parseFunctionDefinition(WebObjectSpecification spec, JSONObject api, String func) throws JSONException
	{
		WebObjectFunctionDefinition def = new WebObjectFunctionDefinition(func);
		def.setPropertyDescription(new PropertyDescription(func, TypesRegistry.getType(FunctionPropertyType.TYPE_NAME), api.get(func)));
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
						def.addParameter(new PropertyDescription((String)param.get("name"), propertyType, config, null, null, null, false, null, null, null,
							Boolean.TRUE.equals(param.opt("optional"))));
					}
				}
				else if ("returns".equals(key))
				{
					if (jsonDef.get("returns") instanceof JSONObject)
					{
						JSONObject returnType = jsonDef.getJSONObject("returns");
						ParsedProperty pp = WebObjectSpecification.parsePropertyString(returnType.getString("type"), spec.foundTypes, spec.getName());
						PropertyDescription desc = new PropertyDescription("return", resolveArrayType(pp));
						def.setReturnType(desc);
					}
					else
					{
						ParsedProperty pp = WebObjectSpecification.parsePropertyString(jsonDef.getString("returns"), spec.foundTypes, spec.getName());
						PropertyDescription desc = new PropertyDescription("return", resolveArrayType(pp));
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
					def.setDocumentation(jsonDef.getString("description"));
				}
				else if ("private".equals(key))
				{
					def.setPrivate(jsonDef.getBoolean("private"));
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
			return TypesRegistry.createNewType(CustomJSONArrayType.TYPE_NAME, new PropertyDescription(ARRAY_ELEMENT_PD_NAME, pp.type));
		}
		if (pp.varArgs)
		{
			return TypesRegistry.createNewType(CustomVariableArgsType.TYPE_NAME, new PropertyDescription(ARRAY_ELEMENT_PD_NAME, pp.type));
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
	static Map<String, IPropertyType< ? >> parseTypes(JSONObject json) throws JSONException
	{
		Map<String, IPropertyType< ? >> foundTypes = new HashMap<>();
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
				ICustomType< ? > type = (ICustomType< ? >)foundTypes.get(typeName);
				JSONObject typeJSON = jsonObject.getJSONObject(typeName);
				PropertyDescription pd = new PropertyDescriptionBuilder(specName != null ? (specName + "." + typeName) : typeName, type).putAll(
					typeJSON.has("model") ? parseProperties("model", typeJSON, foundTypes, specName)
						: parseProperties(typeName, jsonObject, foundTypes, specName)).create();
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

		public StandardTypeConfigSettings(Object defaultValue, Object initialValue, boolean hasDefault, PushToServerEnum pushToServer, JSONObject tags,
			List<Object> values)
		{
			this.defaultValue = defaultValue;
			this.initialValue = initialValue;
			this.hasDefault = hasDefault;
			this.pushToServer = pushToServer;
			this.tags = tags;
			this.values = values;
		}

		public StandardTypeConfigSettings()
		{
			this(null, null, false, PushToServerEnum.reject, null, null);
		}

	}

	protected static Map<String, PropertyDescription> parseProperties(String propKey, JSONObject json, Map<String, IPropertyType< ? >> foundTypes,
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
					pds.put(key, new PropertyDescription(key, TypesRegistry.getType(FunctionPropertyType.TYPE_NAME), value));
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
							elementConfig = new JSONObject();
							// for the standard configuration settings in this case - inherit them where it's possible from array (currently that is only pushToServer to make it easier to declare arrays with pushToServer); TODO should we just use defaults always here?
							elementStandardConfigurationSettings = new StandardTypeConfigSettings(null, null, false, standardConfigurationSettings.pushToServer,
								null, null);
						}

						PropertyDescription elementDescription = new PropertyDescription(ARRAY_ELEMENT_PD_NAME, type, type.parseConfig(elementConfig), null,
							elementStandardConfigurationSettings.defaultValue, elementStandardConfigurationSettings.initialValue,
							elementStandardConfigurationSettings.hasDefault, elementStandardConfigurationSettings.values,
							elementStandardConfigurationSettings.pushToServer, elementStandardConfigurationSettings.tags, false);
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
						new PropertyDescription(key, type, type.parseConfig(configObject), null, standardConfigurationSettings.defaultValue,
							standardConfigurationSettings.initialValue, standardConfigurationSettings.hasDefault, standardConfigurationSettings.values,
							standardConfigurationSettings.pushToServer, standardConfigurationSettings.tags, false));
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
		PushToServerEnum pushToServer = PushToServerEnum.reject;
		JSONObject tags = null;
		List<Object> values = null;

		defaultValue = configObject.opt("default");
		initialValue = configObject.opt("initialValue");
		hasDefault = configObject.has("default");

		pushToServer = PushToServerEnum.fromString(configObject.optString(PUSH_TO_SERVER_KEY, pushToServer.name()));
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

		return new StandardTypeConfigSettings(defaultValue, initialValue, hasDefault, pushToServer, tags, values);
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
}
