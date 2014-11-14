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
import java.net.URL;
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

	private final String endpointType;

	public WebEntry(String endpointType)
	{
		this.endpointType = endpointType;
	}

	/**
	 * @return the endpointType
	 */
	public String getEndpointType()
	{
		return endpointType;
	}

	/**
	 * Provide all the webcompontent bundle names. 
	 * @return the bundle names
	 */
	public abstract String[] getWebComponentBundleNames();

	/**
	 * Provide all the service bundle names. 
	 * @return the bundle names
	 */
	public abstract String[] getServiceBundleNames();

	@Override
	public void init(final FilterConfig fc) throws ServletException
	{
		//register the session factory at the manager
		WebsocketSessionManager.setWebsocketSessionFactory(getEndpointType(), createSessionFactory());

		WebComponentSpecProvider.init(fc.getServletContext(), getWebComponentBundleNames());

		WebServiceSpecProvider.init(fc.getServletContext(), getServiceBundleNames());
	}

	/**
	 * Make it possible for subclasses to supply contributions
	 * @return the contributions as collection of strings
	 */
	protected Collection<String> getJSContributions()
	{
		return null;
	}

	/**
	 * Make it possible for subclasses to supply contributions
	 * @return the contributions as collection of strings
	 */
	protected Collection<String> getCSSContributions()
	{
		return null;
	}

	/**
	 * Make it possible for subclasses to replace variables
	 * @return the variable name,value as map
	 */
	protected Map<String, String> getVariableSubstitution()
	{
		return null;
	}

	/**
	 * Provide the websocketsessionfactory
	 * @return the factory
	 */
	protected abstract IWebsocketSessionFactory createSessionFactory();

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
	{
		HttpServletRequest request = (HttpServletRequest)servletRequest;

		URL indexPageResource = getIndexPageResource(request);
		if (indexPageResource != null)
		{
			((HttpServletResponse)servletResponse).setContentType("text/html");

			PrintWriter w = servletResponse.getWriter();
			IndexPageEnhancer.enhance(indexPageResource, request.getContextPath(), getCSSContributions(), getJSContributions(), getVariableSubstitution(), w);
			w.flush();
			return;
		}

		filterChain.doFilter(servletRequest, servletResponse);
	}

	protected URL getIndexPageResource(HttpServletRequest request) throws IOException
	{
		if ("/index.html".equals(request.getServletPath()))
		{
			return request.getServletContext().getResource(request.getServletPath());
		}
		return null;
	}

	@Override
	public void destroy()
	{
	}
}