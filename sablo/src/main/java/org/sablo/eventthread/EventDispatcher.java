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
import java.util.function.Predicate;

import org.sablo.util.ValueReference;
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
			dispatch(EVENT_LEVEL_DEFAULT, NO_TIMEOUT, true, null);
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

	/**
	 * @param shouldWaitIfNoEventFoundToDispatch if true, this call will wait for an event to be added if none was found that can be executed (matching the criteria)
	 * @param eventFilter filters events that can be used; if it returns true, then that event can be used, otherwise it will not be used; this argument can be null
	 *
	 * @return true if an event was executed as a result of this call; false otherwise
	 */
	private boolean dispatch(int minEventLevelToDispatch, long endMillis, boolean shouldWaitIfNoEventFoundToDispatch, Predicate<Event> eventFilter)
	{
		boolean wasExecuted = false;
		currentMinEventLevel = minEventLevelToDispatch;

		int i;
		Event event = null;
		try
		{
			synchronized (events)
			{
				long remainingMillis = 123456; // dummy value just to compile

				// find the next event that can be executed, or wait for one that can be executed to be added
				while (!exit && event == null && (endMillis == NO_TIMEOUT || (remainingMillis = endMillis - System.currentTimeMillis()) > 0))
				{
					i = 0;
					// find the next event that could be executed according to minEventLevelToDispatch
					while (event == null && i < events.size())
					{
						event = events.get(i);

						if (event.getEventLevel() < minEventLevelToDispatch || (eventFilter != null && !eventFilter.test(event))) event = null; // disqualified
						else events.remove(i); // it will be used

						i++;
					}

					// if there is no such event, wait for one to be added
					if (event == null)
					{
						if (shouldWaitIfNoEventFoundToDispatch)
						{
							try
							{
								events.wait(endMillis == NO_TIMEOUT ? 0 : remainingMillis);
							}
							catch (InterruptedException e)
							{
								// Server shutdown
								log.debug("Interrupted while waiting for events", e); //$NON-NLS-1$
								break;
							}
						}
						else break; // exit the while loop; "shouldWaitIfNoEventFoundToDispatch" says we should give up when not found
					}
				}
			}

			// if one was found, execute it
			if (event != null)
			{
				stack.add(event);
				event.execute();
				if (stack.getLast() != event)
				{
					throw new Exception("State not expected"); //$NON-NLS-1$
				}
				stack.remove(event);
				synchronized (events)
				{
					events.notifyAll();
				}
				wasExecuted = true;
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
				log.error("[dispatch()] handleException raised a new error or runtime exception. Initial one was: ", t); //$NON-NLS-1$
				log.error("[dispatch()] handleException raised this new error or runtime exception: ", t2); //$NON-NLS-1$
			}
		}
		return wasExecuted;
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
		return new Event(session, event, eventLevel, getCurrentEventIfOnEventThread());
	}

	protected Event getCurrentEventIfOnEventThread()
	{
		// so the current event that is being executed right now in current (event) thread
		// wants to add a new event; do remember which Events were spawned by another Event running on the event thread
		// (the idea is that if application.updateUI() gets called, other events coming from the client, or from
		// database updates from other clients, that are already in the list should not execute - because then
		// they would execute before the ones spawned by the current running event, if new ones were always added last)
		return isEventDispatchThread() ? stack.getLast() : null;
	}

	public void suspend(Object suspendID) throws CancellationException, TimeoutException
	{
		suspend(suspendID, EVENT_LEVEL_DEFAULT, CONFIGURED_TIMEOUT);
	}

	@Override
	public void suspend(Object suspendID, int minEventLevelToDispatch, long timeout) throws CancellationException, TimeoutException
	{
		if (!isEventDispatchThread())
		{
			log.error("suspend called in another thread then the script thread: " + Thread.currentThread(), new RuntimeException()); //$NON-NLS-1$
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

			final ValueReference<String> suspendedEventsValue = new ValueReference<>(null);

			newEventLoop((anEventWasExecutedInLastDispatchCall) -> {
				// the returned value means "should stop dispatching"
				return (suspendedEventsValue.value = suspendedEvents.get(suspendID)) != SUSPENDED_NOT_CANCELED;
			}, minEventLevelToDispatch, timeout, endMillis, true, null);

			event.willResume();

			if (suspendedEventsValue.value != null && !exit)
			{
				suspendedEvents.remove(suspendID);
				if (suspendedEventsValue.value != SUSPENDED_NOT_CANCELED)
					throw new CancellationException("Suspended event cancelled. Reason: " + suspendedEventsValue.value); //$NON-NLS-1$
				else throw new TimeoutException("Suspended event timed out (" + suspendID + "). It was not resumed in " + timeout + " milliseconds."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
	}

	public void runEventsAddedByCurrentEventStack()
	{
		// create a new event loop that processes all pending events that were triggered by currently executing events in the events stack
		newEventLoop((anEventWasExecutedInLastDispatchCall) -> {
			// the returned value means "should stop dispatching"
			return !anEventWasExecutedInLastDispatchCall.booleanValue();
		}, currentMinEventLevel, NO_TIMEOUT, NO_TIMEOUT, false,
			eventToFilter -> stack.stream().anyMatch((runningStackEvent -> (runningStackEvent == eventToFilter.getTriggeringEvent()))));
	}

	/**
	 * @param shouldStopDispatching it will receive a boolean that means "event was executed in last dispatch attempt".
	 */
	private void newEventLoop(Predicate<Boolean> shouldStopDispatching, int minEventLevelToDispatch, long timeout, long endMillis,
		boolean shouldWaitIfNoEventFoundToDispatch, Predicate<Event> eventFilter)
	{
		// if we were already dispatching in a higher currentMinEventLevel, use that one instead of "minEventLevelToDispatch"
		int dispatchEventLevel = Math.max(minEventLevelToDispatch, currentMinEventLevel);

		int oldMinEventLevel = currentMinEventLevel;
		try
		{
			boolean anEventWasExecutedInLastDispatchCall = true;
			while ((shouldStopDispatching == null || !shouldStopDispatching.test(Boolean.valueOf(anEventWasExecutedInLastDispatchCall))) && !exit &&
				(timeout == NO_TIMEOUT || endMillis - System.currentTimeMillis() > 0)) // this condition assumes NO_TIMEOUT <= 0 which is true
			{
				anEventWasExecutedInLastDispatchCall = dispatch(dispatchEventLevel, endMillis, shouldWaitIfNoEventFoundToDispatch, eventFilter);
			}
		}
		finally
		{
			currentMinEventLevel = oldMinEventLevel;
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
			events.add(new Event(session, null, EVENT_LEVEL_DEFAULT, null));
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