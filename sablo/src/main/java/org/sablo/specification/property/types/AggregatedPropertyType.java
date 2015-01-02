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

package org.sablo.specification.property.types;

import org.sablo.specification.PropertyDescription;

/**
 * Property type that is only used to aggregate other property types through a {@link PropertyDescription}'s child properties - at runtime.
 *
 * @author acostescu
 */
public class AggregatedPropertyType extends DefaultPropertyType<Object>
{

	public static final AggregatedPropertyType INSTANCE = new AggregatedPropertyType();

	public static PropertyDescription newAggregatedProperty()
	{
		return new PropertyDescription("", AggregatedPropertyType.INSTANCE)
		{
			@Override
			public String toString()
			{
				return super.toString(true);
			}
		};
	}

	private AggregatedPropertyType()
	{
	}

	@Override
	public String getName()
	{
		return "AggregatedProperty"; //$NON-NLS-1$
	}
}
