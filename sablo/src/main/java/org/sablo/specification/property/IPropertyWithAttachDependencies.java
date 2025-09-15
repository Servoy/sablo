/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2025 Servoy BV

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

import org.sablo.specification.PropertyDescription;

/**
 * Property types that implement this interface depend on the runtime state of other properties in their {@link ISmartPropertyValue} value attach/detach
 * methods.<br/><br/>
 *
 * These property types use {@link ISmartPropertyValue} values and usually read the values of the other properties that they depend on
 * in the {@link ISmartPropertyValue#attachToBaseObject(org.sablo.IChangeListener, org.sablo.IWebObjectContext)}; they might also add property change
 * listeners and update their state when dependent properties change.<br/><br/>
 *
 * That is why, when these properties' values are attached/detached they will follow a stable order sorted taking into account the dependencies as well. For
 * example if PropA depends on PropB then their {@link ISmartPropertyValue#attachToBaseObject(org.sablo.IChangeListener, org.sablo.IWebObjectContext)}
 * will be called in the order: PropB, PropA. And {@link ISmartPropertyValue#detach()} will get called in the order: PropA, PropB.<br/><br/>
 *
 * This helps avoid both random init/attach sequences that result in a property with dependencies trying to initialize multiple times (if
 * they listen for dependent prop. changes) unsuccessfully (if dependencies are not yet ready) before succeeding but also that detach() doesn't
 * try to clean up listeners from another property's value when that property's value was already detached.
 *
 * @author marianvid, acostescu
 */
public interface IPropertyWithAttachDependencies<T> extends IPropertyType<T>
{

	/**
	 * Property types that return this constant in their {@link #getDependencies(PropertyDescription)} method are considered
	 * to want to get attached at the end, after all others that return something else... (usually properties
	 * that have sub-properties in them that can depend on a property in that parent level will return {@link #DEPENDS_ON_ALL},
	 * as the sorting takes place in each such level of nesting, so in main component, then in properties nested in custom object properties or arrays etc.
	 * who's sub-properties might depend on properties from the main component and/or siblings from their own 'parent' custom object; and the nesting can go on).
	 */
	public static final String[] DEPENDS_ON_ALL = new String[0];

	/**
	 * Get the attach/detach property dependencies of this property according to the component's .spec file.
	 *
	 * @param pd the definition of this property.
	 * @return the names of properties that this property depends on in attach/detach. If null or an empty array - it
	 * means that this property has no such dependencies. If this method returns {@link #DEPENDS_ON_ALL}, it is considered
	 * that this property wants to get attached at the end, after all others that return something else (usually properties
	 * that have sub-properties in them that can depend on a property in that parent level will return {@link #DEPENDS_ON_ALL}).
	 */
	String[] getDependencies(PropertyDescription pd);

}
