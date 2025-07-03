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
package org.sablo.specification.property.types;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sablo.specification.ValuesConfig;
import org.sablo.specification.property.IPropertyCanDependsOn;

/**
 * @author jcompagner
 *
 */
public class ValuesPropertyType extends DefaultPropertyType<Object[]> implements IPropertyCanDependsOn
{

	public static final ValuesPropertyType INSTANCE = new ValuesPropertyType();
	public static final String TYPE_NAME = "values";

	private String[] dependencies;

	private ValuesPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return TYPE_NAME;
	}

	@Override
	public Object parseConfig(JSONObject json)
	{
		ValuesConfig config = new ValuesConfig();
		if (json != null)
		{
			if (json.has("default"))
			{
				Object realdef = null;
				Object displaydef = null;
				Object def = json.opt("default");
				if (def instanceof JSONObject)
				{
					realdef = ((JSONObject)def).opt("real");
					displaydef = ((JSONObject)def).opt("display");
				}
				else
				{
					// some value, both real and display
					realdef = def;
				}
				config.addDefault(realdef, displaydef == null ? null : displaydef.toString());
			}

			Object values = json.opt("values");
			if (values instanceof JSONArray)
			{
				int len = ((JSONArray)values).length();
				Object[] real = new Object[len];
				String[] display = new String[len];
				for (int i = 0; i < len; i++)
				{
					Object elem = ((JSONArray)values).opt(i);
					Object displayval;
					if (elem instanceof JSONObject)
					{
						// real and display
						real[i] = ((JSONObject)elem).opt("real");
						displayval = ((JSONObject)elem).opt("display");
					}
					else
					{
						// some value, both real and display
						real[i] = elem;
						displayval = elem;
					}
					display[i] = displayval == null ? "" : displayval.toString();
				}
				config.setValues(real, display);
			}

			config.setEditable(json.optBoolean("editable"));
			config.setMultiple(json.optBoolean("multiple"));
		}
		dependencies = getDependencies(json, dependencies);
		return config;
	}

	@Override
	public String[] getDependencies()
	{
		return dependencies;
	}
}
