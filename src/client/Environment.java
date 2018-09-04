/**
 * A singleton Environment class to grab settings provided by user
 */
package client;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Random;

public class Environment 
{
	private static Environment instance = null;
	private final static byte[] CLIENT_ID = "CSE".getBytes(Charset.forName("UTF-8"));
	private final static byte[] VERSION = "310".getBytes(Charset.forName("UTF-8"));
	private static ByteBuffer peerId;
	private String rootDownloadDirectory = "BitTorrent Downloads";
	private int MAX_CONNECTIONS = 20;
	private int MAX_TORRENTS = 5;
	
	private Environment()
	{
		buildPeerId();		
	}
	
	public static Environment getInstance()
	{
		if (instance == null)
			instance = new Environment();
		return instance;
	}
		
	private static void buildPeerId()
	{		
		byte[] peerIdBytes = new byte[20];
		peerIdBytes[0] = '-';
		System.arraycopy(CLIENT_ID, 0, peerIdBytes, 1, CLIENT_ID.length);
		System.arraycopy(VERSION, 0, peerIdBytes, CLIENT_ID.length + 1, VERSION.length);
		peerIdBytes[CLIENT_ID.length + VERSION.length + 1] = '-';
		Random rand = new Random();
		byte[] randBytes = new byte[12];
		rand.nextBytes(randBytes);
		System.arraycopy(randBytes, 0, peerIdBytes, CLIENT_ID.length + VERSION.length + 2, randBytes.length);
		
		Environment.peerId = ByteBuffer.wrap(peerIdBytes);
	}
	
	public ByteBuffer getPeerId()
	{
		return peerId;
	}
	
	public void setPeerId(ByteBuffer peerId)
	{
		Environment.peerId = peerId;
	}
	
	public String getRootDownloadDirectory() {
		return rootDownloadDirectory;
	}

	public void setRootDownloadDirectory(String rootDownloadDirectory) {
		this.rootDownloadDirectory = rootDownloadDirectory;
	}

	public int getMAX_CONNECTIONS() {
		return MAX_CONNECTIONS;
	}

	public void setMAX_CONNECTIONS(int mAX_CONNECTIONS) {
		MAX_CONNECTIONS = mAX_CONNECTIONS;
	}

	public int getMAX_TORRENTS() {
		return MAX_TORRENTS;
	}

	public void setMAX_TORRENTS(int mAX_TORRENTS) {
		MAX_TORRENTS = mAX_TORRENTS;
	}	
}
