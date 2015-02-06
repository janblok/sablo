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

package org.sablo.example;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.sablo.Container;
import org.sablo.example.forms.AnotherForm;
import org.sablo.example.forms.MainForm;
import org.sablo.websocket.BaseWindow;

/**
 * RAGTEST doc
 * @author rgansevles
 *
 */
public class HelloWorldWindow extends BaseWindow{

	protected final ConcurrentMap<String, Container> createdForms = new ConcurrentHashMap<>();
	
	public HelloWorldWindow(String name) {
		super(name);
	}


	@Override
	public Container getForm(String formName)
	{
		Container form = createdForms.get(formName);
		if (form == null)
		{
			createdForms.put(formName, form = createForm(formName));
		}
		return form;
	}

	public Container createForm(String formName)
	{
		switch (formName)
		{
			case "mainForm" :

				return new MainForm(formName);
				 
			case "anotherForm" :
				
				return new AnotherForm(formName);
		}
		throw new IllegalArgumentException("unkown form: " + formName);
	}
}
