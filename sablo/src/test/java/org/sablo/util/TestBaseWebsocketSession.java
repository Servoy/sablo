/*
 * Copyright (C) 2021 Servoy BV
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

package org.sablo.util;

import java.util.Locale;

import org.sablo.websocket.BaseWebsocketSession;
import org.sablo.websocket.WebsocketSessionKey;

public class TestBaseWebsocketSession extends BaseWebsocketSession
{
	private Locale locale = Locale.getDefault();

	public TestBaseWebsocketSession(WebsocketSessionKey getSessionKey)
	{
		super(getSessionKey);
	}

	@Override
	public Locale getLocale()
	{
		return locale;
	}

	public void setLocale(Locale locale)
	{
		this.locale = locale;
	}

	@Override
	public String getLogInformation()
	{
		return ""; //$NON-NLS-1$
	}
}