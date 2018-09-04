package torrent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.Environment;
import client.LoggingClient;
import metainfo.InfoDictionary;
import metainfo.Metainfo;
import metainfo.MultiFileInfoDict;
import metainfo.Piece;
import metainfo.SingleFileInfoDict;
import metainfo.TorrentDataFile;
import tools.Util;
import peer.PeerManager;

public class Torrent
{
	private ByteBuffer infoHash;
	private String peerId = Util.URLEncode(Environment.getInstance().getPeerId().array());
	private Short port;
	private Long uploaded = 0l;
	private Long downloaded = 0l;
	private Boolean compact = true;
	private Boolean noPeerId = false;
	private String IP;
	private Long numWant;
	private String key;
	private String trackerId;
	
	private String name;
	private Long size;
	private Metainfo metainfo;	
	private Piece[] pieces;
	private BitSet completedPieces;
	private BitSet requestedPieces;
	private volatile boolean downloading = false;
	private boolean completed = false;
	
	private TorrentManager torrentManager;
	private PeerManager peerManager;	
	private TrackerCommunicator tracker;
	private TorrentFileSystem torrentFileSystem;
	private Thread mainLoop;
	public static final int TYPE_STATUS = 1;
	public static final int TYPE_PROGRESS = 2;
	Logger logger = LoggingClient.getInstance().logger;
	
	public Torrent(TorrentManager torrentManager, String name, Metainfo metainfo, Short port)
	{
		this.torrentManager = torrentManager;
		this.name = name;
		this.metainfo = metainfo;
		this.port = port;		
		this.infoHash = metainfo.getInfoHash();
		
		this.tracker = new TrackerCommunicator(this);
		this.peerManager = new PeerManager(this);
		this.torrentFileSystem = new TorrentFileSystem(this);
		
		this.calculateTotalSize();
		this.mainLoop = new Thread(new MainLoop());
		this.mainLoop.setDaemon(true);
	}
	
	/*
	 * Start downloading this torrent
	 * Initialize pieces, torrent filesystem
	 * Load tracker, connect to peers received
	 * Set tracker interval
	 */
	@SuppressWarnings("unchecked")
	public void startDownload() throws IOException
	{
		notifyTorrentManager(TYPE_STATUS, "Starting...");
		setDownloading(true);
		initializePieces();
		setCompletedPieces(new BitSet(getPieces().length));
		getTorrentFileSystem().init();
		getTracker().onTorrentDownloadStarted();
		logger.log(Level.INFO, "Tracker response received : " + getTracker());
		if (getTracker().getPeers() instanceof ByteBuffer)
			getPeerManager().createPeers((ByteBuffer) getTracker().getPeers());
		else if (getTracker().getPeers() instanceof List<?>)
			getPeerManager().createPeers((List<HashMap<ByteBuffer, Object>>) getTracker().getPeers());		
		Long interval = getTracker().getMinInterval();	//TODO this isn't correct, but whatever
		if (interval == null)
			interval = getTracker().getInterval();
		setRequestedPieces(new BitSet(getPieces().length));
		
		logger.log(Level.INFO, "Starting download of " + this + ". Attempting to connect to discovered peers");
		System.out.println("Starting download at: " + new Date(System.currentTimeMillis()));
		getPeerManager().connectToPeers();
		
		mainLoop.start();
		notifyTorrentManager(TYPE_STATUS, "Downloading");
	}
	
	/*
	 * Stop downloading the torrent
	 */
	public void stopDownload() throws IOException	//TODO need to halt a few other things perhaps?
	{
		if (isDownloading())
		{
			notifyTorrentManager(TYPE_STATUS, "Stopping...");
			setDownloading(false);
			logger.log(Level.FINER, "Fired Torrent Download stopped event for " + this);
			getTracker().onTorrentDownloadStopped();
			getPeerManager().onTorrentDownloadStopped();
			getTorrentFileSystem().onTorrentDownloadStopped();
			notifyTorrentManager(TYPE_STATUS, "Stopped");
		}
	}
	
	/*
	 * Initialize all pieces in this torrent
	 */
	public void initializePieces()
	{
		Long pieceLength = getMetainfo().getInfo().getPieceLength();
		pieces = new Piece[getMetainfo().getInfo().getPieces().array().length / 20];
		byte[] pieceHash;
		for (int index = 0; index < pieces.length; index++)
		{
			pieceHash = new byte[20];
			getMetainfo().getInfo().getPieces().get(pieceHash);
			pieces[index] = new Piece(getTorrentFileSystem(), ByteBuffer.wrap(pieceHash), (long) index, pieceLength, pieceLength*index);
		}
		//TODO pieces.length-1 will underflow if there is only 1 piece
		pieces[pieces.length-1].setLength(getSize() - pieceLength*(pieces.length-1));	//Handling the highly likely case that the last piece might not be exactly piece length
	}
	
	/*
	 * Calculate total size of torrent over all files
	 * TODO this should have been done in TorrentFileReader!
	 */
	public void calculateTotalSize()
	{
		InfoDictionary info = getMetainfo().getInfo();
		Long size = 0l;
		if (info instanceof SingleFileInfoDict)
			size += ((SingleFileInfoDict) info).getFile().getLength();
		else if (info instanceof MultiFileInfoDict)
		{
			for (TorrentDataFile f : ((MultiFileInfoDict) info).getFiles())
				size += f.getLength();
		}
		setSize(size);
		logger.log(Level.FINER, "Total size of torrent : " + getSize());
	}
	
	/*
	 * Calculate number of bytes left to download
	 * TODO fix this shit - do this with some sort of event handling when piece is downloaded
	 */
	public Long getLeft()
	{
		return getSize() - getDownloaded();
	}

	/*
	 * Return piece at given index
	 */
	public Piece getPiece(int index)
	{
		if (index < 0 || index > getPieces().length)
		{
			logger.log(Level.WARNING, "Invalid index in getPiece for " + this);
			return null;
		}			
		return getPieces()[index];
	}
	
	/*
	 * Sequential piece selector
	 * Find pieces in available pieces that we don't already have or haven't already requested
	 * Return the first such piece found
	 */
	public Piece selectNextPiece(BitSet availablePieces)	//TODO this method seems out of place here, can we move it somewhere else?
	{
		logger.log(Level.FINER, "Selecting next interesting piece from " + availablePieces);
		BitSet missingButAvailablePieces = availablePieces;
		missingButAvailablePieces.andNot(getCompletedPiecesCopy());
		missingButAvailablePieces.andNot(getRequestedPiecesCopy());
		logger.log(Level.FINEST, "Actual interesting pieces: " + missingButAvailablePieces);
		
		if (missingButAvailablePieces.cardinality() == 0)
			return null;
		return getPiece(missingButAvailablePieces.nextSetBit(0));
	}
	
	/*
	 * Handler for 'tracker update received' event
	 * Print tracker response
	 * Create peers through peer manager and try to connect to them
	 */
	@SuppressWarnings("unchecked")
	public void onTrackerUpdateReceived() //TODO also update interval/mininterval
	{
		logger.log(Level.INFO, "Tracker update received : " + getTracker());
		if (getTracker().getPeers() instanceof ByteBuffer)
			getPeerManager().createPeers((ByteBuffer) getTracker().getPeers());
		else if (getTracker().getPeers() instanceof List<?>)
			getPeerManager().createPeers((List<HashMap<ByteBuffer, Object>>) getTracker().getPeers());
		logger.log(Level.INFO, "Attempting to connect to newly discovered peers for " + this);
		getPeerManager().connectToPeers();
	}
	
	public void onDownloadCompleted()
	{
		if (!isCompleted())
		{
			notifyTorrentManager(TYPE_STATUS, "Finalizing...");
			setDownloading(false);
			setCompleted(true);
			logger.log(Level.FINER, "Fired Torrent Download completed event for " + this);
			getTracker().onTorrentDownloadCompleted();
			getPeerManager().onTorrentDownloadCompleted();
			getTorrentFileSystem().onTorrentDownloadCompleted();
			System.out.println(getName() + " download completed at: " + new Date(System.currentTimeMillis()));
			notifyTorrentManager(TYPE_STATUS, "Completed");
		}
	}
	
	public void onPieceDownloaded(double percentage)
	{
		notifyTorrentManager(TYPE_PROGRESS, percentage);
	}
	
	public BitSet getCompletedPiecesCopy() 
	{
		synchronized (getCompletedPieces()) 
		{
			return (BitSet) getCompletedPieces().clone();
		}
	}
	
	public BitSet getRequestedPiecesCopy() 
	{
		synchronized (getRequestedPieces()) 
		{
			return (BitSet) getRequestedPieces().clone();
		}
	}
	
	public BitSet getCompletedPieces() {
		return completedPieces;
	}

	public void setCompletedPieces(BitSet completedPieces) {
		this.completedPieces = completedPieces;
	}
	
	public BitSet getRequestedPieces() 
	{
		return requestedPieces;
	}

	public void setRequestedPieces(BitSet requestedPieces) {
		this.requestedPieces = requestedPieces;
	}

	public ByteBuffer getInfoHash() {
		return infoHash;
	}

	public void setInfoHash(ByteBuffer infoHash) {
		this.infoHash = infoHash;
	}

	public String getPeerId() {
		return peerId;
	}

	public void setPeerId(String peerId) {
		this.peerId = peerId;
	}

	public Short getPort() {
		return port;
	}

	public void setPort(Short port) {
		this.port = port;
	}

	public Long getUploaded() {
		return uploaded;
	}

	public void setUploaded(Long uploaded) {
		this.uploaded = uploaded;
	}

	public Long getDownloaded() {
		return downloaded;
	}

	public void setDownloaded(Long downloaded) {
		this.downloaded = downloaded;
	}

	public Boolean isCompact() {
		return compact;
	}

	public void setCompact(Boolean compact) {
		this.compact = compact;
	}

	public Boolean isNoPeerId() {
		return noPeerId;
	}

	public void setNoPeerId(Boolean noPeerId) {
		this.noPeerId = noPeerId;
	}

	public String getIP() {
		return IP;
	}

	public void setIP(String iP) {
		IP = iP;
	}

	public Long getNumWant() {
		return numWant;
	}

	public void setNumWant(Long numWant) {
		this.numWant = numWant;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getTrackerId() {
		return trackerId;
	}

	public void setTrackerId(String trackerId) {
		this.trackerId = trackerId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}

	public Metainfo getMetainfo() {
		return metainfo;
	}

	public void setMetainfo(Metainfo metainfo) {
		this.metainfo = metainfo;
	}

	public Piece[] getPieces() {
		return pieces;
	}

	public void setPieces(Piece[] pieces) {
		this.pieces = pieces;
	}

	public boolean isDownloading() {
		return downloading;
	}

	public void setDownloading(boolean downloading) {
		this.downloading = downloading;
	}
	
	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}
	
	public TorrentManager getTorrentManager() {
		return torrentManager;
	}

	public void setTorrentManager(TorrentManager torrentManager) {
		this.torrentManager = torrentManager;
	}

	public PeerManager getPeerManager() {
		return peerManager;
	}

	public void setPeerManager(PeerManager peerManager) {
		this.peerManager = peerManager;
	}

	public TrackerCommunicator getTracker() {
		return tracker;
	}

	public void setTracker(TrackerCommunicator tracker) {
		this.tracker = tracker;
	}

	public TorrentFileSystem getTorrentFileSystem() {
		return torrentFileSystem;
	}

	public void setTorrentFileSystem(TorrentFileSystem torrentFileSystem) {
		this.torrentFileSystem = torrentFileSystem;
	}
	
	public String toString()
	{
		BitSet completed = null;
		BitSet requested = null;
		if (getCompletedPieces() != null)
			completed = getCompletedPiecesCopy();
		if (getRequestedPieces() != null)
			requested = getRequestedPiecesCopy();
		return (new StringBuilder())
				.append("{Torrent->")
				.append("Name: ")
				.append(getName())
				.append(", Downloading: ")
				.append(isDownloading())
				.append(", Completed: ")
				.append(isCompleted())
				.append(", Uploaded: ")
				.append(getUploaded())
				.append(", Downloaded: ")
				.append(getDownloaded())
				.append(completed != null ? ", Completed pieces: " + completed : "")
				.append(requested != null ? ", Requested pieces: " + requested : "")
				.append("}").toString();
	}
	
	private class MainLoop implements Runnable
	{
		private final static int UNCHOKE_WAIT_TIME = 30;
		
		public void run()
		{
			while (isDownloading())
			{
				try
				{
					TimeUnit.SECONDS.sleep(UNCHOKE_WAIT_TIME);
					if (!isDownloading())
						return;
					logger.log(Level.INFO, "Trying to unchoke peers...");
					getPeerManager().unchokePeers();
				} catch (InterruptedException e)
				{
					logger.log(Level.WARNING, "Unchoker thread was interrupted");
				}
			}
		}
	}
	
	public void notifyTorrentManager(int type, Object event)
	{
		getTorrentManager().onNewEvent(this, type, event);
	}
}
