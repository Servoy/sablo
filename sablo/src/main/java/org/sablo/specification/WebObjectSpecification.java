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
	private final String definition;
	private final JSONArray libraries;
	private final String displayName;
	private final String categoryName;
	private final String icon;
	private final String packageName;

	private final Map<String, IPropertyType< ? >> foundTypes;

	private URL serverScript;

	private URL specURL;

	private URL definitionURL;

	private final String preview;

	private boolean supportsGrouping;

	public WebObjectSpecification(String name, String packageName, String displayName, String categoryName, String icon, String preview, String definition,
		JSONArray libs)
	{
		super(name, null);
		this.packageName = packageName;
		this.displayName = displayName;
		this.categoryName = categoryName;
		this.icon = icon;
		this.preview = preview;
		this.definition = definition;
		this.libraries = libs != null ? libs : new JSONArray();
		this.foundTypes = new HashMap<>();
	}

	public WebObjectSpecification(String name, String packageName, String displayName, String categoryName, String icon, String preview, String definition,
		JSONArray libs, Object configObject)
	{
		super(name, null, configObject);
		this.packageName = packageName;
		this.displayName = displayName;
		this.categoryName = categoryName;
		this.icon = icon;
		this.preview = preview;
		this.definition = definition;
		this.libraries = libs != null ? libs : new JSONArray();
		this.foundTypes = new HashMap<>();
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

	public Map<String, WebObjectFunctionDefinition> getApiFunctions()
	{
		return Collections.unmodifiableMap(apis);
	}

	public String getDisplayName()
	{
		return displayName == null ? getName() : displayName;
	}

	public String getCategoryName()
	{
		return categoryName;
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

	private ParsedProperty parsePropertyString(final String propertyString)
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
		IPropertyType< ? > t = foundTypes.get(property);
		try
		{
			if (t == null) t = TypesRegistry.getType(property);
		}
		catch (RuntimeException e)
		{
			t = ObjectPropertyType.INSTANCE;
			if (!"${dataproviderType}".equals(property))
			{
				String message = "Unknown type name '" + property + "' encountered while parsing spec " + this.getName();
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
		WebObjectSpecification spec = new WebObjectSpecification("", "", "", null, null, null, "", null);
		spec.parseTypes(typesContainer);
		return spec.foundTypes;
	}

	@SuppressWarnings("unchecked")
	public static WebObjectSpecification parseSpec(String specfileContent, String packageName, IPackageReader reader) throws JSONException
	{
		JSONObject json = new JSONObject(specfileContent);

		WebObjectSpecification spec = new WebObjectSpecification(json.getString("name"), packageName, json.optString("displayName", null),
			json.optString("categoryName", null), json.optString("icon", null), json.optString("preview", null), json.getString("definition"),
			json.optJSONArray("libraries"));

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
		// first types, can be used in properties
		spec.parseTypes(json);

		// properties
		spec.putAll(spec.parseProperties("model", json));

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
		spec.setSupportGrouping(json.optBoolean("group", true));
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
							PropertyDescription propertyDescription = spec.parseProperties("", parseJSON).get(param.get("name"));
							propertyType = propertyDescription.getType();
							config = propertyDescription.getConfig();
						}
						else
						{
							ParsedProperty pp = spec.parsePropertyString(param.getString("type"));
							propertyType = resolveArrayType(pp);
							// hmm why not set the array field instead of configObject here?
							config = param;
						}
						def.addParameter(new PropertyDescription((String)param.get("name"), propertyType, config, null, null, false, null, null, null,
							Boolean.TRUE.equals(param.opt("optional"))));
					}
				}
				else if ("returns".equals(key))
				{
					if (jsonDef.get("returns") instanceof JSONObject)
					{
						JSONObject returnType = jsonDef.getJSONObject("returns");
						ParsedProperty pp = spec.parsePropertyString(returnType.getString("type"));
						PropertyDescription desc = new PropertyDescription("return", resolveArrayType(pp));
						def.setReturnType(desc);
					}
					else
					{
						ParsedProperty pp = spec.parsePropertyString(jsonDef.getString("returns"));
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
	void parseTypes(JSONObject json) throws JSONException
	{
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
				if (typeJSON.has("model"))
				{
					// TODO will we really use anything else but model (like api/handlers)? Cause if not, we can just drop the need for "model"
					type.getCustomJSONTypeDefinition().putAll(parseProperties("model", typeJSON));
				}
				else
				{
					// allow custom types to be defined even without the "model" clutter
					type.getCustomJSONTypeDefinition().putAll(parseProperties(typeName, jsonObject));
				}
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
	}

	protected Map<String, PropertyDescription> parseProperties(String propKey, JSONObject json) throws JSONException
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
				Object defaultValue = null;
				Object initialValue = null;
				boolean hasDefault = false;
				PushToServerEnum pushToServer = PushToServerEnum.reject;
				JSONObject tags = null;
				List<Object> values = null;
				ParsedProperty pp = null;
				if (value instanceof String)
				{
					pp = parsePropertyString((String)value);
				}
				else if (value instanceof JSONObject && ((JSONObject)value).has("type"))
				{
					pp = parsePropertyString(((JSONObject)value).getString("type"));
					configObject = ((JSONObject)value);
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
				}
				else if (value instanceof JSONObject && "handlers".equals(propKey))
				{
					pds.put(key, new PropertyDescription(key, TypesRegistry.getType(FunctionPropertyType.TYPE_NAME), value));
				}
				if (pp != null && pp.type != null)
				{
					IPropertyType< ? > type = pp.type;
					if (pp.array || pp.varArgs)
					{
						// here we could have something like { type: 'myprop[]', a: ..., b: ... } so with a config object;
						// the config object will be used by the 'CustomJSONArray' type;
						// a config for the element type can be specified like this: { type: 'myprop[]', a: ..., b: ..., elementConfig: {...} } and we could give that to the elementDescription instead
						JSONObject elementConfig = configObject != null && configObject.optJSONObject(CustomJSONArrayType.ELEMENT_CONFIG_KEY) != null
							? configObject.optJSONObject(CustomJSONArrayType.ELEMENT_CONFIG_KEY) : new JSONObject();
						PropertyDescription elementDescription = new PropertyDescription(ARRAY_ELEMENT_PD_NAME, type, type.parseConfig(elementConfig),
							defaultValue, initialValue, hasDefault, values, pushToServer, tags, false);
						if (pp.array)
						{
							type = TypesRegistry.createNewType(CustomJSONArrayType.TYPE_NAME, elementDescription);
						}
						else
						{
							type = TypesRegistry.createNewType(CustomVariableArgsType.TYPE_NAME, elementDescription);
						}
					}

					pds.put(key, new PropertyDescription(key, type, type.parseConfig(configObject), defaultValue, initialValue, hasDefault, values,
						pushToServer, tags, false));
				}
			}
		}
		return pds;
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
