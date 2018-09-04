package peer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.Environment;
import client.LoggingClient;
import metainfo.Piece;
import tools.Util;
import torrent.Torrent;

public class PeerManager 
{
	private final static int MAX_DOWNLOADERS = 4;
	private Torrent torrent;	
	private Queue<Peer> peerList = new ConcurrentLinkedQueue<Peer>();
	private Map<ByteBuffer, Peer> connectedPeers = new ConcurrentHashMap<ByteBuffer, Peer>();
	private Map<ByteBuffer, Peer> unchokedPeers = new ConcurrentHashMap<ByteBuffer, Peer>();	
	private ExecutorService executorService;	
	Logger logger = LoggingClient.getInstance().logger;
	
	public PeerManager(Torrent torrent)
	{
		this.torrent = torrent;
		this.executorService = Executors.newFixedThreadPool(
				Environment.getInstance().getMAX_CONNECTIONS());
	}
	
	/*
	 * Instantiate peers from compact peers buffer received from tracker
	 */
	public void createPeers(ByteBuffer peers)	//TODO verify that peers.remaining() % 6 == 0
	{
		for (int index = 0; index < peers.array().length; index += 6)
		{
			Peer peer = new Peer(Util.intToIPString(peers.getInt()), 
					(Integer) (peers.getShort() & 0xffff), getTorrent(), this);
			if (!isConnectedAlready(peer))
			{
				getPeerList().add(peer);
				logger.log(Level.FINE, "Discovered new " + peer);
			}
		}
	}
	
	/*
	 * Instantiate peers from peers list received from tracker
	 */
	public void createPeers(List<HashMap<ByteBuffer, Object>> peers)
	{
		for (HashMap<ByteBuffer, Object> rawPeer : peers)
		{
			Peer peer = new Peer((ByteBuffer) (rawPeer.get(Util.stringToByteBuffer("peer id"))),
					Util.byteBufferToString(rawPeer.get(Util.stringToByteBuffer("ip"))),
					((Long) rawPeer.get(Util.stringToByteBuffer("port"))).intValue(), getTorrent(), this);
			if (!isConnectedAlready(peer))
			{
				getPeerList().add(peer);
				logger.log(Level.FINE, "Discovered new " + peer);
			}
		}
	}
	
	/*
	 * Try to connect to instantiated peers
	 */
	public void connectToPeers()	//TODO can be done better
	{
		for (Peer peer : getPeerList())
		{
			if (peer.isConnected())
			{
				logger.log(Level.FINER, "Tried to connect to already connected " + peer);
				continue;
			}
			getExecutorService().execute
			(new Runnable()	
				{
					public void run()
					{
						try
						{
							peer.setConnecting(true);
							if (peer.connect())
							{
								connectedPeers.put(peer.getPeerId(), peer);
								logger.log(Level.FINE, "Added newly connected " + peer + " to connectedPeers list");
							}
							else
								logger.log(Level.WARNING, "Failed to connect to " + peer);
						} catch(IOException e)	
						{
							logger.log(Level.WARNING, "Interrupted while connecting to " + peer, e);
							Thread.currentThread().interrupt();
						}
						finally
						{
							peer.setConnecting(false);
							getPeerList().remove(peer);
						}
					}
				}
			);
		}
	}
	
	/*
	 * Download the next available piece from peer
	 */
	private synchronized void downloadNextAvailablePieceFromPeer(Peer peer)	//TODO try to give a better name to this method
	{
		BitSet availablePieces = peer.getAvailablePiecesCopy();
		logger.log(Level.FINE, "Pieces downloaded so far: " + getTorrent().getCompletedPiecesCopy());
		logger.log(Level.FINE, "Selecting next piece to download from " + peer);
		Piece nextPiece = getTorrent().selectNextPiece(availablePieces);
		if (nextPiece == null)
		{
			logger.log(Level.FINE, peer + " is boring. Nothing downloadable atm.");
			return;
		}		
		getTorrent().getRequestedPieces().set(nextPiece.getIndex().intValue());
		logger.log(Level.FINER, "Pieces currently requested " + getTorrent().getRequestedPiecesCopy());
		logger.log(Level.FINER, "Attempting to download " + nextPiece + " from " + peer);
		peer.downloadPiece(nextPiece);
	}
	
	/*
	 * Handler for 'peer choke' event
	 * If a piece was requested from the peer, clear it from the requested pieces bitset
	 */
	public synchronized void onPeerChoked(Peer peer)
	{
		if (!peer.isConnected())
		{
			logger.log(Level.WARNING, "Choked by unconnected " + peer + "... wtf?");
			return;
		}
		
		Piece requestedPiece = peer.getDownloadingPiece();
		if (requestedPiece == null)
		{
			logger.log(Level.FINE, "No piece was requested from " + peer + ". No piece request to be cleared.");
			return;
		}
		getTorrent().getRequestedPieces().clear(requestedPiece.getIndex().intValue());
		logger.log(Level.FINER, requestedPiece + " cleared from requested pieces: " + getTorrent().getRequestedPiecesCopy());
	}
	
	/*
	 * Handler for 'peer unchoke' event
	 * If the peer is interesting, try to download next available piece from peer
	 */
	public synchronized void onPeerUnchoked(Peer peer)
	{
		if (!peer.isConnected())
		{
			logger.log(Level.WARNING, "Unchoked by unconnected " + peer + "... wtf?");
			return;
		}
		
		if (peer.getAm_interested())
		{
			logger.log(Level.FINE, peer + " seems interesting and has also unchoked us. Attempting to download next available piece...");
			downloadNextAvailablePieceFromPeer(peer);
		}
		else
			logger.log(Level.FINE, peer + " does not seem interesting. Continuing...");
	}
	
	/*
	 * Handler for 'peer interested' event
	 * Currently does nothing
	 */
	public synchronized void onPeerInterested(Peer peer)
	{
		if (!peer.isConnected())
		{
			logger.log(Level.WARNING, "Interested by unconnected " + peer + "... wtf?");
			return;
		}
	}
	
	/*
	 * Handler for 'peer not interested' event
	 * Currently does nothing
	 */
	public synchronized void onPeerNotInterested(Peer peer)
	{
		if (!peer.isConnected())
		{
			logger.log(Level.WARNING, "Not interested by unconnected " + peer + "... wtf?");
			return;
		}
	}
	
	/*
	 * Handler for 'bitfield received' event
	 * Send an interested message if peer has any pieces that we don't already have,
	 * or haven't already requested from another peer
	 */
	public synchronized void onPeerBitfieldReceived(Peer peer, BitSet availablePieces)
	{
		availablePieces.andNot(getTorrent().getCompletedPiecesCopy());
		availablePieces.andNot(getTorrent().getRequestedPiecesCopy());
		logger.log(Level.FINE, "Interesting pieces: " + availablePieces + ", from " + peer);
		
		if (availablePieces.cardinality() > 0)
			peer.startBeingInterested();
		else
			logger.log(Level.FINE, peer + " does not seem interesting. Continuing...");
		
		//Bitfield is the first message that may ever be received
		//Also, all peers start out as choked by default
		//So it's pointless to try to downloadFromPeer at this point
	}
	
	/*
	 * Handler for 'peer has a new piece' event
	 * Send an interested message if we don't already have this piece,
	 * or haven't already requested this piece from another peer,
	 * and we haven't already sent an interested message before
	 * Try to start downloading the next available piece from peer if we are interested,
	 * and the peer hasn't choked us
	 */
	public synchronized void onPeerHasNewPiece(Peer peer, int pieceIndex)
	{
		if (!getTorrent().getCompletedPiecesCopy().get(pieceIndex)
				&& !getTorrent().getRequestedPiecesCopy().get(pieceIndex)
				&& !peer.getAm_interested())
		{
			logger.log(Level.FINE, peer + " seems interesting now! " + getTorrent().getPiece(pieceIndex) + " is neither complete nor requested.");
			peer.startBeingInterested();
		}	
		
		if (!peer.getPeer_choking() && peer.getAm_interested() 
				&& peer.getDownloadingPiece() == null)
		{
			logger.log(Level.FINE, peer + " already has us unchoked. Attempting to download next available piece.");
			downloadNextAvailablePieceFromPeer(peer);
			return;
		}
		else if (peer.getAm_interested() && peer.getDownloadingPiece() == null)
		{
			logger.log(Level.FINE, peer + " is interesting but has us choked. Continuing...");
			return;
		}
		logger.log(Level.FINE, peer + " is boring. Continuing...");
	}
	
	/*
	 * Handler for 'piece downloaded from peer' event
	 * Clear the piece from requested pieces and make it available on completed pieces
	 * Notify peers (who doesn't already have this piece) about our new acquisition
	 * Try to start downloading the next available piece from peer if we are interested,
	 * and the peer hasn't choked us
	 */
	public synchronized void onPieceDownloadedFromPeer(Peer peer, Piece piece)
	{
		getTorrent().setDownloaded(getTorrent().getDownloaded() + piece.getLength());
		getTorrent().getRequestedPieces().clear(piece.getIndex().intValue());
		getTorrent().getCompletedPieces().set(piece.getIndex().intValue());
		logger.log(Level.INFO, "Total bytes downloaded so far: " + getTorrent().getDownloaded());
		logger.log(Level.FINE, "Requested pieces atm: " + getTorrent().getRequestedPiecesCopy());
		logger.log(Level.FINE, "Completed pieces atm: " + getTorrent().getCompletedPiecesCopy());
		double percentage = ((double) getTorrent().getDownloaded() / (double) getTorrent().getSize())*100.0;
		//System.out.printf("Downloaded: %.2f%%\n", percentage);
		getTorrent().onPieceDownloaded(percentage);
		
		for (Peer connectedPeer : connectedPeers.values())
		{
			if (connectedPeer.isConnected())
			{
				if (!connectedPeer.getAvailablePiecesCopy().get(piece.getIndex().intValue()))	//Reduces bittorrent traffic heavily
				{
					logger.log(Level.FINE, connectedPeer + " doesn't have newly acquired " + piece + ", should notify.");
					connectedPeer.notifyNewAcquiredPiece(piece);
				}
				else
					logger.log(Level.FINE, connectedPeer + " already has our newly acquired " + piece);
			}
			else
				logger.log(Level.WARNING, connectedPeer + " not connected? WTF!");					
		}
		if (getTorrent().getCompletedPiecesCopy().cardinality() == getTorrent().getPieces().length)
		{
			logger.log(Level.INFO, "Woohoo! All pieces downloaded!");
			logger.log(Level.FINER, "On download completed event fired for " + getTorrent());
			System.out.println("Finalizing download...");
			getTorrent().onDownloadCompleted();				
			return;
		}
				
		if (!peer.getPeer_choking() && peer.getAm_interested())
		{
			logger.log(Level.FINE, peer + " already has us unchoked. Already got a piece from this peer... time to get a new one!");
			downloadNextAvailablePieceFromPeer(peer);
		}
	}
	
	public synchronized void onTorrentDownloadStopped()	//TODO wait wut?
	{
		logger.log(Level.FINE, "Torrent Download Stopped event..?");
		onTorrentDownloadCompleted();
	}
	
	public synchronized void onTorrentDownloadCompleted()
	{
		logger.log(Level.FINE, "Torrent Download Completed event");
		getExecutorService().shutdown();
		logger.log(Level.FINER, "Executor service stopped taking new requests");
		for (Peer peer : connectedPeers.values())
			peer.disconnect();
		for (Peer peer : getPeerList())
		{
			if (peer.isConnecting() || peer.isConnected()) //peer is connected in peer list..? WTF!!
				peer.disconnect();
		}
		
		logger.log(Level.FINER, "All connected peers for " + getTorrent() + " disconnected! Shutting down peer manager now...");
		shutdownPeerManager();
	}
	
	/*
	 * Handler for 'block uploaded' event
	 * Record the amount uploaded
	 */
	public synchronized void onBlockUploadedToPeer(int size) //TODO maybe we should record when we have uploaded a piece, and not a block?
	{
		getTorrent().setUploaded(getTorrent().getUploaded() + size);
		logger.log(Level.FINE, "Block of size " + size + " uploaded. Total number of bytes uploaded so far: " + getTorrent().getUploaded());
	}
	
	/*
	 * Handler for 'peer disconnected' event
	 * Clear piece requested (if any) from this peer
	 * Remove peer from connected peers list
	 */
	public synchronized void onPeerDisconnected(Peer peer)
	{
		Piece requestedPiece = peer.getDownloadingPiece();
		if (requestedPiece == null)
			logger.log(Level.FINE, "No piece was requested from " + peer + ". No requested pieces to clear.");
		else
		{
			logger.log(Level.FINE, "Piece request to be cleared: " + requestedPiece);
			synchronized (getTorrent().getRequestedPieces())
			{
				getTorrent().getRequestedPieces().clear(requestedPiece.getIndex().intValue());
				logger.log(Level.FINER, requestedPiece + " cleared from requested pieces: " + getTorrent().getRequestedPiecesCopy());
			}
		}
		
		if (peer != null && peer.getPeerId() != null)
			getConnectedPeers().remove(peer.getPeerId());
		logger.log(Level.FINEST, peer + " removed from connected peers list.");
		
		if (requestedPiece != null)
		{
			logger.log(Level.FINE, "Checking if any other peer has the cleared piece...");
			for (Peer connectedPeer : connectedPeers.values())
			{
				if (connectedPeer.getDownloadingPiece() == null)
				{
					BitSet availablePieces = connectedPeer.getAvailablePiecesCopy();
					availablePieces.andNot(getTorrent().getCompletedPiecesCopy());
					availablePieces.andNot(getTorrent().getRequestedPiecesCopy());
					logger.log(Level.FINE, "Interesting pieces: " + availablePieces + ", from " + connectedPeer);
					if (availablePieces.cardinality() > 0)
					{
						if (!connectedPeer.getAm_interested())
							connectedPeer.startBeingInterested();
						else
						{
							if (!peer.getPeer_choking() & peer.getDownloadingPiece() == null)
								downloadNextAvailablePieceFromPeer(connectedPeer);
							break;
						}
					}
				}
			}
		}
	}
	
	/*
	 * Check if peer is connected already
	 */
	public synchronized boolean isConnectedAlready(Peer peerToConnectTo)	//TODO what a pile of redundant bullshit! -_-
	{
		if (peerToConnectTo == null)
			return false;		
		
		for (Peer connectedPeer : connectedPeers.values())
		{
			if (peerToConnectTo.getPeerId() != null)	//If the tracker sent a peerID, we can directly check if the peerIDs are equal
			{
				if (connectedPeer.getPeerId().compareTo(peerToConnectTo.getPeerId()) == 0)
					return true;
				continue;
			}
			
			if (connectedPeer.getIP().compareTo(peerToConnectTo.getIP()) == 0
					&& connectedPeer.getPort().compareTo(peerToConnectTo.getPort()) == 0)	//else check for equality of IP and port
				return true;
		}
		
		for (Peer peer : getPeerList())
		{
			if (peerToConnectTo.getPeerId() != null)	//If the tracker sent a peerID, we can directly check if the peerIDs are equal
			{
				if (peer.getPeerId().compareTo(peerToConnectTo.getPeerId()) == 0)
				{
					if (peer.isConnecting())
						return true;
				}
				continue;
			}
			
			if (peer.getIP().compareTo(peerToConnectTo.getIP()) == 0
					&& peer.getPort().compareTo(peerToConnectTo.getPort()) == 0)	//else check for equality of IP and port
			{
				if (peer.isConnecting())
					return true;
			}
		}
		
		return false;
	}
	
	/*
	 * Shutdown executor service so that it doesn't keep running after main thread exits
	 */
	public void shutdownPeerManager()
	{
		getExecutorService().shutdown();	//TODO Redundant
		try
		{
			logger.log(Level.FINE, "Awaiting termination of executor service... bhalo koira boltesi");
			if (!getExecutorService().awaitTermination(15, TimeUnit.SECONDS))
			{
				logger.log(Level.FINE, "EXECUTOR SERVICE! ABORT MISSION!");
				getExecutorService().shutdownNow();				
				while (!getExecutorService().awaitTermination(15, TimeUnit.SECONDS))
					logger.log(Level.SEVERE, "Pool didn't terminate. WTF is wrong with you executor service?");
			}
			logger.log(Level.FINER, "PeerManager shutdown successful!");
		} catch (InterruptedException e) 
		{
			logger.log(Level.WARNING, "Interrupted while shutting down executor service", e);
			getExecutorService().shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
	
	/*
	 * Lame unchoking algorithm
	 * Needs to be far more intelligent
	 * But that would require a proper 'download/upload speed' implementation
	 */
	public void unchokePeers()
	{
		for (Entry<ByteBuffer, Peer> peer : unchokedPeers.entrySet())
		{
			if (!peer.getValue().isConnected())
			{
				logger.log(Level.FINER, "Disconnected " + peer.getValue() + " removed from unchoked peers");
				unchokedPeers.remove(peer.getKey());
			}				
			else if (peer.getValue().getPeer_choking())
			{
				logger.log(Level.FINER, peer.getValue() + ", amare choke korsos..? Toreo choke korlam!");
				peer.getValue().choke();
				unchokedPeers.remove(peer.getKey());
			}
		}
		
		if (unchokedPeers.size() < MAX_DOWNLOADERS)
		{
			for (Entry<ByteBuffer, Peer> peer : connectedPeers.entrySet())
			{
				if (!unchokedPeers.containsKey(peer.getKey()))
				{
					if (peer.getValue().getPeer_interested() && peer.getValue().getAm_choking())
					{
						if (peer.getValue().getDownloadedPiecesCopy().cardinality() > 0)
						{
							logger.log(Level.FINER, peer.getValue() + ", amakeo dise... tai amio take dilam!");
							unchokedPeers.put(peer.getKey(), peer.getValue());
							peer.getValue().unchoke();
						}						
					}								
				}
			}
		}		
	}
	
	public Torrent getTorrent() {
		return torrent;
	}
	
	public void setTorrent(Torrent torrent) {
		this.torrent = torrent;
	}
	
	public Queue<Peer> getPeerList() {
		return peerList;
	}

	public void setPeerList(Queue<Peer> peerList) {
		this.peerList = peerList;
	}

	public Map<ByteBuffer, Peer> getConnectedPeers() {
		return connectedPeers;
	}

	public void setConnectedPeers(Map<ByteBuffer, Peer> connectedPeers) {
		this.connectedPeers = connectedPeers;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public void setExecutorService(ExecutorService executorService) {
		this.executorService = executorService;
	}
}
