/*
 * Copyright (C) 2019 Servoy BV
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

public final class WebsocketSessionKey
{
	private final String httpSessionid; // should never be used in the application, other then on key equivalence
	private final int clientnr;

	public WebsocketSessionKey(String httpSessionid, int clientnr)
	{
		this.httpSessionid = httpSessionid;
		this.clientnr = clientnr;
	}

	// Should not be needed, the http session id should never leak.
//	public String getHttpSessionid()
//	{
//		return httpSessionid;
//	}

	/**
	 * @return the clientnr
	 */
	public int getClientnr()
	{
		return clientnr;
	}

	@Override
	public int hashCode()
	{
		return httpSessionid.hashCode() + clientnr;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof WebsocketSessionKey)
		{
			WebsocketSessionKey other = (WebsocketSessionKey)obj;
			return other.httpSessionid.equals(httpSessionid) && other.clientnr == clientnr;
		}
		return false;
	}

	@Override
	public String toString()
	{
		// do  not expose the http session id
		return (httpSessionid.length() <= 7 ? httpSessionid : httpSessionid.substring(0, 7)) + ":" + clientnr;
	}

}