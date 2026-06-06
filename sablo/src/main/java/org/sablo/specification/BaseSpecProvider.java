/*
 * Copyright (C) 2015 Servoy BV
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

import java.util.Collection;

import org.sablo.specification.Package.IPackageReader;

/**
 * @author lvostinar
 *
 */
public abstract class BaseSpecProvider
{
	protected final WebSpecReader reader;

	protected BaseSpecProvider(WebSpecReader reader)
	{
		this.reader = reader;
	}

	/**
	 * Updates available packages. "toRemove" provided contents will be removed, "toAdd" will be made available.
	 */
	public void updatePackages(Collection<IPackageReader> toRemove, Collection<IPackageReader> toAdd)
	{
		reader.updatePackages(toRemove, toAdd);
	}
}
