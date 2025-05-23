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

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author lvostinar
 *
 */
@SuppressWarnings({ "hiding", "nls" })
abstract class AbstractWebObjectSpecificationBuilder<B extends AbstractWebObjectSpecificationBuilder<B, P>, P extends WebObjectSpecification>
	extends AbstractPropertyDescriptionBuilder<B, P>
{
	protected String packageName = "";
	protected String packageType;
	protected String displayName;
	protected String categoryName;
	protected String icon;
	protected String preview;
	protected String definition = "";
	protected JSONArray libraries;
	protected JSONArray keywords;
	protected JSONObject ng2Config;
	protected String styleVariantCategory;
	protected String standalone;

	public B withPackageName(String packageName)
	{
		this.packageName = packageName;
		return getThis();
	}

	public B withPackageType(String packageType)
	{
		this.packageType = packageType;
		return getThis();
	}

	public B withDisplayName(String displayName)
	{
		this.displayName = displayName;
		return getThis();
	}

	protected String getDisplayname()
	{
		return displayName == null ? name : displayName;
	}

	public B withCategoryName(String categoryName)
	{
		this.categoryName = categoryName;
		return getThis();
	}

	public B withStyleVariantCategory(String styleVariantCategory)
	{
		this.styleVariantCategory = styleVariantCategory;
		return getThis();
	}

	public B withIcon(String icon)
	{
		this.icon = icon;
		return getThis();
	}

	public B withPreview(String preview)
	{
		this.preview = preview;
		return getThis();
	}

	public B withDefinition(String definition)
	{
		this.definition = definition;
		return getThis();
	}

	public B withStandalone(String standalone)
	{
		this.standalone = standalone;
		return getThis();
	}

	public B withLibraries(JSONArray libraries)
	{
		this.libraries = libraries;
		return getThis();
	}

	public B withKeywords(JSONArray keywords)
	{

		this.keywords = keywords;
		return getThis();
	}

	public B withNG2Config(JSONObject ng2Config)
	{
		this.ng2Config = ng2Config;
		return getThis();
	}
}
