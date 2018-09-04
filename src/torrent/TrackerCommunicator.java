package torrent;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.LoggingClient;
import tools.BencodeReader;
import tools.Util;

public class TrackerCommunicator
{
	private Long interval;
	private Long minInterval;
	private String trackerId;
	private Long complete;
	private Long incomplete;
	private Object peers;
	
	private Torrent torrent;
	private Thread updateTask;
	private volatile boolean running = false;
	private boolean behindProxy = false;
	Logger logger = LoggingClient.getInstance().logger;

	public TrackerCommunicator(Torrent torrent)
	{
		this.torrent = torrent;
	}
	
	/*
	 * Load the tracker by announcing 'start' event
	 * 
	 */
	public void onTorrentDownloadStarted() throws IOException
	{
		if (!isRunning())
		{
			setRunning(true);
			announce("started");
			setUpdateTask(new Thread(new UpdateTask()));
			getUpdateTask().start();
			logger.log(Level.INFO, "Tracker started event");
		}		
	}
	
	public void onTorrentDownloadStopped() throws IOException
	{
		if (isRunning())
		{
			setRunning(false);
			announce("stopped");
			logger.log(Level.INFO, "Tracker stopped event");
		}
	}
	
	//TODO these methods need better names
	public void onTorrentDownloadCompleted()
	{
		if (isRunning())
		{
			try
			{
				setRunning(false);	//TODO an important decision - if we want to stop the tracker after we are done downloading
				announce("completed");
				logger.log(Level.INFO, "Tracker completed event");
			} catch (IOException e)
			{
				logger.log(Level.INFO, "Failed to send 'completed' event to tracker");
			}
			logger.log(Level.FINER, "Tracker Communicator shutdown successful!");
		}
	}
	
	/*
	 * Announce to tracker the event
	 * Parse the response
	 */
	public synchronized void announce(String event) throws IOException
	{
		String req = buildRequest(event);
		URL url = new URL(req);
		logger.log(Level.INFO, "Sending GET request to tracker at " + url);
		HttpURLConnection conn = null;
		
		if (isBehindProxy())	//Use if behind proxy
		{
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.1.5", 8080));
			logger.log(Level.FINE, "Setting up proxy for tracker");
			conn = (HttpURLConnection) url.openConnection(proxy);
		}
		else
			conn = (HttpURLConnection) url.openConnection();
		conn.setRequestProperty("Accept", "text/plain");
		
		HashMap<ByteBuffer, Object> response = new BencodeReader(conn.getInputStream()).read();
		String failureReason = Util.byteBufferToString(response.get(Util.stringToByteBuffer("failure reason")));
		if (failureReason != null)
		{
			logger.log(Level.WARNING, "Request to tracker failed: " + failureReason);
			conn.disconnect();
			return;
		}
		
		String warningMessage = Util.byteBufferToString(response.get(Util.stringToByteBuffer("warning message")));
		if (warningMessage != null)
		{
			logger.log(Level.INFO, "Tracker sent a warning message: " + warningMessage);
		}
		
		Long interval = (Long) response.get(Util.stringToByteBuffer("interval"));
		if (interval == null)
		{
			TrackerException e = new TrackerException("No interval specified");
			logger.log(Level.WARNING, "Malformed tracker response", e);
			throw e;
		}
			
		setInterval(interval);
		
		Long minInterval = (Long) response.get(Util.stringToByteBuffer("min interval"));
		if (minInterval != null)
			setMinInterval(minInterval);
		
		String trackerId = Util.byteBufferToString(response.get(Util.stringToByteBuffer("tracker id")));
		if (trackerId != null)
			setTrackerId(trackerId);
		
		Long complete = (Long) response.get(Util.stringToByteBuffer("complete"));
		if (complete != null)
			setComplete(complete);
		
		Long incomplete = (Long) response.get(Util.stringToByteBuffer("incomplete"));
		if (incomplete != null)
			setIncomplete(incomplete);
		
		Object peers = response.get(Util.stringToByteBuffer("peers"));
		if (peers == null)
		{
			TrackerException e = new TrackerException("No peers specified");
			logger.log(Level.WARNING, "Malformed tracker response", e);
			throw e;
		}
		setPeers(peers);
	}
	
	/*
	 * Build a GET request to tracker URL
	 */
	public String buildRequest(String event)
	{
		StringBuilder getReq = new StringBuilder();
		
		getReq.append(getTorrent().getMetainfo().getAnnounce());
		getReq.append("?info_hash=");
		getReq.append(Util.URLEncode(getTorrent().getMetainfo().getInfoHash().array()));
		getReq.append("&peer_id=");
		getReq.append(getTorrent().getPeerId());
		getReq.append("&port=");
		getReq.append(getTorrent().getPort());
		getReq.append("&uploaded=");
		getReq.append(getTorrent().getUploaded());
		getReq.append("&downloaded=");
		getReq.append(getTorrent().getDownloaded());
		getReq.append("&left=");
		getReq.append(getTorrent().getLeft());
		if (getTorrent().isCompact() != null)
		{
			getReq.append("&compact=");
			getReq.append(Util.boolToInt(getTorrent().isCompact()));
		}			
		if (event != null)
		{
			getReq.append("&event=");
			getReq.append(event);
		}
		if (getTorrent().getIP() != null)
		{
			getReq.append("&ip=");
			getReq.append(getTorrent().getIP());
		}
		if (getTorrent().getNumWant() != null)
		{
			getReq.append("&numwant=");
			getReq.append(getTorrent().getNumWant());
		}
		if (getTorrent().getKey() != null)
		{
			getReq.append("&key=");
			getReq.append(getTorrent().getKey());
		}
		if (getTorrent().getTrackerId() != null)
		{
			getReq.append("&trackerId=");
			getReq.append(getTorrent().getTrackerId());
		}
		
		return getReq.toString();
	}
	
	/*
	 * Print response from tracker
	 */
	public String toString()
	{
		Long interval = getInterval();
		Long minInterval = getMinInterval();
		String trackerId = getTrackerId();
		Long complete = getComplete();
		Long incomplete = getIncomplete();
		
		return (new StringBuilder())
				.append(interval != null ? ", Interval: " + interval : "")
				.append(minInterval != null ? ", Min Interval: " + minInterval : "")
				.append(trackerId != null ? ", Tracker Id: " + trackerId : "")
				.append(complete != null ? ", Complete: " + complete : "")
				.append(incomplete != null ? ", Incomplete: " + incomplete : "")
				.append("}").toString();
	}
	
	/*
	 * UpdateTask that sleeps for given interval and announces to the tracker
	 */
	private class UpdateTask implements Runnable
	{
		public void run()
		{
			while (isRunning())
			{
				Long currInterval = getMinInterval();
				if (currInterval == null)
					currInterval = interval;
				try {
					TimeUnit.SECONDS.sleep(currInterval); //TODO uncomment this in production
					//TimeUnit.SECONDS.sleep(30);
					if (!isRunning())
						return;
					announce(null);
					logger.log(Level.INFO, "Tracker update received!");
					getTorrent().onTrackerUpdateReceived();
				} catch (IOException e) 
				{
					logger.log(Level.WARNING, "Couldn't communicate with tracker", e);
				} catch (InterruptedException e)
				{
					logger.log(Level.WARNING, "Tracker UpdateTask interrupted", e);
					try 
					{
						announce("Stopped");	//TODO fix this bullshit logic
						setRunning(false);
						logger.log(Level.FINER, "Stopping interrupted tracker");
					} catch (IOException e1) 
					{
						logger.log(Level.FINER, "Couldn't communicate with interrupted tracker", e1);
					}
				}
			}
		}
	}
	
	public Long getInterval() {
		return interval;
	}

	public void setInterval(Long interval) {
		this.interval = interval;
	}

	public Long getMinInterval() {
		return minInterval;
	}

	public void setMinInterval(Long minInterval) {
		this.minInterval = minInterval;
	}

	public String getTrackerId() {
		return trackerId;
	}

	public void setTrackerId(String trackerId) {
		this.trackerId = trackerId;
	}

	public Long getComplete() {
		return complete;
	}

	public void setComplete(Long complete) {
		this.complete = complete;
	}

	public Long getIncomplete() {
		return incomplete;
	}

	public void setIncomplete(Long incomplete) {
		this.incomplete = incomplete;
	}

	public Object getPeers() {
		return peers;
	}

	public void setPeers(Object peers) {
		this.peers = peers;
	}
		
	public Torrent getTorrent() {
		return torrent;
	}

	public void setTorrent(Torrent torrent) {
		this.torrent = torrent;
	}
	
	public Thread getUpdateTask() {
		return updateTask;
	}

	public void setUpdateTask(Thread updateTask) {
		this.updateTask = updateTask;
	}
	
	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	public boolean isBehindProxy() {
		return behindProxy;
	}

	public void setBehindProxy(boolean behindProxy) {
		this.behindProxy = behindProxy;
	}
}
