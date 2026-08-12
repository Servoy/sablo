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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;


/**
 * @author rgansevles
 */
@SuppressWarnings("nls")
public class PackageSpecification<T extends WebObjectSpecification>
{
	private static final String CSS_DESIGN_LIBS = "CSS-DesignLibs";
	private static final String CSS_CLIENT_LIBS = "CSS-ClientLibs";
	private static final String NG2_CSS_CLIENT_LIBS = "NG2-CSS-ClientLibs";
	private static final String NG2_CSS_DESIGN_LIBS = "NG2-CSS-DesignLibs";
	private static final String NG2_MODULE = "NG2-Module";
	private static final String NG2_COMPONENTS = "NG2-Components";
	private static final String ENTRY_POINT = "Entry-Point";
	private static final String NPM_PACKAGE_NANE = "NPM-PackageName";
	private static final String JS_DESIGN_LIBS = "JS-DesignLibs";
	private static final String JS_CLIENT_LIBS = "JS-ClientLibs";

	private final String packageName;
	private final String packageDisplayname;
	private final String ng2Module;
	private final String ng2Components;
	private final String npmName;
	private final String entryPoint;
	private final List<String> cssClientLibrary;
	private final List<String> cssDesignLibrary;
	private final CssLibSet ng2CssClientLibrary;
	private final List<String> ng2CssDesignLibrary;
	private final List<String> jsClientLibrary;
	private final List<String> jsDesignLibrary;
	private final Map<String, T> specifications;
	private final Manifest mf;

	public PackageSpecification(String packageName, String packageDisplayname, Map<String, T> specifications, Manifest mf)
	{
		this.packageName = packageName;
		this.packageDisplayname = packageDisplayname;
		this.mf = mf;

		this.specifications = specifications;
		Attributes attributes = mf != null ? mf.getMainAttributes() : null;
		if (attributes != null)
		{
			this.ng2Module = attributes.getValue(NG2_MODULE);
			this.ng2Components = attributes.getValue(NG2_COMPONENTS);
			this.npmName = attributes.getValue(NPM_PACKAGE_NANE);
			this.entryPoint = attributes.getValue(ENTRY_POINT);
			this.cssClientLibrary = getAttributeValue(attributes, CSS_CLIENT_LIBS);
			this.cssDesignLibrary = getAttributeValue(attributes, CSS_DESIGN_LIBS);
			this.ng2CssClientLibrary = getCssLibAttributeValue(attributes, NG2_CSS_CLIENT_LIBS);
			this.ng2CssDesignLibrary = getAttributeValue(attributes, NG2_CSS_DESIGN_LIBS);
			this.jsClientLibrary = getAttributeValue(attributes, JS_CLIENT_LIBS);
			this.jsDesignLibrary = getAttributeValue(attributes, JS_DESIGN_LIBS);
		}
		else
		{
			this.ng2Module = null;
			this.ng2Components = null;
			this.cssClientLibrary = null;
			this.cssDesignLibrary = null;
			this.ng2CssClientLibrary = null;
			this.ng2CssDesignLibrary = null;
			this.jsClientLibrary = null;
			this.jsDesignLibrary = null;
			this.npmName = null;
			this.entryPoint = null;
		}
	}

	private List<String> getAttributeValue(Attributes mainAttrs, String attributeName)
	{
		String value = mainAttrs.getValue(attributeName);
		if (value != null)
		{
			return Arrays.asList(value.split(","));
		}
		return null;
	}

	private CssLibSet getCssLibAttributeValue(Attributes mainAttrs, String attributeName)
	{
		String value = mainAttrs.getValue(attributeName);
		if (value != null)
		{
			CssLibSet set = new CssLibSet();
			String[] libs = value.split(",");
			for (String lib : libs)
			{
				set.add(new CssLib(lib));
			}
			return set;
		}
		return null;
	}

	/**
	 * @return the packageName
	 */
	public String getPackageName()
	{
		return packageName;
	}

	/**
	 * @return the packageDisplayname
	 */
	public String getPackageDisplayname()
	{
		return packageDisplayname == null ? packageName : packageDisplayname;
	}

	/**
	 * @return the ng2Module
	 */
	public String getNg2Module()
	{
		return ng2Module;
	}

	/**
	 * @return the comma-separated list of standalone component class names, or null if not specified
	 */
	public String getNg2Components()
	{
		return ng2Components;
	}

	/**
	 * @return true if this package exports standalone components (NG2-Components attribute is set)
	 */
	public boolean hasStandaloneComponents()
	{
		return ng2Components != null && !ng2Components.isEmpty();
	}

	/**
	 * @return the npmName
	 */
	public String getNpmPackageName()
	{
		return npmName;
	}

	/**
	 * @return the entryPoint
	 */
	public String getEntryPoint()
	{
		return entryPoint;
	}

	public List<String> getCssClientLibrary()
	{
		return cssClientLibrary;
	}

	public List<String> getJsClientLibrary()
	{
		return jsClientLibrary;
	}

	public List<String> getCssDesignLibrary()
	{
		return cssDesignLibrary;
	}

	/**
	 * @return the ng2CssLibrary
	 */
	public Set<CssLib> getNg2CssLibrary()
	{
		return ng2CssClientLibrary;
	}

	/**
	 * @return the ng2CssDesignLibrary
	 */
	public List<String> getNg2CssDesignLibrary()
	{
		return ng2CssDesignLibrary;
	}

	public List<String> getJsDesignLibrary()
	{
		return jsDesignLibrary;
	}

	/**
	 * @return the specifications
	 */
	public Map<String, T> getSpecifications()
	{
		return Collections.unmodifiableMap(specifications);
	}

	public T getSpecification(String name)
	{
		return specifications.get(name);
	}

	public Manifest getManifest()
	{
		return mf;
	}
}
