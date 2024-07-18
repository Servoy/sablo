/*
 * Copyright (C) 2023 Servoy BV
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

package org.sablo.specification;

/**
 * @author lvostinar
 *
 */
public class WebObjectApiFunctionDefinition extends WebObjectFunctionDefinition
{
	private boolean delayUntilFormLoads = false;
	private boolean async = false;
	private boolean asyncNow = false;
	private boolean preDataServiceCall;
	private boolean blockEventProcessing = true;
	private boolean discardPreviouslyQueuedSimilarCalls = false;

	public WebObjectApiFunctionDefinition(String name)
	{
		super(name);
	}

	public void setPreDataServiceCall(boolean preDataServiceCall)
	{
		this.preDataServiceCall = preDataServiceCall;
	}

	public boolean isPreDataServiceCall()
	{
		return preDataServiceCall;
	}

	/**
	 * Only for components, not services. This is a special type of async method call that waits for a form to be loaded on client before executing the method.
	 * Calling this kind of methods will not forcefully load the form in hidden DOM just to call the method.
	 * @return
	 */
	public boolean shouldDelayUntilFormLoads()
	{
		return delayUntilFormLoads;
	}

	public void setDelayUntilFormLoads(boolean delayUntilFormLoads)
	{
		this.delayUntilFormLoads = delayUntilFormLoads;
	}

	public void setBlockEventProcessing(boolean blockEventProcessing)
	{
		this.blockEventProcessing = blockEventProcessing;
	}

	/**
	 * When true (default), an API call to client will block normal operation until it gets a response or it times out.
	 *
	 * You should set it to false if you want client to continue operating normally (the user should still be able to interact with forms) while API call
	 * is in progress and if the API call should not time-out. For example if an API call shows a modal dialog containing a form and needs to be blocking from a scripting
	 * point of view - it should have this set to false - so that it doesn't time out and it allows used to interact with the form-in-modal.
	 *
	 * For now, having this set to false is interpreted as an API call that waits for user input. This information can be used for ignoring the time spent
	 * calling this api when profiling/looking for performance bottlenecks.
	 */
	// We can separate "blockEventProcessing" from a 'waitsForUserAction' in the future
	// if anyone needs to blockEventProcessing while call is in progress but still wait for an user action; in that case we also have to check for waitsForUserAction
	// in WebSocketEndpoint.suspend (where the timeout is given)... maybe some long running task in client that computes something for a long time intentionally
	public boolean getBlockEventProcessing()
	{
		return blockEventProcessing;
	}


	/**
	 * False by default.<br/><br/>
	 *
	 * Only component methods support this, not service methods.
	 *
	 * When true (only makes sense for 'async' or 'delayUntilFormLoads' type of calls), only the last call (inside an event handler on the event thread - when multiple async/delayed API calls get queued
	 * before being sent to the client) to this method (identified by method name) on any component on the current window will be executed. The previous calls are discarded.<br/><br/>
	 *
	 * For example when the user clicks a button, an event handler on the server that executes lots of code might end up calling .requestFocus() on many components for many different forms on this window.
	 * But to keep things fast, only the last requestFocus() is really relevant and only that really needs to get executed on the client - there is no use in executing any of the others.
	 * So by marking requestFocus() with this flag in the .spec you can achieve that.
	 */
	public boolean shouldDiscardPreviouslyQueuedSimilarCalls()
	{
		return discardPreviouslyQueuedSimilarCalls;
	}

	/**
	 * @see #shouldDiscardPreviouslyQueuedSimilarCalls()
	 */
	public void setDiscardPreviouslyQueuedSimilarCalls(boolean discardPreviouslyQueuedSimilarCalls)
	{
		this.discardPreviouslyQueuedSimilarCalls = discardPreviouslyQueuedSimilarCalls;
	}

	/**
	 * Setter.
	 * @see #isAsync()
	 */
	public void setAsync(boolean async)
	{
		this.async = async;
	}

	/**
	 * Async methods are to be executed later and they do not wait for a return value.
	 * @return the async
	 */
	public boolean isAsync()
	{
		return async;
	}

	/**
	 * Async-now methods are to be executed right away but do not wait for a return value.
	 * The async-now call does not send any component/service pending changes - or call other pending async/delayed api to client; it just calls the method.
	 *
	 * @return the asyncNow
	 */
	public boolean isAsyncNow()
	{
		return asyncNow;
	}

	/**
	 * Setter.
	 * @see #isAsyncNow()
	 */
	public void setAsyncNow(boolean asyncNow)
	{
		this.asyncNow = asyncNow;
	}
}
