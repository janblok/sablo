package org.sablo.websocket;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import org.sablo.filter.SecurityFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store the http session for a very short time, until picked up from openSession.
 *
 * Also validate the origin against an originCheck setting.
 * <ul>Possible values:
 * <li>&lt;None&gt; no checking
 * <li>&lt;Host&gt; check against Host header (default)
 * <li>whitelist: comma-separated list of host names to allow
 * </ul>
 */
public class GetHttpSessionConfigurator extends Configurator
{
	private static final Logger log = LoggerFactory.getLogger(GetHttpSessionConfigurator.class.getCanonicalName());

	public static final int NO_EXPIRE_TIMEOUT = -3;

	public static final String DISABLE_ORIGIN_CHECK = "<None>";
	public static final String USE_HOST_HEADER = "<Host>";

	private static String originCheck;

	/**
	 * Connect nr parameter, used to link http call with http session to websocket session.
	 */
	private static final String CONNECT_NR = "connectNr";
	private static final Map<String, HttpSession> SESSIONMAP = new ConcurrentHashMap<>();

	@Override
	public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
	{
		HttpSession httpSession = (HttpSession)request.getHttpSession();
		if (httpSession != null)
		{
			List<String> connectNr = request.getParameterMap().get(CONNECT_NR);
			if (connectNr == null || connectNr.size() != 1)
			{
				throw new IllegalArgumentException("connectNr request parameter missing");
			}

			SESSIONMAP.put(connectNr.get(0), httpSession);
		}
	}

	public static HttpSession getHttpSession(Session session)
	{
		List<String> connectNr = session.getRequestParameterMap().get(CONNECT_NR);
		if (connectNr == null || connectNr.size() != 1)
		{
			throw new IllegalArgumentException("connectNr session parameter missing");
		}
		HttpSession httpSession = SESSIONMAP.remove(connectNr.get(0));
		if (httpSession != null)
		{
			httpSession.setMaxInactiveInterval(NO_EXPIRE_TIMEOUT); // make sure it never expires, set to minus 3 to see that it is more a value that we have set.
		}

		return httpSession;
	}


	@Override
	public boolean checkOrigin(String originHeaderValue)
	{
		String hostToCheckAgainst = getHostsToCheckAgainst();
		if (hostToCheckAgainst == null)
		{
			// checking disabled
			log.trace("checkOrigin: checking disabled");
			return true;
		}

		String originHost;
		try
		{
			originHost = getHostFromOriginHeader(originHeaderValue);
		}
		catch (ParseException e)
		{
			log.warn("checkOrigin: Cannot parse origin header '" + originHeaderValue + "'");
			return false;
		}

		boolean originOk = Arrays.stream(hostToCheckAgainst.split(",")) //
			.map(String::trim) //
			.map(acceptedHost -> USE_HOST_HEADER.equalsIgnoreCase(acceptedHost) ? getHostFromCurrentHostHeader() : acceptedHost) //
			.anyMatch(originHost::equalsIgnoreCase);

		if (!originOk)
		{
			log.warn("checkOrigin: originHost '" + originHost + "' does not match hosts '" + hostToCheckAgainst + "'");
		}
		else if (log.isTraceEnabled())
		{
			log.trace("checkOrigin: originHost '" + originHost + "' matches hosts '" + hostToCheckAgainst + "'");
		}
		return originOk;
	}

	private static String getHostsToCheckAgainst()
	{
		if (DISABLE_ORIGIN_CHECK.equalsIgnoreCase(originCheck))
		{
			return null;
		}

		if (originCheck == null)
		{
			return USE_HOST_HEADER; //default;
		}

		return originCheck;
	}

	/*
	 * Parse the host from the origin header.
	 *
	 * Origin: null
	 *
	 * Origin: <scheme> "://" <hostname> [ ":" <port> ]
	 */
	private static String getHostFromOriginHeader(String originHeader) throws ParseException
	{
		if (originHeader == null || originHeader.equals("null"))
		{
			throw new IllegalArgumentException("No or empty origin header");
		}

		String[] split = originHeader.split(":");
		if (split.length < 2 || split.length > 3 || !split[1].startsWith("//"))
		{
			throw new ParseException(originHeader, 0);
		}
		return split[1].substring(2);
	}

	/*
	 * Parse the host from the Host header of the current request.
	 *
	 * Host = uri-host [ ":" port ]
	 */
	private static String getHostFromCurrentHostHeader()
	{
		String hostHeader = SecurityFilter.getCurrentHostHeader();
		if (hostHeader == null || hostHeader.trim().length() == 0)
		{
			throw new IllegalArgumentException(
				"checkOrigin: No Host header set for this request, is the SecurityFilter correctly set-up in the web.xml/fragment?");
		}

		//  Host = uri-host [ ":" port ]
		return hostHeader.split(":")[0];
	}

	public static void setOriginCheck(String originCheck)
	{
		GetHttpSessionConfigurator.originCheck = originCheck;
	}
}