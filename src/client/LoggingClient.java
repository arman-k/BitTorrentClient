package client;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggingClient
{
	private static LoggingClient instance = null;
	public final Logger logger = Logger.getLogger(LoggingClient.class.getName());
	
	private LoggingClient()
	{
		setupLogger();
	}
	
	public static LoggingClient getInstance()
	{
		if (instance == null)
			instance = new LoggingClient();
		return instance;
	}
	
	private void setupLogger()
	{
		try 
		{
			FileHandler fh = new FileHandler("L:/Eclipse Workspace/BitTorrentClient/logs/log.txt");
			fh.setFormatter(new SimpleFormatter());
			logger.addHandler(fh);
			logger.setUseParentHandlers(false);
			logger.setLevel(Level.WARNING);
		} catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
}
