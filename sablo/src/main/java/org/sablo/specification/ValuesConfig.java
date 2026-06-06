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

/**
 * Specific config class for values type
 */
public class ValuesConfig
{
	private boolean hasDefault = false;
	private Object[] real;
	private String[] display;
	private Object realDefault;
	private String displayDefault;
	private boolean editable;
	private boolean multiple;

	public ValuesConfig addDefault(Object realDef, String displayDef)
	{
		this.hasDefault = true;
		this.realDefault = realDef;
		this.displayDefault = displayDef;
		return this;
	}

	public boolean hasDefault()
	{
		return hasDefault;
	}

	public Object getRealDefault()
	{
		return realDefault;
	}

	public String getDisplayDefault()
	{
		return displayDefault;
	}

	public ValuesConfig setValues(Object[] real, String[] display)
	{
		this.real = real;
		this.display = display;
		return this;
	}

	public ValuesConfig setValues(Object[] realAndDisplay)
	{
		String[] strings;
		if (realAndDisplay instanceof String[])
		{
			strings = (String[])realAndDisplay;
		}
		else
		{
			strings = new String[realAndDisplay.length];
			for (int i = 0; i < realAndDisplay.length; i++)
			{
				strings[i] = realAndDisplay[i] == null ? "" : realAndDisplay[i].toString();
			}
		}
		return setValues(realAndDisplay, strings);
	}

	public Object[] getReal()
	{
		return real;
	}

	public String[] getDisplay()
	{
		return display;
	}

	public int getRealIndexOf(Object value)
	{
		if (real != null)
		{
			for (int i = 0; i < real.length; i++)
			{
				if ((value == null && real[i] == null) || (value != null && value.equals(real[i])))
				{
					return hasDefault ? i + 1 : i;
				}
			}
		}
		return -1;
	}

	public ValuesConfig setEditable(boolean editable)
	{
		this.editable = editable;
		return this;
	}

	public boolean isEditable()
	{
		return editable;
	}

	public ValuesConfig setMultiple(boolean multiple)
	{
		this.multiple = multiple;
		return this;
	}

	public boolean isMultiple()
	{
		return multiple;
	}
}