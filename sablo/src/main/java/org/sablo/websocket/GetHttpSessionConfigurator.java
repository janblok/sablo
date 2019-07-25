package org.sablo.websocket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store the http session for a very short time, until picked up from openSession.
 */
public class GetHttpSessionConfigurator extends Configurator
{
	private static Logger log; // log is initialized lazily, creating a logger before log4j is initialized gives errors.

	public static final int NO_EXPIRE_TIMEOUT = -3;
	/**
	 * Connect nr parameter, used to link http call with http session to websocket session.
	 */
	static final String CONNECT_NR = "connectNr";
	private static final Map<String, HttpSession> SESSIONMAP = new ConcurrentHashMap<>();

	private static Logger getLogger()
	{
		if (log == null)
		{
			log = LoggerFactory.getLogger(GetHttpSessionConfigurator.class.getCanonicalName());
		}
		return log;
	}

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
			if (getLogger().isDebugEnabled()) getLogger().debug("HttpSession stored " + httpSession + " for connectNr: " + connectNr.get(0));
			SESSIONMAP.put(connectNr.get(0), httpSession);
		}
		else if (getLogger().isDebugEnabled()) getLogger().debug("HttpSession not found for " + request.getParameterMap().get(CONNECT_NR));
	}

	public static HttpSession getHttpSession(Session session)
	{
		List<String> connectNr = session.getRequestParameterMap().get(CONNECT_NR);
		if (connectNr == null || connectNr.size() != 1)
		{
			throw new IllegalArgumentException("connectNr session parameter missing");
		}
		HttpSession httpSession = SESSIONMAP.remove(connectNr.get(0));
		if (getLogger().isDebugEnabled()) getLogger().debug("Get HttpSession " + httpSession + " for connectNr: " + connectNr.get(0));
		if (httpSession != null)
		{
			httpSession.setMaxInactiveInterval(NO_EXPIRE_TIMEOUT); // make sure it never expires, set to minus 3 to see that it is more a value that we have set.
		}

		return httpSession;
	}
}