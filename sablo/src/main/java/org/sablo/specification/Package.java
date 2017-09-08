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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.ServletContext;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstraction of package that contains Servoy NG web-components / web-services.
 *
 * @author acostescu
 */
public class Package
{
	private static final Logger log = LoggerFactory.getLogger(Package.class.getCanonicalName());
	private static final String GLOBAL_TYPES_MANIFEST_ATTR = "Global-Types"; //$NON-NLS-1$
	public static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName"; // for package name //$NON-NLS-1$
	public static final String BUNDLE_NAME = "Bundle-Name"; // for package display name //$NON-NLS-1$
	public static final String PACKAGE_TYPE = "Package-Type"; //$NON-NLS-1$

	public interface IPackageReader
	{
		/**
		 * Identifier used in the manifest of service packages to list web services. Can also be returned by {@link #getPackageType()}.
		 */
		public static final String WEB_SERVICE = "Web-Service"; //$NON-NLS-1$
		/**
		 * Identifier used in the manifest of component packages to list web components. Can also be returned by {@link #getPackageType()}.
		 */
		public static final String WEB_COMPONENT = "Web-Component"; //$NON-NLS-1$

		/**
		 * Identifier used in the manifest of layout packages to list web services. Can also be returned by {@link #getPackageType()}.
		 */
		public static final String WEB_LAYOUT = "Web-Layout"; //$NON-NLS-1$

		String getName();

		String getPackageName();

		String getPackageDisplayname();

		String getVersion();

		Manifest getManifest() throws IOException;

		String readTextFile(String path, Charset charset) throws IOException;

		URL getUrlForPath(String path) throws MalformedURLException;

		URL getPackageURL();

		void reportError(String specpath, Exception e);

		void clearError();

		/**
		 * A package can contain either components or services. This method looks in the manifest for the first (not necessarily in definition order) type of web object it can find declared
		 * and returns that type.
		 * @return one of {@link #WEB_SERVICE}, {@link #WEB_COMPONENT} or {@link #WEB_LAYOUT}; null if no such entry is found in the manifest.
		 * @throws IOException if the manifest file cannot be read.
		 */
		String getPackageType();

		/**
		 * returns the File reference for this resource if it has one.
		 */
		File getResource();

	}
	public interface ISpecificationFilter
	{
		boolean filter(WebObjectSpecification spec);
	}

	private IPackageReader reader;

	public Package(IPackageReader reader)
	{
		if (reader == null) throw new NullPointerException();
		this.reader = reader;
	}

	public String getName()
	{
		return reader.getName();
	}

	public String getPackageName()
	{
		return reader.getPackageName();
	}

	IPackageReader getReader()
	{
		return reader;
	}


	/**
	 * Returns true if any globally defined types were appended.
	 */
	public boolean appendGlobalTypesJSON(JSONObject allGlobalTypesFromAllPackages) throws IOException
	{
		boolean globalTypesFound = false;
		Manifest mf = reader.getManifest();

		if (mf != null)
		{
			Attributes mainAttrs = mf.getMainAttributes();
			if (mainAttrs != null)
			{
				String globalTypesSpecPath = mainAttrs.getValue(GLOBAL_TYPES_MANIFEST_ATTR);
				if (globalTypesSpecPath != null)
				{
					try
					{
						String specfileContent = reader.readTextFile(globalTypesSpecPath, Charset.forName("UTF8")); // TODO: check encoding
						if (specfileContent != null)
						{
							JSONObject json = new JSONObject(specfileContent);
							Object types = json.get(WebObjectSpecification.TYPES_KEY);
							if (types instanceof JSONObject)
							{
								Iterator<String> typesIt = ((JSONObject)types).keys();
								while (typesIt.hasNext())
								{
									String key = typesIt.next();
									allGlobalTypesFromAllPackages.put(key, ((JSONObject)types).get(key));
									globalTypesFound = true;
								}
							}
						}
					}
					catch (Exception e)
					{
						reader.reportError(globalTypesSpecPath, e);
					}
				}
			}
		}
		return globalTypesFound;
	}

	public PackageSpecification<WebObjectSpecification> getWebObjectDescriptions(String attributeName) throws IOException
	{
		String packageName = reader.getPackageName();
		String packageDisplayname = null;
		Map<String, WebObjectSpecification> descriptions = new HashMap<>();
		Manifest mf = reader.getManifest();
		if (mf != null)
		{
			packageDisplayname = reader.getPackageDisplayname();

			for (String specpath : getWebEntrySpecNames(mf, attributeName))
			{
				try
				{
					String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
					if (specfileContent != null)
					{
						WebObjectSpecification parsed = WebObjectSpecification.parseSpec(specfileContent, reader.getPackageName(), reader);
						if (reader instanceof ISpecificationFilter && ((ISpecificationFilter)reader).filter(parsed)) continue;
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						if (parsed.getDefinition() != null)
						{
							String definition;
							if (packageName != null && parsed.getDefinition().startsWith(packageName + '/'))
							{
								definition = parsed.getDefinition().substring(packageName.length() + 1);
							}
							else if (packageName != null && parsed.getDefinition().startsWith("/"))
							{
								definition = parsed.getDefinition();
							}
							else
							{
								log.warn("Definition file for spec file " + specpath + " does not start with package name '" + packageName + "'");
								definition = parsed.getDefinition().substring(parsed.getDefinition().indexOf("/") + 1);
							}
							parsed.setDefinitionFileURL(reader.getUrlForPath(definition));
						}
						descriptions.put(parsed.getName(), parsed);
					}
					else
					{
						String s = "could not read specification files content of " + specpath + ", url is not resolved, casing problem?";
						log.warn(s);
						reader.reportError("META-INF/MANIFEST.MF", new RuntimeException(s));
					}
				}
				catch (Exception e)
				{
					reader.reportError("META-INF/MANIFEST.MF", e);
				}
			}
		}

		return new PackageSpecification<>(packageName, packageDisplayname, descriptions, mf);
	}

	public PackageSpecification<WebLayoutSpecification> getLayoutDescriptions() throws IOException
	{
		String packageName = null;
		String packageDisplayname = null;
		Map<String, WebLayoutSpecification> descriptions = new HashMap<>();
		Manifest mf = reader.getManifest();
		if (mf != null)
		{
			packageName = reader.getPackageName();
			packageDisplayname = reader.getPackageDisplayname();
			for (String specpath : getWebEntrySpecNames(mf, IPackageReader.WEB_LAYOUT))
			{
				try
				{
					String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
					if (specfileContent != null)
					{
						WebLayoutSpecification parsed = WebLayoutSpecification.parseLayoutSpec(specfileContent, packageName, reader);
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						String definition = parsed.getDefinition();
						if (definition != null)
						{
							if (definition.startsWith(packageName + '/'))
							{
								definition = definition.substring(packageName.length() + 1);
							}
							parsed.setDefinitionFileURL(reader.getUrlForPath(definition));
						}
						descriptions.put(parsed.getName(), parsed);
					}
				}
				catch (Exception e)
				{
					reader.reportError(specpath, e);
				}
			}
			// TODO deprecate and only use Web-Layout
			for (String specpath : getWebEntrySpecNames(mf, "Web-Composite"))
			{
				try
				{
					String specfileContent = reader.readTextFile(specpath, Charset.forName("UTF8")); // TODO: check encoding
					if (specfileContent != null)
					{
						WebLayoutSpecification parsed = WebLayoutSpecification.parseLayoutSpec(specfileContent, packageName, reader);
						parsed.setSpecURL(reader.getUrlForPath(specpath));
						String definition = parsed.getDefinition();
						if (definition != null)
						{
							if (definition.startsWith(packageName + '/'))
							{
								definition = definition.substring(packageName.length() + 1);
							}
							parsed.setDefinitionFileURL(reader.getUrlForPath(definition));
						}
						descriptions.put(parsed.getName(), parsed);
					}
				}
				catch (Exception e)
				{
					reader.reportError(specpath, e);
				}
			}
		}
		return new PackageSpecification<>(packageName, packageDisplayname, descriptions, mf);
	}

	private static List<String> getWebEntrySpecNames(Manifest mf, String attributeName)
	{
		List<String> names = new ArrayList<String>();
		for (Entry<String, Attributes> entry : mf.getEntries().entrySet())
		{
			if ("true".equalsIgnoreCase((String)entry.getValue().get(new Attributes.Name(attributeName))))
			{
				names.add(entry.getKey());
			}
		}

		return names;
	}

	public void dispose()
	{
		reader = null;
	}

	public static class JarServletContextReader implements IPackageReader
	{
		private final ServletContext servletContext;
		private final String resourcePath;

		private Manifest manifest = null;

		public JarServletContextReader(ServletContext servletContext, String resourcePath)
		{
			this.servletContext = servletContext;
			this.resourcePath = resourcePath;
		}

		@Override
		public String getName()
		{
			return resourcePath;
		}

		@Override
		public String getPackageName()
		{
			try
			{
				String packageName = Package.getPackageName(getManifest());
				if (packageName != null) return packageName;
			}
			catch (Exception e)
			{
				log.error("Error getting package name." + getName(), e);
			}
			return FilenameUtils.getBaseName(resourcePath);
		}

		@Override
		public String getVersion()
		{
			try
			{
				return getManifest().getMainAttributes().getValue("Bundle-Version");
			}
			catch (IOException e)
			{
			}
			return null;
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = Package.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (IOException e)
			{
				log.error("Error getting package display name." + getName(), e);
			}

			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			if (manifest == null)
			{
				try (JarInputStream jarInputStream = new JarInputStream(servletContext.getResourceAsStream(resourcePath)))
				{
					manifest = jarInputStream.getManifest();
				}
			}
			return manifest;
		}

		@Override
		public URL getUrlForPath(String path) throws MalformedURLException
		{
			return servletContext.getResource(path.charAt(0) == '/' ? path : '/' + getPackageName() + '/' + path);
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			String pathWithSlashPrefix = path.charAt(0) == '/' ? path : '/' + path;
			try (InputStream inputStream = servletContext.getResourceAsStream(pathWithSlashPrefix))
			{
				if (inputStream != null)
				{
					return IOUtils.toString(inputStream, charset);
				}
			}
			return null;
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package '" + toString() + "'. ", e);
		}

		@Override
		public void clearError()
		{
		}


		@Override
		public String toString()
		{
			return "JarPackage: " + getName();
		}

		@Override
		public URL getPackageURL()
		{
			try
			{
				return servletContext.getResource(resourcePath);
			}
			catch (MalformedURLException e)
			{
				log.error("MalformedURL", e);
			}
			return null;
		}

		@Override
		public String getPackageType()
		{
			try
			{
				return Package.getPackageType(getManifest());
			}
			catch (IOException e)
			{
				log.error("Error getting package type." + getName(), e);
			}
			return null;
		}

		@Override
		public File getResource()
		{
			return null;
		}
	}


	public static class ZipPackageReader implements Package.IPackageReader
	{
		private final File file;
		private final String name;
		private Manifest manifest;

		public ZipPackageReader(File file, String name)
		{
			this.name = name;
			this.file = file;
		}

		@Override
		public String getName()
		{
			return name;
		}

		@Override
		public String getPackageName()
		{
			try
			{
				String packageDisplayname = Package.getPackageName(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (Exception e)
			{
				log.error("Error in getPackageName", e);
			}

			// fall back to symbolic name
			return FilenameUtils.getBaseName(getName());
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = Package.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (Exception e)
			{
				log.error("Error in getPackageDisplayname", e); //$NON-NLS-1$
			}
			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public String getVersion()
		{
			try
			{
				return getManifest().getMainAttributes().getValue("Bundle-Version");
			}
			catch (IOException e)
			{
			}
			return null;
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			if (manifest == null)
			{
				try (ZipFile zip = new ZipFile(file))
				{
					ZipEntry m = zip.getEntry("META-INF/MANIFEST.MF"); //$NON-NLS-1$
					if (m == null)
					{
						reportError(zip.getName(), new IllegalStateException(zip.getName() + " doesn't have a manifest"));
						manifest = new Manifest();
					}
					else
					{
						try (InputStream is = zip.getInputStream(m))
						{
							manifest = new Manifest(is);
						}
					}
				}
			}
			return manifest;
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			try (ZipFile zip = new ZipFile(file))
			{
				ZipEntry entry = zip.getEntry(path);
				if (entry != null)
				{
					try (InputStream is = zip.getInputStream(entry))
					{
						return IOUtils.toString(is, charset);
					}
				}
			}
			return null;
		}

		@Override
		public URL getUrlForPath(String path) throws MalformedURLException
		{
			String pathWithSlashPrefix = path.startsWith("/") ? path : "/" + path; //$NON-NLS-1$ //$NON-NLS-2$
			String pathWithoutSlashPrefix = path.startsWith("/") ? path.substring(1) : path;
			try (ZipFile zip = new ZipFile(file))
			{
				ZipEntry entry = zip.getEntry(pathWithoutSlashPrefix);
				if (entry != null)
				{
					return new URL("jar:" + file.toURI().toString() + "!" + pathWithSlashPrefix); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			catch (IOException e)
			{
				log.error("Exception in getUrlForPath", e);
			}
			log.warn("Could not find " + path + " in " + file.getName());
			return null;
		}

		@Override
		public URL getPackageURL()
		{
			try
			{
				return file.toURI().toURL();
			}
			catch (MalformedURLException e)
			{
				log.error("Error in getPackageURL ", e);
			}
			return null;
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Error at specpath " + specpath, e); //$NON-NLS-1$
		}

		@Override
		public void clearError()
		{
		}


		@Override
		public String getPackageType()
		{
			try
			{
				return Package.getPackageType(getManifest());
			}
			catch (IOException e)
			{
				log.error("Error getting package type." + getName(), e);
			}
			return null;
		}

		@Override
		public File getResource()
		{
			return file;
		}

		@Override
		public String toString()
		{
			return "ZipPackage: " + file;
		}

	}

	public static class DirPackageReader implements IPackageReader
	{

		protected final File dir;
		protected Manifest manifest;

		public DirPackageReader(File dir)
		{
			if (!dir.isDirectory()) throw new IllegalArgumentException("Non-directory package cannot be read by directory reader: " + dir.getAbsolutePath());
			this.dir = dir;
		}

		@Override
		public String getName()
		{
			return dir.getAbsolutePath();
		}

		@Override
		public String getPackageName()
		{
			try
			{
				String packageName = Package.getPackageName(getManifest());
				if (packageName != null) return packageName;
			}
			catch (IOException e)
			{
				log.error("Error getting package name", e);
			}
			return dir.getName();
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = Package.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (IOException e)
			{
				log.error("Error getting package display name", e);
			}

			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public String getVersion()
		{
			try
			{
				return getManifest().getMainAttributes().getValue("Bundle-Version");
			}
			catch (IOException e)
			{
			}
			return null;
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			if (manifest == null)
			{
				try (InputStream is = new BufferedInputStream(new FileInputStream(new File(dir, "META-INF/MANIFEST.MF"))))
				{
					manifest = new Manifest(is);
				}
			}
			return manifest;

		}

		@Override
		public URL getUrlForPath(String path)
		{
			File file = new File(dir, path);
			if (file.exists())
			{
				try
				{
					return file.toURI().toURL();
				}
				catch (MalformedURLException e)
				{
					log.error("MalformedURLException", e);
				}
			}
			return null;
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			try (InputStream is = new BufferedInputStream(new FileInputStream(new File(dir, path))))
			{
				return IOUtils.toString(is, charset);
			}
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package '" + toString() + "'. ", e);
		}

		@Override
		public void clearError()
		{
		}

		@Override
		public String toString()
		{
			return "DirPackage: " + dir.getAbsolutePath();
		}

		@Override
		public URL getPackageURL()
		{
			try
			{
				return dir.toURI().toURL();
			}
			catch (MalformedURLException e)
			{
				log.error("MalformedURLException", e);
			}
			return null;
		}

		@Override
		public String getPackageType()
		{
			try
			{
				Manifest man = getManifest();
				return Package.getPackageType(man);
			}
			catch (Exception e)
			{
				log.error("Error getting package type." + getName(), e);
			}
			return null;
		}

		@Override
		public File getResource()
		{
			return dir;
		}
	}

	public static class WarURLPackageReader implements Package.IPackageReader, Package.ISpecificationFilter
	{
		private final URL urlOfManifest;
		private final String packageName;
		private final ServletContext servletContext;
		private HashSet<String> usedWebObjects;
		private Manifest manifest;

		public WarURLPackageReader(ServletContext servletContext, String packageName) throws MalformedURLException
		{
			this.packageName = packageName.endsWith("/") ? packageName : packageName + "/";
			this.urlOfManifest = servletContext.getResource(this.packageName + "META-INF/MANIFEST.MF");
			this.servletContext = servletContext;
			if (urlOfManifest == null)
			{
				throw new IllegalArgumentException("Package " + this.packageName + "META-INF/MANIFEST.MF not found in this context");
			}
			if (servletContext.getResource("/WEB-INF/exported_web_objects.properties") != null)
			{
				try (InputStream is = servletContext.getResourceAsStream("/WEB-INF/exported_web_objects.properties"))
				{
					Properties properties = new Properties();
					properties.load(is);
					usedWebObjects = new HashSet<String>(Arrays.asList(properties.getProperty("usedWebObjects").split(",")));
				}
				catch (Exception e)
				{
					throw new IllegalArgumentException("Exception while reading exported_web_objects.properties...", e);
				}
			}
		}

		@Override
		public String getName()
		{
			return urlOfManifest.toExternalForm();
		}

		@Override
		public String getPackageName()
		{
			return packageName.replaceAll("/", "");
		}

		@Override
		public String getPackageDisplayname()
		{
			try
			{
				String packageDisplayname = Package.getPackageDisplayname(getManifest());
				if (packageDisplayname != null) return packageDisplayname;
			}
			catch (IOException e)
			{
				log.error("getting package display name." + getName(), e);
			}

			// fall back to symbolic name
			return getPackageName();
		}

		@Override
		public String getVersion()
		{
			try
			{
				return getManifest().getMainAttributes().getValue("Bundle-Version");
			}
			catch (IOException e)
			{
			}
			return null;
		}

		@Override
		public Manifest getManifest() throws IOException
		{
			if (manifest == null)
			{
				try (InputStream is = urlOfManifest.openStream())
				{
					manifest = new Manifest(is);
				}
			}
			return manifest;

		}

		@Override
		public URL getUrlForPath(String path)
		{
			try
			{
				return servletContext.getResource(packageName + path);// path includes /
			}
			catch (MalformedURLException e)
			{
				log.error("MalformedURLException", e);
				return null;
			}
		}

		@Override
		public String readTextFile(String path, Charset charset) throws IOException
		{
			URL url = getUrlForPath(path);
			if (url == null) return null;

			try (InputStream is = url.openStream())
			{
				return IOUtils.toString(is, charset);
			}
		}

		@Override
		public void reportError(String specpath, Exception e)
		{
			log.error("Cannot parse spec file '" + specpath + "' from package 'WarReeader[ " + urlOfManifest + " ]'. ", e);
		}

		@Override
		public void clearError()
		{
		}


		@Override
		public URL getPackageURL()
		{
			return null;
		}

		/**
		 * @return true if the component is not in the list of the used web objects
		 */
		@Override
		public boolean filter(WebObjectSpecification spec)
		{
			return usedWebObjects != null && !usedWebObjects.contains(spec.getName());
		}

		@Override
		public String getPackageType()
		{
			try
			{
				return Package.getPackageType(getManifest());
			}
			catch (IOException e)
			{
				log.error("Error getting package type." + getName(), e);
			}
			return null;
		}

		@Override
		public File getResource()
		{
			return null;
		}

	}

	@Override
	public String toString()
	{
		return "WebComponent-package: " + getPackageName();
	}

	public static String getPackageName(Manifest manifest)
	{
		String bundleName = null;
		if (manifest != null)
		{
			bundleName = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME);

			if (bundleName != null && bundleName.indexOf(';') > 0)
			{
				return bundleName.substring(0, bundleName.indexOf(';')).trim();
			}
		}
		return bundleName;
	}

	public static String getPackageType(Manifest manifest)
	{
		//first try to find the attribute
		String packageType = manifest.getMainAttributes().getValue("Package-Type");
		if (packageType != null) return packageType;
		//else we have to search for the first element to see what kind of elements this package contains
		if (manifest.getEntries() != null)
		{
			for (Entry<String, Attributes> entry : manifest.getEntries().entrySet())
			{
				if ("true".equalsIgnoreCase((String)entry.getValue().get(new Attributes.Name(IPackageReader.WEB_SERVICE))))
				{
					return IPackageReader.WEB_SERVICE;
				}
				else if ("true".equalsIgnoreCase((String)entry.getValue().get(new Attributes.Name(IPackageReader.WEB_COMPONENT))))
				{
					return IPackageReader.WEB_COMPONENT;
				}
				else if ("true".equalsIgnoreCase((String)entry.getValue().get(new Attributes.Name(IPackageReader.WEB_LAYOUT))))
				{
					return IPackageReader.WEB_LAYOUT;
				}
			}
		}
		return null;
	}

	public static String getPackageDisplayname(Manifest manifest)
	{
		if (manifest == null) return null;
		return manifest.getMainAttributes().getValue(BUNDLE_NAME);
	}

	public static class DuplicateEntityException extends Exception
	{
		public DuplicateEntityException(String message)
		{
			super(message);
		}
	}

}
