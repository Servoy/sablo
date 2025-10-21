/*
 * Copyright (C) 2025 Servoy BV
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

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

/**
 * this is a simple listener that just wants to log a warning when the sessions are disposed at a time we don't expect it.
 *
 * @author jcompagner
 *
 * @since 2025.12
 *
 */
public class HttpSessionDisposeListener implements Serializable, HttpSessionBindingListener
{
	public static final String DISPOSE_LISTENER_KEY = HttpSessionDisposeListener.class.getName();

	private static final Logger log = LoggerFactory.getLogger(HttpSessionDisposeListener.class.getCanonicalName());

	private boolean checkDispose = true;

	public void goingToBeDisposed()
	{
		checkDispose = false;
	}

	@Override
	public void valueUnbound(HttpSessionBindingEvent event)
	{
		if (checkDispose)
		{
			log.warn("Warning: HttpSession is being disposed while it was not expected! Session id: " + event.getSession().getId(), new RuntimeException());
		}
	}
}
