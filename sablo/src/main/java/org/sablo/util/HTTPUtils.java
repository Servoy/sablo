/*
 * Copyright (C) 2015 Servoy BV
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Class that provides static utility methods related to HTTP.
 *
 * @author acostescu
 */

public class HTTPUtils
{

	public static final String IF_MODIFIED_SINCE = "If-Modified-Since"; //$NON-NLS-1$
	public static final String LAST_MODIFIED = "Last-Modified"; //$NON-NLS-1$

	/**
	 * This method tries to make peace between different browsers, versions and browser bugs for no-caching response headers.<br>
	 * It will set response headers to prevent the response from being cached.
	 * @param response the HTTP response on which header fields will be set.
	 */
	public static void setNoCacheHeaders(HttpServletResponse response)
	{
		setNoCacheHeaders(response, null);
	}

	/**
	 * This method tries to make peace between different browsers, versions and browser bugs for no-caching response headers.<br>
	 * It will set response headers to prevent the response from being cached.
	 * @param response the HTTP response on which header fields will be set.
	 * @param extraCacheControl some extra cache control additions.
	 */
	@SuppressWarnings("nls")
	public static void setNoCacheHeaders(HttpServletResponse response, String extraCacheControl)
	{
		String cacheControl = "max-age=0, must-revalidate, proxy-revalidate";
		if (extraCacheControl != null)
		{
			cacheControl += "," + extraCacheControl;
		}
		response.setHeader("Cache-Control", cacheControl); //HTTP 1.1
		response.setHeader("Expires", "0");//$NON-NLS-1$ // mentioned as an invalid (but used anyway in HTTP 1.0) value which MUST be interpreted correctly in HTTP 1.1 specs. (this means interpreted as no-cache); we can use this to avoid incorrectly synced clocks that would cause problems if expires clause would be used as described in specs.

//		if (request.isSecure())
//		{
//			response.setDateHeader("Expires", System.currentTimeMillis() + 5000);//$NON-NLS-1$
//		}
//		else
//		{
//		    ... do normal headers
//		}
//		//prevents save to disk in https, Expires should be enough
//		response.setHeader("Proxy", "no-cache"); //$NON-NLS-1$//$NON-NLS-2$
//		response.setHeader("Pragma", "no-cache"); //HTTP 1.0 //$NON-NLS-1$ //$NON-NLS-2$ // Pragma: no-cache is meant to be used in requests, not responses; in responses it's only taken into consideration by some browsers, such as IE in https, but this also results in a bug for IE 6
//		//seems not to work in IE
//		response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
//		response.setHeader("Cache-Control","no-store"); //HTTP 1.1
//
		//let cache for 5 seconds in browser, workarround for IE browser (which has a known bug with https and expires); see http://support.microsoft.com/kb/323308
//		response.setDateHeader("Expires", System.currentTimeMillis() + 5000);//$NON-NLS-1$ // works except for when system clocks are out of sync or when you do the operation in less then 5 secs.
	}

	/**
	 * Checks if a requested resource has been modified or not. It will also set the "Last-Modified" header in response.<br><br>
	 * If it has not been modified, it will return true and set response status HttpServletResponse.SC_NOT_MODIFIED.
	 * If it has been modified or request didn't want to use this check it will just return false.
	 * @param lastModifiedTime the last modification time (millis) of the requested resource.
	 */
	public static boolean checkAndSetUnmodified(HttpServletRequest servletRequest, HttpServletResponse servletResponse, long lastModifiedTime)
	{
		long l = lastModifiedTime / 1000 * 1000;
		servletResponse.setDateHeader(LAST_MODIFIED, l);
		long lm = servletRequest.getDateHeader(IF_MODIFIED_SINCE);
		if (lm != -1 && lm == l)
		{
			servletResponse.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return true;
		}
		return false;
	}


	/**
	 * Generate a query string in application/x-www-form-urlencoded format from a http request parametermap.
	 *
	 * @param parameterMap
	 * @return
	 * @throws ServletException
	 */
	public static String generateQueryString(Map<String, String[]> parameterMap) throws ServletException
	{
		StringBuilder queryString = new StringBuilder();
		for (Entry<String, String[]> parameter : parameterMap.entrySet())
		{
			for (String value : parameter.getValue())
			{
				if (queryString.length() > 0)
				{
					queryString.append('&');
				}

				try
				{
					queryString.append(URLEncoder.encode(parameter.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(value, "UTF-8"));
				}
				catch (UnsupportedEncodingException e)
				{
					throw new ServletException(e);
				}
			}
		}
		return queryString.toString();
	}


}
