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

package org.sablo.example;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;

import org.sablo.WebEntry;
import org.sablo.example.endpoint.HelloWorldEndpoint;
import org.sablo.example.endpoint.HelloWorldWebsocketSession;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.IWebsocketSessionFactory;

/**
 * Configuration class to define entry points and factories for sample sablo application.
 * 
 * @author rgansevles
 */

@WebFilter(urlPatterns = { "/*" })
public class HelloWorld extends WebEntry
{
	public HelloWorld()
	{
		super(HelloWorldEndpoint.HELLO_WORLD_ENDPOINT);
	}

	@Override
	public String[] getWebComponentBundleNames()
	{
		return new String[] { "/mycomp" };
	}
	
	@Override
	public String[] getServiceBundleNames()
	{
		return new String[] {  };
	}

	@Override
	protected IWebsocketSessionFactory createSessionFactory()
	{
		return new IWebsocketSessionFactory()
		{
			public IWebsocketSession createSession(String uuid) throws Exception
			{
				return new HelloWorldWebsocketSession(uuid);
			}
		};
	}
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		doFilter(request, response, chain, null, getJsContributions(), null);	
	}

	private Collection<String> getJsContributions()
	{
		return Arrays.asList("forms/mainForm.js", "forms/anotherForm.js");
	}

}
