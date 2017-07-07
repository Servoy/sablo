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

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.IllegalComponentAccessException;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.BrowserConverterContext;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
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

	/*
	 * connection with browser
	 */

	private final String endpointType;

	private volatile Session session;

	private volatile IWindow window;

	private final Map<Integer, List<Object>> pendingMessages = new HashMap<>();

	private final AtomicLong lastPingTime = new AtomicLong(System.currentTimeMillis());

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

	public void start(Session newSession, String sessionid, String winname, final String winid) throws Exception
	{
		this.session = newSession;

		String uuid = "null".equalsIgnoreCase(sessionid) ? null : sessionid;
		String windowId = "null".equalsIgnoreCase(winid) ? null : winid;
		String windowName = "null".equalsIgnoreCase(winname) ? null : winname;

		final IWebsocketSession wsSession = WebsocketSessionManager.getOrCreateSession(endpointType, uuid, true);

		CurrentWindow.set(window = wsSession.getOrCreateWindow(windowId, windowName));

		try
		{
			final IWindow win = window;

			wsSession.init();

			// send initial setup to client in separate thread in order to release current connection
			wsSession.getEventDispatcher().addEvent(new Runnable()
			{
				@Override
				public void run()
				{
					win.setEndpoint(WebsocketEndpoint.this);

					if (CurrentWindow.safeGet() == win && session != null) // window or session my already be closed
					{
						win.onOpen(session.getRequestParameterMap());
						if (session.isOpen())
						{
							wsSession.onOpen(session.getRequestParameterMap());
						}
					}
				}
			});
		}
		finally
		{
			CurrentWindow.set(null);
		}

		WebsocketSessionManager.closeInactiveSessions();
	}

	@Override
	public void closeSession()
	{
		closeSession(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Application Server shutdown!!!!!!!"));
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
		window = null;
	}

	public void onClose(final CloseReason closeReason)
	{
		if (window != null)
		{
			if (window.getSession() != null)
			{
				final IEventDispatcher eventDispatcher = window.getSession().getEventDispatcher();
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
				// this should never happen; window.getSession() can never be null (session is a final field in BaseWindow that is always initialized with non-null values)
				// (I didn't comment out the if/else though because all this goes through interfaces that don't guarantee that...)
				unbindWindow();
			}

		}
		session = null;

		if (closeReason.getCloseCode() != CloseCodes.SERVICE_RESTART) WebsocketSessionManager.closeInactiveSessions();
	}

	private void unbindWindow()
	{
		if (window != null)
		{
			window.setEndpoint(null);
			window = null;
		}
	}

	public void onError(Throwable t)
	{
		if (t instanceof IOException)
		{
			log.warn("IOException happened", t.getMessage()); // TODO if it has no message but has a 'cause' it will not print anything useful
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
				log.warn("could not reply to ping message for window " + window, e);
			}
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
							"Discarded response for obsolete pending message (it probably timed - out waiting for response before it got one): " + suspendID);

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
							catch (IllegalComponentAccessException ilcae)
							{
								log.warn("Warning: " + ilcae.getMessage());
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
									log.warn("return value of the service call: " + methodName + " is ignored because the websocket is already closed");
								}
								else
								{
									try
									{
										if (error == null)
										{
											Object resultObject = result;
											PropertyDescription objectType = null;
											if (result instanceof TypedData)
											{
												resultObject = ((TypedData< ? >)result).content;
												objectType = ((TypedData< ? >)result).contentType;
											}
											getWindow().sendChanges();
											sendResponse(msgId, resultObject, objectType, true);
										}
										else
										{
											getWindow().sendChanges();
											sendResponse(msgId, error, null, false);
										}
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
					log.info("Unknown service called from the client: " + serviceName);
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
								log.error("JSONException while executing service " + serviceScriptingName + " datachange.", e);
								return;
							}
						}
					}, eventLevel);
				}
				else
				{
					log.info("Unknown service datapush from client; ignoring: " + serviceScriptingName);
				}

			}

			else
			{
				window.getSession().handleMessage(obj);
			}
		}
		catch (JSONException e)
		{
			log.error("JSONException while processing message from client:", e);
			return;
		}
		finally
		{
			CurrentWindow.set(null);
		}

	}

	protected void sendResponse(Object msgId, Object object, PropertyDescription objectType, boolean success) throws IOException
	{
		Map<String, Object> data = new HashMap<>();
		String key = success ? "ret" : "exception";
		data.put(key, object);
		data.put("cmsgid", msgId);
		PropertyDescription dataTypes = null;
		if (objectType != null)
		{
			dataTypes = AggregatedPropertyType.newAggregatedProperty();
			dataTypes.putProperty(key, objectType);
		}

		try
		{
			sendText(window.getNextMessageNumber(), JSONUtils.writeDataWithConversions(FullValueToJSONConverter.INSTANCE, data, dataTypes,
				BrowserConverterContext.NULL_WEB_OBJECT_WITH_NO_PUSH_TO_SERVER));
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	public void sendText(int messageNumber, String text) throws IOException
	{
		sendText(messageNumber + "#" + text);
	}

	public synchronized void sendText(String txt) throws IOException
	{
		if (session == null)
		{
			throw new IOException("No session");
		}
		session.getBasicRemote().sendText(txt);
	}


	/**
	 * Wait for a response message with given messsageId.
	 *
	 * @throws TimeoutException see {@link IEventDispatcher#suspend(Object, int, long)} for more details.
	 * @throws CancellationException see {@link IEventDispatcher#suspend(Object, int, long)} for more details.
	 */
	public Object waitResponse(Integer messageId, String text, boolean blockEventProcessing) throws IOException, CancellationException, TimeoutException
	{
		List<Object> ret = new ArrayList<>(2); // 1st element is return value; should always be set by callback even if it's
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
			throw new RuntimeException("Unexpected: Incorrect return value (" + ret.size() +
				" - even null/undefined) from client for message even though it seems to have received a response. Content: " + ret);
		}

		return ret.get(0);
	}

	public boolean hasSession()
	{
		return session != null;
	}

	public long getLastPingTime()
	{
		return lastPingTime.get();
	}

}
