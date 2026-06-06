/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

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

import org.sablo.specification.WebObjectSpecification.PushToServerEnum;


/**
 * Types that want to add special behavior to the pushToServer setting from spec files can implement this interface.
 * Some types want to be able to receive messages from client side even though no pushToServer is set in .spec file for them (not even allow) because
 * they need some internal protocol to be operational.
 *
 * For example a type might want to request certain data from server without modifying any data on the server, so in spec it doesn't have 'allow' but the request
 * from client shouldn't be blocked by the system. The type will be responsible for blocking that itself based on
 * {@link IConvertedPropertyType#fromJSON(Object, Object, IBrowserConverterContext)}'s {@link IBrowserConverterContext#getPushToServerValue()}.
 *
 * @author acostescu
 *
 * @see PushToServerEnum
 * @see IConvertedPropertyType
 * @see IBrowserConverterContext
 * @see IPropertyType
 */
public interface IPushToServerSpecialType
{

	/**
	 * If true then the updates comming from client will not be blocked by the system even if the spec file doesn't declare a pushToServer value.
	 * It is the responsibility of the property type in this case to adhere to the pushToServer value specified in spec file (it should inforce it in it's own
	 * {@link IConvertedPropertyType#fromJSON(Object, Object, IBrowserConverterContext)} implementation).
	 *
	 * @return true if this property type always needs to get incomming traffic or false if spec file 'pushToServer' value should be used
	 */
	boolean shouldAlwaysAllowIncommingJSON();

}
