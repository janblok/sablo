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

package org.sablo;

/**
 * RAGTEST doc
 * @author rgansevles
 *
 */
public class IllegalComponentAccessException extends RuntimeException
{

	private final String accessType;
	private final String componentName;
	private final String eventType;

	public IllegalComponentAccessException(String accessType, String componentName, String eventType)
	{
		this.accessType = accessType;
		this.componentName = componentName;
		this.eventType = eventType;
	}

	/**
	 * @return the accessType
	 */
	public String getAccessType()
	{
		return accessType;
	}

	/**
	 * @return the componentName
	 */
	public String getComponentName()
	{
		return componentName;
	}

	/**
	 * @return the eventType
	 */
	public String getEventType()
	{
		return eventType;
	}

	@Override
	public String getMessage()
	{
		return new StringBuilder("Access to component ").append(getComponentName()).append(" eventType ").append(getEventType()).append(" denied (").append(
			getAccessType()).append(")").toString();
	}
}
