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

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import org.sablo.websocket.CurrentWindow;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.IWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The default Event class used by the {@link IEventDispatcher}
 *
 * @author jcompagner
 *
 */
public class Event
{
	private static final Logger log = LoggerFactory.getLogger(Event.class.getCanonicalName());

	private volatile boolean runInBackground;
	private volatile boolean suspended;

	private final Runnable runnable;
	private volatile boolean executed;
	private volatile Exception exception;

	private final IWindow currentWindow;
	private final IWebsocketSession session;
	private final int eventLevel;

	private final Event wasAddedByEvent;

	/**
	 * @param wasAddedByEvent if the event was created & added by code running in the event thread when,
	 *                        then this is the event that was running at that time on the event thread; it can be null,
	 *                        if this event was created & added by another thread.
	 */
	public Event(IWebsocketSession session, Runnable runnable, int eventLevel, Event wasAddedByEvent)
	{
		this.session = session;
		this.runnable = runnable;
		this.eventLevel = eventLevel;
		this.wasAddedByEvent = wasAddedByEvent;

		if (CurrentWindow.exists())
		{
			currentWindow = CurrentWindow.get();
		}
		else
		{
			// this is a runnable added to the event thread from a none request thread
			currentWindow = null;
		}
	}

	/**
	 * @return the session
	 */
	protected IWebsocketSession getSession()
	{
		return session;
	}

	public int getEventLevel()
	{
		return eventLevel;
	}

	/**
	 * Called by the script thread to execute itself.
	 */
	public final void execute()
	{
		IWindow window = getWindow();

		CurrentWindow.runForWindow(window, new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					beforeExecute();
					if (runnable != null)
					{
						runnable.run();
					}
				}
				catch (CancellationException ex)
				{
					log.info("Execution cancelled", ex);
					exception = ex;
				}
				catch (Exception e)
				{
					log.error("Exception in execute", e);
					exception = e;
				}
				finally
				{
					executed = true;
					try
					{
						afterExecute();
					}
					catch (Exception e)
					{
						log.error("Exception in after execute", e);
						exception = exception != null ? exception : e;
					}
				}
			}
		});
	}

	protected WebsocketSessionWindows createWebsocketSessionWindows()
	{
		return new WebsocketSessionWindows(session);
	}

	/**
	 *  called right before the runnable gets executed
	 */
	protected void afterExecute()
	{
		session.stopHandlingEvent();
	}

	/**
	 * called after the runnable is executed (normal or exception result)
	 */
	protected void beforeExecute()
	{
		session.startHandlingEvent();
	}

	/**
	 * @return the exception
	 */
	public Exception getException()
	{
		return exception;
	}

	/**
	 * Called when this event will be suspended, will set this event in a suspended state.
	 */
	public void willSuspend()
	{
		suspended = true;
		session.stopHandlingEvent();
	}

	/**
	 * Called when this event will be resumed will set this event in a resumed state.
	 */
	public void willResume()
	{
		suspended = false;
		session.startHandlingEvent();
	}

	/**
	 * @return the executed
	 */
	public boolean isExecuted()
	{
		return executed;
	}

	/**
	 * @return true This will return true if this event is in a suspended state.
	 */
	public boolean isSuspended()
	{
		return suspended;
	}

	/**
	 * Must be called when this event will be executed in the background (the ui will be painted)
	 */
	public void executeInBackground()
	{
		runInBackground = true;
	}

	/**
	 * @return true when {@link #executeInBackground()} was called.
	 */
	public boolean isExecutingInBackground()
	{
		return runInBackground;
	}

	public IWindow getWindow()
	{
		IWindow window = currentWindow;
		if (window == null)
		{
			// this was an event from not triggered by a specific endpoint, just relay it to all the endpoints
			// could be changes in the data model that must be pushed to all endpoints, or a close/shutdown/logout
			window = createWebsocketSessionWindows();
		}
		return window;
	}

	public void destroy()
	{
		if (runnable instanceof Future)
		{
			((Future< ? >)runnable).cancel(true);
		}
	}

	/**
	 * If the event was created & added by code running in the event thread when,
	 * then this is the event that was running at that time on the event thread; it can be null,
	 * if this event was created & added by another thread.
	 */
	public Event getTriggeringEvent()
	{
		return wasAddedByEvent;
	}
}