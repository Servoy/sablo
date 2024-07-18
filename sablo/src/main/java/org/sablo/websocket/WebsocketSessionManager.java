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
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;

import org.sablo.eventthread.IEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Websocket user session management
 * @author jblok, rgansevles
 */
public class WebsocketSessionManager
{
	private static final Logger log = LoggerFactory.getLogger(WebsocketSessionManager.class.getCanonicalName());

	private static final String HTTP_SESSION_COUNTER = "httpSessionCounter";
	private static final String LAST_CLIENT_NUMBER = "lastClientNumber";

	private final static Map<String, IWebsocketSessionFactory> websocketSessionFactories = new HashMap<>();

	private final static ExecutorService expiredThreadPool = Executors.newFixedThreadPool(1, new ThreadFactory()
	{
		@Override
		public Thread newThread(Runnable r)
		{
			return new Thread(r, "Sablo Session closer"); //$NON-NLS-1$
		}
	});

	private static volatile boolean pingEndpointsThreadShouldStop = false;
	private final static Thread pingEndpointsThread = new Thread(new Runnable()
	{
		@Override
		public void run()
		{
			while (!pingEndpointsThreadShouldStop)
			{
				for (IWebsocketSession session : wsSessions.values())
				{
					if (session != null && session.shouldTest())
					{
						long timeout = session.getWindowTimeout() * 1000;
						long wantedLastPingTime = System.currentTimeMillis() - timeout;
						for (IWindow window : session.getWindows())
						{
							try
							{
								if (window.getLastPingTime() > 0 && window.getLastPingTime() < wantedLastPingTime)
								{
									window.getEndpoint().closeSession(
										new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "WebSocket didn't ping for " + timeout + "ms"));
								}
								else
								{
									window.getEndpoint().sendText("P");
								}
							}
							catch (Exception e)
							{
								// ignore not much we can do here.
							}
						}
					}
				}
				try
				{
					Thread.sleep(4000);
				}
				catch (InterruptedException e)
				{
					// ignore
				}
			}
		}
	}, "Endpoint pinger");

	static
	{
		pingEndpointsThread.setDaemon(true);
		pingEndpointsThread.start();
	}

	//maps form WebsocketSessionKey to session
	private final static ConcurrentMap<WebsocketSessionKey, IWebsocketSession> wsSessions = new ConcurrentHashMap<>();

	private final static ReentrantLock closingLock = new ReentrantLock();
	private final static ReentrantLock creatingLock = new ReentrantLock();

	public static void addSession(IWebsocketSession wsSession)
	{
		wsSessions.put(wsSession.getSessionKey(), wsSession);
	}

	public static void removeSession(WebsocketSessionKey sessionKey)
	{
		// if there is a current window, first send all pending changes
		if (CurrentWindow.exists() && CurrentWindow.get().hasEndpoint())
		{
			try
			{
				CurrentWindow.get().sendChanges();
			}
			catch (IOException ioe)
			{
				log.warn("Error sending changes when session is removed", ioe);
			}
			catch (Exception e)
			{
				log.error("Error sending changes when session is removed", e);
			}
		}
		IWebsocketSession websocketSession = wsSessions.remove(sessionKey);
		if (websocketSession != null)
		{
			websocketSession.dispose();
		}
	}

	public static IWebsocketSession getSession(String endpointType, HttpSession httpSession, int clientnr)
	{
		try
		{
			return getOrCreateSession(endpointType, httpSession, clientnr, false);
		}
		catch (Exception e)
		{
			log.error("exception calling getSession (not create) should not happen", e);
		}
		return null;
	}

	/**
	 * This method only throws an exception if the creation of the client fails (so the create boolean is true)
	 *
	 * @param endpointType
	 * @param httpSessionid
	 * @param clientnr
	 * @param create
	 * @return
	 * @throws Exception
	 */
	static IWebsocketSession getOrCreateSession(String endpointType, HttpSession httpSession, int clientnr, boolean create) throws Exception
	{
		IWebsocketSession wsSession = null;
		if (create) creatingLock.lock();
		try
		{
			WebsocketSessionKey key = getSessionKey(httpSession, clientnr);
			wsSession = wsSessions.get(key);
			if (wsSession == null || !wsSession.isValid())
			{
				wsSession = null;
				if (create && websocketSessionFactories.containsKey(endpointType))
				{
					AtomicInteger lastNumber = getCounter(httpSession, LAST_CLIENT_NUMBER);
					if (clientnr <= lastNumber.intValue())
					{
						// if the give clientnr is smaller then the number that is already given
						// make sure that a new session key is generated to have a new number bigger then the last
						key = getSessionKey(httpSession, -1);
					}
					else
					{
						// if it is the bigger then it was a restart, make sure we reuse that one and make the current counter the same to that value.
						lastNumber.set(clientnr);
						// the only thing that is not fixed if a reconnect with a "1" after restart does come later then a new request (-1) then the
						// reconnect with a 1 will just take the same session.
					}

					wsSession = websocketSessionFactories.get(endpointType).createSession(key);
					if (wsSession != null)
					{
						wsSession.setHttpSession(httpSession);
						AtomicInteger sessionCounter = getCounter(httpSession, HTTP_SESSION_COUNTER);
						sessionCounter.incrementAndGet();

						wsSessions.put(key, wsSession);
						wsSession.addDisposehandler(() -> {
							// invalidate http session when last session is disposed
							try
							{
								AtomicInteger counter = getCounter(httpSession, HTTP_SESSION_COUNTER);
								if (counter.decrementAndGet() == 0)
								{
									httpSession.invalidate();
								}
							}
							catch (Exception ignore)
							{
								// http session can already be invalid..
							}
						});
					}
				}
			}
		}
		finally
		{
			if (create) creatingLock.unlock();
		}
		return wsSession;
	}

	private static WebsocketSessionKey getSessionKey(HttpSession httpSession, int prevClientnr)
	{
		int clientnr;
		if (prevClientnr == -1)
		{
			// new client, generate new number
			clientnr = getCounter(httpSession, LAST_CLIENT_NUMBER).incrementAndGet();
		}
		else
		{
			clientnr = prevClientnr;
		}
		return new WebsocketSessionKey(httpSession.getId(), clientnr);
	}

	private static synchronized AtomicInteger getCounter(HttpSession httpSession, String attribute)
	{
		AtomicInteger counter = (AtomicInteger)httpSession.getAttribute(attribute);
		if (counter == null)
		{
			counter = new AtomicInteger()
			{
				private void writeObject(ObjectOutputStream out) throws IOException
				{
					// if this is serialized also set the counter to 0, because clients can't be serialized or transfered over
					set(0);
					out.defaultWriteObject();
				}
			};
			httpSession.setAttribute(attribute, counter);
		}
		return counter;
	}

	public static void closeInactiveSessions()
	{
		closeSessions(true);
	}

	public static void closeAllSessions()
	{
		closeSessions(false);
	}

	public static void destroy()
	{
		pingEndpointsThreadShouldStop = true;
		closeAllSessions();
		pingEndpointsThread.interrupt();
		expiredThreadPool.shutdown();
		long time = System.currentTimeMillis();
		try
		{
			if (!expiredThreadPool.awaitTermination(30, TimeUnit.SECONDS))
			{
				log.warn("After 30 seconds the expired session thread pool still did not finish");
			}
		}
		catch (InterruptedException e)
		{
			log.warn("Waiting for the expired session thread pool to terminate", e);
		}
		log.info("Expired threadpool waiting for :  " + (System.currentTimeMillis() - time));
	}

	private static void closeSessions(boolean checkForWindowActivity)
	{
		boolean hasLock = false;
		if (!checkForWindowActivity)
		{
			closingLock.lock();
			hasLock = true;
		}
		else
		{
			hasLock = closingLock.tryLock();
		}
		if (hasLock)
		{
			final List<IWebsocketSession> expiredSessions = new ArrayList<>(3);
			try
			{
				Iterator<IWebsocketSession> sessions = wsSessions.values().iterator();
				while (sessions.hasNext())
				{
					IWebsocketSession session = sessions.next();
					if (!checkForWindowActivity || session.checkForWindowActivity())
					{
						sessions.remove();
						expiredSessions.add(session);
					}
				}
			}
			finally
			{
				closingLock.unlock();
			}

			if (!expiredSessions.isEmpty()) expiredThreadPool.execute(new Runnable()
			{

				@Override
				public void run()
				{
					for (IWebsocketSession session : expiredSessions)
					{
						try
						{
							Thread.currentThread().setName("Sablo Session closer: " + session.getSessionKey()); //$NON-NLS-1$
							if (!checkForWindowActivity)
							{
								// this is a force close, look if we can interrupt the event thread.
								IEventDispatcher eventDispatcher = session.getEventDispatcher(false);
								if (eventDispatcher != null) eventDispatcher.interruptEventThread();
							}
							session.sessionExpired();
							Thread.currentThread().setName("Sablo Session closer"); //$NON-NLS-1$
						}
						catch (Exception e)
						{
							log.error("Error expiring session " + session.getSessionKey(), e); //$NON-NLS-1$
						}

						try
						{
							session.dispose();
						}
						catch (Exception e)
						{
							log.error("Error disposing expired session " + session.getSessionKey(), e); //$NON-NLS-1$
						}
					}
				}
			});

		}

	}

	public static void setWebsocketSessionFactory(String endpointType, IWebsocketSessionFactory factory)
	{
		websocketSessionFactories.put(endpointType, factory);
	}

	public static IWebsocketSessionFactory getWebsocketSessionFactory(String endpointType)
	{
		return websocketSessionFactories.get(endpointType);
	}
}
