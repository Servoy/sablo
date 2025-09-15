/*
 * Copyright (C) 2025 Servoy BV
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.sablo.specification.property.IPropertyWithAttachDependencies;
import org.sablo.specification.property.ISmartPropertyValue;

/**
 * A comparator that can be used to sort properties based on
 * attach/detach dependencies between their sablo values. See {@link ISmartPropertyValue} and {@link IPropertyWithAttachDependencies}.
 *
 * @author acostescu
 */
public class AttachComparator implements Comparator<String>
{

	private final Map<String, Integer> propertyToLocationInSortedProperties;

	public AttachComparator(Map<String, PropertyDescription> properties)
	{
		int index = 0;
		propertyToLocationInSortedProperties = new HashMap<>(properties != null ? properties.size() : 0);
		if (properties != null)
		{
			for (String propertyName : properties.keySet())
			{
				propertyToLocationInSortedProperties.put(propertyName, Integer.valueOf(index++));
			}
		}
	}

	@Override
	public int compare(String pn1, String pn2)
	{
		Integer idx1 = propertyToLocationInSortedProperties.get(pn1);
		Integer idx2 = propertyToLocationInSortedProperties.get(pn2);
		return (idx1 != null ? idx1.intValue() : -100) - (idx2 != null ? idx2.intValue() : -100); // -100 is a random number that can be anything negative so that unknown names are at the beginning
	}

}
