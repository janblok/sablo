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

package org.sablo.eventthread;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.sablo.websocket.IWebsocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runnable of the ScriptThread that executes {@link Event} objects.
 *
 * @author rgansevles
 *
 */
public class EventDispatcher implements Runnable, IEventDispatcher
{
	private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class.getCanonicalName());

	private final ConcurrentMap<Object, Event> suspendedEvents = new ConcurrentHashMap<Object, Event>();

	private final List<Event> events = new ArrayList<Event>();
	private final LinkedList<Event> stack = new LinkedList<Event>();

	private volatile boolean exit = false;

	private volatile Thread scriptThread = null;

	private int currentMinEventLevel = EVENT_LEVEL_DEFAULT;

	private final IWebsocketSession session;

	public EventDispatcher(IWebsocketSession session)
	{
		this.session = session;
	}

	public void run()
	{
		scriptThread = Thread.currentThread();
		while (!exit)
		{
			dispatch(EVENT_LEVEL_DEFAULT);
		}
	}

	private void dispatch(int minEventLevelToDispatch)
	{
		currentMinEventLevel = minEventLevelToDispatch;

		int i;
		try
		{
			Event event = null;
			synchronized (events)
			{
				while (event == null)
				{
					i = 0;
					while (event == null && i < events.size())
					{
						event = events.get(i);
						if (event.getEventLevel() < minEventLevelToDispatch) event = null;
						else events.remove(i);

						i++;
					}
					if (event == null)
					{
						events.wait();
					}
				}
			}
			stack.add(event);
			event.execute();
			if (stack.getLast() != event)
			{
				throw new Exception("State not expected");
			}
			stack.remove(event);
			synchronized (events)
			{
				events.notifyAll();
			}
		}
		catch (Throwable t)
		{
			log.error("Exception in dispatch()", t);
		}
	}

	@Override
	public boolean isEventDispatchThread()
	{
		return scriptThread == Thread.currentThread();
	}

	@Override
	public void addEvent(Runnable event)
	{
		addEvent(event, IEventDispatcher.EVENT_LEVEL_DEFAULT);
	}

	@Override
	public void addEvent(Runnable event, int eventLevel)
	{

		if (isEventDispatchThread() && currentMinEventLevel <= eventLevel)
		{
			// we can execute it right away
			createEvent(event, eventLevel).execute();
		}
		else
		{
			synchronized (events)
			{
				events.add(createEvent(event, eventLevel));
				events.notifyAll();
				// non-blocking
//				while (!(event.isExecuted() || event.isSuspended() || event.isExecutingInBackground()))
//				{
//					try
//					{
//						events.wait();
//					}
//					catch (InterruptedException e)
//					{
//						Debug.error(e);
//					}
//				}
			}
		}
	}

	protected Event createEvent(Runnable event, int eventLevel)
	{
		return new Event(session, event, eventLevel);
	}

	public void suspend(Object suspendID)
	{
		suspend(suspendID, EVENT_LEVEL_DEFAULT);
	}

	@Override
	public void suspend(Object suspendID, int minEventLevelToDispatch)
	{
		// TODO should this one be called in the execute event thread, should an check be done??
		if (!isEventDispatchThread())
		{
			log.error("suspend called in another thread then the script thread: " + Thread.currentThread(), new RuntimeException());
			return;
		}
		Event event = stack.getLast();
		if (event != null)
		{
			suspendedEvents.put(suspendID, event);
			event.willSuspend();
			synchronized (events)
			{
				events.notifyAll();

			}

			// if we were already dispatching in a higher currentMinEventLevel, use that one instead of "minEventLevelToDispatch"
			int dispatchEventLevel = Math.max(minEventLevelToDispatch, currentMinEventLevel);

			int oldMinEventLevel = currentMinEventLevel;
			try
			{
				while (suspendedEvents.containsKey(suspendID) && !exit)
				{
					dispatch(dispatchEventLevel);
				}
			}
			finally
			{
				currentMinEventLevel = oldMinEventLevel;
			}
			event.willResume();
		}
	}

	public void resume(Object eventKey)
	{
		suspendedEvents.remove(eventKey);
	}

	private void addEmptyEvent()
	{
		synchronized (events)
		{
			// add a nop event so that the dispatcher is triggered.
			events.add(new Event(session, null, EVENT_LEVEL_DEFAULT));
			events.notifyAll();
		}
	}

	public void destroy()
	{
		exit = true;
		addEmptyEvent();
	}

}