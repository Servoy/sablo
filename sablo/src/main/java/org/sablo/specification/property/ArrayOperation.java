/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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

import java.util.Set;

@SuppressWarnings("nls")
public class ArrayOperation
{

	public static final int CHANGE = 0;
	public static final int INSERT = 1;
	public static final int DELETE = 2;

	public final int startIndex;
	public final int endIndex;
	public final int type;

	/**
	 * In case of rows with column/cells, this is null if it's a whole row, and non-null if only one or more cells of the row will be in this change/row data.<br/>
	 * In case of simple arrays, this is null if the array item if fully changed by ref, or something else - content depends on who uses this class - if that array item is just partially changed.
	 */
	public final Set<String> cellNames;

	public ArrayOperation(int startIndex, int endIndex, int type)
	{
		this(startIndex, endIndex, type, null);
	}

	public ArrayOperation(int startIndex, int endIndex, int type, Set<String> cellNames)
	{
		this.startIndex = startIndex;
		this.endIndex = endIndex;
		this.type = type;
		this.cellNames = cellNames;
	}

	@Override
	public String toString()
	{
		return "ArrayOperation [startIndex=" + startIndex + ", endIndex=" + endIndex + ", type=" + type + ", cellNames=" + cellNames + "]";
	}

}