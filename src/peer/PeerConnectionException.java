package peer;

import java.io.IOException;

public class PeerConnectionException extends IOException
{
	private static final long serialVersionUID = 1L;
	
	public PeerConnectionException(String message)
	{
		super("PeerConnectionException: " + message);
	}
}
