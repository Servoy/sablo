/*
 * Copyright (C) 2022 Servoy BV
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
 * This class is used to order a css lib/url to based on the ;order=x
 * So that some libs can become less or more important then others
 * a 0 means that it is most importand (which is default) and will be generated at the client side last.
 *
 * @author jcompagner
 * @since 2022.03
 */
@SuppressWarnings("nls")
public final class CssLib implements Comparable<CssLib>
{
	private final String url;
	private final int priority;

	public CssLib(String url)
	{
		String[] splitted = url.split(";priority=");
		if (splitted.length > 1)
		{
			this.url = splitted[0];
			int parsed = 9; // default to 9
			try
			{
				parsed = Integer.parseInt(splitted[1]);
			}
			catch (Exception e)
			{
			}
			this.priority = parsed;
		}
		else
		{
			this.url = url;
			this.priority = 9; // default prio set set to 9, so that once that do set it can get in front of these.
		}
	}

	/**
	 * @return the url
	 */
	public String getUrl()
	{
		return url;
	}

	/**
	 * @return the priority
	 */
	public int getPriority()
	{
		return priority;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof CssLib)
		{
			return this.url.equals(((CssLib)obj).url);
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return this.url.hashCode();
	}

	@Override
	public int compareTo(CssLib o)
	{
		if (this.url.equals(o.url)) return 0;
		int compare = o.priority - this.priority; // lower prio should be higher in the list
		if (compare == 0)
		{
			compare = this.url.compareTo(o.url);
		}
		return compare;
	}

	@Override
	public String toString()
	{
		return "CssLib[" + this.url + ";priority=" + this.priority + ']';
	}
}
