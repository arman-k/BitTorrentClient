package torrent;

import java.io.IOException;

public class TrackerException extends IOException
{
	private static final long serialVersionUID = 1L;
	
	public TrackerException(String message)
	{
		super("TrackerException: " + message);
	}
}
