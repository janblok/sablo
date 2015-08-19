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
package org.sablo.specification.property;

import org.sablo.BaseWebObject;
import org.sablo.specification.WebComponentSpecification.PushToServerEnum;

/**
 * Context for data converters
 * @author gboros
 */
public class BrowserConverterContext extends WrappingContext implements IBrowserConverterContext
{

	private final PushToServerEnum parentPropertyPushToServerValue;

	public BrowserConverterContext(BaseWebObject webObject, PushToServerEnum rootPropertyPushToServerValue)
	{
		super(webObject);
		this.parentPropertyPushToServerValue = rootPropertyPushToServerValue;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parentPropertyPushToServerValue == null) ? 0 : parentPropertyPushToServerValue.hashCode());
		result = prime * result + ((webObject == null) ? 0 : webObject.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		BrowserConverterContext other = (BrowserConverterContext)obj;
		if (parentPropertyPushToServerValue == null)
		{
			if (other.parentPropertyPushToServerValue != null) return false;
		}
		else if (!parentPropertyPushToServerValue.equals(other.parentPropertyPushToServerValue)) return false;
		if (webObject == null)
		{
			if (other.webObject != null) return false;
		}
		else if (!webObject.equals(other.webObject)) return false;
		return true;
	}

	@Override
	public PushToServerEnum getParentPropertyPushToServerValue()
	{
		return parentPropertyPushToServerValue;
	}

}
