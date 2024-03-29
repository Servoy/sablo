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

package org.sablo.specification;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.sablo.specification.Package.IPackageReader;

/**
 * This class represents an immutable state of the web components or services provider state.
 *
 * @author rgansevles
 *
 */
public class SpecProviderState
{
	private static final String DIR_PACKAGE = "DirPackage"; //$NON-NLS-1$
	private static final String ZIP_PACKAGE = "ZipPackage"; //$NON-NLS-1$
	private final Map<String, PackageSpecification<WebObjectSpecification>> cachedComponentOrServiceDescriptions;
	private final Map<String, PackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions;
	private final Map<String, WebObjectSpecification> allWebObjectSpecifications;
	private final List<IPackageReader> packageReaders;

	public SpecProviderState(Map<String, PackageSpecification<WebObjectSpecification>> cachedComponentOrServiceDescriptions,
		Map<String, PackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions, Map<String, WebObjectSpecification> allWebObjectSpecifications,
		List<IPackageReader> packageReaders)
	{
		this.cachedComponentOrServiceDescriptions = Collections.unmodifiableMap(new HashMap<>(cachedComponentOrServiceDescriptions));
		this.cachedLayoutDescriptions = Collections.unmodifiableMap(new HashMap<>(cachedLayoutDescriptions));
		this.allWebObjectSpecifications = Collections.unmodifiableMap(new HashMap<>(allWebObjectSpecifications));
		this.packageReaders = Collections.unmodifiableList(new ArrayList<>(packageReaders));
	}

	/**
	 * Works only for service/component specs, not for layouts.
	 */
	public synchronized WebObjectSpecification getWebObjectSpecification(String webObjectTypeName)
	{
		return allWebObjectSpecifications.get(webObjectTypeName);
	}

	/**
	 * Returns only component/service package specifications; not layout specifications.
	 */
	public synchronized Map<String, PackageSpecification<WebObjectSpecification>> getWebObjectSpecifications()
	{
		return cachedComponentOrServiceDescriptions;
	}

	/**
	 * Returns only component/service specs, not layouts.
	 */
	public synchronized WebObjectSpecification[] getAllWebObjectSpecifications()
	{
		return allWebObjectSpecifications.values().toArray(new WebObjectSpecification[allWebObjectSpecifications.size()]);
	}

	public Map<String, PackageSpecification<WebLayoutSpecification>> getLayoutSpecifications()
	{
		return cachedLayoutDescriptions;
	}

	/**
	 * Get the map of packages and package URLs.
	 */
	public Map<String, URL> getPackagesToURLs()
	{
		Map<String, URL> result = new HashMap<String, URL>();
		for (IPackageReader reader : packageReaders)
		{
			result.put(reader.getPackageName(), reader.getPackageURL());
		}
		return result;
	}

	/**
	 * Get the map of packages and package display names.
	 */
	public Map<String, String> getPackagesToDisplayNames()
	{
		Map<String, String> result = new HashMap<String, String>();
		for (IPackageReader reader : packageReaders)
		{
			result.put(reader.getPackageName(), reader.getPackageDisplayname());
		}
		return result;
	}

	public IPackageReader getPackageReader(String packageName)
	{
		for (IPackageReader reader : packageReaders)
		{
			if (reader.getPackageName().equals(packageName)) return reader;
		}
		return null;
	}

	/**
	 * This is a package reader for WPM. When a package is installed as a ZIP
	 * and also added as a reference then the reference package has priority.
	 * @param packageName the packageName
	 * @return the
	 */
	public IPackageReader getPackageReaderForWpm(String packageName)
	{
		Optional<IPackageReader> dirPackageReader = packageReaders.stream()
			.filter(p -> p.getPackageName().equals(packageName) && p.toString().contains(DIR_PACKAGE))
			.findFirst();

		Optional<IPackageReader> zipPackageReader = packageReaders.stream()
			.filter(p -> p.getPackageName().equals(packageName) && p.toString().contains(ZIP_PACKAGE))
			// in case there are more then one packageReader for the same package (that is an error and should not happen)
			// make sure we always return the same one, as 'packageReaders' does not guarantee they are always in the same order
			.sorted((p1, p2) -> p1.getResource().getAbsolutePath().compareTo(p2.getResource().getAbsolutePath()))
			.findFirst();

		return dirPackageReader.isPresent() ? dirPackageReader.get() : zipPackageReader.isPresent() ? zipPackageReader.get() : null;
	}

	public String getPackageType(String packageName)
	{
		IPackageReader packageReader = getPackageReader(packageName);
		return packageReader != null ? packageReader.getPackageType() : null;
	}

	public String getPackageDisplayName(String packageName)
	{
		return getPackagesToDisplayNames().get(packageName);
	}

	/**
	 * Returns only component/service package names. NOT layout package names.
	 */
	public Collection<String> getPackageNames()
	{
		return getWebObjectSpecifications().keySet();
	}

	/**
	 * Get a list of all webObjects contained by provided package name
	 */
	public Collection<String> getWebObjectsInPackage(String packageName)
	{
		PackageSpecification<WebObjectSpecification> pkg = getWebObjectSpecifications().get(packageName);
		return pkg == null ? Collections.<String> emptyList() : pkg.getSpecifications().keySet();
	}

	/**
	 * Get a list of all layouts contained by provided package name
	 */
	public Collection<String> getLayoutsInPackage(String packageName)
	{
		PackageSpecification<WebLayoutSpecification> pkg = getLayoutSpecifications().get(packageName);
		return pkg == null ? Collections.<String> emptyList() : pkg.getSpecifications().keySet();
	}

	public IPackageReader[] getAllPackageReaders()
	{
		// not sure why we don't return here simply packageReaders.toArray(new IPackageReader[packageReaders.size()]) ... maybe we won't want to return
		// readers for packages that don't have any components/services/layouts in them? in which case getWebObjectSpecifications and getLayoutSpecifications would not contain them...

		Set<String> packageNames = new HashSet<>(getWebObjectSpecifications().keySet()); // components/services
		packageNames.addAll(getLayoutSpecifications().keySet()); // layouts

		List<IPackageReader> readers = new ArrayList<>();
		for (String name : packageNames)
		{
			for (IPackageReader reader : packageReaders)
			{
				if (reader.getPackageName().equals(name))
				{
					readers.add(reader);
				}
			}
		}
		return readers.toArray(new IPackageReader[readers.size()]);
	}

}
