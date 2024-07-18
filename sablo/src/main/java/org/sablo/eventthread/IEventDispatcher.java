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
import java.util.concurrent.TimeoutException;

import org.sablo.websocket.IEventDispatchAwareServerService;
import org.sablo.websocket.IWebsocketSession;


/**
 * A {@link IWebsocketSession} returns a instance of this that should start up a thread
 * that will serve then as the dispatching thread for all the events coming from the browser.
 *
 * @author jcompagner
 *
 */
public interface IEventDispatcher extends Runnable
{

	public final static int EVENT_LEVEL_DEFAULT = 0;

	/**
	 * Sync service calls to client (that wait for a response on the event dispatch thread) will continue dispatching events of event level
	 * minimum {@link #EVENT_LEVEL_SYNC_API_CALL} while waiting and block only the rest. This is to avoid deadlocks in case the sync api call to client needs to wait
	 * for some initialization call to be executed on server (that initialization call can use a higher event level through {@link IEventDispatchAwareServerService#getMethodEventThreadLevel(String, org.json.JSONObject)}).
	 */
	public static final int EVENT_LEVEL_SYNC_API_CALL = 500;

	/**
	 * Value used for suspend timeout parameter when it is expected that a suspend call is long running. (for example modal dialogs who's open method returns a value)
	 */
	public static final int NO_TIMEOUT = 0;

	/**
	 * 1 minute in milliseconds - default timeout for suspend calls. Implementing classes may decide to use a different value as default timeout.
	 */
	public static final long DEFAULT_TIMEOUT = 60000;

	boolean isEventDispatchThread();

	/**
	 * Adds an event to be handled by the event dispatch thread.
	 * The event level is considered to be {@link #EVENT_LEVEL_DEFAULT} (0).
	 *
	 * @param event the event to be handled on the event dispatch thread.
	 */
	void addEvent(Runnable event);

	/**
	 * Adds an event to be handled by the event dispatch thread.
	 * The eventLevel is only relevant when using {@link #suspend(Object, int)}.
	 *
	 * @param event the event to be handled on the event dispatch thread.
	 * @param eventLevel see description of minEventLevelToDispatch in {@link #suspend(Object, int)}.
	 */
	void addEvent(Runnable event, int eventLevel);

	/**
	 * Adds an event event queue.
	 * The event level is considered to be {@link #EVENT_LEVEL_DEFAULT} (0).
	 *
	 * @param event the event to be handled on the event dispatch thread.
	 */
	void postEvent(Runnable event);

	/**
	 * Works in tandem with {@link #resume(Object)}.
	 * When suspend is called, the current event will stop executing and other events will continue being dispatched until {@link #resume(Object)} is called
	 * using the same "suspendID" parameter.
	 *
	 * @param suspendID The Object that is the suspend operation identifier.
	 * @param minEventLevelToDispatch while current event is suspended, the minimum event level to dispatch will be "minEventLevelToDispatch". So
	 * events added with "eventLevel" < "minEventLevelToDispatch" will not get dispatched. Events added with "eventLevel" >= "minEventLevelToDispatch" will
	 * continue being dispatched.
	 * @param timeout can be {@link #NO_TIMEOUT}; number of milliseconds to wait for a {@link #resume(Object)} call with the same suspendID. If no {@link #resume(Object)} will be called
	 * on this id for more then "timeout" ms then this suspend will fail with an exception. (to prevent locked application state or ever climbing event loops if event levels require it)
	 *
	 * @throws CancellationException in case  {@link #cancelSuspend(Integer)} is called for this suspendID later on.
	 * @throws TimeoutException when the timeout expires before getting a resume with this suspendID.
	 */
	void suspend(Object suspendID, int minEventLevelToDispatch, long timeout) throws CancellationException, TimeoutException;

	/**
	 * Same as {@link #suspend(Object, int, long)} with "minEventLevelToDispatch" having a value of {@link #EVENT_LEVEL_DEFAULT} and "timeout" of {@link #DEFAULT_TIMEOUT} (the default timeout can be modified by implementing classes).
	 * So all other events will continue to dispatch.
	 *
	 * @param suspendID The Object that is the suspend operation identifier.
	 *
	 * @throws CancellationException in case  {@link #cancelSuspend(Integer)} is called for this suspendID later on.
	 * @throws TimeoutException when the {@link #CONFIGURED_TIMEOUT} (that can be modified by implemented classes) expires before getting a resume with this suspendID.
	 */
	void suspend(Object suspendID) throws CancellationException, TimeoutException;

	/**
	 * See {@link #suspend(Object)}.
	 * Should be called directly only from the Event Dispatch thread.
	 * @param suspendID The Object that was used as a suspend operation identifier in a previous call to one of the "suspend" methods.
	 */
	void resume(Object suspendID);

	/**
	 * Resumes a previous suspend by throwing a CancellationException to the suspend calling code.
	 * Should be called directly only from the Event Dispatch thread.
	 *
	 * @param suspendID the if of the suspend operation.
	 * @param cancelReason a user-readable message for why the suspend was cancelled.
	 */
	void cancelSuspend(Integer suspendID, String cancelReason);

	/**
	 * destroys this event dispatcher thread.
	 */
	public void destroy();

	/**
	 * interrupts the event dispatch thread, and gives back the current stack that was interrupted
	 */
	public String interruptEventThread();
}
