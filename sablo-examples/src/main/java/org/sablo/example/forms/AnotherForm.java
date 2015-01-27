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

package org.sablo.example.forms;

import org.sablo.Container;
import org.sablo.IEventHandler;
import org.sablo.WebComponent;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.websocket.WebsocketEndpoint;

/**
 * Form in sample sablo application.
 * 
 * @author rgansevles
 *
 */
public class AnotherForm extends Container
{

	private static final WebComponentSpecification FORM_SPEC = new WebComponentSpecification("form_spec", "", "", null, null, "", null);

	private final WebComponent theLabel;
	private final WebComponent theTextField;
	private final WebComponent theCounter;
	private final WebComponent theButton;

	public AnotherForm(String name)
	{
		super(name, FORM_SPEC);

		add(theLabel = new WebComponent("mylabel", "thelabel"));
		theLabel.setProperty("text", "initial server value");

		add(theTextField = new WebComponent("mytextfield", "thetextfield"));
		theTextField.setProperty("value", "changeme");
		theTextField.setVisible(false);

		add(theCounter = new WebComponent("mycounter", "thecounter"));
		theCounter.setProperty("n", Integer.valueOf(99));

		add(theButton = new WebComponent("mybutton", "thebutton"));
		theButton.addEventHandler("onClick", new IEventHandler()
		{
			@Override
			public Object executeEvent(Object[] args)
			{
				System.err.println("I was pushed! theTextField = "+theTextField.isVisible());
				theTextField.setVisible(!theTextField.isVisible());
				// copy value from text field to label, will be automatically synchronised to browser
				Object textvalue = theTextField.getProperty("value");
				theLabel.setProperty("text", textvalue);
				// call a function on an element
				theCounter.invokeApi("increment", new Object[] { 2 });
				
				WebsocketEndpoint.get().getWebsocketSession().setCurrentFormUrl("forms/mainForm.html");

				return null;
			}
		});
	}

}
