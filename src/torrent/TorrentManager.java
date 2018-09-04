package torrent;

import static tools.Util.byteBufferToString;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.Environment;
import client.LoggingClient;
import gui.TorrentEventListener;
import metainfo.TorrentDataFile;
import metainfo.InfoDictionary;
import metainfo.Metainfo;
import metainfo.MultiFileInfoDict;
import metainfo.SingleFileInfoDict;
import metainfo.TorrentFileReader;
import tools.Util;

public class TorrentManager
{
	private static Short PORT = 6883;	
	List<Torrent> torrents = new LinkedList<Torrent>();
	private List<TorrentEventListener> listeners = new ArrayList<TorrentEventListener>();
	public static final int TYPE_STATUS = 1;
	public static final int TYPE_PROGRESS = 2;
	Logger logger = LoggingClient.getInstance().logger;
		
	/*
	 * Parse a .torrent file and add it to torrents list
	 */
	public Torrent addTorrent(String filename)
	{
		Torrent torrent = null;
		if (torrents.size() > Environment.getInstance().getMAX_TORRENTS())
		{
			System.out.println("You can only add up to " + Environment.getInstance().getMAX_TORRENTS() 
					+ " torrents. Remove a torrent first.");
			return torrent;
		}
		try
		{
			//Construct a TorrentFileReader to parse out the raw metainfo from the .torrent metafile specified by the user
			TorrentFileReader reader = new TorrentFileReader(filename); 
			Metainfo metainfo = reader.readMetainfo();
			torrent = new Torrent(this, filename, metainfo, PORT);
			torrents.add(torrent);
			logger.log(Level.INFO, "Added new torrent: " + filename);
		}
		catch (FileNotFoundException e)
		{
			logger.log(Level.WARNING, "File not found: " + filename, e);
		}
		catch (IOException e)
		{
			logger.log(Level.WARNING, "Couldn't read file: " + filename, e);
		}
		return torrent;
	}
	
	/*
	 * Print all available torrents
	 */
	public void printTorrents()
	{
		int i = 1;
		
		for (Torrent torrent : torrents)
			System.out.println("(" + i++ + ") " + torrent.getName());
	}
	
	/*
	 * Download torrent at given index
	 */
	public void startTorrent(int i)	//TODO do this in a separate thread
	{
		Torrent torrent = torrents.get(i);
		
		try
		{
			torrent.startDownload();
		} catch (IOException e)
		{
			logger.log(Level.WARNING, "Failed to start download!", e);
		}
	}
	
	/*
	 * Stop downloading torrent at given index
	 */
	public void stopTorrent(int i)	//TODO do this in a separate thread
	{
		Torrent torrent = torrents.get(i);
		
		try
		{
			torrent.stopDownload();
		} catch (IOException e)
		{
			logger.log(Level.WARNING, "Failed to stop download!", e);
		}
	}
	
	public void removeTorrent(int i)
	{
		Torrent torrent = torrents.get(i);
		
		try
		{
			torrent.stopDownload();
			torrents.remove(torrent);
		} catch (IOException e)
		{
			logger.log(Level.WARNING, "Failed to remove torrent!", e);
		}
	}
	
	/*
	 * Print available metainfo in this torrent
	 */
	@SuppressWarnings("unchecked")
	public String printMetainfo(int i)
	{
		StringBuilder builder = new StringBuilder();
		Metainfo metainfo = torrents.get(i).getMetainfo();
		builder.append("Announce URL: " + metainfo.getAnnounce() + "\n");
		List<Object> announceList = metainfo.getAnnounceList();
		if (announceList != null)
		{
			builder.append("Announce URL list (multi-tracker torrents are not supported): ");
			for (Object list : announceList)
				for (ByteBuffer elem : (ArrayList<ByteBuffer>) list)
					builder.append(byteBufferToString(elem) + "\n");
		}
		
		if (metainfo.getCreationDate() != null)
			builder.append("Creation date: " + metainfo.getCreationDate() + "\n");
		if (metainfo.getComment() != null)
			builder.append("Comment: " + metainfo.getComment() + "\n");
		if (metainfo.getEncoding() != null)
			builder.append("Encoding: " + metainfo.getEncoding() + "\n");
		if (metainfo.getCreatedBy() != null)
			builder.append("Created by: " + metainfo.getCreatedBy() + "\n");
		
		InfoDictionary info = metainfo.getInfo();
		builder.append("Total hash length: " + info.getPieces().array().length + "\n");
		builder.append("Piece length: " + info.getPieceLength() + " bytes" + "\n");
		builder.append("External peer source allowed: " + Util.booleanObjectToPrimitive(info.getNoExternalPeerSource()) + "\n");
		
		if (info instanceof SingleFileInfoDict)
		{
			builder.append("File name: " + ((SingleFileInfoDict) info).getFile().getPath() + "\n");
			builder.append("File length: " + ((SingleFileInfoDict) info).getFile().getLength() + " bytes" + "\n");
			
			if (((SingleFileInfoDict) info).getFile().getMd5sum() != null)
				builder.append("Md5sum: " + ((SingleFileInfoDict) info).getFile().getMd5sum() + "\n");
		}
		
		else if (info instanceof MultiFileInfoDict)
		{
			builder.append("Root directory name: " + ((MultiFileInfoDict) info).getDirectoryName() + "\n");
			
			for (TorrentDataFile f : ((MultiFileInfoDict) info).getFiles())
			{
				builder.append("File path: " + f.getPath());
				builder.append(" => Length: " + f.getLength() + " bytes");
				builder.append(" => Offset: " + f.getOffset());
				if (f.getMd5sum() != null)
					builder.append("Md5sum: " + f.getMd5sum());
				builder.append("\n");
			}
		}
		
		builder.append("Total size: " + torrents.get(i).getSize() + " bytes\n");
		builder.append("Info hash: " + Util.URLEncode(metainfo.getInfoHash().array()) + "\n");
		
		return builder.toString();
	}
	
	public void registerForStatusEvents(TorrentEventListener listener)
	{
		listeners.add(listener);
	}
	
	public void onNewEvent(Torrent torrent, int type, Object event)
	{
		for (int i = 0; i < torrents.size(); i++)
		{
			if (torrents.get(i) == torrent)
			{
				notifyListeners(i, type, event);
				break;
			}
		}
	}
	
	public void notifyListeners(int torrentIndex, int type, Object event)
	{
		for (TorrentEventListener listener : listeners)
		{
			if (type == TYPE_STATUS)
				listener.onStatusUpdated(torrentIndex, (String) event);
			if (type == TYPE_PROGRESS)
				listener.onProgressMade(torrentIndex, (Double) event);
		}
	}
	
	/*
	 * Close all torrents
	 */
	public void exit()
	{
		logger.log(Level.INFO, "Exiting torrent manager...");
		for (Torrent torrent : torrents)
		{
			try
			{
				torrent.stopDownload();
			} catch (IOException e)
			{
				logger.log(Level.SEVERE, "Couldn't stop download!", e);
			}			
		}
		logger.log(Level.INFO, "Stopped all torrents.");
	}
}
