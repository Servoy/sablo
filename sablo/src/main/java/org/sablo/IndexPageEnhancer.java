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

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PackageSpecification;
import org.sablo.specification.SpecProviderState;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebLayoutSpecification;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Take an index page, enhance it with required libs/csses and replace variables
 * @author jblok
 */
@SuppressWarnings("nls")
public class IndexPageEnhancer
{
	/**
	 * Token in html page after which we add component contributions. They have to be before the solution stylesheet.
	 */
	private static final String COMPONENT_CONTRIBUTIONS = "<!-- component_contributions -->";

	private static final Logger log = LoggerFactory.getLogger(IndexPageEnhancer.class.getCanonicalName());

	private static String VAR_START = "##";
	private static String VAR_END = "##";

	private IndexPageEnhancer()
	{
	}

	/**
	 * Enhance the provided index.html
	 * @param resource url to index.html
	 * @param request servlet request
	 * @param cssContributions possible css contributions
	 * @param jsContributions possible js contributions
	 * @param variableSubstitution replace variables
	 * @param writer the writer to write to
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public static void enhance(URL resource, HttpServletRequest request, Collection<String> cssContributions, Collection<String> jsContributions,
		Collection<String> extraMetaData, Map<String, Object> variableSubstitution, Writer writer, IContributionFilter contributionFilter,
		IContributionEntryFilter contributionEntryFilter, String contentSecurityPolicyNonce) throws IOException
	{
		String index_file = IOUtils.toString(resource, "UTF-8");

		//use real html parser here instead?
		if (variableSubstitution != null)
		{
			for (Entry<String, Object> entry : variableSubstitution.entrySet())
			{
				String value;
				if (entry.getValue() == null || entry.getValue() instanceof Number)
				{
					value = String.valueOf(entry.getValue());
				}
				else
				{
					value = Matcher.quoteReplacement(TextUtils.escapeForDoubleQuotedJavascript(entry.getValue().toString()));
				}

				index_file = index_file.replaceAll(VAR_START + entry.getKey() + VAR_END, value);
			}
		}

		Object[] allContributions = getAllContributions(null, contributionEntryFilter);
		List<String> allCSSContributions = (List<String>)allContributions[0];
		List<String> allJSContributions = (List<String>)allContributions[1];

		StringBuilder sb = new StringBuilder(index_file);

		int componentContributionsIndex = sb.toString().indexOf(COMPONENT_CONTRIBUTIONS);
		if (componentContributionsIndex < 0)
		{
			log.warn("Could not find marker for component contributions: " + COMPONENT_CONTRIBUTIONS + " for resource " + resource);
		}
		else
		{
			sb.insert(componentContributionsIndex + COMPONENT_CONTRIBUTIONS.length(), getAllContributions(cssContributions, jsContributions, extraMetaData,
				contributionFilter, allCSSContributions, allJSContributions, contentSecurityPolicyNonce));
		}
		writer.append(sb);
	}

	public static Object[] getAllContributions(Boolean supportGrouping, IContributionEntryFilter ceFilter)
	{
		return getAllContributions(null, null, supportGrouping, ceFilter);
	}

	/**
	 * Gets all JS and CSS contributions.
	 * @param exportedPackages
	 * @param set2
	 * @param set
	 * @param supportGrouping Boolean; if TRUE returns the contributions which support grouping,
	 * 								   if FALSE returns the contributions which do not support grouping
	 * 								   if NULL returns all contributions
	 * @return an object array which has as a first element the collection of css contributions, and as
	 * the second element the collection of the js contributions.
	 */
	public static Object[] getAllContributions(Set<String> exportedWebObjects, Set<String> exportedPackages, Boolean supportGrouping,
		IContributionEntryFilter ceFilter)
	{
		ArrayList<String> allCSSContributions = new ArrayList<String>();
		ArrayList<String> allJSContributions = new ArrayList<String>();

		LinkedHashMap<String, JSONObject> allLibraries = new LinkedHashMap<>();
		Collection<PackageSpecification< ? extends WebObjectSpecification>> webComponentPackagesDescriptions = new ArrayList<>();
		SpecProviderState componentSpecProviderState = WebComponentSpecProvider.getSpecProviderState();

		Collection<PackageSpecification<WebLayoutSpecification>> layoutPackageSpecifications;
		webComponentPackagesDescriptions.addAll(layoutPackageSpecifications = componentSpecProviderState.getLayoutSpecifications().values());
		webComponentPackagesDescriptions.addAll(componentSpecProviderState.getWebObjectSpecifications().values());
		webComponentPackagesDescriptions.addAll(WebServiceSpecProvider.getSpecProviderState().getWebObjectSpecifications().values());

		for (PackageSpecification< ? extends WebObjectSpecification> packageDesc : webComponentPackagesDescriptions)
		{
			if (exportedPackages != null && !exportedPackages.contains(packageDesc.getPackageName())) continue;
			if (packageDesc.getCssClientLibrary() != null)
			{
				mergeLibs(allLibraries, packageLibsToJSON(packageDesc.getCssClientLibrary(), "text/css"), supportGrouping);
			}
			if (packageDesc.getJsClientLibrary() != null)
			{
				mergeLibs(allLibraries, packageLibsToJSON(packageDesc.getJsClientLibrary(), "text/javascript"), supportGrouping);
			}

			if (!layoutPackageSpecifications.contains(packageDesc)) // don't try to group layout spec. definitions as those would be json files not js files...
			{
				for (WebObjectSpecification spec : packageDesc.getSpecifications().values())
				{
					if (exportedWebObjects != null && !exportedWebObjects.contains(spec.getName())) continue;

					if (supportGrouping == null || spec.supportGrouping() == supportGrouping.booleanValue())
					{
						if (spec.getDefinition() != null)
						{
							allJSContributions.add(spec.getDefinition());
						}
					}
					mergeLibs(allLibraries, spec.getLibraries(), supportGrouping);
				}
			}
		}

		for (JSONObject lib : allLibraries.values())
		{
			if (ceFilter != null)
			{
				lib = ceFilter.filterContributionEntry(lib);
			}
			switch (lib.optString("mimetype"))
			{
				case "text/javascript" :
					allJSContributions.add(lib.optString("url"));
					break;
				case "text/css" :
					allCSSContributions.add(lib.optString("url"));
					break;
				default :
					log.warn("Unknown mimetype " + lib);
			}
		}
		return new Object[] { allCSSContributions, allJSContributions };
	}

	/**
	 * Returns the contributions for webcomponents and services
	 * @return headContributions
	 */
	static String getAllContributions(Collection<String> cssContributions, Collection<String> jsContributions, Collection<String> extraMetaData,
		IContributionFilter contributionFilter, List<String> allCSSContributions, List<String> allJSContributions,
		String contentSecurityPolicyNonce)
	{
		if (cssContributions != null)
		{
			allCSSContributions.addAll(cssContributions);
		}

		if (jsContributions != null)
		{
			allJSContributions.addAll(jsContributions);
		}


		StringBuilder retval = new StringBuilder();
		List<String> filteredCSSContributions = contributionFilter != null ? contributionFilter.filterCSSContributions(allCSSContributions)
			: allCSSContributions;
		for (String lib : filteredCSSContributions)
		{
			retval.append("<link rel=\"stylesheet\" href=\"").append(lib).append("\"/>\n");
		}
		List<String> filteredJSContributions = contributionFilter != null ? contributionFilter.filterJSContributions(allJSContributions) : allJSContributions;
		for (String lib : filteredJSContributions)
		{
			retval.append("<script").append(getNonceTag(contentSecurityPolicyNonce)).append(" src=\"").append(lib).append("\"></script>\n");
		}
		if (extraMetaData != null)
		{
			for (String extra : extraMetaData)
			{
				retval.append(extra).append('\n');
			}
		}

		return retval.toString();
	}

	private static String getNonceTag(String nonce)
	{
		if (nonce == null)
		{
			return "";
		}
		return " nonce='" + nonce + "'";
	}

	/**
	 * Merge libs into allLibs, by keeping only the lib with the highest version
	 * @param allLibs JSONObject list with libraries from all components
	 * @param libs JSONObject list with new libraries to add
	 */
	private static void mergeLibs(LinkedHashMap<String, JSONObject> allLibs, JSONArray libs, Boolean supportGrouping)
	{
		JSONObject lib;
		for (int i = 0; i < libs.length(); i++)
		{
			lib = libs.optJSONObject(i);
			if (lib != null)
			{
				boolean group = lib.optString("url").toLowerCase().startsWith("http") ? false : lib.optBoolean("group", true);
				if (supportGrouping != null && supportGrouping.booleanValue() != group) continue;
				String name = lib.optString("name", null);
				String version = lib.optString("version", null);
				if (name != null && lib.has("url") && lib.has("mimetype"))
				{
					String key = name + "," + lib.optString("mimetype");
					JSONObject allLib = allLibs.get(key);
					if (allLib != null)
					{
						String storedVersion = allLib.optString("version");
						if (storedVersion != null && version != null)
						{
							int versionCheck = version.compareTo(storedVersion);
							if (versionCheck < 0)
							{
								if (!"-1".equals(version)) log.warn("same lib with lower version found: " + lib + " using lib: " + allLib);
								continue;
							}
							else if (versionCheck > 0)
							{
								if (!"-1".equals(storedVersion)) log.warn("same lib with lower version found: " + allLib + " using lib: " + lib);
							}
						}
						else if (storedVersion != null)
						{
							log.warn("same lib with no version found: " + lib + ", using the lib (" + allLib + ") with version: " + storedVersion);
							continue;
						}
						else
						{
							log.warn("same lib with no version found: " + allLib + ", using the lib (" + lib + ") with version: " + version);
						}
					}
					allLibs.put(key, lib);
				}
				else
				{
					log.warn("Invalid lib description : " + lib);
				}
			}
		}
	}

	private static JSONArray packageLibsToJSON(List<String> packageLibs, String mimeType)
	{
		JSONArray packageLibsToJSON = new JSONArray();

		try
		{
			for (String sLib : packageLibs)
			{
				JSONObject jsonLib = new JSONObject();
				StringTokenizer st = new StringTokenizer(sLib, ";");
				while (st.hasMoreTokens())
				{
					String t = st.nextToken().trim();
					if (t.startsWith("name="))
					{
						jsonLib.put("name", t.substring(5));
					}
					else if (t.startsWith("version="))
					{
						jsonLib.put("version", t.substring(8));
					}
					else
					{
						jsonLib.put("url", t);
					}
				}
				jsonLib.put("mimetype", mimeType);
				if (!jsonLib.has("name") && jsonLib.has("url"))
				{
					jsonLib.put("name", jsonLib.get("url"));
				}
				packageLibsToJSON.put(jsonLib);
			}
		}
		catch (JSONException ex)
		{
			log.error("Error converting package lib to json", ex);
		}

		return packageLibsToJSON;
	}
}