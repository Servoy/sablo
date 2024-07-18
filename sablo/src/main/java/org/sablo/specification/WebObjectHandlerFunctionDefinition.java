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
public class WebObjectHandlerFunctionDefinition extends WebObjectFunctionDefinition
{
	private boolean ignoreNGBlockDuplicateEvents = false;

	public WebObjectHandlerFunctionDefinition(String name)
	{
		super(name);
	}

	/**
	 * False by default.<br/><br/>
	 * Whatever the NG_BLOCK_DUPLICATE_EVENTS system property should be ignored
	 */
	public boolean shouldIgnoreNGBlockDuplicateEvents()
	{
		return ignoreNGBlockDuplicateEvents;
	}

	/**
	 * @see #shouldIgnoreNGBlockDuplicateEvents()
	 */
	public void setIgnoreNGBlockDuplicateEvents(boolean ignoreNGBlockDuplicateEvents)
	{
		this.ignoreNGBlockDuplicateEvents = ignoreNGBlockDuplicateEvents;
	}
}
