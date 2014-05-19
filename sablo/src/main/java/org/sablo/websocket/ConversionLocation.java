package org.sablo.websocket;

/**
 * Enum values that can be used to specify a conversion needs to take place based on a value FROM a source type or TO a destination type. 
 */
public enum ConversionLocation
{

	/**
	 * A conversion needs to happen FROM JSON design-time value or TO JSON design-time value
	 */
	DESIGN,

	/**
	 * A conversion needs to happen FROM update JSON browser sent value or TO an update JSON that will be sent to browser.
	 */
	BROWSER_UPDATE,

	/**
	 * A conversion needs to happen (FROM JSON browser sent value or) TO a JSON that will be sent to browser.
	 */
	BROWSER,

	/**
	 * A conversion needs to happen FROM server implementation specific type or TO server implementation specific type.
	 */
	SERVER
}