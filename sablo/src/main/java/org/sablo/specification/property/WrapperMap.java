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

import java.util.Map;

/**
 * This map is able to do the wrap/unwrap operations that sablo base objects usually do internally.
 *
 * @author acostescu
 */
//TODO these ET and WT are improper - as for object type they can represent multiple types (a different set for each child key), but they help to avoid some bugs at compile-time
public class WrapperMap<ExternalT, BaseT> extends ConvertedMap<ExternalT, BaseT>
{

	protected Map<String, IWrapperType<ExternalT, BaseT>> types;
	protected IDataConverterContext dataConverterContext;

	public WrapperMap(Map<String, ExternalT> external, Map<String, IWrapperType<ExternalT, BaseT>> types, IDataConverterContext dataConverterContext,
		boolean dummyFlag) // this last arg is just to disambiguate the between constructors */
	{
		super();
		this.types = types;
		this.dataConverterContext = dataConverterContext;
		initFromExternal(external);
	}

	public WrapperMap(Map<String, BaseT> base, Map<String, IWrapperType<ExternalT, BaseT>> types, IDataConverterContext dataConverterContext)
	{
		super(base);
		this.types = types;
		this.dataConverterContext = dataConverterContext;
	}

	@Override
	protected ExternalT convertFromBase(String key, BaseT value)
	{
		IWrapperType<ExternalT, BaseT> wt = types.get(key);
		return wt != null ? wt.unwrap(value) : (ExternalT)value;
	}

	@Override
	protected BaseT convertToBase(String key, ExternalT value)
	{
		IWrapperType<ExternalT, BaseT> wt = types.get(key);
		return wt != null ? wt.wrap(value, key == null ? null : baseMap.get(key), dataConverterContext) : null;
	}

}
