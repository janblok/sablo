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

import org.sablo.websocket.IWebsocketSession;


/**
 * A {@link IWebsocketSession} returns a instance of this that should start up a thread
 * that will serve then as the dispatching thread for all the events coming from the browser.
 *
 * @author jcompagner
 *
 */
public interface IEventDispatcher extends Runnable
{

	public final static int EVENT_LEVEL_DEFAULT = 0;

	boolean isEventDispatchThread();

	/**
	 * Adds an event to be handled by the event dispatch thread.
	 * The event level is considered to be {@link #EVENT_LEVEL_DEFAULT} (0).
	 *
	 * @param event the event to be handled on the event dispatch thread.
	 */
	void addEvent(Runnable event);

	/**
	 * Adds an event to be handled by the event dispatch thread.
	 * The eventLevel is only relevant when using {@link #suspend(Object, int)}.
	 *
	 * @param event the event to be handled on the event dispatch thread.
	 * @param eventLevel see description of minEventLevelToDispatch in {@link #suspend(Object, int)}.
	 */
	void addEvent(Runnable event, int eventLevel);

	/**
	 * Works in tandem with {@link #resume(Object)}.
	 * When suspend is called, the current event will stop executing and other events will continue being dispatched until {@link #resume(Object)} is called
	 * using the same "suspendID" parameter.
	 *
	 * @param suspendID The Object that is the suspend operation identifier.
	 * @param minEventLevelToDispatch while current event is suspended, the minimum event level to dispatch will be "minEventLevelToDispatch". So
	 * events added with "eventLevel" < "minEventLevelToDispatch" will not get dispatched. Events added with "eventLevel" >= "minEventLevelToDispatch" will
	 * continue being dispatched.
	 */
	void suspend(Object suspendID, int minEventLevelToDispatch);

	/**
	 * Same as {@link #suspend(Object, int)} with "minEventLevelToDispatch" having a value of {@link #EVENT_LEVEL_DEFAULT}. So all other events
	 * will continue to dispatch.
	 *
	 * @param suspendID The Object that is the suspend operation identifier.
	 */
	void suspend(Object suspendID);

	/**
	 * See {@link #suspend(Object)}.
	 *
	 * @param suspendID The Object that was used as a suspend operation identifier in a previous call to one of the "suspend" methods.
	 */
	void resume(Object suspendID);

	/**
	 * destroys this event dispatcher thread.
	 */
	public void destroy();

}
