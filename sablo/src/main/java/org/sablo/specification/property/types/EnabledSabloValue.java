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

package org.sablo.specification.property.types;

import java.util.Collection;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IWrappingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author emera
 */
public class EnabledSabloValue
{
	private final Logger log = LoggerFactory.getLogger(EnabledSabloValue.class.getCanonicalName());

	protected boolean value;
	protected final IWrappingContext context;

	public EnabledSabloValue(boolean value, IWrappingContext dataConverterContext)
	{
		this.value = value;
		this.context = dataConverterContext;
	}

	public JSONWriter toJSON(JSONWriter writer)
	{
		try
		{
			writer.value(getValue());
		}
		catch (JSONException e)
		{
			log.error(e.getMessage());
		}
		return writer;
	}

	public boolean getValue()
	{
		if (!value) return false;
		BaseWebObject component = context.getWebObject();
		if (component instanceof WebComponent && ((WebComponent)component).getParent() != null) return ((WebComponent)component).getParent().isEnabled();
		return true;
	}

	public void setEnabled(boolean newValue)
	{
		if (value != newValue)
		{
			this.value = newValue;
			flagChanged(context.getWebObject(), context.getPropertyName());
		}
	}

	protected void flagChanged(BaseWebObject comp, String propName)
	{
		comp.markPropertyAsChangedByRef(propName);
		if (!comp.getProperties().content.containsKey(propName))
		{
			// if it doesn't have this property yet, now push it (take the one from the default map)
			EnabledSabloValue defaultValue = (EnabledSabloValue)comp.getRawPropertyValue(propName);
			comp.setProperty(propName, Boolean.valueOf(defaultValue.value));
		}
		if (comp instanceof Container)
		{
			for (WebComponent child : ((Container)comp).getComponents())
			{
				Collection<PropertyDescription> properties = child.getSpecification().getProperties(EnabledPropertyType.INSTANCE);
				for (PropertyDescription propertyDescription : properties)
				{
					flagChanged(child, propertyDescription.getName());
				}
			}
		}
	}
}
