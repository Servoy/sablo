/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2014 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package org.sablo.specification.property;

import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;



/**
 * This class represents property types - which can have normal usage as declared in their type definition JSON,
 * can be default types or can have special handling (in case of complex properties, keeping different structures of data at design time/server side/client side).
 * 
 * @author acostescu
 */
public interface IPropertyType<T>
{

	/**
	 * The type name as used in .spec files.
	 * @return the type name as used in .spec files.
	 */
	String getName();

	/**
	 * Parse the JSON property configuration object into something that is easily usable later on (through {@link PropertyDescription#getConfig()}) by the property type implementation.<BR>
	 * Example of JSON: "myComponentProperty: { type: 'myCustomType', myCustomTypeConfig1: true, myCustomTypeConfig2: [2, 4 ,6] }"<BR><BR>
	 * 
	 * If this is null but the property declaration contains configuration information, {@link PropertyDescription#getConfig()} will contain the actual JSON object. 
	 * 
	 * it should return the config object itself if it doesn't do anything.
	 * 
	 * @return a custom object generated from the json or the json object itself.
	 */
	public Object parseConfig(JSONObject config);
	
	public T defaultValue();

}
