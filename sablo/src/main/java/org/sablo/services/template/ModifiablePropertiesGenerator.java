/*
 * Copyright (C) 2015 Servoy BV
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

package org.sablo.services.template;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebObjectSpecification.PushToServerEnum;

/**
 * In order to improve performance, not all properties on all components/services are watched for client/browser changes in angular.
 * Depending on the spec files, just some of them need to be watched (for sending changes back to server) - so watch count is greatly reduced clientside.
 *
 * This class generates a list of properties to be watched for each component/service type.
 *
 * @author acostescu
 */
@SuppressWarnings("nls")
public class ModifiablePropertiesGenerator
{
	public final static String PUSH_TO_SERVER_BINDINGS_LIST = "pushToServerData";

	/**
	 * @param w writer used to write the JSON.
	 */
	public static void start(PrintWriter w)
	{
		w.print("angular.module('");
		w.print(ModifiablePropertiesGenerator.PUSH_TO_SERVER_BINDINGS_LIST);
		w.println("',['pushToServer']).run(function ($propertyWatchesRegistry) {");
		w.println("  $propertyWatchesRegistry.clearAutoWatchPropertiesList();");
	}

	/**
	 * Writes the given set of watched properties to the JSON, under the given category name.
	 * @param webComponentSpecifications specifications to look in for watched properties
	 * @param categoryName for example "components", "services"
	 */
	public static void appendAll(PrintWriter w, WebObjectSpecification[] webComponentSpecifications, String categoryName)
	{
		w.print("  $propertyWatchesRegistry.setAutoWatchPropertiesList('");
		w.print(categoryName);
		w.println("', {");
		boolean first1 = true;
		for (WebObjectSpecification webComponentSpec : webComponentSpecifications)
		{
			Collection<PropertyDescription> pushToServerWatchProps = new ArrayList<>();
			for (PropertyDescription desc : webComponentSpec.getProperties().values())
			{
				if (desc.getPushToServer() == PushToServerEnum.deep || desc.getPushToServer() == PushToServerEnum.shallow)
				{
					pushToServerWatchProps.add(desc);
				}
			}

			if (pushToServerWatchProps.size() > 0)
			{
				if (!first1) w.println(',');
				first1 = false;
				w.print("    '");
				w.print(webComponentSpec.getScriptingName()); // use just name for now as if two packages define components with the exact same name that won't work correctly anyway; and new web object action does include package name in name by default
				w.println("': {");
				boolean first2 = true;
				for (PropertyDescription prop : pushToServerWatchProps)
				{
					if (!first2) w.println(',');
					first2 = false;
					w.print("      '");
					w.print(prop.getName());
					w.print("': ");
					w.print(prop.getPushToServer() == PushToServerEnum.deep);
				}
				if (!first2) w.println("");
				w.print("    }");
			}
		}
		if (!first1) w.println("");
		w.println("  });");
	}

	public static void finish(PrintWriter w)
	{
		w.print("});");
	}

}
