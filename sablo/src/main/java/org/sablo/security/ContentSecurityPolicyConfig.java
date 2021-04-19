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

package org.sablo.security;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for ContentSecurityPolicy header directives.
 *
 * @author rgansevles
 *
 */
public class ContentSecurityPolicyConfig
{
	public static final String DEFAULT_DEFAULT_SRC_DIRECTIVE_VALUE = "'self'";
	public static final String DEFAULT_FRAME_SRC_DIRECTIVE_VALUE = "* data:";
	public static final String DEFAULT_FRAME_ANCESTORS_DIRECTIVE_VALUE = "'self'";
	public static final String DEFAULT_SCRIPT_SRC_DIRECTIVE_VALUE = "'unsafe-eval' 'nonce-${nonce}' 'strict-dynamic'"; // can we get rid of unsafe-eval?
	// We cannot use random nonce for styles because this is would block inline style attributes on elements,
	// when style-src-attr is supported by the major browsers we can use that to override inline styles for elements.
	// Styles may be loaded by scripts from any source, unless we list them in the component manifest and include them here we have to allow all style sources
	public static final String DEFAULT_STYLE_SRC_DIRECTIVE_VALUE = "* 'unsafe-inline'";
	public static final String DEFAULT_IMG_SRC_DIRECTIVE_VALUE = "* data: blob:";
	public static final String DEFAULT_FONT_SRC_DIRECTIVE_VALUE = "* data:";
	public static final String DEFAULT_OBJECT_SRC_DIRECTIVE_VALUE = "'none'";

	private final String nonce;

	private final Map<String, String> directives;

	public ContentSecurityPolicyConfig(String nonce)
	{
		this.nonce = nonce;
		directives = defaultDirectives(nonce);
	}

	public String getNonce()
	{
		return nonce;
	}

	public String getDirective(String directive)
	{
		return directives.get(directive);
	}

	public void setDirective(String directive, String value)
	{
		setDirective(directives, directive, value, nonce);
	}

	public Map<String, String> getDirectives()
	{
		return directives;
	}

	private static void setDirective(Map<String, String> directives, String directive, String value, String nonce)
	{
		directives.put(directive, value == null ? "" : value.replace("${nonce}", nonce));
	}

	private static Map<String, String> defaultDirectives(String nonce)
	{
		Map<String, String> defaultDirectives = new HashMap<>();
		setDirective(defaultDirectives, "frame-src", DEFAULT_FRAME_SRC_DIRECTIVE_VALUE, nonce);
		setDirective(defaultDirectives, "frame-ancestors", DEFAULT_FRAME_ANCESTORS_DIRECTIVE_VALUE, nonce);
		setDirective(defaultDirectives, "script-src", DEFAULT_SCRIPT_SRC_DIRECTIVE_VALUE, nonce);
		setDirective(defaultDirectives, "style-src", DEFAULT_STYLE_SRC_DIRECTIVE_VALUE, nonce);
		setDirective(defaultDirectives, "img-src", DEFAULT_IMG_SRC_DIRECTIVE_VALUE, nonce);
		setDirective(defaultDirectives, "font-src", DEFAULT_FONT_SRC_DIRECTIVE_VALUE, nonce);
		setDirective(defaultDirectives, "object-src", DEFAULT_OBJECT_SRC_DIRECTIVE_VALUE, nonce);
		return defaultDirectives;
	}
}