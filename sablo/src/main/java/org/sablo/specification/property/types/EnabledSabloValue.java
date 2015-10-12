/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
*/

package org.sablo.specification.property.types;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.Container;
import org.sablo.IChangeListener;
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.ISmartPropertyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author emera
 */
public class EnabledSabloValue implements ISmartPropertyValue
{
	private final Logger log = LoggerFactory.getLogger(EnabledSabloValue.class.getCanonicalName());

	private IChangeListener monitor;
	protected boolean value;
	private WebComponent parent;
	private BaseWebObject component;

	public EnabledSabloValue(boolean value)
	{
		this.value = value;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sablo.specification.property.ISmartPropertyValue#attachToBaseObject(org.sablo.IChangeListener, org.sablo.BaseWebObject)
	 */
	@Override
	public void attachToBaseObject(IChangeListener changeMonitor, BaseWebObject webObject)
	{
		this.component = webObject;
		if (component instanceof WebComponent)
		{
			this.parent = ((WebComponent)component).getParent();
		}
		this.monitor = changeMonitor;

	}


	/*
	 * (non-Javadoc)
	 *
	 * @see org.sablo.specification.property.ISmartPropertyValue#detach()
	 */
	@Override
	public void detach()
	{
	}

	public JSONWriter toJSON(JSONWriter writer)
	{
		try
		{
			writer.value(getValue());
		}
		catch (JSONException e)
		{
			log.error(e.getMessage());
		}
		return writer;
	}

	public boolean getValue()
	{
		boolean val = true;
		if (parent != null)
		{
			val = parent.isEnabled();
		}
		return value && val;
	}

	public void setEnabled(boolean newValue)
	{
		if (value != newValue)
		{
			this.value = newValue;
			monitor.valueChanged();
			if (component instanceof Container)
			{
				Container c = (Container)component;
				if (c.getComponents().size() > 0)
				{
					for (WebComponent comp : c.getComponents())
					{
						for (PropertyDescription prop : comp.getSpecification().getProperties(EnabledPropertyType.INSTANCE))
						{
							EnabledSabloValue child = (EnabledSabloValue)comp.getProperty(prop.getName());
							child.monitor.valueChanged();
						}
					}
				}
			}
		}
	}

	public boolean getComponentValue()
	{
		return value;
	}
}
