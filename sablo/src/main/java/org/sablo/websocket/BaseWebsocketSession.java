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
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;
import org.sablo.IChangeListener;
import org.sablo.eventthread.EventDispatcher;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.services.FormServiceHandler;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.impl.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Base class for handling a websocket session.
 * @author rgansevles
 */
public abstract class BaseWebsocketSession implements IWebsocketSession, IChangeListener
{
	public static final String SABLO_SERVICE = "$sabloService";

	private static final Logger log = LoggerFactory.getLogger(WebsocketEndpoint.class.getCanonicalName());
	private final Map<String, IServerService> serverServices = new HashMap<>();
	private final Map<String, IClientService> services = new HashMap<>();
	private final List<IWindow> windows = new ArrayList<IWindow>();

	//maps window to time
	private static Map<IWindow, Long> nonActiveWindows = new HashMap<>();
	private static final long WINDOW_TIMEOUT = 1 * 60 * 1000;

	private final String uuid;
	private volatile IEventDispatcher executor;

	private final AtomicInteger handlingEvent = new AtomicInteger(0);

	private boolean proccessChanges;


	public BaseWebsocketSession(String uuid)
	{
		this.uuid = uuid;
		registerServerService("formService", createFormService());
	}

	@Override
	public Collection<IWindow> getWindows()
	{
		return Collections.unmodifiableCollection(windows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sablo.websocket.IWebsocketSession#getOrCreateWindow(java.lang.String, java.lang.String)
	 */
	@Override
	public IWindow getOrCreateWindow(String windowId, String windowName)
	{
		synchronized (windows)
		{
			for (IWindow window : windows)
			{
				if (windowId == null)
				{
					if (window.getUuid() == null &&
						((windowName == null && window.getName() == null) || (windowName != null && windowName.equals(window.getName()))))
					{
						// window was created serverside but had no uuid yet
						window.setUuid(UUID.randomUUID().toString());
						nonActiveWindows.remove(window);
						return window;
					}
				}
				else if (windowId.equals(window.getUuid()))
				{
					nonActiveWindows.remove(window);
					return window;
				}
			}

			// not found, create a new one
			IWindow window = createWindow(windowName);
			window.setSession(this);
			window.setUuid(windowId == null ? UUID.randomUUID().toString() : windowId);
			windows.add(window);
			return window;
		}
	}

	protected IWindow createWindow(String windowName)
	{
		return new BaseWindow(windowName);
	}

	public void addWindow(IWindow window)
	{
		synchronized (windows)
		{
			windows.add(window);
			invalidateWindow(window);
		}
	}

	/**
	 * @param endpointType
	 * @param uuid
	 */
	public void invalidateWindow(IWindow window)
	{
		synchronized (windows)
		{
			// mark current window as non active
			nonActiveWindows.put(window, new Long(System.currentTimeMillis()));
		}
	}

	/**
	 * Check nonactive windows in timeout, return true when no more windows are left
	 * @return
	 */
	public boolean checkForWindowActivity()
	{
		List<IWindow> inactiveWindows = new ArrayList<>();
		synchronized (windows)
		{
			//do global non active cleanup
			long currentTime = System.currentTimeMillis();
			Iterator<Entry<IWindow, Long>> iterator = nonActiveWindows.entrySet().iterator();
			while (iterator.hasNext())
			{
				Entry<IWindow, Long> entry = iterator.next();
				if (currentTime - entry.getValue().longValue() > WINDOW_TIMEOUT)
				{
					IWindow window = entry.getKey();
					windows.remove(window);
					iterator.remove();
					inactiveWindows.add(window);
				}
			}

			for (IWindow window : inactiveWindows)
			{
				try
				{
					window.destroy();
				}
				catch (Exception e)
				{
					log.warn("Error destroying window " + window, e);
				}
			}

			return windows.size() == 0;
		}
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

	public void onOpen(String... arguments)
	{
	}

	@Override
	public void closeSession()
	{
		synchronized (windows)
		{
			for (IWindow window : windows)
			{
				window.closeSession();
			}
			windows.clear();
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

	@Override
	public IClientService getService(String name)
	{
		IClientService clientService = services.get(name);
		if (clientService == null)
		{
			clientService = createClientService(name);
			services.put(name, clientService);
		}
		return clientService;
	}

	@Override
	public Collection<IClientService> getServices()
	{
		return Collections.unmodifiableCollection(services.values());
	}

	/**
	 * @param name
	 * @return
	 */
	protected IClientService createClientService(String name)
	{
		return new ClientService(name, WebServiceSpecProvider.getInstance().getWebServiceSpecification(name));
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
				CurrentWindow.get().sendChanges();
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
	}
}
