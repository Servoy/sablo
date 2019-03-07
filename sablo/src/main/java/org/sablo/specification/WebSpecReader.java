/*
 * Copyright (C) 2014 Servoy BV
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.Package.DuplicateEntityException;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.property.types.TypesRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jcompagner
 */
class WebSpecReader
{
	private static final Logger log = LoggerFactory.getLogger(WebSpecReader.class.getCanonicalName());

	private final Map<String, PackageSpecification<WebObjectSpecification>> cachedDescriptions = new HashMap<>();
	private final Map<String, PackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions = new TreeMap<>();
	private final Map<String, WebObjectSpecification> allWebObjectSpecifications = new HashMap<>();
	private final Map<String, List<IPackageReader>> allPackages = new HashMap<>();
	private final Set<String> packagesWithGloballyDefinedTypes = new HashSet<>(); // packageNames of packages that have globally defined types

	private final List<IPackageReader> packageReaders;

	private final String attributeName;

	private final SpecReloadSubject specReloadSubject;

	private long lastLoadTimestamp;

	private SpecProviderState specProviderState;


	WebSpecReader(IPackageReader[] packageReaders, String attributeName, SpecReloadSubject specReloadSubject)
	{
		this.packageReaders = new ArrayList<>(Arrays.asList(packageReaders));
		this.attributeName = attributeName;
		this.specReloadSubject = specReloadSubject;
		load();
	}

	public long getLastLoadTimestamp()
	{
		return lastLoadTimestamp;
	}

	synchronized void load()
	{
		lastLoadTimestamp = System.currentTimeMillis();

		cachedDescriptions.clear();
		cachedLayoutDescriptions.clear();
		allWebObjectSpecifications.clear();
		allPackages.clear();
		packagesWithGloballyDefinedTypes.clear();
		List<Package> packages = new ArrayList<>();
		for (IPackageReader packageReader : packageReaders)
		{
			packages.add(new Package(packageReader));
		}
		try
		{
			readGloballyDefinedTypes(packages);
			cacheWebObjectSpecs(packages);
		}
		finally
		{
			for (Package p : packages)
			{
				p.dispose();
			}
		}

		specReloadSubject.fireWebObjectSpecificationReloaded();
		specReloadSubject.removeOtherSpecReloadListeners(allWebObjectSpecifications.keySet());

		specProviderState = null;
	}

	/**
	 * Updates available packages. "toRemove" provided contents will be removed, "toAdd" will be made available.
	 */
	public synchronized void updatePackages(Collection<IPackageReader> packagesToRemove, Collection<IPackageReader> packagesToAdd)
	{
		if (packagesToRemove.size() == 0 && packagesToAdd.size() == 0) return;

		specProviderState = null;

		lastLoadTimestamp = System.currentTimeMillis();
		List<String> removedOrReloadedSpecs = new ArrayList<>(); // so not newly added

		boolean shouldReloadAllDueToGlobalTypeChanges = false;
		for (IPackageReader packageToRemove : packagesToRemove)
		{
			// find the package name from cache - cause the IPackageReader might already be invalid (contents deleted and such - so we can't rely on it's .getPackageName())
			String packageNameToRemove = null;
			for (Entry<String, List<IPackageReader>> e : allPackages.entrySet())
			{
				if (e.getValue().contains(packageToRemove))
				{
					packageNameToRemove = e.getKey();
					break;
				}
			}

			if (packageNameToRemove != null)
			{
				if (packagesWithGloballyDefinedTypes.contains(packageNameToRemove))
				{
					shouldReloadAllDueToGlobalTypeChanges = true;
					break; // we will reload all in this case anyway
				}

				// unload package
				cachedLayoutDescriptions.remove(packageNameToRemove);
				packagesWithGloballyDefinedTypes.remove(packageNameToRemove);
				PackageSpecification<WebObjectSpecification> removedPackageSpecs = cachedDescriptions.remove(packageNameToRemove);
				if (removedPackageSpecs != null) for (String specName : removedPackageSpecs.getSpecifications().keySet())
				{
					allWebObjectSpecifications.remove(specName);
					removedOrReloadedSpecs.add(specName);
				}
				List<IPackageReader> oldPackageReader = allPackages.remove(packageNameToRemove);
				if (oldPackageReader != null)
				{
					// first remove all of them from the package readers
					packageReaders.removeAll(oldPackageReader);
					// remove the one we really want to remove from this list
					oldPackageReader.remove(packageToRemove);
					// then add the list back into the packagesToAdd so they are added
					if (oldPackageReader.size() > 0)
					{
						for (IPackageReader pr : oldPackageReader)
						{
							if (!packagesToAdd.contains(pr) && !packagesToRemove.contains(pr))
							{
								packagesToAdd.add(pr);
							}
							pr.clearError();
						}
					}
				}
			}
			else
			{
				log.warn("Cannot unload an ng package: " + packageToRemove);
			}
		}

		boolean shouldStillFireReloadListeners = true;

		if (shouldReloadAllDueToGlobalTypeChanges)
		{
			load(); // reload all because removed packages had global types
			shouldStillFireReloadListeners = false;
		}
		else
		{
			// load new packages
			packageReaders.addAll(packagesToAdd);

			List<Package> packages = new ArrayList<>();
			for (IPackageReader packageReader : packagesToAdd)
			{
				packages.add(new Package(packageReader));
			}
			try
			{
				if (readGloballyDefinedTypes(packages))
				{
					load(); // reload all because added packages have global types
					shouldStillFireReloadListeners = false;
				}
				else
				{
					cacheWebObjectSpecs(packages);
				}
			}
			finally
			{
				for (Package p : packages)
				{
					p.dispose();
				}
			}
		}


		if (shouldStillFireReloadListeners)
		{
			specReloadSubject.fireWebObjectSpecificationReloaded(removedOrReloadedSpecs);
			specReloadSubject.removeOtherSpecReloadListeners(allWebObjectSpecifications.keySet());
		}
	}

	protected synchronized boolean readGloballyDefinedTypes(List<Package> packages)
	{
		boolean globalTypesFound = false;
		try
		{
			JSONObject typeContainer = new JSONObject();
			JSONObject allGlobalTypesFromAllPackages = new JSONObject();
			typeContainer.put(WebObjectSpecification.TYPES_KEY, allGlobalTypesFromAllPackages);

			for (Package p : packages)
			{
				try
				{
					if (p.appendGlobalTypesJSON(allGlobalTypesFromAllPackages))
					{
						globalTypesFound = true;
						packagesWithGloballyDefinedTypes.add(p.getPackageName());
					}
				}
				catch (Exception e)
				{
					log.error("Cannot read globally defined types from package: " + p.getName(), e); //$NON-NLS-1$
				}
			}

			try
			{
				TypesRegistry.addTypes(WebObjectSpecification.getTypes(typeContainer).values());
			}
			catch (Exception e)
			{
				log.error("Cannot parse flattened global types - from all web component packages.", e);
			}
		}
		catch (JSONException e)
		{
			// should never happen
			log.error("Error Creating a simple JSON object hierarchy while reading globally defined types...");
		}
		return globalTypesFound;
	}

	private void cacheWebObjectSpecs(List<Package> packages)
	{
		for (Package p : packages)
		{
			try
			{
				cache(p);
			}
			catch (Exception e)
			{
				log.error("Cannot read web component specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
			try
			{
				PackageSpecification<WebLayoutSpecification> layoutDescriptions = p.getLayoutDescriptions();
				if (layoutDescriptions.getSpecifications().size() > 0) cachedLayoutDescriptions.put(p.getPackageName(), layoutDescriptions);
			}
			catch (Exception e)
			{
				log.error("Cannot read web layout specs from package: " + p.getName(), e); //$NON-NLS-1$
			}
		}
	}

	private void cache(Package p) throws IOException
	{
		List<IPackageReader> list = allPackages.get(p.getPackageName());
		if (list == null)
		{
			list = new ArrayList<>(3);
			allPackages.put(p.getPackageName(), list);
		}
		else if (list.size() > 0)
		{
			IPackageReader oldPackage = list.get(0);
			if (oldPackage != null)
			{
				File pResource = p.getReader().getResource();
				File oldPackageResource = oldPackage.getResource();

				// if we have duplicate packages, and one is a project (its resource is a folder)
				// and the other one is a file (zip), then just use the project, and skip the error
				boolean isPackageConflict = true;
				if (pResource != null && oldPackageResource != null)
				{
					if (oldPackageResource.isFile() && pResource.isDirectory())
					{
						list.remove(oldPackage);
						isPackageConflict = false;
					}
					else if (oldPackageResource.isDirectory() && pResource.isFile())
					{
						isPackageConflict = false;
					}
				}

				if (isPackageConflict)
				{
					log.error("Conflict found! Duplicate web component / web service / web layout package name: " + oldPackage.getPackageName());
					log.error("Location 1 : " + oldPackage.getPackageURL());
					log.error("Location 2 : " + p.getReader().getPackageURL());
					log.error("Will discard location 1 and load location 2... But this should be adressed by the solution.");
					p.getReader().reportError("", new DuplicateEntityException("Duplicate package found: " + oldPackage.getPackageName()));
				}
			}
		}
		list.add(p.getReader());
		PackageSpecification<WebObjectSpecification> webComponentPackageSpecification = p.getWebObjectDescriptions(attributeName);
		if (!cachedDescriptions.containsKey(webComponentPackageSpecification.getPackageName()))
		{
			cachedDescriptions.put(webComponentPackageSpecification.getPackageName(), webComponentPackageSpecification);
			Map<String, WebObjectSpecification> webComponentDescriptions = webComponentPackageSpecification.getSpecifications();
			for (WebObjectSpecification desc : webComponentDescriptions.values())
			{
				WebObjectSpecification old = allWebObjectSpecifications.put(desc.getName(), desc);
				if (old != null)
				{
					String s = "Duplicate web object definition found; name: " + old.getName() + ". Packages: " + old.getPackageName() + " and " +
						desc.getPackageName() + ".";
					log.error(s);
					p.getReader().reportError(desc.getSpecURL().toString(), new DuplicateEntityException(s));
				}
			}
		}
	}

	/**
	 * Get the current state of spec providers, returns an immutable state.
	 */
	public synchronized SpecProviderState getSpecProviderState()
	{
		if (specProviderState == null)
		{
			specProviderState = new SpecProviderState(cachedDescriptions, cachedLayoutDescriptions, allWebObjectSpecifications, packageReaders);
		}
		return specProviderState;
	}


}
