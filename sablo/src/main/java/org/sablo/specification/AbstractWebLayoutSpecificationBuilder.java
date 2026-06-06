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

package org.sablo.specification;

import java.util.List;

/**
 * @author lvostinar
 *
 */
abstract class AbstractWebLayoutSpecificationBuilder<B extends AbstractWebLayoutSpecificationBuilder<B, P>, P extends WebLayoutSpecification>
	extends AbstractWebObjectSpecificationBuilder<B, P>
{
	protected boolean topContainer;
	protected String layout;
	protected List<String> allowedChildren;
	protected String designStyleClass;
	protected List<String> excludedChildren;
	protected List<String> directives;

	public B withTopContainer(boolean topContainer)
	{
		this.topContainer = topContainer;
		return getThis();
	}

	public B withLayout(String layout)
	{
		this.layout = layout;
		return getThis();
	}

	public B withDesignStyleClass(String designStyleClass)
	{
		this.designStyleClass = designStyleClass;
		return getThis();
	}

	public B withAllowedChildren(List<String> allowedChildren)
	{
		this.allowedChildren = allowedChildren;
		return getThis();
	}

	public B withExcludedChildren(List<String> excludedChildren)
	{
		this.excludedChildren = excludedChildren;
		return getThis();
	}

	public B withDirectives(List<String> directives)
	{
		this.directives = directives;
		return getThis();
	}
}
