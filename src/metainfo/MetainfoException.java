/**
 * This is a general Exception raised by methods in TorrentFileReader
 */

package metainfo;

import java.io.IOException;

public class MetainfoException extends IOException
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public MetainfoException(String message)
	{
		super("MetainfoException: " + message);
	}
}
