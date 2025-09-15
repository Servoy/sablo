package org.sablo.filter;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionActivationListener;
import jakarta.servlet.http.HttpSessionEvent;

/**
 * Separate out the default session with another session based on a list of paths.
 * <p>
 * This filter will allow 2 http sessions based on one underlying session.
 * Only when both sessions are invalidated will the underlying session be invalidated.
 * <p>
 * Parameters:
 * <ul>
 * <li>paths: list of initial paths (separated by ':') of the requests that are part of the separate session.
 * <br>Request not matching any of these paths are assigned to the standard session.
 * </ul>
 * <p>
 * Example web.xml snippet:
 * <pre>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;SeparateSessionFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.sablo.filter.SeparateSessionFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;paths&lt;/param-name&gt;
 *       &lt;param-value&gt;/rfb/:/websocket/:/solutions/:/spec/:/resources/&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;SeparateSessionFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 * &lt;/filter-mapping&gt;
 * </pre>
 *
 *
 * @author rgansevles
 */
public class SeparateSessionFilter implements Filter
{
	// config
	private List<String> paths = emptyList();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
		String pathsConfig = filterConfig.getInitParameter("paths");
		if (pathsConfig != null)
		{
			paths = stream(pathsConfig.split(":")).map(String::trim).filter(string -> !string.isEmpty()).collect(toList());
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
	{
		if (!paths.isEmpty() && request instanceof HttpServletRequest)
		{
			chain.doFilter(new SeparateSessionRequestWrapper(this, (HttpServletRequest)request), response);
		}
		else
		{
			chain.doFilter(request, response);
		}
	}

	@Override
	public void destroy()
	{
	}

	boolean isSeparateRequest(String requestPath)
	{
		return paths.stream().anyMatch(requestPath::startsWith);
	}

	private static class SeparateSessionData implements Serializable, HttpSessionActivationListener
	{
		private boolean separatePresent = false;
		private boolean nonseparatePresent = false;
		private Integer defaultMaxInactiveInterval = null;
		private Integer separateMaxInactiveInterval = null;
		private Integer nonseparateMaxInactiveInterval = null;
		private final Map<String, String> separateAttributeNames = new ConcurrentHashMap<>();
		private final Map<String, String> nonseparateAttributeNames = new ConcurrentHashMap<>();

		synchronized void register(boolean isSeparateRequest)
		{
			if (isSeparateRequest)
			{
				separatePresent = true;
			}
			else
			{
				nonseparatePresent = true;
			}
		}

		/**
		 * deregister session id for separate session, return whether this was only session for this id.
		 */
		synchronized boolean deregister(boolean isSeparateRequest)
		{
			if (isSeparateRequest)
			{
				separatePresent = false;
				separateMaxInactiveInterval = null;
				separateAttributeNames.clear();
				return !nonseparatePresent;
			}
			else
			{
				nonseparatePresent = false;
				nonseparateMaxInactiveInterval = null;
				nonseparateAttributeNames.clear();
				return !separatePresent;
			}
		}

		@Override
		public void sessionWillPassivate(HttpSessionEvent se)
		{
			// if this happens we need to set the timout interval back to the default, else the next time the session will never be cleared.
			Integer interval = getDefaultMaxInactiveInterval();
			if (interval != null)
				se.getSession().setMaxInactiveInterval(interval.intValue());
		}

		Integer getDefaultMaxInactiveInterval()
		{
			return defaultMaxInactiveInterval;
		}

		void setDefaultMaxInactiveInterval(int interval)
		{
			defaultMaxInactiveInterval = Integer.valueOf(interval);
		}

		void setMaxInactiveIntervalSeparateHttpSession(boolean isSeparateRequest, int interval)
		{
			if (isSeparateRequest)
			{
				separateMaxInactiveInterval = Integer.valueOf(interval);
			}
			else
			{
				nonseparateMaxInactiveInterval = Integer.valueOf(interval);
			}
		}

		Integer getEffectiveMaxInactiveInterval()
		{
			if (separateMaxInactiveInterval != null)
			{
				if (nonseparateMaxInactiveInterval != null)
				{
					// determine highest (longest interval)
					if (separateMaxInactiveInterval.intValue() <= 0 || nonseparateMaxInactiveInterval.intValue() <= 0)
					{
						return Integer.valueOf(0);
					}
					return Integer.valueOf(Math.max(separateMaxInactiveInterval.intValue(), nonseparateMaxInactiveInterval.intValue()));
				}

				return separateMaxInactiveInterval;
			}

			if (nonseparateMaxInactiveInterval != null)
			{
				return nonseparateMaxInactiveInterval;
			}

			return defaultMaxInactiveInterval;
		}

		void registerAttributeName(boolean separateRequest, String name)
		{
			(separateRequest ? separateAttributeNames : nonseparateAttributeNames).put(name, name);
		}

		Collection<String> getAttributeNames(boolean separateRequest)
		{
			return new HashSet<>((separateRequest ? separateAttributeNames : nonseparateAttributeNames).values());
		}
	}

	private static class SeparateSessionRequestWrapper extends HttpServletRequestWrapper
	{
		private static final String SEPARATE_SESSION_DATA_ATTRIBUTE = "separateSessionFilter.data";

		private final SeparateSessionFilter separateSessionFilter;

		SeparateSessionRequestWrapper(SeparateSessionFilter separateSessionFilter, HttpServletRequest request)
		{
			super(request);
			this.separateSessionFilter = separateSessionFilter;
		}

		@Override
		public HttpServletRequest getRequest()
		{
			return (HttpServletRequest)super.getRequest();
		}

		@Override
		public HttpSession getSession()
		{
			return getSession(true);
		}

		@Override
		public HttpSession getSession(boolean create)
		{
			HttpSession originalSession = getRequest().getSession(create);
			if (originalSession == null)
			{
				return null;
			}

			String requestPath = getRequestURI().substring(getContextPath().length());
			boolean isSeparateRequest = separateSessionFilter.isSeparateRequest(requestPath);
			SeparateHttpSession separateHttpSession = new SeparateHttpSession(this, originalSession, isSeparateRequest);
			registerSession(separateHttpSession);
			return separateHttpSession;
		}

		void registerSession(SeparateHttpSession separateHttpSession)
		{
			SeparateSessionData separateSessionData = getSeparateSessionData(separateHttpSession);
			separateSessionData.register(separateHttpSession.isSeparateRequest());
		}

		void setMaxInactiveIntervalSeparateHttpSession(SeparateHttpSession separateHttpSession, int interval)
		{
			SeparateSessionData separateSessionData = getSeparateSessionData(separateHttpSession);

			if (separateSessionData.getDefaultMaxInactiveInterval() == null)
			{
				separateSessionData.setDefaultMaxInactiveInterval(separateHttpSession.getOriginalSession().getMaxInactiveInterval());
			}

			separateSessionData.setMaxInactiveIntervalSeparateHttpSession(separateHttpSession.isSeparateRequest(), interval);

			Integer effectiveInterval = separateSessionData.getEffectiveMaxInactiveInterval();
			separateHttpSession.getOriginalSession().setMaxInactiveInterval(effectiveInterval.intValue());
		}

		private SeparateSessionData getSeparateSessionData(SeparateHttpSession separateHttpSession)
		{
			HttpSession originalSession = separateHttpSession.getOriginalSession();
			synchronized (originalSession)
			{
				SeparateSessionData separateSessionData = (SeparateSessionData)originalSession.getAttribute(SEPARATE_SESSION_DATA_ATTRIBUTE);
				if (separateSessionData == null)
				{
					separateSessionData = new SeparateSessionData();
					originalSession.setAttribute(SEPARATE_SESSION_DATA_ATTRIBUTE, separateSessionData);
				}
				return separateSessionData;
			}
		}

		/**
		 * Invalidate the original session only if both sessions are invalidated.
		 */
		void invalidateSeparateHttpSession(SeparateHttpSession separateHttpSession)
		{
			SeparateSessionData separateSessionData = getSeparateSessionData(separateHttpSession);
			Collection<String> attributeNames = separateSessionData.getAttributeNames(separateHttpSession.isSeparateRequest());
			boolean wasLast = separateSessionData.deregister(separateHttpSession.isSeparateRequest());
			if (wasLast)
			{
				// not used in other app
				separateHttpSession.getOriginalSession().invalidate();
			}
			else
			{
				// remove attributes and restore maxinterval
				HttpSession originalSession = separateHttpSession.getOriginalSession();

				attributeNames.forEach(originalSession::removeAttribute);

				Integer effectiveMaxInactiveInterval = separateSessionData.getEffectiveMaxInactiveInterval();
				if (effectiveMaxInactiveInterval != null)
				{
					originalSession.setMaxInactiveInterval(effectiveMaxInactiveInterval.intValue());
				}
			}
		}

		void registerAttributeName(SeparateHttpSession separateHttpSession, String name)
		{
			getSeparateSessionData(separateHttpSession).registerAttributeName(separateHttpSession.isSeparateRequest(), name);
		}
	}

	private static class SeparateHttpSession implements HttpSession
	{
		private final SeparateSessionRequestWrapper separateSessionRequestWrapper;
		private final HttpSession originalSession;
		private final boolean isSeparateRequest;

		SeparateHttpSession(SeparateSessionRequestWrapper separateSessionRequestWrapper, HttpSession originalSession, boolean isSeparateRequest)
		{
			this.separateSessionRequestWrapper = separateSessionRequestWrapper;
			this.originalSession = originalSession;
			this.isSeparateRequest = isSeparateRequest;
		}

		HttpSession getOriginalSession()
		{
			return originalSession;
		}

		boolean isSeparateRequest()
		{
			return isSeparateRequest;
		}

		public long getCreationTime()
		{
			return originalSession.getCreationTime();
		}

		public String getId()
		{
			return originalSession.getId();
		}

		public long getLastAccessedTime()
		{
			return originalSession.getLastAccessedTime();
		}

		public ServletContext getServletContext()
		{
			return originalSession.getServletContext();
		}

		public void setMaxInactiveInterval(int interval)
		{
			separateSessionRequestWrapper.setMaxInactiveIntervalSeparateHttpSession(this, interval);
		}

		public int getMaxInactiveInterval()
		{
			return originalSession.getMaxInactiveInterval();
		}

		public Object getAttribute(String name)
		{
			return originalSession.getAttribute(name);
		}

		public Enumeration<String> getAttributeNames()
		{
			return originalSession.getAttributeNames();
		}

		public void setAttribute(String name, Object value)
		{
			separateSessionRequestWrapper.registerAttributeName(this, name);
			originalSession.setAttribute(name, value);
		}

		public void removeAttribute(String name)
		{
			originalSession.removeAttribute(name);
		}

		public void invalidate()
		{
			separateSessionRequestWrapper.invalidateSeparateHttpSession(this);
		}

		public boolean isNew()
		{
			return originalSession.isNew();
		}
	}
}
