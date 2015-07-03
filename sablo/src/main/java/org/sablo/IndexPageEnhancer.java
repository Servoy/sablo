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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sablo.services.template.ModifiablePropertiesGenerator;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	 * @param contextPath the path to express in base tag
	 * @param cssContributions possible css contributions
	 * @param jsContributions possible js contributions
	 * @param variableSubstitution replace variables
	 * @param writer the writer to write to
	 * @throws IOException
	 */
	public static void enhance(URL resource, String contextPath, Collection<String> cssContributions, Collection<String> jsContributions,
		Map<String, String> variableSubstitution, Writer writer) throws IOException
	{
		String index_file = IOUtils.toString(resource);
		String lowercase_index_file = index_file.toLowerCase();
		int headstart = lowercase_index_file.indexOf("<head>");
		int headend = lowercase_index_file.indexOf(COMPONENT_CONTRIBUTIONS);

		//use real html parser here instead?
		if (variableSubstitution != null)
		{
			for (String variableName : variableSubstitution.keySet())
			{
				String variableReplace = VAR_START + variableName + VAR_END;
				index_file = index_file.replaceAll(Matcher.quoteReplacement(variableReplace), variableSubstitution.get(variableName));
			}
		}

		StringBuilder sb = new StringBuilder(index_file);
		if (headend < 0)
		{
			log.warn("Could not find marker for component contributions: " + COMPONENT_CONTRIBUTIONS + " for resource " + resource);
		}
		else
		{
			sb.insert(headend + COMPONENT_CONTRIBUTIONS.length(), getAllContributions(cssContributions, jsContributions));
		}
		if (headstart < 0)
		{
			log.warn("Could not find empty head tag for base tag for resource " + resource);
		}
		else
		{
			sb.insert(headstart + 6, getBaseTag(contextPath));
		}
		writer.append(sb);
	}

	/**
	 * Get the Base tag to use
	 * @param contextPath the contextPath to be used in base tag
	 * @return the decorated base tag
	 */
	private static String getBaseTag(String contextPath)
	{
		return String.format("<base href=\"%s/\">\n", contextPath);
	}

	/**
	 * Returns the contributions for webcomponents and services
	 * @return headContributions
	 */
	static String getAllContributions(Collection<String> cssContributions, Collection<String> jsContributions)
	{
		StringBuilder retval = new StringBuilder();

		WebComponentSpecification[] webComponentDescriptions = WebComponentSpecProvider.getInstance().getAllWebComponentSpecifications();
		LinkedHashMap<String, JSONObject> allLibraries = new LinkedHashMap<>();
		for (WebComponentSpecification spec : webComponentDescriptions)
		{
			retval.append(String.format("<script src=\"%s\"></script>\n", spec.getDefinition()));
			mergeLibs(allLibraries, spec.getLibraries());
		}

		WebComponentSpecification[] webServiceDescriptions = WebServiceSpecProvider.getInstance().getAllWebServiceSpecifications();
		for (WebComponentSpecification spec : webServiceDescriptions)
		{
			retval.append(String.format("<script src=\"%s\"></script>\n", spec.getDefinition()));
			mergeLibs(allLibraries, spec.getLibraries());
		}

		for (JSONObject lib : allLibraries.values())
		{
			switch (lib.optString("mimetype"))
			{
				case "text/javascript" :
					retval.append(String.format("<script src=\"%s\"></script>\n", lib.optString("url")));
					break;
				case "text/css" :
					retval.append(String.format("<link rel=\"stylesheet\" href=\"%s\"/>\n", lib.optString("url")));
					break;
				default :
					log.warn("Unknown mimetype " + lib);
			}
		}

		if (cssContributions != null)
		{
			for (String lib : cssContributions)
			{
				retval.append(String.format("<link rel=\"stylesheet\" href=\"%s\"/>\n", lib));
			}
		}

		if (jsContributions != null)
		{
			for (String lib : jsContributions)
			{
				retval.append(String.format("<script src=\"%s\"></script>\n", lib));
			}
		}

		// lists properties that need to be watched for client to server changes for each component/service type
		retval.append("<script src=\"spec/").append(ModifiablePropertiesGenerator.TWO_WAY_BINDINGS_LIST).append(".js\"></script>\n");

		return retval.toString();
	}

	/**
	 * Merge libs into allLibs, by keeping only the lib with the highest version
	 * @param allLibs JSONObject list with libraries from all components
	 * @param libs JSONObject list with new libraries to add
	 */
	private static void mergeLibs(LinkedHashMap<String, JSONObject> allLibs, JSONArray libs)
	{
		JSONObject lib;
		for (int i = 0; i < libs.length(); i++)
		{
			lib = libs.optJSONObject(i);
			if (lib != null)
			{
				String name = lib.optString("name");
				String version = lib.optString("version");
				if (name != null && version != null && lib.has("url") && lib.has("mimetype"))
				{
					String key = name + "," + lib.optString("mimetype");
					JSONObject allLib = allLibs.get(key);
					if (allLib != null && version.compareTo(allLib.optString("version")) < 0)
					{
						log.warn("same lib with lower version found: " + lib);
						continue;
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
}