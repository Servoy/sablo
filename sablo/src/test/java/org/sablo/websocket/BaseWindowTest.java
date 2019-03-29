/*
 * Copyright (C) 2016 Servoy BV
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author rgansevles
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class BaseWindowTest
{
	@Mock
	private IWebsocketSession websocketSessionMock;

	@Mock
	private IWebsocketEndpoint websocketEndpointMock;

	@Test
	public void shouldSetAndClearEndpoint() throws Exception
	{
		Mockito.when(websocketEndpointMock.hasSession()).thenReturn(Boolean.TRUE);
		BaseWindow window = new BaseWindow(websocketSessionMock, 42, "test");

		assertFalse(window.hasEndpoint());

		window.setEndpoint(websocketEndpointMock);

		assertTrue(window.hasEndpoint());
		assertSame(window.getEndpoint(), websocketEndpointMock);

		window.setEndpoint(null);

		assertFalse(window.hasEndpoint());
	}

	@Test
	public void shouldKeepLastEndpoint() throws Exception
	{
		BaseWindow window = new BaseWindow(websocketSessionMock, 42, "test");

		IWebsocketEndpoint endpoint2 = Mockito.mock(IWebsocketEndpoint.class);
		Mockito.when(endpoint2.hasSession()).thenReturn(Boolean.TRUE);
		IWebsocketEndpoint endpoint3 = Mockito.mock(IWebsocketEndpoint.class);
		Mockito.when(endpoint3.hasSession()).thenReturn(Boolean.TRUE);

		window.setEndpoint(websocketEndpointMock);
		window.setEndpoint(endpoint2);

		assertTrue(window.hasEndpoint());
		assertSame(window.getEndpoint(), endpoint2);

		window.setEndpoint(endpoint3);

		assertTrue(window.hasEndpoint());
		assertSame(window.getEndpoint(), endpoint3);

		window.setEndpoint(null); // clear endpoint1

		assertTrue(window.hasEndpoint());
		assertSame(window.getEndpoint(), endpoint3);

		window.setEndpoint(null); // clear endpoint2

		assertTrue(window.hasEndpoint());
		assertSame(window.getEndpoint(), endpoint3);

		window.setEndpoint(null);

		assertFalse(window.hasEndpoint());
	}

}
