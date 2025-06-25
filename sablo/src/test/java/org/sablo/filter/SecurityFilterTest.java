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
package org.sablo.filter;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author rgansevles
 */
@RunWith(MockitoJUnitRunner.class)
public class SecurityFilterTest
{
	private static final String TEST_SERVOY = "test.servoy.com:8080";

	@Mock
	private HttpServletRequest requestMock;

	@Mock
	private HttpServletResponse responseMock;

	@Mock
	private FilterChain chainMock;

	@Mock
	private FilterConfig filterConfigMock;

	private final SecurityFilter securityFilter = new SecurityFilter();

	@Before
	public void setup() throws ServletException
	{
		when(requestMock.getHeader("Host")).thenReturn(TEST_SERVOY);

		securityFilter.init(filterConfigMock);
	}

	@After
	public void teardown()
	{
		securityFilter.destroy();
	}

	@Test
	public void currentHostHeader() throws Exception
	{
		doAnswer(invocation -> {
			String currentHostHeader = SecurityFilter.getCurrentHostHeader();
			assertThat(currentHostHeader, is(TEST_SERVOY));
			return null;
		}).when(chainMock).doFilter(any(), any());

		securityFilter.doFilter(requestMock, responseMock, chainMock);

		verify(chainMock).doFilter(any(), any());
		String afterHostHeader = SecurityFilter.getCurrentHostHeader();
		assertNull(afterHostHeader);
	}

	@Test
	public void currentHostHeaderExceptionhandling() throws Exception
	{
		doThrow(new RuntimeException("test")).when(chainMock).doFilter(any(), any());

		try
		{
			securityFilter.doFilter(requestMock, responseMock, chainMock);
			fail("exception expected");
		}
		catch (RuntimeException e)
		{
		}

		verify(chainMock).doFilter(any(), any());
		String afterHostHeader = SecurityFilter.getCurrentHostHeader();
		assertNull(afterHostHeader);
	}

}
