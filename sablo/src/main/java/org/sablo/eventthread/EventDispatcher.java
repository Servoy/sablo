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

package org.sablo.eventthread;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.sablo.websocket.IWebsocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runnable of the ScriptThread that executes {@link Event} objects.
 *
 * @author rgansevles
 *
 */
public class EventDispatcher implements Runnable, IEventDispatcher
{
	private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class.getCanonicalName());

	/**
	 * 1 minute in milliseconds - default timeout for suspend calls.<br/>
	 * Can be overridden via system property sablo.internal.APICallToClientTimeout. Use 0 for no-timeout.
	 */
	public static final long CONFIGURED_TIMEOUT;
	static
	{
		long timeout;
		try
		{
			timeout = Long.parseLong(System.getProperty("sablo.internal.APICallToClientTimeout", String.valueOf(IEventDispatcher.DEFAULT_TIMEOUT)));
		}
		catch (NumberFormatException e)
		{
			timeout = IEventDispatcher.DEFAULT_TIMEOUT;
			log.error("Please check system property values. 'sablo.internal.APICallToClientTimeout' is not a number.");
		}
		CONFIGURED_TIMEOUT = timeout;
	}

	private final ConcurrentMap<Object, String> suspendedEvents = new ConcurrentHashMap<Object, String>();
	/**
	 * When this is a value in {@link #suspendedEvents} above it's a normal suspend mode. When the value in {@link #suspendedEvents} is another String
	 * then the suspend will be cancelled with that String as a reason.
	 */
	private static final String SUSPENDED_NOT_CANCELED = "_.,,._"; //$NON-NLS-1$

	private final List<Event> events = new ArrayList<Event>();
	private final LinkedList<Event> stack = new LinkedList<Event>();

	private volatile boolean exit = false;

	private volatile Thread scriptThread = null;

	private int currentMinEventLevel = EVENT_LEVEL_DEFAULT;

	private final IWebsocketSession session;

	public EventDispatcher(IWebsocketSession session)
	{
		this.session = session;
	}

	public void run()
	{
		scriptThread = Thread.currentThread();
		while (!exit)
		{
			dispatch(EVENT_LEVEL_DEFAULT, NO_TIMEOUT);
		}

		synchronized (events)
		{
			if (events.size() > 0)
			{
				// make sure that we cancel all events that are still left (and are able to be destroyed/cancelled)
				events.forEach(event -> event.destroy());
			}
		}
	}

	private void dispatch(int minEventLevelToDispatch, long endMillis)
	{
		currentMinEventLevel = minEventLevelToDispatch;

		int i;
		Event event = null;
		try
		{
			synchronized (events)
			{
				long remainingMillis = 123456; // dummy value just to compile
				while (!exit && event == null && (endMillis == NO_TIMEOUT || (remainingMillis = endMillis - System.currentTimeMillis()) > 0))
				{
					i = 0;
					while (event == null && i < events.size())
					{
						event = events.get(i);
						if (event.getEventLevel() < minEventLevelToDispatch) event = null;
						else events.remove(i);

						i++;
					}
					if (event == null)
					{
						try
						{
							events.wait(endMillis == NO_TIMEOUT ? 0 : remainingMillis);
						}
						catch (InterruptedException e)
						{
							// Server shutdown
							log.debug("Interrupted while waiting for events", e);
							break;
						}
					}
				}
			}

			if (event != null)
			{
				stack.add(event);
				event.execute();
				if (stack.getLast() != event)
				{
					throw new Exception("State not expected");
				}
				stack.remove(event);
				synchronized (events)
				{
					events.notifyAll();
				}
			}
		}
		catch (Throwable t)
		{
			try
			{
				handleException(event, t);
			}
			catch (Throwable t2)
			{
				log.error("[dispatch()] handleException raised a new error or runtime exception. Initial one was: ", t);
				log.error("[dispatch()] handleException raised this new error or runtime exception: ", t2);
			}
		}
	}

	protected void handleException(Event event, Throwable t)
	{
		log.error("[dispatch()] Exception happened in dispatch()", t);
	}

	@Override
	public boolean isEventDispatchThread()
	{
		return scriptThread == Thread.currentThread();
	}

	@Override
	public void addEvent(Runnable event)
	{
		addEvent(event, IEventDispatcher.EVENT_LEVEL_DEFAULT);
	}

	@Override
	public void addEvent(Runnable event, int eventLevel)
	{

		if (isEventDispatchThread() && currentMinEventLevel <= eventLevel)
		{
			// we can execute it right away
			createEvent(event, eventLevel).execute();
		}
		else
		{
			postEvent(event, eventLevel);
		}
	}

	public void postEvent(Runnable event)
	{
		postEvent(event, IEventDispatcher.EVENT_LEVEL_DEFAULT);
	}

	public void postEvent(Runnable event, int eventLevel)
	{
		synchronized (events)
		{
			if (!exit)
			{
				events.add(createEvent(event, eventLevel));
				events.notifyAll();
				// non-blocking
//				while (!(event.isExecuted() || event.isSuspended() || event.isExecutingInBackground()))
//				{
//					try
//					{
//						events.wait();
//					}
//					catch (InterruptedException e)
//					{
//						Debug.error(e);
//					}
//				}
			}
			else
			{
				// someone already destroyed this client/client's thread/event dispatcher; so it is likely that the new "event" is also a request to shut-down the client,
				// maybe from the "Sablo Session closer" thread (but shutdown already happened meanwhile); so do cancel the event if possible - so that it doesn't block
				// forever if it's a Future that some other thread is waiting for
				if (event instanceof Future)
				{
					((Future< ? >)event).cancel(true);
				}
			}
		}
	}

	protected Event createEvent(Runnable event, int eventLevel)
	{
		return new Event(session, event, eventLevel);
	}

	public void suspend(Object suspendID) throws CancellationException, TimeoutException
	{
		suspend(suspendID, EVENT_LEVEL_DEFAULT, CONFIGURED_TIMEOUT);
	}

	@Override
	public void suspend(Object suspendID, int minEventLevelToDispatch, long timeout) throws CancellationException, TimeoutException
	{
		// TODO should this one be called in the execute event thread, should an check be done??
		if (!isEventDispatchThread())
		{
			log.error("suspend called in another thread then the script thread: " + Thread.currentThread(), new RuntimeException());
			return;
		}
		long endMillis = (timeout != NO_TIMEOUT ? System.currentTimeMillis() + timeout : NO_TIMEOUT);
		Event event = stack.getLast();
		if (event != null)
		{
			suspendedEvents.put(suspendID, SUSPENDED_NOT_CANCELED);
			event.willSuspend();
			synchronized (events)
			{
				events.notifyAll();
			}

			// if we were already dispatching in a higher currentMinEventLevel, use that one instead of "minEventLevelToDispatch"
			int dispatchEventLevel = Math.max(minEventLevelToDispatch, currentMinEventLevel);

			String suspendedEventsValue;
			int oldMinEventLevel = currentMinEventLevel;
			try
			{
				while ((suspendedEventsValue = suspendedEvents.get(suspendID)) == SUSPENDED_NOT_CANCELED && !exit &&
					(timeout == NO_TIMEOUT || endMillis - System.currentTimeMillis() > 0)) // this condition assumes NO_TIMEOUT <= 0 which is true
				{
					dispatch(dispatchEventLevel, endMillis);
				}
			}
			finally
			{
				currentMinEventLevel = oldMinEventLevel;
			}

			event.willResume();

			if (suspendedEventsValue != null && !exit)
			{
				suspendedEvents.remove(suspendID);
				if (suspendedEventsValue != SUSPENDED_NOT_CANCELED)
					throw new CancellationException("Suspended event cancelled. Reason: " + suspendedEventsValue);
				else throw new TimeoutException("Suspended event timed out (" + suspendID + "). It was not resumed in " + timeout + " milliseconds.");
			}
		}
	}

	public void resume(Object eventKey)
	{
		if (!isEventDispatchThread())
		{
			log.error("resume called in another thread than the script thread: " + Thread.currentThread(), new RuntimeException());
			return;
		}
		suspendedEvents.remove(eventKey);
	}

	@Override
	public void cancelSuspend(Integer suspendID, String cancelReason)
	{
		if (!isEventDispatchThread())
		{
			log.error("cancelSuspend called in another thread than the script thread: " + Thread.currentThread(), new RuntimeException());
			return;
		}
		if (suspendedEvents.containsKey(suspendID))
		{
			if (cancelReason == null) cancelReason = "unspecified."; // our map can't handle null values
			suspendedEvents.put(suspendID, "(" + suspendID + ") " + cancelReason);
		}
	}

	private void addEmptyEvent()
	{
		synchronized (events)
		{
			// add a nop event so that the dispatcher is triggered.
			events.add(new Event(session, null, EVENT_LEVEL_DEFAULT));
			events.notifyAll();
		}
	}

	public void destroy()
	{
		exit = true;
		addEmptyEvent();
	}

	@Override
	public String interruptEventThread()
	{
		Thread t = scriptThread;
		if (t != null)
		{
			StringBuilder sb = new StringBuilder();
			StackTraceElement[] stackTrace = t.getStackTrace();
			for (StackTraceElement stackTraceElement : stackTrace)
			{
				sb.append(stackTraceElement);
				sb.append('\n');
			}
			t.interrupt();
			return sb.toString();
		}
		return "";
	}

}