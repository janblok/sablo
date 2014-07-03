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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.IWebsocketSessionFactory;
import org.sablo.websocket.WebsocketSessionManager;

/**
 * Configuration class to define entry points and factories.
 * Subclasses should carry the @WebFilter annotation
 * @author jblok
 */
public abstract class WebEntry implements Filter
{
//	/**
//	 * Provide all the single point interfaces / applications, normally one. 
//	 */
//	public abstract Class<WebApplication>[] getWebApplications();

	/**
	 * Provide all the webcompontent bundle names. 
	 * @return the bundle names
	 */
	public abstract String[] getWebComponentBundleNames();
	
	@Override
	public void init(final FilterConfig fc) throws ServletException
	{
		//register the session factory at the manager
		WebsocketSessionManager.setWebsocketSessionFactory(createSessionFactory());

		WebComponentSpecProvider provider = WebComponentSpecProvider.init(fc.getServletContext(), getWebComponentBundleNames());

		WebServiceSpecProvider.init(fc.getServletContext());
	}

	/**
	 * Make it possible for subclasses to supply contributions
	 * @return the contributions as collection of strings
	 */
	protected Collection<String> getJSContributions() {
		return null;
	}

	/**
	 * Make it possible for subclasses to supply contributions
	 * @return the contributions as collection of strings
	 */
	protected Collection<String> getCSSContributions() {
		return null;
	}

	/**
	 * Make it possible for subclasses to replace variables
	 * @return the variable name,value as map
	 */
	protected Map<String,String> getVariableSubstitution(){
		return null;
	}
	
	/**
	 * Provide the websocketsessionfactory
	 * @return the factory
	 */
	protected abstract IWebsocketSessionFactory createSessionFactory();
//	{
//		return new IWebsocketSessionFactory(){
//			@Override
//			public IWebsocketSession createSession(String endpointType, String uuid) throws Exception 
//			{
//				return new BaseWebsocketSession(uuid)
//				{
//
//					@Override
//					public boolean isValid() {
//						return true;
//					}
//
//					@Override
//					public void handleMessage(JSONObject obj) 
//					{
//						// TODO Auto-generated method stub
//					}
//				};
//			}
//		};
//	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
	{
		try
		{
			HttpServletRequest request = (HttpServletRequest)servletRequest;
			String uri = request.getRequestURI();
			if (uri != null && uri.endsWith("index.html"))
			{
				((HttpServletResponse)servletResponse).setContentType("text/html");
				
				PrintWriter w = servletResponse.getWriter();
				IndexPageEnhancer.enhance(getClass().getResource("index.html"),request.getContextPath(), getCSSContributions(), getJSContributions() , getVariableSubstitution(), w);
				w.flush();
				
				return;
			}
			
			filterChain.doFilter(servletRequest, servletResponse);
		}
		catch (RuntimeException | Error e)
		{
			throw e;
		}
	}
	
	@Override
	public void destroy()
	{
	}
}