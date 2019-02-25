package org.sablo.websocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	 *
	 */
	private static final String CONNECT_NR = "connectNr";
	private static final Map<String, HttpSession> SESSIONMAP = new HashMap<>();

	@Override
	public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
	{
		List<String> connectNr = request.getParameterMap().get(CONNECT_NR);
		if (connectNr == null || connectNr.size() != 1)
		{
			throw new IllegalArgumentException("connectNr request parameter missing");
		}

		SESSIONMAP.put(connectNr.get(0), (HttpSession)request.getHttpSession());
	}

	public static HttpSession getHttpSession(Session session)
	{
		List<String> connectNr = session.getRequestParameterMap().get(CONNECT_NR);
		if (connectNr == null || connectNr.size() != 1)
		{
			throw new IllegalArgumentException("connectNr session parameter missing");
		}
		HttpSession httpSession = SESSIONMAP.remove(connectNr.get(0));
		if (httpSession == null)
		{
			throw new IllegalArgumentException("no http session?");
		}
		return httpSession;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.websocket.server.ServerEndpointConfig.Configurator#checkOrigin(java.lang.String)
	 */
//	@Override
//	public boolean checkOrigin(String originHeaderValue)
//	{
//		// TODO Auto-generated method stub
//		return super.checkOrigin(originHeaderValue);
//	}
}