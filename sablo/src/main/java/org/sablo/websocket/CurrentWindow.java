/*
 * Copyright (C) 2015 Servoy BV
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


/**
 * RAGTEST doc
 * @author rgansevles
 *
 */
public class CurrentWindow
{
//	public static final Logger log = LoggerFactory.getLogger(CurrentWindow.class.getCanonicalName());

	private static ThreadLocal<IWindow> currentWindow = new ThreadLocal<>();

	public static IWindow get()
	{
		IWindow window = currentWindow.get();
		if (window == null)
		{
			throw new IllegalStateException("no current window set");
		}

		return window;
	}

	public static boolean exists()
	{
		return currentWindow.get() != null;
	}

	public static IWindow set(IWindow window)
	{
		IWindow old = currentWindow.get();
		if (window == null)
		{
			currentWindow.remove();
		}
		else
		{
			currentWindow.set(window);
		}
		return old;
	}
}
