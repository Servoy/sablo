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

package org.sablo.specification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

/**
 * Parsed web component / web service api function definition.
 *
 * @author rgansevles
 */
public class WebObjectFunctionDefinition
{

	private final String name;
	private final List<PropertyDescription> parameters = new ArrayList<>();
	private PropertyDescription returnType;
	private JSONObject customConfigOptions;
	private String documentation;

	private boolean blockEventProcessing = true;
	private boolean delayUntilFormLoads = false;
	private boolean async = false;
	private boolean discardPreviouslyQueuedSimilarCalls = false;
	private PropertyDescription asPropertyDescription;
	private boolean preDataServiceCall;
	private boolean priv;

	public WebObjectFunctionDefinition(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void addParameter(PropertyDescription parameter)
	{
		parameters.add(parameter);
	}

	public List<PropertyDescription> getParameters()
	{
		return Collections.unmodifiableList(parameters);
	}

	public void setReturnType(PropertyDescription returnType)
	{
		this.returnType = returnType;
	}

	public PropertyDescription getReturnType()
	{
		return returnType;
	}

	void setCustomConfigOptions(JSONObject customConfigOptions)
	{
		this.customConfigOptions = customConfigOptions;
	}

	public JSONObject getCustomConfigOptions()
	{
		return this.customConfigOptions;
	}

	@Override
	public String toString()
	{
		return "WebObjectApiDefinition[name:" + name + ",returnType:" + returnType + ", parameters:" + parameters + "]";
	}

	public void setDocumentation(String documentation)
	{
		this.documentation = documentation;
	}

	public String getDocumentation()
	{
		return documentation;
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

	public boolean shouldDelayUntilFormLoads()
	{
		return delayUntilFormLoads;
	}

	public void setDelayUntilFormLoads(boolean delayUntilFormLoads)
	{
		this.delayUntilFormLoads = delayUntilFormLoads;
	}

	/**
	 * @param async the async to set
	 */
	public void setAsync(boolean async)
	{
		this.async = async;
	}

	/**
	 * @return the async
	 */
	public boolean isAsync()
	{
		return async;
	}

	/**
	 * False by default.<br/><br/>
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

	public PropertyDescription getAsPropertyDescription()
	{
		return asPropertyDescription;
	}

	public void setPropertyDescription(PropertyDescription propertyDescription)
	{
		this.asPropertyDescription = propertyDescription;

	}

	/**
	 * @param b
	 */
	public void setPreDataServiceCall(boolean preDataServiceCall)
	{
		this.preDataServiceCall = preDataServiceCall;
	}

	/**
	 * @return the applyFirst
	 */
	public boolean isPreDataServiceCall()
	{
		return preDataServiceCall;
	}

	/**
	 * @param boolean1
	 */
	public void setPrivate(boolean priv)
	{
		this.priv = priv;
	}

	/**
	 * @return the priv
	 */
	public boolean isPrivate()
	{
		return priv;
	}

}
