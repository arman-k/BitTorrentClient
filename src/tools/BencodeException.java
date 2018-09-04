/**
 * This is a general Exception raised by methods in Bencode
 */

package tools;

import java.io.IOException;

public class BencodeException extends IOException
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public BencodeException(String message)
	{
		super("BencodeException: " + message);
	}
}
