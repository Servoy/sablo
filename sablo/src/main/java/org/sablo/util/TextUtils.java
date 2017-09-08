/*
 * Copyright (C) 2017 Servoy BV
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

package org.sablo.util;

/**
 * @author rgansevles
 *
 */
public class TextUtils
{

	/**
	 * Escape the input string so that it the result can be used in javascript surrounded in double quotes.
	 * @param s
	 * @return
	 */
	public static final String escapeForDoubleQuotedJavascript(String s)
	{
		if (s == null || (s.indexOf('"') == -1 && s.indexOf('\\') == -1))
		{
			return s;
		}

		int len = s.length();
		StringBuilder escaped = new StringBuilder(len + 10);
		for (int i = 0; i < len; i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
				case '"' :
				case '\\' :
					escaped.append('\\');
					break;
			}
			escaped.append(c);
		}

		return escaped.toString();
	}
}
