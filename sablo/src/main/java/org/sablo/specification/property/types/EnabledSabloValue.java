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
import org.sablo.WebComponent;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IWrappingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author emera
 */
public class EnabledSabloValue
{
	private final Logger log = LoggerFactory.getLogger(EnabledSabloValue.class.getCanonicalName());

	private boolean value;
	private final BaseWebObject parent;
	private final BaseWebObject component;

	public EnabledSabloValue(boolean value, IWrappingContext dataConverterContext)
	{
		this.value = value;
		this.component = dataConverterContext.getWebObject();
		this.parent = component instanceof WebComponent ? ((WebComponent)component).getParent() : null;
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
		return value && (parent == null || parent.isEnabled());
	}

	public void setEnabled(boolean newValue)
	{
		if (value != newValue)
		{
			this.value = newValue;
			if (component instanceof Container)
			{
				Container c = (Container)component;
				for (WebComponent comp : c.getComponents())
				{
					for (PropertyDescription prop : comp.getSpecification().getProperties(EnabledPropertyType.INSTANCE))
					{
						comp.flagPropertyAsDirty(prop.getName(), true);
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
