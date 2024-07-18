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

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.servlet.http.HttpSession;

import org.json.JSONObject;
import org.sablo.IChangeListener;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.eventthread.WebsocketSessionWindows;
import org.sablo.services.client.SabloService;
import org.sablo.services.client.TypesRegistryService;
import org.sablo.services.server.ConsoleLoggerServiceHandler;
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
	protected static final Logger SHUTDOWNLOGGER = LoggerFactory.getLogger("SHUTDOWNLOGGER"); //$NON-NLS-1$


	private static final String PROPERTY_WINDOW_TIMEOUT = "sablo.window.timeout.secs";
	public static final String DEFAULT_WINDOW_TIMEOUT = "60";
	private static Long windowTimeout;
	private Long sessionWindowTimeout;

	private static final Logger log = LoggerFactory.getLogger(BaseWebsocketSession.class.getCanonicalName());

	private final Map<String, IServerService> serverServices = new ConcurrentHashMap<>();
	private final Map<String, IClientService> servicesByName = new ConcurrentHashMap<>();
	private final Map<String, IClientService> servicesByScriptingName = new ConcurrentHashMap<>(); // TODO some/most services (mostly the ones that have to do with UI should be per window right? not per session...)
	private final List<ObjectReference<IWindow>> windows = new CopyOnWriteArrayList<>();

	private final WebsocketSessionKey sessionKey;
	protected volatile IEventDispatcher executor;

	private final AtomicInteger handlingEvent = new AtomicInteger(0);

	private boolean proccessChanges;
	private final WebsocketSessionWindows allWindowsProxy = new WebsocketSessionWindows(this);

	private final DisposeHandlersSubject disposeHandlersSubject = new DisposeHandlersSubject();
	private int windowCounter;
	private HttpSession httpSession;


	public BaseWebsocketSession(WebsocketSessionKey sessionKey)
	{
		this.sessionKey = sessionKey;
		registerServerService("formService", createFormService());
		registerServerService("consoleLogger", createConsoleLoggerService());
	}

	@Override
	public void init(Map<String, List<String>> requestParams) throws Exception
	{
	}

	@Override
	public Collection< ? extends IWindow> getWindows()
	{
		return windows.stream() //
			.map(ObjectReference::getObject) //
			.filter(IWindow::hasEndpoint) //
			.collect(toList());
	}

	public long getLastAccessed()
	{
		return windows.stream() //
			.mapToLong(ObjectReference::getLastAccessed) //
			.max().orElse(Long.MIN_VALUE);
	}

	/**
	 * returns the last ping time of all the windows that are still connected.
	 */
	public long getLastPingTime()
	{
		return windows.stream() //
			.map(ObjectReference::getObject) //
			.mapToLong(IWindow::getLastPingTime) //
			.max().orElse(0);
	}

	@Override
	public IWindow getOrCreateWindow(int windowNr, String windowName)
	{
		if (windowNr != -1)
		{
			for (ObjectReference<IWindow> ref : windows)
			{
				IWindow window = ref.getObject();
				if (windowNr == window.getNr())
				{
					if ((windowName == null && window.getName() == null) || (windowName != null && windowName.equals(window.getName())))
					{
						// window matches on name and nr
						return window;
					}
					// else:
					// window with this nr exists, but windowname is different, this can happen when a new tab is opened
					// and sessionstorage (containing windownr) is copied to the new tab.
				}
			}
		}

		// not found, create a new one
		IWindow window = createWindow(++windowCounter, windowName);
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

	protected IWindow createWindow(int windowNr, String windowName)
	{
		return new BaseWindow(this, windowNr, windowName);
	}

	public void updateLastAccessed(IWindow window)
	{
		windows.stream() //
			.filter(ref -> window == ref.getObject()) //
			.forEach(ObjectReference::updateLastAccessed);
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
		for (ObjectReference<IWindow> ref : windows)
		{
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

	protected IServerService createConsoleLoggerService()
	{
		return new ConsoleLoggerServiceHandler(this);
	}

	public boolean isValid()
	{
		return true;
	}

	public final IEventDispatcher getEventDispatcher()
	{
		return getEventDispatcher(true);
	}

	public final IEventDispatcher getEventDispatcher(boolean create)
	{
		if (executor == null && create)
		{
			synchronized (this)
			{
				if (executor == null)
				{
					executor = createEventDispatcher();
					if (executor != null)
					{
						Thread thread = new Thread(executor, getDispatcherThreadName());
						thread.setDaemon(true);
						thread.start();
						if (SHUTDOWNLOGGER.isDebugEnabled()) SHUTDOWNLOGGER.debug("Executor created for client: " + getSessionKey()); //$NON-NLS-1$
					}
				}
			}
		}
		return executor;
	}

	protected String getDispatcherThreadName()
	{
		return "Executor,uuid:" + sessionKey; //$NON-NLS-1$
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
	public void addDisposehandler(Disposehandler handler)
	{
		disposeHandlersSubject.addDisposehandler(handler);
	}

	@Override
	public void removeDisposehandler(Disposehandler handler)
	{
		disposeHandlersSubject.addDisposehandler(handler);
	}


	@Override
	public final void dispose()
	{
		if (SHUTDOWNLOGGER.isDebugEnabled()) SHUTDOWNLOGGER.debug("Disposing websocket session for client: " + getSessionKey()); //$NON-NLS-1$

		onDispose();

		disposeHandlersSubject.callHandlers();
		disposeHandlersSubject.clear();

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
					if (SHUTDOWNLOGGER.isDebugEnabled()) SHUTDOWNLOGGER.debug("Executor destroyed in dispose for client: " + getSessionKey()); //$NON-NLS-1$
					executor.destroy();
					executor = null;
				}
			}
		}
		else
		{
			if (SHUTDOWNLOGGER.isDebugEnabled()) SHUTDOWNLOGGER.debug("Executor was already destroyed in dispose for client: " + getSessionKey()); //$NON-NLS-1$
		}

		servicesByName.clear();
		servicesByScriptingName.clear();
	}

	protected void onDispose()
	{
	}

	/**
	 * @return the sessionKey
	 */
	public WebsocketSessionKey getSessionKey()
	{
		return sessionKey;
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

	protected void forAllWindows(Consumer<IWindow> action)
	{
		// getWindows() will return only the windows that have an attached end-point
		getWindows().forEach(action);
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
	public TypesRegistryService getTypesRegistryService()
	{
		return new TypesRegistryService(getClientService(TypesRegistryService.TYPES_REGISTRY_SERVICE));
	}

	@Override
	public Collection<IClientService> getServices()
	{
		return Collections.unmodifiableCollection(servicesByName.values());
	}

	protected IClientService createClientService(String name)
	{
		return new ClientService(name, WebServiceSpecProvider.getSpecProviderState().getWebObjectSpecification(name));
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

	@Override
	public boolean shouldTest()
	{
		return true;
	}

	/**
	 * Default this is not enabled, sub classes can override it to get all events (start,stop, message send and receive)
	 */
	@Override
	public IMessageLogger getMessageLogger(IWindow window)
	{
		return null;
	}

	@Override
	public void setHttpSession(HttpSession httpSession)
	{
		this.httpSession = httpSession;
	}

	@Override
	public HttpSession getHttpSession()
	{
		return httpSession;
	}
}
