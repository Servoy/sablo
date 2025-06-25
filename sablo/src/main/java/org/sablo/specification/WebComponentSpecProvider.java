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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Manifest;

import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.Package.JarServletContextReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;

/**
 * Class responsible for loading a set of web component packages and specs.
 * @author acostescu
 */
public class WebComponentSpecProvider extends BaseSpecProvider
{
	private static final Logger log = LoggerFactory.getLogger(WebComponentSpecProvider.class.getCanonicalName());

	private static volatile WebComponentSpecProvider instance;
	private static final SpecReloadSubject specReloadSubject = new SpecReloadSubject();

	public static WebComponentSpecProvider getInstance()
	{
		return instance;
	}

	public static void disposeInstance()
	{
		instance = null;
	}

	public static SpecReloadSubject getSpecReloadSubject()
	{
		return specReloadSubject;
	}

	public static synchronized void init(IPackageReader[] locations, IDefaultComponentPropertiesProvider defaultComponentPropertiesProvider)
	{
		instance = new WebComponentSpecProvider(new WebSpecReader(locations, "Web-Component", specReloadSubject, defaultComponentPropertiesProvider));
	}

	public static WebComponentSpecProvider init(ServletContext servletContext, String[] webComponentBundleNames,
		IDefaultComponentPropertiesProvider defaultComponentPropertiesProvider)
	{
		if (instance == null)
		{
			synchronized (WebComponentSpecProvider.class)
			{
				if (instance == null)
				{
					try
					{
						List<IPackageReader> readers = new ArrayList<IPackageReader>();
						for (String location : webComponentBundleNames)
						{
							readers.add(new Package.WarURLPackageReader(servletContext, location));
						}

						// scan all jars for components
						for (String resourcePath : servletContext.getResourcePaths("/WEB-INF/lib"))
						{
							if (resourcePath.toLowerCase().endsWith(".jar") || resourcePath.toLowerCase().endsWith(".zip"))
							{
								IPackageReader reader = new JarServletContextReader(servletContext, resourcePath);
								Manifest mf = reader.getManifest();
								if (mf != null && Package.IPackageReader.WEB_COMPONENT.equals(Package.getPackageType(mf))) readers.add(reader);
							}
						}

						instance = new WebComponentSpecProvider(new WebSpecReader(readers.toArray(new IPackageReader[readers.size()]), "Web-Component",
							specReloadSubject, defaultComponentPropertiesProvider));
					}
					catch (Exception e)
					{
						log.error("Exception during init", e);
					}
				}
			}
		}
		return instance;
	}

	/**
	 * Get the current state of spec providers, returns an immutable state.
	 */
	public static SpecProviderState getSpecProviderState()
	{
		if (instance == null)
		{
			log.warn(
				"Called WebComponentSpecProvider.getSpecProviderState() on a none initialzed provider, this can be just a problem in startup, returning an empty state",
				new RuntimeException("spec component provider is null"));
			return new SpecProviderState(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
		}
		return instance.reader.getSpecProviderState();
	}

	public static boolean isLoaded()
	{
		return instance != null;
	}

	public static long getLastLoadTimestamp()
	{
		synchronized (WebComponentSpecProvider.class)
		{
			return instance.reader.getLastLoadTimestamp();
		}
	}

	private WebComponentSpecProvider(WebSpecReader reader)
	{
		super(reader);
	}

	public ClientSideTypeCache getClientSideTypeCache()
	{
		return reader.getClientSideTypeCache();

	}
}
