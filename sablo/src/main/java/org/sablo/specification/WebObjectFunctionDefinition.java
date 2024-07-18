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

import org.json.JSONObject;

/**
 * Parsed web component / web service api function definition.
 *
 * @author rgansevles
 */
@SuppressWarnings("nls")
public abstract class WebObjectFunctionDefinition
{
	// TODO we could split this class into a callable API function class and an event handler function class - so that it is clear what is used in either case
	// for example 'delayUntilFormLoads' is only for API functions while 'ignoreNGBlockDuplicateEvents' is only useful for handlers...

	private final String name;
	private final FunctionParameters parameters = new FunctionParameters();
	private PropertyDescription returnType;
	private JSONObject customConfigOptions;
	private String documentation;

	private PropertyDescription asPropertyDescription;
	private boolean priv;
	private String deprecated = null;
	private String allowaccess;

	public WebObjectFunctionDefinition(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void addParameter(PropertyDescription parameter)
	{
		parameters.add(parameter);
	}

	public IFunctionParameters getParameters()
	{
		return parameters;
	}

	public void setReturnType(PropertyDescription returnType)
	{
		this.returnType = returnType;
	}

	public PropertyDescription getReturnType()
	{
		return returnType;
	}

	void setCustomConfigOptions(JSONObject customConfigOptions)
	{
		this.customConfigOptions = customConfigOptions;
	}

	public JSONObject getCustomConfigOptions()
	{
		return this.customConfigOptions;
	}


	public void setDocumentation(String documentation)
	{
		this.documentation = documentation;
	}

	public String getDocumentation()
	{
		return documentation;
	}

	public PropertyDescription getAsPropertyDescription()
	{
		return asPropertyDescription;
	}

	public void setPropertyDescription(PropertyDescription propertyDescription)
	{
		this.asPropertyDescription = propertyDescription;

	}

	public void setPrivate(boolean priv)
	{
		this.priv = priv;
	}

	public boolean isPrivate()
	{
		return priv;
	}

	public void setDeprecated(String deprecated)
	{
		this.deprecated = deprecated;
	}

	public String getDeprecated()
	{
		return this.deprecated;
	}

	public boolean isDeprecated()
	{
		return deprecated != null && !"false".equalsIgnoreCase(deprecated.trim());
	}

	public String getDeprecatedMessage()
	{
		if (deprecated != null && !"false".equalsIgnoreCase(deprecated.trim()) && !"true".equalsIgnoreCase(deprecated.trim()))
		{
			return deprecated;
		}
		return "";
	}

	/**
	 * Setter.
	 * @see #getAllowAccess()
	 */
	public void setAllowAccess(String allowaccess)
	{
		this.allowaccess = allowaccess;
	}

	/**
	 * Execution can be blocked by certain model properties, like "visible" - functions does not execute if
	 * the component is not visible; allowaccess contains the list of the model properties (comma separated) that should
	 * be ignored in blocking.
	 * @return the allowaccess
	 */
	public String getAllowAccess()
	{
		return allowaccess;
	}


	@Override
	public String toString()
	{
		return "WebObjectFunctionDefinition [name=" + name + ",\nreturnType=" + returnType + ",\nparameters=" + parameters + ",\n" +
			"allowaccess=" + allowaccess + "]";
	}
}
