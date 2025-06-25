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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Manifest;

import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.Package.JarServletContextReader;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;

/**
 * Class responsible for loading the service spec files.
 *
 * @author jcompagner
 */
public class WebServiceSpecProvider extends BaseSpecProvider
{
	private static final Logger log = LoggerFactory.getLogger(WebServiceSpecProvider.class.getCanonicalName());

	private static volatile WebServiceSpecProvider instance;

	private EmbeddableJSONWriter[] clientSideTypesWithConversionsOnAllServices; // array with 1 item (so that we can make a difference if it's not yet cached (null) or if it is cached (array[1] that can contain either null if there is nothing to be sent or something))

	private static SpecReloadSubject specReloadSubject = new SpecReloadSubject()
	{
		@Override
		void fireWebObjectSpecificationReloaded(Collection<String> specNames)
		{
			if (instance != null) instance.clientSideTypesWithConversionsOnAllServices = null;
			super.fireWebObjectSpecificationReloaded(specNames);
		}
	};


	private WebServiceSpecProvider(WebSpecReader reader)
	{
		super(reader);
	}

	public static WebServiceSpecProvider getInstance()
	{
		return instance;
	}

	public static SpecReloadSubject getSpecReloadSubject()
	{
		return specReloadSubject;
	}

	public static synchronized void init(IPackageReader[] locations)
	{
		instance = new WebServiceSpecProvider(new WebSpecReader(locations, IPackageReader.WEB_SERVICE, specReloadSubject, null));
	}

	public static void disposeInstance()
	{
		instance = null;
	}

	public static WebServiceSpecProvider init(ServletContext servletContext, String[] webComponentBundleNames)
	{
		if (instance == null)
		{
			synchronized (WebServiceSpecProvider.class)
			{
				if (instance == null)
				{
					List<IPackageReader> readers = new ArrayList<IPackageReader>();
					for (String location : webComponentBundleNames)
					{
						try
						{
							readers.add(new Package.WarURLPackageReader(servletContext, location));
						}
						catch (Exception e)
						{
							log.error("Exception during init", e);
						}
					}

					// scan all jars for services
					for (String resourcePath : servletContext.getResourcePaths("/WEB-INF/lib"))
					{
						if (resourcePath.toLowerCase().endsWith(".jar") || resourcePath.toLowerCase().endsWith(".zip"))
						{
							try
							{
								IPackageReader reader = new JarServletContextReader(servletContext, resourcePath);
								Manifest mf = reader.getManifest();
								if (mf != null && IPackageReader.WEB_SERVICE.equals(Package.getPackageType(mf))) readers.add(reader);
							}
							catch (Exception e)
							{
								log.error("Exception during init", e);
							}
						}
					}

					instance = new WebServiceSpecProvider(
						new WebSpecReader(readers.toArray(new IPackageReader[readers.size()]), IPackageReader.WEB_SERVICE, specReloadSubject, null));
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
				"Called WebServiceSpecProvider.getSpecProviderState() on a none initialzed provider, this can be just a problem in startup, returning an empty state",
				new RuntimeException("spec service provider is null"));
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
		synchronized (WebServiceSpecProvider.class)
		{
			return instance.reader.getLastLoadTimestamp();
		}
	}

	public EmbeddableJSONWriter getClientSideSpecs()
	{
		if (clientSideTypesWithConversionsOnAllServices == null)
		{
			// find any types that have client-side conversions that are used by services
			// and cache them

			WebObjectSpecification[] allServices = getSpecProviderState().getAllWebObjectSpecifications();
			boolean hasClientSideTypes = false;
			EmbeddableJSONWriter toBeSent = new EmbeddableJSONWriter();
			if (allServices != null && allServices.length > 0)
			{
				toBeSent.object(); // keys are spec names, values are objects so: { serviceNameFromSpec: { /* see comment from ClientSideTypeCache.getClientSideTypesFor() */ } , ... }
				for (WebObjectSpecification serviceSpec : allServices)
				{
					EmbeddableJSONWriter clSideTypesForThisComponent = ClientSideTypeCache.buildClientSideTypesFor(serviceSpec);
					if (clSideTypesForThisComponent != null)
					{
						// normally scriptingName (camel-case instead of dashes) does include package name (to make it unique) and it is also what is used to find service and service specs. client-side...
						toBeSent.key(serviceSpec.getScriptingName()).value(clSideTypesForThisComponent);
						hasClientSideTypes = true;
					}
				}
				toBeSent.endObject();
			}

			clientSideTypesWithConversionsOnAllServices = new EmbeddableJSONWriter[1]; // create the cache so that we don't re-search next time (that is why it's an array of 1 when cached val is there; the item in it could be null or not
			if (hasClientSideTypes) clientSideTypesWithConversionsOnAllServices[0] = toBeSent;
		}
		return clientSideTypesWithConversionsOnAllServices[0];
	}

}
