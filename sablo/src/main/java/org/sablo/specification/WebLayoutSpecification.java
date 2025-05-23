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
import java.util.Collections;
import java.util.HashMap;
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

		List<String> directives = null;
		JSONArray directivesJson = json.optJSONArray("directives");
		if (directivesJson != null)
		{
			directives = new ArrayList<>();
			for (int i = 0; i < directivesJson.length(); i++)
			{
				directives.add(directivesJson.optString(i));
			}
		}

		String jsonConfig = null;
		if (json.optString("definition", null) != null)
		{
			jsonConfig = reader.readTextFile(json.getString("definition"), Charset.forName("UTF8"));
		}
		// properties
		Map<String, PropertyDescription> properties = new HashMap<String, PropertyDescription>();
		properties.putAll(WebObjectSpecification.parseProperties("model", json, null, json.getString("name")));

		if (json.has("tagType"))
		{
			if (properties.get("tagType") != null)
			{
				PropertyDescription pd = properties.get("tagType");
				properties.put("tagType",
					new PropertyDescriptionBuilder().withName("tagType").withType(pd.getType()).withConfig(pd.getConfig()).withDefaultValue(
						json.get("tagType")).withHasDefault(true).withValues(pd.getValues()).withPushToServer(pd.getPushToServer()).withOptional(
							pd.isOptional())
						.withDeprecated(pd.getDeprecated()).build());
			}
			else
			{
				JSONObject tags = new JSONObject();
				tags.put("scope", "private");
				properties.put("tagType", new PropertyDescriptionBuilder().withName("tagType").withType(StringPropertyType.INSTANCE).withDefaultValue(
					json.get("tagType")).withHasDefault(true).withTags(tags).build());
			}
		}

		WebLayoutSpecification spec = new WebLayoutSpecificationBuilder().withName(json.getString("name")).withPackageName(packageName).withDisplayName(
			json.optString("displayName", null)).withCategoryName(json.optString("categoryName", null)).withIcon(json.optString("icon", null)).withPreview(
				json.optString("preview", null))
			.withDefinition(json.optString("definition")).withConfig(jsonConfig).withTopContainer(
				topContainer)
			.withAllowedChildren(children).withExcludedChildren(excludes).withDesignStyleClass(
				json.optString("designStyleClass"))
			.withLayout(json.optString("layout", null)).withProperties(properties).withDeprecated(
				json.optString("deprecated", null))
			.withDirectives(directives)
			.build();

		if (json.has("attributes"))
		{
			String attributes = reader.readTextFile(json.getString("attributes"), Charset.forName("UTF8"));
			spec.putAllAttributes(WebObjectSpecification.parseProperties("attributes", new JSONObject(attributes), null, spec.getName()));
		}
		spec.setReplacement(json.optString("replacement", null));
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
	private final List<String> directives;

	WebLayoutSpecification(String name, String packageName, String displayName, String categoryName, String icon, String preview, String definition,
		Object configObject, boolean topContainer, List<String> allowedChildren, List<String> excludedChildren, String designStyleClass, String layout,
		Map<String, PropertyDescription> properties, String deprecated, List<String> directives)
	{
		super(name, packageName, IPackageReader.WEB_LAYOUT, displayName, categoryName, null, icon, preview, definition, null, configObject, properties,
			deprecated, null, null, null);
		this.topContainer = topContainer;
		this.allowedChildren = allowedChildren;
		this.excludedChildren = excludedChildren;
		this.designStyleClass = designStyleClass;
		this.layout = layout;
		this.directives = directives;
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
		return allowedChildren == null ? Collections.emptyList() : Collections.unmodifiableList(allowedChildren);
	}

	/**
	 * @return the list of components/layouts which cannot be added in this layout component
	 */
	public List<String> getExcludedChildren()
	{
		return excludedChildren == null ? Collections.emptyList() : Collections.unmodifiableList(excludedChildren);
	}

	/**
	 * @return the list of angular (ng2) directives that should be attached to this container.
	 */
	public List<String> getDirectives()
	{
		return directives == null ? Collections.emptyList() : Collections.unmodifiableList(directives);
	}

	/**
	 * @return the designStyleClass
	 */
	public String getDesignStyleClass()
	{
		return designStyleClass;
	}
}
