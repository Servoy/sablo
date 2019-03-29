package org.sablo.websocket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

/**
 * Store the http session for a very short time, until picked up from openSession.
 */
public class GetHttpSessionConfigurator extends Configurator
{
	/**
	 * Connect nr parameter, used to link http call with http session to websocket session.
	 */
	private static final String CONNECT_NR = "connectNr";
	private static final Map<String, HttpSession> SESSIONMAP = new ConcurrentHashMap<>();

	@Override
	public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
	{
		HttpSession httpSession = (HttpSession)request.getHttpSession();
		if (httpSession != null)
		{
			List<String> connectNr = request.getParameterMap().get(CONNECT_NR);
			if (connectNr == null || connectNr.size() != 1)
			{
				throw new IllegalArgumentException("connectNr request parameter missing");
			}

			SESSIONMAP.put(connectNr.get(0), httpSession);
		}
	}

	public static HttpSession getHttpSession(Session session)
	{
		List<String> connectNr = session.getRequestParameterMap().get(CONNECT_NR);
		if (connectNr == null || connectNr.size() != 1)
		{
			throw new IllegalArgumentException("connectNr session parameter missing");
		}
		HttpSession httpSession = SESSIONMAP.remove(connectNr.get(0));
		if (httpSession != null)
		{
			httpSession.setMaxInactiveInterval(0); // make sure it never expires
		}

		return httpSession;
	}
}