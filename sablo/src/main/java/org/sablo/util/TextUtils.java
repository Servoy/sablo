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

import java.util.regex.Pattern;

/**
 * @author rgansevles
 *
 */
@SuppressWarnings("nls")
public class TextUtils
{

	private static Pattern regexP1 = Pattern.compile("(?m:^\\s*/\\*\\* )"); // /**space
	private static Pattern regexP2 = Pattern.compile("(?m:^\\s*/\\*\\*)"); // /**
	private static Pattern regexP3 = Pattern.compile("(?m:^\\s*/\\* )"); // /*space
	private static Pattern regexP4 = Pattern.compile("(?m:^\\s*/\\*)"); // /*
	private static Pattern regexP5 = Pattern.compile("(?m:\\s*\\*/)"); // */
	private static Pattern regexP6 = Pattern.compile("(?m:^\\s*\\* )"); // *space
	private static Pattern regexP7 = Pattern.compile("(?m:^\\s*\\*)"); // *

	/**
	 * This only strips down some whitespace as well as start/end block comment chars. It does not look for one line comments, so //.
	 */
	public static String stripCommentStartMiddleAndEndChars(String doc)
	{
		String stripped = doc;
		stripped = regexP1.matcher(stripped).replaceAll("");
		stripped = regexP2.matcher(stripped).replaceAll("");
		stripped = regexP3.matcher(stripped).replaceAll("");
		stripped = regexP4.matcher(stripped).replaceAll("");
		stripped = regexP5.matcher(stripped).replaceAll("");
		stripped = regexP6.matcher(stripped).replaceAll("");
		return regexP7.matcher(stripped).replaceAll("");
	}

	public static String newLinesToBackslashN(String source)
	{
		return source.replace("\r\n", "\n");
	}

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
