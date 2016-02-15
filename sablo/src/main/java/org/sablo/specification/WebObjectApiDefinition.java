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

	// TODO turn these into a bitmask?
	private boolean blockEventProcessing = true;
	private boolean delayUntilFormLoad = false;
	private boolean globalExclusive = false;
//	private final boolean waitsForUserAction = false;

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
		return "WebObjectApiDefinition[name:" + name + ",returnType:" + returnType + ", parameters:" + parameters + "]";
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

	/**
	 * When true, an API call to client will block normal operation until it gets a response on timeout. True by default.
	 * You want to set it to false for if you want client to continue operating normally (the user should still be able to interact with forms) while API call
	 * is in progress and if the API call should not time-out. For example if an API call shows a modal dialog containing a form and needs to be blocking from a scripting
	 * point of view - it should have this set to false - so that it doesn't time out and it allows used to interact with the form-in-modal.
	 */
	public boolean getBlockEventProcessing()
	{
		return blockEventProcessing;
	}

	public boolean isDelayUntilFormLoad()
	{
		return delayUntilFormLoad;
	}

	public void setDelayUntilFormLoad(boolean delayUntilFormLoad)
	{
		this.delayUntilFormLoad = delayUntilFormLoad;
	}

	/**
	 * I think this is meant so that when multiple delayed calls are called on the same API,
	 * only one of them really gets called. (the requestFocus() type of call...)
	 */
	public boolean isGlobalExclusive()
	{
		return globalExclusive;
	}

	public void setGlobalExclusive(boolean globalExclusive)
	{
		this.globalExclusive = globalExclusive;
	}

//	/**
//	 * False by default. When true it announces that this sync API call will wait for the user to perform an action
//	 * before returning. This information is useful for ignoring the time spent calling this api when profiling/looking
//	 * for performance bottlenecks.
//	 */
//	public boolean waitsForUserAction()
//	{
//		return waitsForUserAction;
//	}
//
	// For now we rely on blockEventProcessing; when that is false we interpret it as a user action; we can separate the two in the future
	// if anyone needs to blockEventProcessing while call is in progress but still wait for an user action; in that case we also have
	// to check for waitsForUserAction in WebSocketEndpoint.suspend where the timeout is given...
	//
	// For now I didn't see a need for something like this - maybe some long running task in client but what could the client compute
	// for a long time intentionally?
//	public void setWaitsForUserAction(boolean waitsFonUserAction)
//	{
//		this.waitsForUserAction = waitsFonUserAction;
//	}

}
