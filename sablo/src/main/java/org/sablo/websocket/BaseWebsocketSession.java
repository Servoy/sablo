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

package org.sablo.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;
import org.sablo.IChangeListener;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.eventthread.WebsocketSessionWindows;
import org.sablo.services.client.SabloService;
import org.sablo.services.server.FormServiceHandler;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.impl.ClientService;
import org.sablo.websocket.utils.ObjectReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Base class for handling a websocket session.
 * @author rgansevles
 */
public abstract class BaseWebsocketSession implements IWebsocketSession, IChangeListener
{
	private static final String PROPERTY_WINDOW_TIMEOUT = "sablo.window.timeout.secs";
	private static final String DEFAULT_WINDOW_TIMEOUT = "60";
	private static Long windowTimeout;
	private Long sessionWindowTimeout;

	private static final Logger log = LoggerFactory.getLogger(BaseWebsocketSession.class.getCanonicalName());

	private final Map<String, IServerService> serverServices = new HashMap<>();
	private final Map<String, IClientService> servicesByName = new HashMap<>();
	private final Map<String, IClientService> servicesByScriptingName = new HashMap<>();
	private final List<ObjectReference<IWindow>> windows = new CopyOnWriteArrayList<>();

	private final String uuid;
	private volatile IEventDispatcher executor;

	private final AtomicInteger handlingEvent = new AtomicInteger(0);

	private boolean proccessChanges;
	private final WebsocketSessionWindows allWindowsProxy = new WebsocketSessionWindows(this);


	public BaseWebsocketSession(String uuid)
	{
		this.uuid = uuid;
		registerServerService("formService", createFormService());
	}

	@Override
	public void init() throws Exception
	{
	}

	@Override
	public Collection< ? extends IWindow> getWindows()
	{
		List<IWindow> wins = new ArrayList<>(windows.size());
		for (ObjectReference<IWindow> ref : windows)
		{
			if (ref.getObject().hasEndpoint()) wins.add(ref.getObject());
		}
		return wins;
	}

	public long getLastAccessed()
	{
		long lastAccessed = Long.MIN_VALUE;
		for (ObjectReference<IWindow> ref : windows)
		{
			lastAccessed = Math.max(lastAccessed, ref.getLastAccessed());
		}
		return lastAccessed;
	}

	/**
	 * returns the last ping time of all the windows that are still connected.
	 */
	public long getLastPingTime()
	{
		long lastPingTime = 0;
		for (ObjectReference<IWindow> ref : windows)
		{
			lastPingTime = Math.max(lastPingTime, ref.getObject().getLastPingTime());
		}
		return lastPingTime;
	}

	@Override
	public IWindow getOrCreateWindow(String windowId, String windowName)
	{
		if (windowId != null)
		{
			for (ObjectReference<IWindow> ref : windows)
			{
				IWindow window = ref.getObject();
				if (windowId.equals(window.getUuid()))
				{
					if ((windowName == null && window.getName() == null) || (windowName != null && windowName.equals(window.getName())))
					{
						// window matches on name and uuid
						return window;
					}
					// else:
					// window with this uuid exists, but windowname is different, this can happen when a new tab is opened
					// and sessionstorage (containing windowid) is copied to the new tab.
				}
			}
		}

		// not found, create a new one
		IWindow window = createWindow(UUID.randomUUID().toString(), windowName);
		windows.add(new ObjectReference<IWindow>(window));
		return window;
	}

	@Override
	public IWindow getActiveWindow(String windowName)
	{
		for (ObjectReference<IWindow> ref : windows)
		{
			IWindow window = ref.getObject();
			if (ref.getObject().hasEndpoint() &&
				((windowName == null && window.getName() == null) || (windowName != null && windowName.equals(window.getName()))))
			{
				return window;
			}
		}

		// not found
		return null;
	}

	protected IWindow createWindow(String windowUuid, String windowName)
	{
		return new BaseWindow(this, windowUuid, windowName);
	}

	public void updateLastAccessed(IWindow window)
	{
		for (ObjectReference<IWindow> ref : windows)
		{
			if (window == ref.getObject())
			{
				ref.updateLastAccessed();
			}
		}
	}

	/**
	 * Check nonactive windows in timeout, return true when no more windows are left
	 * @return
	 */
	public boolean checkForWindowActivity()
	{
		List<IWindow> inactiveWindows = new ArrayList<>();
		//do global non active cleanup
		long currentTime = System.currentTimeMillis();
		Iterator<ObjectReference<IWindow>> iterator = windows.iterator();
		while (iterator.hasNext())
		{
			ObjectReference<IWindow> ref = iterator.next();
			long timeout = getWindowTimeout() * 1000;
			long lastTime = ref.getObject().getLastPingTime();
			if (lastTime == 0)
			{
				lastTime = ref.getLastAccessed();
			}
			if ((currentTime - lastTime) > timeout)
			{
				// this can't be an iterator.remove() CopyOnWrite doesn't support this, but we can just call remove because it is a CopyOnWrite
				windows.remove(ref);
				inactiveWindows.add(ref.getObject());
			}
		}

		for (IWindow window : inactiveWindows)
		{
			try
			{
				window.dispose();
			}
			catch (Exception e)
			{
				log.warn("Error destroying window " + window, e);
			}
		}

		return windows.size() == 0;
	}

	@Override
	public long getWindowTimeout()
	{
		if (sessionWindowTimeout != null)
		{
			return sessionWindowTimeout.longValue();
		}

		if (windowTimeout == null)
		{
			try
			{
				windowTimeout = Long.valueOf(System.getProperty(PROPERTY_WINDOW_TIMEOUT, DEFAULT_WINDOW_TIMEOUT));
			}
			catch (NumberFormatException e)
			{
				log.warn("Could not parse window timeout property " + PROPERTY_WINDOW_TIMEOUT + " '" +
					System.getProperty(PROPERTY_WINDOW_TIMEOUT, DEFAULT_WINDOW_TIMEOUT) + "', reverting to default : " + e.getMessage());
				windowTimeout = Long.valueOf(DEFAULT_WINDOW_TIMEOUT);
			}
		}

		return windowTimeout.longValue();
	}

	/**
	 * @param sessionWindowTimeout the sessionWindowTimeout to set
	 */
	public void setSessionWindowTimeout(Long sessionWindowTimeout)
	{
		this.sessionWindowTimeout = sessionWindowTimeout;
	}

	protected IServerService createFormService()
	{
		return FormServiceHandler.INSTANCE;
	}

	public boolean isValid()
	{
		return true;
	}

	public final IEventDispatcher getEventDispatcher()
	{
		if (executor == null)
		{
			synchronized (this)
			{
				if (executor == null)
				{
					Thread thread = new Thread(executor = createEventDispatcher(), "Executor,uuid:" + uuid);
					thread.setDaemon(true);
					thread.start();
				}
			}
		}
		return executor;
	}

	/**
	 * Method to create the {@link IEventDispatcher} runnable
	 */
	protected IEventDispatcher createEventDispatcher()
	{
		return new EventDispatcher(this);
	}

	public void onOpen(final Map<String, List<String>> requestParams)
	{
	}

	@Override
	public void sessionExpired()
	{
	}

	@Override
	public final void dispose()
	{
		onDispose();

		Collection< ? extends IWindow> allWindows = getWindows();
		windows.clear();

		for (IWindow window : allWindows)
		{
			try
			{
				window.closeSession();
			}
			catch (Exception e)
			{
				log.error("Error closing window", e);
			}
		}

		if (executor != null)
		{
			synchronized (this)
			{
				if (executor != null)
				{
					executor.destroy();
					executor = null;
				}
			}
		}

		servicesByName.clear();
		servicesByScriptingName.clear();
	}

	protected void onDispose()
	{
	}

	/**
	 * @return the uuid
	 */
	public String getUuid()
	{
		return uuid;
	}

	public void registerServerService(String name, IServerService service)
	{
		if (service != null)
		{
			serverServices.put(name, service);
		}
	}

	public IServerService getServerService(String name)
	{
		return serverServices.get(name);
	}

	protected void registerClientService(IClientService clientService)
	{
		if (clientService != null)
		{
			servicesByName.put(clientService.getName(), clientService);
			servicesByScriptingName.put(clientService.getScriptingName(), clientService);
		}
	}

	@Override
	public IClientService getClientService(String name)
	{
		IClientService clientService = servicesByName.get(name);
		if (clientService == null)
		{
			clientService = createClientService(name);
			servicesByName.put(name, clientService);
			servicesByScriptingName.put(clientService.getScriptingName(), clientService);
		}
		return clientService;
	}

	@Override
	public IClientService getClientServiceByScriptingName(String scriptingName)
	{
		IClientService clientService = servicesByScriptingName.get(scriptingName);

		if (clientService == null && scriptingName != null)
		{
			// hmm, it was not accessed before using it's real name (that would have created the IClientService instance for it and registered it also by scripting name)...
			// we have to find a client service who's scripting name we know but real name we don't (the conversion cannot be done directly)
			// first search for it directly (in case original name didn't have dashes, it will be the same)
			WebObjectSpecification sd = ClientService.getServiceDefinitionFromScriptingName(scriptingName);
			if (sd != null) clientService = getClientService(sd.getName()); // from now on we will know this service by scriptingName as well as it will be created here
		}

		return clientService;
	}

	@Override
	public SabloService getSabloService()
	{
		return new SabloService(getClientService(SabloService.SABLO_SERVICE));
	}

	@Override
	public Collection<IClientService> getServices()
	{
		return Collections.unmodifiableCollection(servicesByName.values());
	}

	protected IClientService createClientService(String name)
	{
		return new ClientService(name, WebServiceSpecProvider.getSpecProviderState().getWebComponentSpecification(name));
	}

	public void startHandlingEvent()
	{
		handlingEvent.incrementAndGet();
	}

	public void stopHandlingEvent()
	{
		handlingEvent.decrementAndGet();
		valueChanged();
	}

	@Override
	public void valueChanged()
	{
		// if there is an incoming message or an Event running on event thread, postpone sending until it's done; else push it.
		if (!proccessChanges && CurrentWindow.exists() && CurrentWindow.get().hasEndpoint() && handlingEvent.get() == 0)
		{
			proccessChanges = true;
			try
			{
				allWindowsProxy.sendChanges();
			}
			catch (IOException e)
			{
				log.error("Error sending changes", e);
			}
			finally
			{
				proccessChanges = false;
			}
		}
	}

	@Override
	public void handleMessage(JSONObject obj)
	{
		log.info("Unknown message from client ignored: " + obj.toString());
	}

}
