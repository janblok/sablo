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

package org.sablo.specification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

/**
 * Parsed web component / web service api function definition.
 * @author rgansevles
 */
public class WebObjectApiDefinition
{

	private final String name;
	private final List<PropertyDescription> parameters = new ArrayList<>();
	private PropertyDescription returnType;
	private JSONObject customConfigOptions;
	private String documentation;
	private boolean blockEventProcessing = true;
	private boolean delayUntilFormLoad = false;
	private boolean globalExclusive = false;

	public WebObjectApiDefinition(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void addParameter(PropertyDescription parameter)
	{
		parameters.add(parameter);
	}

	public List<PropertyDescription> getParameters()
	{
		return Collections.unmodifiableList(parameters);
	}

	public void setReturnType(PropertyDescription returnType)
	{
		this.returnType = returnType;
	}

	public PropertyDescription getReturnType()
	{
		return returnType;
	}

	void setCustomConfigOptions(JSONObject customConfigOptions)
	{
		this.customConfigOptions = customConfigOptions;
	}

	public JSONObject getCustomConfigOptions()
	{
		return this.customConfigOptions;
	}

	@Override
	public String toString()
	{
		return "WebComponentApiDefinition[name:" + name + ",returnType:" + returnType + ", parameters:" + parameters + "]";
	}

	public void setDocumentation(String documentation)
	{
		this.documentation = documentation;
	}

	public String getDocumentation()
	{
		return documentation;
	}

	public void setBlockEventProcessing(boolean blockEventProcessing)
	{
		this.blockEventProcessing = blockEventProcessing;
	}

	public boolean getBlockEventProcessing()
	{
		return blockEventProcessing;
	}

	/**
	 * @return the delayUntilFormLoad
	 */
	public boolean isDelayUntilFormLoad()
	{
		return delayUntilFormLoad;
	}

	/**
	 * @param delayUntilFormLoad the delayUntilFormLoad to set
	 */
	public void setDelayUntilFormLoad(boolean delayUntilFormLoad)
	{
		this.delayUntilFormLoad = delayUntilFormLoad;
	}

	/**
	 * @return the globalExclusive
	 */
	public boolean isGlobalExclusive()
	{
		return globalExclusive;
	}

	/**
	 * @param globalExclusive the globalExclusive to set
	 */
	public void setGlobalExclusive(boolean globalExclusive)
	{
		this.globalExclusive = globalExclusive;
	}

}
