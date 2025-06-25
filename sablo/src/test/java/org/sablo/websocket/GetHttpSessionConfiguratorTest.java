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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sablo.websocket.GetHttpSessionConfigurator.DISABLE_ORIGIN_CHECK;
import static org.sablo.websocket.GetHttpSessionConfigurator.USE_HOST_HEADER;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sablo.filter.SecurityFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author rgansevles
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class GetHttpSessionConfiguratorTest
{
	@Mock
	private HttpServletRequest requestMock;

	@Mock
	private HttpServletResponse responseMock;

	@Mock
	private FilterChain chainMock;

	private final GetHttpSessionConfigurator getHttpSessionConfigurator = new GetHttpSessionConfigurator();

	@Before
	public void resetToDefault() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(null); // default
	}

	@After
	public void cleanup() throws Exception
	{
		resetToDefault();
	}

	@Test
	public void checkDisabled() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(DISABLE_ORIGIN_CHECK);

		boolean result = getHttpSessionConfigurator.checkOrigin("http://evil.com");

		assertTrue(result);
	}

	@Test(expected = IllegalArgumentException.class)
	public void noOriginHeader() throws Exception
	{
		getHttpSessionConfigurator.checkOrigin(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullOriginHeader() throws Exception
	{
		getHttpSessionConfigurator.checkOrigin("null");
	}

	@Test
	public void cannotParseOriginHeader() throws Exception
	{
		boolean result = getHttpSessionConfigurator.checkOrigin("what:is:this");

		assertFalse(result);
	}

	@Test
	public void checkWhitelistRejected() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck("crm.servoy.com, test.servoy.com");

		boolean result = getHttpSessionConfigurator.checkOrigin("http://evil.com");

		assertFalse(result);
	}

	@Test
	public void checkWhitelistAccepted() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck("crm.servoy.com, test.servoy.com");

		boolean result = getHttpSessionConfigurator.checkOrigin("http://test.servoy.com");

		assertTrue(result);
	}

	@Test(expected = IllegalArgumentException.class)
	public void checkAgainHostHeaderNoHostHeader() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(USE_HOST_HEADER);

		getHttpSessionConfigurator.checkOrigin("http://test.servoy.com");
	}

	@Test
	public void checkAgainHostHeaderAccepted() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(USE_HOST_HEADER);

		boolean result = checkOrigin("test.servoy.com:8080", "http://test.servoy.com");

		assertTrue(result);
	}

	@Test
	public void checkAgainHostHeaderDefaultAccepted() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(null);

		boolean result = checkOrigin("test.servoy.com:8080", "http://test.servoy.com");

		assertTrue(result);
	}

	@Test
	public void checkAgainHostHeaderAcceptedWithDifferentPort() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(USE_HOST_HEADER);

		boolean result = checkOrigin("test.servoy.com:8080", "http://test.servoy.com:8888");

		assertTrue(result);
	}

	@Test
	public void checkAgainHostHeaderRejected() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(USE_HOST_HEADER);

		boolean result = checkOrigin("test.servoy.com:8080", "http://evil.com");

		assertFalse(result);
	}

	@Test
	public void checkAgainHostHeaderDefaultRejected() throws Exception
	{
		GetHttpSessionConfigurator.setOriginCheck(null);

		boolean result = checkOrigin("test.servoy.com:8080", "http://evil.com");

		assertFalse(result);
	}

	private boolean checkOrigin(String host, String origin) throws IOException, ServletException
	{
		AtomicBoolean result = new AtomicBoolean();

		when(requestMock.getHeader("Host")).thenReturn(host);
		doAnswer(invocation -> {
			result.set(getHttpSessionConfigurator.checkOrigin(origin));
			return null;
		}).when(chainMock).doFilter(any(), any());

		new SecurityFilter().doFilter(requestMock, responseMock, chainMock);

		verify(chainMock).doFilter(any(), any());

		return result.get();
	}
}
