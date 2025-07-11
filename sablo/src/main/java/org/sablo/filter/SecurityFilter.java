/*
 * Copyright (C) 2019 Servoy BV
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

package org.sablo.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author rgansevles
 *
 */
public class SecurityFilter implements Filter
{
	private static final ThreadLocal<String> CURRENT_HOST_HEADER = new ThreadLocal<>();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
	{
		try
		{
			CURRENT_HOST_HEADER.set(((HttpServletRequest)request).getHeader("Host"));
			chain.doFilter(request, response);
		}
		finally
		{
			CURRENT_HOST_HEADER.remove();
		}
	}

	@Override
	public void destroy()
	{
	}

	public static String getCurrentHostHeader()
	{
		return CURRENT_HOST_HEADER.get();
	}

}