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

package org.sablo.websocket;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.IllegalChangeFromClientException;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The websocket endpoint for communication between the WebSocketWindow instance on the server and the browser.
 * This class handles:
 * <ul>
 * <li>creating of websocket sessions and rebinding after reconnect
 * <li>messages protocol with request/response
 * <li>messages protocol with data conversion (currently only date)
 * <li>service calls (both server to client and client to server)
 * </ul>
 *
 * @author jcompagner, rgansevles
/*/
public abstract class WebsocketEndpoint implements IWebsocketEndpoint
{
	public static final Logger log = LoggerFactory.getLogger(WebsocketEndpoint.class.getCanonicalName());

	public IMessageLogger messageLogger;

	/*
	 * connection with browser
	 */

	private final String endpointType;

	private volatile Session session;

	private volatile IWindow window;

	private final Map<Integer, List<Object>> pendingMessages = new HashMap<>();

	private final AtomicLong lastPingTime = new AtomicLong(System.currentTimeMillis());

	private String logInfo = ""; //$NON-NLS-1$

	public WebsocketEndpoint(String endpointType)
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
	 * @return the window
	 */
	public IWindow getWindow()
	{
		return window;
	}

	@Override
	public Session getSession()
	{
		return session;
	}

	public void start(Session newSession, String clntnr, String winname, String winnr) throws Exception
	{
		this.session = newSession;

		HttpSession httpSession = null;
		int clientnr = -1;
		int windowNr = -1;
		String windowName = null;
		try
		{
			clientnr = "null".equalsIgnoreCase(clntnr) ? -1 : Integer.parseInt(clntnr); //$NON-NLS-1$
			windowNr = "null".equalsIgnoreCase(winnr) ? -1 : Integer.parseInt(winnr); //$NON-NLS-1$
			windowName = "null".equalsIgnoreCase(winname) ? null : winname; //$NON-NLS-1$
			httpSession = getHttpSession(newSession);
		}
		catch (Exception e)
		{
			// ignore, parse errors of clntnr or the connect_nr is not given, if this happening then old illegal clients are reconnection make sure we just cancel the session
			httpSession = null;
		}
		if (httpSession == null)
		{
			List<String> connectNr = session.getRequestParameterMap().get(GetHttpSessionConfigurator.CONNECT_NR);
			// this can happen when the server is restarted and the client reconnects the websocket
			log.warn(
				"Cannot find httpsession for websocket session, server restarted or client was suspended by the browser/os and did a websocket reconnect after the client was already destroyed at the server? clientnr=" +
					clntnr + ", winnr=" + winnr + ", winname=" + winname +
					", connectnr: " + connectNr);
			cancelSession(CLOSE_REASON_CLIENT_OUT_OF_SYNC);
			return;
		}
		// if the request contains a lastServerMessageNumber then test if there is an existing session:
		if (session.getRequestParameterMap().containsKey("lastServerMessageNumber") &&
			WebsocketSessionManager.getOrCreateSession(endpointType, httpSession, clientnr, false) == null)
		{
			// client is out of sync because the session was already gone but it does send a lastServerMessageNumber
			// make sure we do a full refresh. This could be a server restart with multiply tabs open in the same browser.
			cancelSession(CLOSE_REASON_CLIENT_OUT_OF_SYNC);
			return;
		}
		IWebsocketSession wsSession = WebsocketSessionManager.getOrCreateSession(endpointType, httpSession, clientnr, true);

		if (wsSession == null)
		{
			// illegal state, no more session make sure the client is closed
			closeSession();
			log.info(getClass().getName() + ": no websocket session anymore, shouldn't happen, hanging editors browsers?");
			return;
		}
		CurrentWindow.set(window = wsSession.getOrCreateWindow(windowNr, windowName));

		messageLogger = wsSession.getMessageLogger(window);

		try
		{
			final IWindow win = window;

			wsSession.init(session.getRequestParameterMap());

			// send initial setup to client in separate thread in order to release current connection
			wsSession.getEventDispatcher().addEvent(new Runnable()
			{
				@Override
				public void run()
				{
					win.setEndpoint(WebsocketEndpoint.this);
					if (CurrentWindow.safeGet() == win && session != null) // window or session my already be closed
					{
						if (messageLogger != null) messageLogger.endPointStarted(session);
						win.onOpen(session.getRequestParameterMap());
						onStart();
						if (session != null && session.isOpen())
						{
							wsSession.onOpen(session.getRequestParameterMap());
							if (wsSession.getHttpSession() != null)
							{
								wsSession.getHttpSession().getId();
								logInfo = wsSession.getLogInformation();
							}
						}
					}
				}
			});
		}
		catch (Exception e)
		{
			handleException(e, wsSession);
			throw e;
		}
		finally
		{
			CurrentWindow.set(null);
		}

		WebsocketSessionManager.closeInactiveSessions();
	}

	/**
	 * @param session
	 */
	protected abstract HttpSession getHttpSession(Session session);

	/**
	 *  Called after from start(), called after the init of the session object.
	 */
	protected void onStart()
	{
	}

	/**
	 * Method to override the behavior to handle exceptions in the start() of this endpoint.
	 *
	 * @param e
	 * @param wsSession
	 */
	protected void handleException(Exception e, IWebsocketSession wsSession)
	{
	}

	@Override
	public void closeSession()
	{
		closeSession(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "CLIENT-SHUTDOWN"));
	}

	@Override
	public void cancelSession(String reason)
	{
		closeSession(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason));
	}

	public void closeSession(CloseReason closeReason)
	{
		if (session != null)
		{
			try
			{
				session.close(closeReason);
			}
			catch (IOException e)
			{
			}
			session = null;
		}
		unbindWindow();
	}

	public void onClose(final CloseReason closeReason)
	{
		if (window != null)
		{
			IEventDispatcher eventDispatcher = window.getSession() == null ? null : window.getSession().getEventDispatcher();
			if (eventDispatcher != null)
			{
				for (final Integer pendingMessageId : pendingMessages.keySet())
				{
					eventDispatcher.addEvent(new Runnable()
					{
						@Override
						public void run()
						{
							eventDispatcher.cancelSuspend(pendingMessageId,
								"Websocket endpoint is closing... (can happen for example due to a full browser refresh). Close reason code: " +
									closeReason.getCloseCode());
						}

					}, IEventDispatcher.EVENT_LEVEL_SYNC_API_CALL);
				}
				pendingMessages.clear();

				eventDispatcher.addEvent(new Runnable()
				{
					@Override
					public void run()
					{
						unbindWindow();
					}
				});
			}
			else
			{
				// When we have a session without client no need to run events. This can happen when we get requests for a non-existing soluti
				unbindWindow();
			}

		}
		session = null;

		if (closeReason.getCloseCode() != CloseCodes.SERVICE_RESTART) WebsocketSessionManager.closeInactiveSessions();
	}

	private void unbindWindow()
	{
		IWindow currentWindow;
		synchronized (this)
		{
			currentWindow = window;
			window = null;
		}
		if (currentWindow != null)
		{
			if (messageLogger != null) messageLogger.endPointClosed();
			currentWindow.setEndpoint(null);
		}
	}

	public void onError(Throwable t)
	{
		if (t instanceof IOException)
		{
			log.info("IOException happened, " + logInfo, t.getMessage()); // TODO if it has no message but has a 'cause' it will not print anything useful
		}
		else
		{
			log.error(t.getMessage(), t);
		}
	}

	private final StringBuilder incomingPartialMessage = new StringBuilder();

	public void incoming(String msg, boolean lastPart)
	{
		String message = msg;
		if (!lastPart)
		{
			incomingPartialMessage.append(message);
			return;
		}
		if (incomingPartialMessage.length() > 0)
		{
			incomingPartialMessage.append(message);
			message = incomingPartialMessage.toString();
			incomingPartialMessage.setLength(0);
		}
		// always set last ping time for any kind of message.
		lastPingTime.set(System.currentTimeMillis());
		// handle heartbeats
		if ("P".equals(message)) // ping
		{
			try
			{
				sendText("p"); // pong, has to be synchronized to prevent pong to interfere with regular messages
			}
			catch (IOException e)
			{
				log.warn("could not reply to ping message for window " + window + "," + logInfo, e);
			}
			return;
		}
		else if ("p".equals(message)) // pong
		{
			return;
		}
		if (window == null)
		{
			log.info("incomming message " + msg + " but the window is already unbinded");
			return;
		}

		CurrentWindow.set(window);
		try
		{
			if (messageLogger != null) messageLogger.messageReceived(message);
			final JSONObject obj = new JSONObject(message);

			if (obj.has("smsgid"))
			{
				window.getSession().getEventDispatcher().addEvent(new Runnable()
				{

					@Override
					public void run()
					{
						// response message
						Integer suspendID = new Integer(obj.optInt("smsgid"));
						List<Object> ret = pendingMessages.remove(suspendID);
						if (ret != null)
						{
							ret.add(obj.opt("ret")); // first element is return value - even if it's null; TODO we should handle here javascript undefined as well (instead of treating it as null)
							if (obj.has("err")) ret.add(obj.opt("err")); // second element is added only if an error happened while calling api in browser
						}
						else log.error(
							"Discarded response for obsolete pending message (it probably timed - out waiting for response before it got one): " + suspendID +
								", " + logInfo);

						window.getSession().getEventDispatcher().resume(suspendID);
					}

				}, IEventDispatcher.EVENT_LEVEL_SYNC_API_CALL);
			}

			else if (obj.has("service"))
			{
				// service call
				final String serviceName = obj.optString("service");
				final IServerService service = window.getSession().getServerService(serviceName);

				if (service != null)
				{
					final String methodName = obj.optString("methodname");
					final int prio = obj.has("prio") ? obj.optInt("prio", IEventDispatcher.EVENT_LEVEL_DEFAULT) : IEventDispatcher.EVENT_LEVEL_DEFAULT;
					final JSONObject arguments = obj.optJSONObject("args");
					int eventLevel = (service instanceof IEventDispatchAwareServerService)
						? ((IEventDispatchAwareServerService)service).getMethodEventThreadLevel(methodName, arguments, prio) : prio;

					window.getSession().getEventDispatcher().addEvent(new Runnable()
					{
						@Override
						public void run()
						{
							Object result = null;
							String error = null;
							try
							{
								result = service.executeMethod(methodName, arguments);
							}
							catch (ParseException pe)
							{
								log.warn("Warning: " + pe.getMessage(), pe);
							}
							catch (IllegalChangeFromClientException | IllegalAccessException e)
							{
								log.warn("Warning: " + e.getMessage());
							}
							catch (Exception e)
							{
								error = "Error: " + e.getMessage();
								log.error(error, e);
							}

							final Object msgId = obj.opt("cmsgid");
							if (msgId != null) // client wants response
							{
								if (session == null && window == null)
								{
									// this websocket endpoint is already closed, ignore the stuff that can't be send..
									log.warn(
										"return value of the service call: " + methodName + " is ignored because the websocket is already closed." + logInfo);
								}
								else
								{
									try
									{
										getWindow().setClientToServerCallReturnValueForChanges(
											new ClientToServerCallReturnValue(error == null ? result : error, error == null, msgId));
										getWindow().sendChanges();
									}
									catch (IOException e)
									{
										log.warn(e.getMessage(), e);
									}
								}
							}
						}
					}, eventLevel);
				}
				else
				{
					log.info("Unknown service called from the client: " + serviceName + "," + logInfo);
				}
			}
			else if (obj.has("servicedatapush"))
			{
				final String serviceScriptingName = obj.optString("servicedatapush");
				final IClientService service = window.getSession().getClientServiceByScriptingName(serviceScriptingName);
				if (service != null)
				{
					final int eventLevel = obj.optInt("prio", IEventDispatcher.EVENT_LEVEL_DEFAULT);

					window.getSession().getEventDispatcher().addEvent(new Runnable()
					{
						@Override
						public void run()
						{
							try
							{
								JSONObject changes = obj.optJSONObject("changes");
								Iterator keys = changes.keys();
								while (keys.hasNext())
								{
									String key = (String)keys.next();
									service.putBrowserProperty(key, changes.opt(key));
								}
							}
							catch (JSONException e)
							{
								log.error("JSONException while executing service " + serviceScriptingName + " datachange." + logInfo, e);
								return;
							}
						}
					}, eventLevel);
				}
				else
				{
					log.info("Unknown service datapush from client; ignoring: " + serviceScriptingName + "," + logInfo);
				}

			}

			else
			{
				window.getSession().handleMessage(obj);
			}
		}
		catch (JSONException e)
		{
			log.error("JSONException while processing message from client, " + logInfo, e);
			return;
		}
		finally
		{
			CurrentWindow.set(null);
		}

	}

	public void sendText(int messageNumber, String text) throws IOException
	{
		if (messageLogger != null) messageLogger.messageSend(text);
		sendText(messageNumber + "#" + text);
	}

	public synchronized void sendText(String txt) throws IOException
	{
		if (session == null)
		{
			throw new IOException("No session to send the message, " + logInfo);
		}
		session.getBasicRemote().sendText(txt);
	}


	/**
	 * Wait for a response message with given messsageId.
	 *
	 * @throws TimeoutException see {@link IEventDispatcher#suspend(Object, int, long)} for more details.
	 * @throws CancellationException see {@link IEventDispatcher#suspend(Object, int, long)} for more details.
	 */
	public Object waitResponse(Integer messageId, boolean blockEventProcessing) throws IOException, CancellationException, TimeoutException
	{
		List<Object> ret = new ArrayList<>(2); // size is 0 now; 1st element will be return value (should always be set by callback even if it's null); second is only set when an error happened client-side
		pendingMessages.put(messageId, ret);

		window.getSession().getEventDispatcher().suspend(messageId,
			blockEventProcessing ? IEventDispatcher.EVENT_LEVEL_SYNC_API_CALL : IEventDispatcher.EVENT_LEVEL_DEFAULT,
			blockEventProcessing ? EventDispatcher.CONFIGURED_TIMEOUT : IEventDispatcher.NO_TIMEOUT);

		if (ret.size() == 2)
		{
			// this means an error happened on client
			throw new RuntimeException(String.valueOf(ret.get(1)));
		}
		else if (ret.size() != 1)
		{
			if (ret.size() == 0 && session == null && window == null)
			{
				throw new CancellationException("Websocket session was closed while waiting for client response, " + logInfo);
			}
			throw new RuntimeException("Unexpected: Incorrect return value (" + ret.size() +
				" - not even null/undefined) from client for message (could be due to a close/exit cancelling all pending sync req. to client). Content: " +
				ret + ", " + logInfo);
		}

		return ret.get(0);
	}

	public boolean hasSession()
	{
		return session != null && session.isOpen();
	}

	public long getLastPingTime()
	{
		return lastPingTime.get();
	}

}
