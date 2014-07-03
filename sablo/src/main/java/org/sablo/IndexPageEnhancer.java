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
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import javax.security.auth.login.Configuration;

import junit.runner.Version;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;

/**
 * Take an index page, enhance it with required libs/csses and replace variables
 * @author jblok
 */
@SuppressWarnings("nls")
public class IndexPageEnhancer
{
	private static String VAR_START = "##";
	private static String VAR_END = "##";
	
	private IndexPageEnhancer()
	{
	}

	/**
	 * Enhance the provided index.html
	 * @param resource url to index.html
	 * @param contextPath the path to express in base tag
	 * @param cssContributions possible css contributions
	 * @param jsContributions possible js contributions
	 * @param variableSubstitution replace variables
	 * @param writer the writer to write to
	 * @throws IOException
	 */
	public static void enhance(URL resource, String contextPath, Collection<String> cssContributions, Collection<String> jsContributions, Map<String,String> variableSubstitution, Writer writer) throws IOException
	{
		String index_file = IOUtils.toString(resource);
		String lowercase_index_file = index_file.toLowerCase();
		int headstart = lowercase_index_file.indexOf("<head>");
		int headend = lowercase_index_file.indexOf("</head>");

		//use real html parser here instead?
		if (variableSubstitution != null)
		{
			for (String variableName : variableSubstitution.keySet())
			{
				String variableReplace = VAR_START + variableName + VAR_END;
				index_file = index_file.replaceAll(Matcher.quoteReplacement(variableReplace), variableSubstitution.get(variableName));
			}
		}
		
		StringBuilder sb = new StringBuilder(index_file);
		sb.insert(headend, getAllContributions(cssContributions, jsContributions));
		sb.insert(headstart+6, getBaseTag(contextPath));
		writer.append(sb);
	}

	/**
	 * Get the Base tag to use
	 * @param contextPath the contextPath to be used in base tag
	 * @return the decorated base tag
	 */
	private static String getBaseTag(String contextPath) 
	{
		return String.format("<base href=\"%s/\">\n", contextPath);
	}

	/**
	 * Returns the contributions for webcomponents and services
	 * @return headContributions
	 */
	private static String getAllContributions(Collection<String> cssContributions, Collection<String> jsContributions) 
	{
		StringBuilder retval = new StringBuilder();
		
		WebComponentSpecification[] webComponentDescriptions = WebComponentSpecProvider.getInstance().getWebComponentSpecifications();
		for (WebComponentSpecification spec : webComponentDescriptions)
		{
			retval.append(String.format("<script src=\"%s\"></script>\n",spec.getDefinition()));
			String[] libraries = spec.getLibraries();
			for (String lib : libraries)
			{
				if (lib.toLowerCase().endsWith(".css"))
				{
					retval.append(String.format("<link rel=\"stylesheet\" href=\"%s\"/>\n",lib));
				}
				else if (lib.toLowerCase().endsWith(".js"))
				{
					retval.append(String.format("<script src=\"%s\"></script>\n",lib));
				}
			}
		}

		WebComponentSpecification[] webServiceDescriptions = WebServiceSpecProvider.getInstance().getWebServiceSpecifications();
		for (WebComponentSpecification spec : webServiceDescriptions)
		{
			retval.append(String.format("<script src=\"%s\"></script>\n",spec.getDefinition()));
			String[] libraries = spec.getLibraries();
			for (String lib : libraries)
			{
				if (lib.toLowerCase().endsWith(".css"))
				{
					retval.append(String.format("<link rel=\"stylesheet\" href=\"%s\"/>\n",lib));
				}
				else if (lib.toLowerCase().endsWith(".js"))
				{
					retval.append(String.format("<script src=\"%s\"></script>\n",lib));
				}
			}
		}

		if (cssContributions != null)
		{
			for (String lib : cssContributions)
			{
				retval.append(String.format("<link rel=\"stylesheet\" href=\"%s\"/>\n",lib));
			}
		}

		if (jsContributions != null)
		{
			for (String lib : jsContributions)
			{
				retval.append(String.format("<script src=\"%s\"></script>\n",lib));
			}
		}

		return retval.toString();
	}
}