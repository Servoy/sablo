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

package org.sablo;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.sablo.services.template.ModifiablePropertiesGenerator;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.util.HTTPUtils;
import org.sablo.websocket.GetHttpSessionConfigurator;
import org.sablo.websocket.IWebsocketSessionFactory;
import org.sablo.websocket.WebsocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class to define entry points and factories.
 * Subclasses should carry the @WebFilter annotation
 * @author jblok
 */
public abstract class WebEntry implements Filter, IContributionFilter, IContributionEntryFilter
{
	private static Logger log; // log is initialized lazily, creating a logger before log4j is initialized gives errors.

	private static Logger getLogger()
	{
		if (log == null)
		{
			log = LoggerFactory.getLogger(WebEntry.class.getCanonicalName());
		}
		return log;
	}

	private final String endpointType;

	public WebEntry(String endpointType)
	{
		this.endpointType = endpointType;
	}

	/**
	 * @return the endpointType
	 */
	public String getEndpointType()
	{
		return endpointType;
	}

	/**
	 * Provide all the webcomponent bundle names.
	 * @return the bundle names
	 */
	public abstract String[] getWebComponentBundleNames();

	/**
	 * Provide all the service bundle names.
	 * @return the bundle names
	 */
	public abstract String[] getServiceBundleNames();

	@Override
	public void init(final FilterConfig fc) throws ServletException
	{
		// register the session factory at the manager
		WebsocketSessionManager.setWebsocketSessionFactory(getEndpointType(), createSessionFactory());

		initWebComponentSpecs(fc);

		WebServiceSpecProvider.init(fc.getServletContext(), getServiceBundleNames());
	}

	public void initWebComponentSpecs(FilterConfig fc)
	{
		WebComponentSpecProvider.init(fc.getServletContext(), getWebComponentBundleNames(), null);
	}

	/**
	 * Provide the websocketsessionfactory
	 * @return the factory
	 */
	protected abstract IWebsocketSessionFactory createSessionFactory();

	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain, Collection<String> cssContributions,
		Collection<String> jsContributions, Collection<String> extraMetaData, Map<String, Object> variableSubstitution, boolean setContentSecurityPolicy)
		throws IOException, ServletException
	{
		HttpServletRequest request = (HttpServletRequest)servletRequest;

		// make sure a session is created. when a sablo client is created, that one should set the timeout to 0
		HttpSession httpSession = request.getSession();
		if (getLogger().isDebugEnabled()) getLogger().debug("HttpSession created: " + httpSession);
		// the session should be picked up in a websocket request very soon, set timeout low so it won't stay in case of robots
		// if it is alreayd the time out the GetHttpSessionConfigurator would set then don't reset it to 60
		if (httpSession.getMaxInactiveInterval() != GetHttpSessionConfigurator.NO_EXPIRE_TIMEOUT)
		{
			if (getLogger().isDebugEnabled()) getLogger().debug("Setting 60 seconds timeout on the HttpSession: " + httpSession);
			httpSession.setMaxInactiveInterval(60);
		}

		String uri = request.getRequestURI();
		if (uri.endsWith("spec/" + ModifiablePropertiesGenerator.PUSH_TO_SERVER_BINDINGS_LIST + ".js"))
		{
			long lastSpecLoadTime = Math.max(WebComponentSpecProvider.getLastLoadTimestamp(), WebServiceSpecProvider.getLastLoadTimestamp());
			if (HTTPUtils.checkAndSetUnmodified(((HttpServletRequest)servletRequest), ((HttpServletResponse)servletResponse), lastSpecLoadTime)) return;

			HTTPUtils.setNoCacheHeaders((HttpServletResponse)servletResponse);

			((HttpServletResponse)servletResponse).setContentType("text/javascript");
			((HttpServletResponse)servletResponse).setCharacterEncoding("UTF-8");
			PrintWriter w = servletResponse.getWriter();
			ModifiablePropertiesGenerator.start(w);
			ModifiablePropertiesGenerator.appendAll(w, WebComponentSpecProvider.getSpecProviderState().getAllWebComponentSpecifications(), "components");
			ModifiablePropertiesGenerator.appendAll(w, WebServiceSpecProvider.getSpecProviderState().getAllWebComponentSpecifications(), "services");
			ModifiablePropertiesGenerator.finish(w);
			w.flush();

			return;
		}

		URL indexPageResource = getIndexPageResource(request);
		if (indexPageResource != null)
		{
			((HttpServletResponse)servletResponse).setContentType("text/html");
			((HttpServletResponse)servletResponse).setCharacterEncoding("UTF-8");
			PrintWriter w = servletResponse.getWriter();
			IndexPageEnhancer.enhance(indexPageResource, cssContributions, jsContributions, extraMetaData, variableSubstitution, w, this, this,
				setContentSecurityPolicy);
			w.flush();
			return;
		}

		filterChain.doFilter(servletRequest, servletResponse);
	}

	public List<String> filterCSSContributions(List<String> cssContributions)
	{
		return cssContributions;
	}

	public List<String> filterJSContributions(List<String> jsContributions)
	{
		return jsContributions;
	}

	protected URL getIndexPageResource(HttpServletRequest request) throws IOException
	{
		if ("/index.html".equals(request.getServletPath()))
		{
			return request.getServletContext().getResource(request.getServletPath());
		}
		return null;
	}

	@Override
	public void destroy()
	{
		WebsocketSessionManager.destroy();

		WebComponentSpecProvider.disposeInstance();

		WebServiceSpecProvider.disposeInstance();
	}
}