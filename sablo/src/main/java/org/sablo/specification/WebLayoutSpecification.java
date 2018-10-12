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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.property.types.StringPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author jcompagner
 *
 */
@SuppressWarnings({ "nls" })
public class WebLayoutSpecification extends WebObjectSpecification
{
	private static final Logger log = LoggerFactory.getLogger(WebLayoutSpecification.class.getCanonicalName());

	public static WebLayoutSpecification parseLayoutSpec(String specfileContent, String packageName, IPackageReader reader) throws JSONException, IOException
	{
		JSONObject json = new JSONObject(specfileContent);
		List<String> children = new ArrayList<>();

		boolean topContainer = json.has("topContainer") ? json.optBoolean("topContainer", false) : false;

		List<String> excludes = null;
		JSONArray excludesChildren = json.optJSONArray("excludes");
		if (excludesChildren != null)
		{
			excludes = new ArrayList<>();
			for (int i = 0; i < excludesChildren.length(); i++)
			{
				excludes.add(excludesChildren.optString(i));
			}
		}

		JSONArray canContainChildren = json.optJSONArray("contains");
		if (canContainChildren != null)
		{
			if (!json.has("excludes"))
			{
				for (int i = 0; i < canContainChildren.length(); i++)
				{
					children.add(canContainChildren.optString(i));
				}
			}
			else
			{
				log.warn("The 'contains' attribute is ignored for the " + json.optString("name") +
					"layout, because the 'excludes' attribute is present to specify excluded children.");
			}
		}

		String jsonConfig = null;
		if (json.getString("definition") != null)
		{
			jsonConfig = reader.readTextFile(json.getString("definition"), Charset.forName("UTF8"));
		}
		WebLayoutSpecification spec = new WebLayoutSpecification(json.getString("name"), packageName, json.optString("displayName", null),
			json.optString("categoryName", null), json.optString("icon", null), json.optString("preview", null), json.getString("definition"), jsonConfig,
			topContainer, children, excludes, json.optString("designStyleClass"), json.optString("layout", null));

		// properties
		spec.putAll(spec.parseProperties("model", json));

		if (json.has("attributes"))
		{
			String attributes = reader.readTextFile(json.getString("attributes"), Charset.forName("UTF8"));
			spec.putAllAttributes(spec.parseProperties("attributes", new JSONObject(attributes)));
		}

		if (json.has("tagType"))
		{
			if (spec.getProperty("tagType") != null)
			{
				PropertyDescription pd = spec.getProperty("tagType");
				spec.putProperty("tagType", new PropertyDescription("tagType", pd.getType(), pd.getConfig(), json.get("tagType"), null, true, pd.getValues(),
					pd.getPushToServer(), null, pd.isOptional()));
			}
			else
			{
				JSONObject tags = new JSONObject();
				tags.put("scope", "private");
				spec.putProperty("tagType",
					new PropertyDescription("tagType", StringPropertyType.INSTANCE, null, json.get("tagType"), null, true, null, null, tags, false));
			}
		}

		return spec;
	}

	private Map<String, PropertyDescription> attributes = null;

	private void putAllAttributes(Map<String, PropertyDescription> parseProperties)
	{
		attributes = new TreeMap<String, PropertyDescription>(parseProperties);
	}

	public Map<String, PropertyDescription> getAttributes()
	{
		return attributes;
	}

	private final boolean topContainer;
	private final String layout;
	private final List<String> allowedChildren;
	private final String designStyleClass;
	private final List<String> excludedChildren;

	public WebLayoutSpecification(String name, String packageName, String displayName, String categoryName, String icon, String preview, String definition,
		Object configObject, boolean topContainer, List<String> allowedChildren, List<String> excludedChildren, String designStyleClass, String layout)
	{
		super(name, packageName, IPackageReader.WEB_LAYOUT, displayName, categoryName, icon, preview, definition, null, configObject);
		this.topContainer = topContainer;
		this.allowedChildren = allowedChildren;
		this.excludedChildren = excludedChildren;
		this.designStyleClass = designStyleClass;
		this.layout = layout;
	}

	/**
	 * @return the parents
	 */
	public boolean isTopContainer()
	{
		return topContainer;
	}

	public boolean isCSSPosition()
	{
		return "css".equals(layout);
	}

	/**
	 * @return the list of components/layouts which can be contained by this layout component
	 */
	public List<String> getAllowedChildren()
	{
		return allowedChildren;
	}

	/**
	 * @return the list of components/layouts which cannot be added in this layout component
	 */
	public List<String> getExcludedChildren()
	{
		return excludedChildren;
	}

	/**
	 * @return the designStyleClass
	 */
	public String getDesignStyleClass()
	{
		return designStyleClass;
	}
}
