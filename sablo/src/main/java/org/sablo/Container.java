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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.WebsocketEndpoint;

/**
 * Container object is a component that can contain other components.
 * @author jblok
 */
public abstract class Container extends WebComponent
{
	protected final Map<String, WebComponent> components = new HashMap<>();

	public Container(String name, WebComponentSpecification spec)
	{
		super(name, spec);
	}

	public void add(WebComponent component)
	{
		components.put(component.getName(), component);
		component.parent = this;
	}

	public void remove(WebComponent component)
	{
		components.remove(component.getName());
		component.parent = null;
	}

	public WebComponent getComponent(String name)
	{
		return components.get(name);
	}

	public Collection<WebComponent> getComponents()
	{
		return Collections.unmodifiableCollection(components.values());
	}

	@Override
	public void dispose()
	{
		super.dispose();
		for (WebComponent component : components.values().toArray(new WebComponent[0]))
		{
			component.dispose();
		}
		components.clear();
	}

	public TypedData<Map<String, Map<String, Object>>> getAllComponentsChanges()
	{
		Map<String, Map<String, Object>> props = new HashMap<String, Map<String, Object>>(8);
		PropertyDescription propTypes = AggregatedPropertyType.newAggregatedProperty();

		ArrayList<WebComponent> allComponents = new ArrayList<WebComponent>();
		allComponents.add(this); // add the container itself
		allComponents.addAll(getComponents());

		for (WebComponent wc : allComponents)
		{
			TypedData<Map<String, Object>> changes = wc.getChanges();
			if (changes.content.size() > 0)
			{
				props.put(wc == this ? "" : wc.getName(), changes.content); //$NON-NLS-1$
				if (changes.contentType != null) propTypes.putProperty(wc == this ? "" : wc.getName(), changes.contentType);
			}
		}
		if (!propTypes.hasChildProperties()) propTypes = null;
		return new TypedData<Map<String, Map<String, Object>>>(props, propTypes);
	}

	public TypedData<Map<String, Map<String, Object>>> getAllComponentsProperties()
	{
		WebsocketEndpoint.get().regisiterContainer(this);
		Map<String, Map<String, Object>> props = new HashMap<String, Map<String, Object>>();
		PropertyDescription propTypes = AggregatedPropertyType.newAggregatedProperty();

		ArrayList<WebComponent> allComponents = new ArrayList<WebComponent>();
		allComponents.add(this); // add the form itself
		allComponents.addAll(getComponents());

		for (WebComponent wc : allComponents)
		{
			TypedData<Map<String, Object>> changes = wc.getProperties();
			wc.clearChanges();
			String name = (wc == this ? "" : wc.getName());
			props.put(name, changes.content);
			if (changes.contentType != null) propTypes.putProperty(name, changes.contentType);
		}
		if (!propTypes.hasChildProperties()) propTypes = null;
		return new TypedData<Map<String, Map<String, Object>>>(props, propTypes);
	}
}
