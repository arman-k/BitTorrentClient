package peer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.BitSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.Environment;
import client.LoggingClient;
import metainfo.Piece;
import tools.Util;
import torrent.Torrent;

public class Peer
{
	private ByteBuffer peerId;
	private String IP;
	private Integer port;
	private SocketChannel socketChannel;
	private Torrent torrent;
	
	private PeerManager manager;	
	private boolean am_choking = true;
	private boolean am_interested = false;
	private boolean peer_choking = true;
	private boolean peer_interested = false;
	private boolean connecting = false;
	private boolean connected = false;	
	
	private BitSet availablePieces;
	private BitSet downloadedPieces;
	private BitSet uploadedPieces;
	
	private PeerMessenger messenger;
	private Piece downloadingPiece;
	private LinkedBlockingQueue<ByteBuffer> requestedBlocks;
	private int requestedBlockOffset;
	
	private static final int REQUEST_PIPELINE_SIZE = 10;
	private static final int MIN_REQUEST_SIZE = 16*Util.SIZE_KB;
	private static final int MAX_REQUEST_SIZE = 128*Util.SIZE_KB;
	Logger logger = LoggingClient.getInstance().logger;
	
	public Peer(String IP, Integer port, Torrent torrent, PeerManager manager)
	{
		this(null, IP, port, torrent, manager);
	}
	
	public Peer(ByteBuffer peerId, String IP, Integer port, Torrent torrent, PeerManager manager)
	{
		this.peerId = peerId;
		this.IP = IP;
		this.port = port;
		this.torrent = torrent;
		this.manager = manager;
	}
	
	/*
	 * Connect to this peer and perform handshake
	 */
	public boolean connect() throws IOException
	{
		if (isConnected())
		{
			logger.log(Level.WARNING, this + " is already connected!");
			return true;
		}
		
		setSocketChannel(SocketChannel.open());
		getSocketChannel().configureBlocking(true);	//Connect in blocking-mode
		getSocketChannel().connect(new InetSocketAddress(IP, port));
		
		logger.log(Level.FINER, "Successfully connected to socketchannel for " + this + "! Attempting to do handshake now");
		Handshake.send(this, getTorrent().getInfoHash(),
				Environment.getInstance().getPeerId());
		ByteBuffer peerIdReceived = Handshake.receive(this, getTorrent().getInfoHash());
		
		if (getPeerId() != null) //This is only checked if the tracker sent a peer ID
		{
			if (getPeerId().compareTo(peerIdReceived) != 0)
			{
				logger.log(Level.WARNING, this + "Peer ID of " + this + " doesn't match the ID sent by tracker");
				abruptDisconnect();
				return false;
			}
		}
		setPeerId(peerIdReceived);		
		logger.log(Level.INFO, "Successfully exchanged handshake with " + this);
		
		getSocketChannel().configureBlocking(false);
		setMessenger(new PeerMessenger(this));	//Create a PeerMessenger to exchange messages with this peer
		setAvailablePieces(new BitSet(getTorrent().getPieces().length));
		setDownloadedPieces(new BitSet(getTorrent().getPieces().length));
		setUploadedPieces(new BitSet(getTorrent().getPieces().length));
		
		startPeerMessenger();
		setConnected(true);
		logger.log(Level.INFO, "Connection fully established with " + this);
		return true;
	}
	
	/*
	 * Disconnect gracefully from this peer
	 */
	public void disconnect()
	{
		cancelQueuedRequests();
		stopBeingInterested();
		if (getMessenger() != null && getMessenger().isRunning())
			getMessenger().stop();
		logger.log(Level.FINE, "Disconnecting from " + this);
		abruptDisconnect();
	}
	
	/*
	 * Disconnect and close all resources
	 */
	public void abruptDisconnect()
	{
		logger.log(Level.WARNING, "Peer disconnected event fired for " + this);
		getManager().onPeerDisconnected(this);
		setAvailablePieces(null);
		setDownloadedPieces(null);
		setUploadedPieces(null);
		setMessenger(null);
		setRequestedBlocks(null);
		setDownloadingPiece(null);
		setConnected(false);
		
		try
		{
			getSocketChannel().close();
		} catch (IOException e)
		{
			logger.log(Level.WARNING, "Couldn't close socket channel while disconnecting from " + this, e);
		}
		logger.log(Level.FINE, "Socketchannel closed for and disconnected from " + this);
	}
	
	public void startPeerMessenger()
	{
		if (!getMessenger().isRunning())
		{
			logger.log(Level.FINE, "Peer messenger started for " + this);
			getMessenger().start();	//Start peer messenger to start exchanging messages with peer
		}			
	}
	
	/*
	 * Send choke message to peer if not already choking
	 */
	public void choke()
	{
		if (!getAm_choking())
		{
			send(PeerMessage.encodeChoke());
			setAm_choking(true);
			logger.log(Level.INFO, "Choked " + this);
		}
	}
	
	/*
	 * Send unchoke message to peer if not already unchoked
	 */
	public void unchoke()
	{
		if (getAm_choking())
		{
			send(PeerMessage.encodeUnchoke());
			setAm_choking(false);
			logger.log(Level.INFO, "Unchoked " + this);
		}
	}
	
	/*
	 * Send interested message to peer if not already intereted
	 */
	public void startBeingInterested()
	{
		if (!getAm_interested())
		{
			send(PeerMessage.encodeInterested());
			setAm_interested(true);
			logger.log(Level.INFO, "Started being interested in " + this);
		}
	}
	
	/*
	 * Send notInterested message to peer if not already uninterested
	 */
	public void stopBeingInterested()
	{
		if (getAm_interested())
		{
			send(PeerMessage.encodeNotInterested());
			setAm_interested(false);
			logger.log(Level.INFO, "Stopped being interested in " + this);
		}
	}
	
	/*
	 * Send the block to the peer via the messenger
	 * Ignore if not connected
	 */
	public void send(ByteBuffer block)
	{
		if (!isConnected())
		{
			logger.log(Level.WARNING, "Attempted to send message to unconnected " + this);
			return;
		}		
		getMessenger().send(block);
		logger.log(Level.FINER, "Message queued for delivery to " + this);
	}
	
	/*
	 * Download the given piece from this peer
	 */
	//TODO might need synchronization
	public void downloadPiece(Piece piece)
	{
		if (!isConnected())
		{
			logger.log(Level.WARNING, "Attempted to download piece from unconnected " + this);
			return;
		}			
		if (piece.isAvailable())
		{
			logger.log(Level.WARNING, "Attempted to download already available piece from " + this);
			return;
		}
		
		setDownloadingPiece(piece);
		setRequestedBlocks(new LinkedBlockingQueue<ByteBuffer>(REQUEST_PIPELINE_SIZE));	//Initialize block request pipeline for this piece
		setRequestedBlockOffset(0);	//We want to start requesting from offset 0
		logger.log(Level.INFO, "Setting up requests for download of " + piece + " from " + this);
		requestRemainingBlocks();	//Start requesting blocks		
	}
	
	/*
	 * Keep requesting blocks till all blocks have been requested or our pipeline is full
	 */
	private void requestRemainingBlocks()
	{
		if (getDownloadingPiece() == null || getRequestedBlocks() == null)
		{
			logger.log(Level.WARNING, "Invalid block request - no piece was being downloaded from " + this);
			return;
		}
		
		while (getRequestedBlocks().remainingCapacity() != 0 
			&& getRequestedBlockOffset() != getDownloadingPiece().getLength())
		{
			//The block length should be the minimum request size, or the piece size - last offset, whichever is smaller
			int blockLength = getDownloadingPiece().getLength().intValue() - getRequestedBlockOffset();
			if (MIN_REQUEST_SIZE < blockLength)
				blockLength = MIN_REQUEST_SIZE;
			logger.log(Level.FINEST, "Requesting block offset: " + getRequestedBlockOffset() + 
					", block length: " + blockLength + " of " + getDownloadingPiece() + "from " + this);
			
			ByteBuffer requestMessage = PeerMessage.encodeRequest(
										getDownloadingPiece().getIndex().intValue(), 
										getRequestedBlockOffset(), blockLength);
			try
			{
				getRequestedBlocks().put(requestMessage);
				send(requestMessage.duplicate());
				logger.log(Level.FINEST, "Requested block offset: " + getRequestedBlockOffset() + 
						", block length: " + blockLength + " of " + getDownloadingPiece() + "from " + this);
				setRequestedBlockOffset(getRequestedBlockOffset() + blockLength);
			} catch (InterruptedException e)
			{
				logger.log(Level.WARNING, "Interrupted while enqueuing block request for " + this);
			}
		}
		if (getRequestedBlocks().remainingCapacity() == 0)
			logger.log(Level.FINEST, "Request pipeline filled up for " + getDownloadingPiece() + " from " + this);
		if (getRequestedBlockOffset() == getDownloadingPiece().getLength())
			logger.log(Level.FINEST, "All blocks already requested for " + getDownloadingPiece() + " from " + this + ". Continuing...");
	}
	
	/*
	 * Find and remove from the pipeline (if queued up at all), the given block request message
	 * Block request messages are typically cleared when the requested block arrives from the peer
	 */
	private void removeBlockRequest(ByteBuffer requestMessage)
	{
		requestMessage.getInt();	//Consume length field
		requestMessage.get();	//Consume type field
		int requestIndex = requestMessage.getInt();
		int requestBegin = requestMessage.getInt();
		int requestlength = requestMessage.getInt();
		requestMessage.rewind();
		
		logger.log(Level.FINEST, "Attempting to remove block request for " + getDownloadingPiece() + 
				", offset: " + requestBegin + ", length: " + requestlength + " from " + this);
		for (ByteBuffer blockRequest : getRequestedBlocks())
		{
			blockRequest.getInt();	//Consume length field
			blockRequest.get();	//Consume type field
			if (requestIndex != blockRequest.getInt())	//Compare the value field
			{
				blockRequest.rewind();
				continue;
			}				
			if (requestBegin != blockRequest.getInt())	//Compare the value field
			{
				blockRequest.rewind();
				continue;
			}
			if (requestlength != blockRequest.getInt())	//Compare the value field
			{
				blockRequest.rewind();
				continue;
			}
			blockRequest.rewind();
			getRequestedBlocks().remove(blockRequest);
			logger.log(Level.FINEST, "Block request removed for " + getDownloadingPiece() + 
					", offset: " + requestBegin + ", length: " + requestlength + " from " + this);
			break;
		}
	}
	
	/*
	 * Send a cancel message for all pipelined requests
	 * This is typically called when the peer chokes us
	 */
	public void cancelQueuedRequests()
	{
		if (getDownloadingPiece() == null || getRequestedBlocks() == null)
		{
			logger.log(Level.WARNING, "No queued requests to cancel - no piece was being downloaded from " + this);	
			return;
		}
		for (ByteBuffer blockRequest : getRequestedBlocks())
		{
			blockRequest.getInt();
			blockRequest.get();
			this.send(PeerMessage.encodeCancel(blockRequest.getInt(), 
					blockRequest.getInt(), blockRequest.getInt()));
			blockRequest.rewind();
			//TODO check this carefully
			getRequestedBlocks().remove(blockRequest); //TODO not sure if this is required
		}
		logger.log(Level.INFO, "Canceled queued requests for " + getDownloadingPiece() + " from " + this);
	}
	
	//Send HAVE message to peer
	public void notifyNewAcquiredPiece(Piece piece)
	{
		send(PeerMessage.encodeHave(piece.getIndex().intValue()));
		logger.log(Level.FINER, "Sent have message for " + piece + " to " + this);
	}
	
	/*
	 * The main message handler that processes peer's messages and fires appropriate events
	 */
	public synchronized void onMessageReceived(ByteBuffer message)
	{
		if (!message.hasRemaining())
		{
			logger.log(Level.FINE, "Keep-alive message received from " + this);
			return; //This is a keep-alive message, ignore
		}
		
		byte messageType = message.get();
		switch (messageType)
		{
			case PeerMessage.CHOKE_ID:
				logger.log(Level.FINE, "CHOKE message received from " + this);
				if (!getPeer_choking())
				{
					setPeer_choking(true);
					logger.log(Level.FINER, "Peer choke event fired for " + this);
					getManager().onPeerChoked(this);
					cancelQueuedRequests();					
				}
				else
					logger.log(Level.WARNING, "Already choked by " + this);
				break;
			case PeerMessage.UNCHOKE_ID:
				logger.log(Level.FINE, "UNCHOKE message received from " + this);
				if (getPeer_choking())
				{
					setPeer_choking(false);
					logger.log(Level.FINER, "Peer unchoke event fired for " + this);
					getManager().onPeerUnchoked(this);					
				}
				else
					logger.log(Level.WARNING, "Already unchoked by " + this);
				break;
			case PeerMessage.INTERESTED_ID:
				logger.log(Level.FINE, "INTERESTED message received from " + this);
				if (!getPeer_interested())
				{
					setPeer_interested(true);
					logger.log(Level.FINER, "Peer interested event fired for " + this);
					getManager().onPeerInterested(this);
				}
				else
					logger.log(Level.WARNING, this + " already interested in us");
				break;
			case PeerMessage.NOT_INTERESTED_ID:
				logger.log(Level.FINE, "NOT_INTERESTED message received from " + this);
				if (getPeer_interested())
				{
					setPeer_interested(false);
					logger.log(Level.FINER, "Peer not interested event fired for " + this);
					getManager().onPeerNotInterested(this);					
				}
				else
					logger.log(Level.WARNING, this + " already not interested in us");
				break;
			case PeerMessage.HAVE_ID:				
				int haveIndex = message.getInt();
				logger.log(Level.FINE, "HAVE message received from " + this + " for piece index: " + haveIndex);
				if (haveIndex < 0 || haveIndex > getTorrent().getPieces().length)
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Invalid piece index " + haveIndex + " in HAVE message from " + this);
					logger.log(Level.WARNING, "Invalid HAVE message" , e);
					throw e;
				}
				synchronized (getAvailablePieces())
				{
					getAvailablePieces().set(haveIndex);
				}
				logger.log(Level.FINER, "Peer has new piece event fired for " + this);
				getManager().onPeerHasNewPiece(this, haveIndex);				
				break;
			case PeerMessage.BITFIELD_ID:
				logger.log(Level.FINE, "BITFIELD message received from " + this);
				ByteBuffer bitfieldBuf = ByteBuffer.allocate(message.remaining()).put(message);
				bitfieldBuf.rewind();
				BitSet bitfieldBitSet = new BitSet(bitfieldBuf.remaining()*8);
				
				if (bitfieldBitSet.size() != getAvailablePieces().size())
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Invalid bitfield size " + 
													bitfieldBitSet.size() + " in BITFIELD message from peer " + this);
					logger.log(Level.WARNING, "Invalid BITFIELD message" , e);
					throw e;
				}
				//TODO check this carefully
				for (int bitIndex = 0; bitIndex < bitfieldBuf.remaining()*8; bitIndex++)	//TODO move this to Util
				{
					if ((bitfieldBuf.get(bitIndex/8) & (1 << (7 - (bitIndex%8)))) > 0)
						bitfieldBitSet.set(bitIndex);	//Big-endian bits to little-endian bits
				}
					
				logger.log(Level.FINE, "Bitfield received: " + bitfieldBitSet);
				synchronized (getAvailablePieces()) 
				{
					getAvailablePieces().or(bitfieldBitSet);
				}
				logger.log(Level.FINER, "Bitfield received event fired for " + this);
				getManager().onPeerBitfieldReceived(this, getAvailablePiecesCopy());
				break;
			case PeerMessage.REQUEST_ID:	//TODO do proper handling of invalid request, don't throw stupid exceptions				
				int requestIndex = message.getInt();
				int requestBegin = message.getInt();
				int requestLength = message.getInt();
				logger.log(Level.FINE, "REQUEST message received for index: " + requestIndex + 
						", offset: " + requestBegin + ", length: " + requestLength + " from " + this);
				
				if (getAm_choking())
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Choked " + this + " is requesting a piece. Halay eto behaya ken?");
					logger.log(Level.WARNING, "Invalid REQUEST message" , e);
					throw e;
				}
				if (requestIndex < 0 || requestIndex > getTorrent().getPieces().length)
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Invalid piece index " + requestIndex + " in REQUEST message from " + this + ". Like are we even on the same page?");
					logger.log(Level.WARNING, "Invalid REQUEST message" , e);
					throw e;
				}
				Piece requestPiece = getTorrent().getPiece(requestIndex);				
				if (!requestPiece.isAvailable())
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Piece index " + requestIndex + " in REQUEST message from " + this + " is not available yet! WTF peer?");
					logger.log(Level.WARNING, "Invalid REQUEST message" , e);
					throw e;
				}
				if (requestBegin < 0 || requestBegin > requestPiece.getLength())
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Invalid starting offset " + requestBegin + " in REQUEST message from " + this + ". Matha kharap naki?");
					logger.log(Level.WARNING, "Invalid REQUEST message" , e);
					throw e;
				}
				if (requestLength > MAX_REQUEST_SIZE)
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Too large a block request (" + requestLength + ") from " + this + ". Halay eto khaishta ken?");
					logger.log(Level.WARNING, "Invalid REQUEST message" , e);
					throw e;
				}
				try
				{
					ByteBuffer block = requestPiece.read(new Long(requestBegin), new Long(requestLength));
					this.send(block);	//Send the requested block to peer
					logger.log(Level.FINER, "Block uploaded event fired for request from " + this + " for " + requestPiece);
					getManager().onBlockUploadedToPeer(requestLength);					
				} catch (IOException e)
				{
					logger.log(Level.WARNING, "Error while reading " + requestPiece + " for " + this);
				}
				break;
			case PeerMessage.PIECE_ID:
				int pieceIndex = message.getInt();
				int pieceBegin = message.getInt();		
				ByteBuffer pieceBlock = ByteBuffer.allocate(message.remaining()).put(message);
				pieceBlock.rewind();
				logger.log(Level.FINE, "PIECE message received for index: " + pieceIndex + ", offset: " 
									+ pieceBegin + ", length: " + pieceBlock.remaining() + " from " + this);
								
				if (pieceIndex < 0 || pieceIndex > getTorrent().getPieces().length)
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Invalid piece index " + pieceIndex + " in PIECE message from " + this + ". Like are we even on the same page?");
					logger.log(Level.WARNING, "Invalid PIECE message" , e);
					throw e;
				}
				Piece pieceReceived = getTorrent().getPiece(pieceIndex);
				if (pieceReceived.isAvailable())
				{
					logger.log(Level.WARNING, this + " sent block for " + pieceReceived + " that we already have. Eto bhalo shajte chay keno?");
					break; //We already have the piece, ignore
				}
				if (pieceBegin < 0 || pieceBegin > pieceReceived.getLength())
				{
					PeerMessage.PeerMessageException e = new PeerMessage.PeerMessageException("Invalid starting offset " + pieceBegin + " in PIECE message for " + pieceReceived + " from " + this + ". Maney ki egular?");
					logger.log(Level.WARNING, "Invalid PIECE message" , e);
					throw e;
				}
				
				removeBlockRequest(PeerMessage.encodeRequest(pieceIndex, pieceBegin, pieceBlock.remaining()));
				try
				{
					logger.log(Level.FINER, "Writing block received for " + pieceReceived + " from " + this);
					pieceReceived.write(pieceBlock, new Long(pieceBegin));					
					if (pieceReceived.isAvailable())	//Check if piece is complete now (successfully hash-verified) after writing the block
					{
						cancelQueuedRequests();
						setDownloadingPiece(null);
						getDownloadedPieces().set(pieceReceived.getIndex().intValue());
						logger.log(Level.INFO, pieceReceived + " from " + this + " downloaded and validated!");
						logger.log(Level.FINER, "Piece downloaded event fired for piece from " + this + " for " + pieceReceived);
						getManager().onPieceDownloadedFromPeer(this, pieceReceived);						
					}
					else
					{
						logger.log(Level.FINER, "Requesting remaining blocks from " + this);
						requestRemainingBlocks();	//Otherwise see if requesting more blocks is possible
					}						
					break;
					
				} catch (IOException e)
				{
					logger.log(Level.WARNING, "Error while writing block received for " + pieceReceived + " from " + this, e);
				}
				break;
			case PeerMessage.CANCEL_ID:
				//TODO cancel outgoing message
				logger.log(Level.FINE, "Ignoring CANCEL message received from " + this);			
				break;
			case PeerMessage.PORT_ID:
				//Required for DHT protocol
				//Ignore for now
				logger.log(Level.FINE, "Ignoring PORT message received from " + this);
				break;
			default:
				logger.log(Level.FINE, "Message ID received from " + this + " not recognized/supported");
				break;
		}
	}
	
	public BitSet getAvailablePiecesCopy() 
	{
		synchronized(availablePieces)
		{
			return (BitSet) getAvailablePieces().clone();
		}
	}
	
	public BitSet getUploadedPiecesCopy() 
	{
		synchronized(uploadedPieces)
		{
			return (BitSet) uploadedPieces.clone();
		}
	}
	
	public BitSet getDownloadedPiecesCopy() 
	{
		synchronized(downloadedPieces)
		{
			return (BitSet) downloadedPieces.clone();
		}
	}
	
	public ByteBuffer getPeerId() {
		return peerId;
	}

	public void setPeerId(ByteBuffer peerId) {
		this.peerId = peerId;
	}

	public String getIP() {
		return IP;
	}

	public void setIP(String iP) {
		IP = iP;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	public void setSocketChannel(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}

	public Torrent getTorrent() {
		return torrent;
	}

	public void setTorrent(Torrent torrent) {
		this.torrent = torrent;
	}

	public PeerManager getManager() {
		return manager;
	}

	public void setManager(PeerManager manager) {
		this.manager = manager;
	}

	public boolean getAm_choking() {
		return am_choking;
	}

	public void setAm_choking(boolean am_choking) {
		this.am_choking = am_choking;
	}

	public boolean getAm_interested() {
		return am_interested;
	}

	public void setAm_interested(boolean am_interested) {
		this.am_interested = am_interested;
	}

	public boolean getPeer_choking() {
		return peer_choking;
	}

	public void setPeer_choking(boolean peer_choking) {
		this.peer_choking = peer_choking;
	}

	public boolean getPeer_interested() {
		return peer_interested;
	}

	public void setPeer_interested(boolean peer_interested) {
		this.peer_interested = peer_interested;
	}
	
	public boolean isConnecting() {
		return connecting;
	}

	public void setConnecting(boolean connecting) {
		this.connecting = connecting;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public BitSet getAvailablePieces() {
		return availablePieces;
	}

	public void setAvailablePieces(BitSet availablePieces) {
		this.availablePieces = availablePieces;
	}
	
	public BitSet getDownloadedPieces() {
		return downloadedPieces;
	}

	public void setDownloadedPieces(BitSet downloadedPieces) {
		this.downloadedPieces = downloadedPieces;
	}

	public BitSet getUploadedPieces() {
		return uploadedPieces;
	}

	public void setUploadedPieces(BitSet uploadedPieces) {
		this.uploadedPieces = uploadedPieces;
	}

	public PeerMessenger getMessenger() {
		return messenger;
	}

	public void setMessenger(PeerMessenger messenger) {
		this.messenger = messenger;
	}

	public Piece getDownloadingPiece() {
		return downloadingPiece;
	}

	public void setDownloadingPiece(Piece downloadingPiece) {
		this.downloadingPiece = downloadingPiece;
	}

	public LinkedBlockingQueue<ByteBuffer> getRequestedBlocks() {
		return requestedBlocks;
	}

	public void setRequestedBlocks(LinkedBlockingQueue<ByteBuffer> requestedBlocks) {
		this.requestedBlocks = requestedBlocks;
	}

	public int getRequestedBlockOffset() {
		return requestedBlockOffset;
	}

	public void setRequestedBlockOffset(int requestedBlockOffset) {
		this.requestedBlockOffset = requestedBlockOffset;
	}
	
	//TODO make this more descriptive, ie choking/unchoking blablabla
	public String toString()
	{
		ByteBuffer peerId = getPeerId();
		BitSet available = null;
		if (getAvailablePieces() != null)
			available = getAvailablePiecesCopy();
		Piece downloading = getDownloadingPiece();
		boolean connected = isConnected();
		StringBuilder builder = new StringBuilder();
		builder.append("{Peer->")
				.append(peerId != null ? 
				"Peer id: " + Util.URLEncode(peerId.array()) + ", " : "")
				.append("IP addr: ")
				.append(getIP())
				.append(", Port: ")
				.append(getPort())
				.append(", Connected: ")
				.append(connected);
		if (connected)
		{
			builder.append(", Am_choking: ")
			.append(getAm_choking())
			.append(", Am_interested: ")
			.append(getAm_interested())
			.append(", Peer_choking: ")
			.append(getPeer_choking())
			.append(", Peer_interested: ")
			.append(getPeer_interested())
			.append(available != null ? ", Available pieces: " + available : "")
			.append(downloading != null ? ", Downloading piece: " + downloading 
			+ ", RequestedBlockOffset: " + getRequestedBlockOffset() : "");
		}
		return builder.append("}").toString();
	}
	
	/*
	 * Handshake protocol implementation
	 */
	private static class Handshake
	{
		private final static byte[] PSTR = "BitTorrent protocol".getBytes();		
		private final static byte PSTRLEN_LENGTH = 1;
		private final static byte PSTRLEN = 19;
		private final static int RESERVED_LENGTH = 8;
		private final static int INFOHASH_LENGTH = 20;
		private final static int PEERID_LENGTH = 20;
		private final static int HANDSHAKE_LENGTH = PSTRLEN_LENGTH+PSTRLEN+RESERVED_LENGTH+INFOHASH_LENGTH+PEERID_LENGTH;
		static Logger logger = LoggingClient.getInstance().logger;
		
		/*
		 * Send your handshake to this peer
		 */
		public static void send(Peer peer, ByteBuffer infoHash, ByteBuffer peerId) throws IOException
		{
			boolean blocking = peer.getSocketChannel().isBlocking();			
			peer.getSocketChannel().configureBlocking(true);	//Do the handshake in blocking mode
			
			ByteBuffer message = ByteBuffer.allocate(HANDSHAKE_LENGTH);
			message.put(PSTRLEN).put(PSTR).put(new byte[RESERVED_LENGTH])
					.put(infoHash.array()).put(peerId.array()).rewind();
			peer.getSocketChannel().write(message);
			
			peer.getSocketChannel().configureBlocking(blocking);	//Reset the channel to whatever blocking-mode it was on before
			logger.log(Level.FINER, "Handshake successfully sent to " + peer);
		}
		
		/*
		 * Receive and validate peer's handshake
		 * Returns peerId of peer
		 */
		public static ByteBuffer receive(Peer peer, ByteBuffer infoHash) throws IOException
		{
			boolean blocking = peer.getSocketChannel().isBlocking();
			peer.getSocketChannel().configureBlocking(true);	//Do the handshake in blocking mode
			ByteBuffer message = ByteBuffer.allocate(HANDSHAKE_LENGTH);
			
			int read = peer.getSocketChannel().read(message);
			message.rewind();
			if (read == -1)
			{
				PeerConnectionException e = new PeerConnectionException("Failed to receive handshake from " + peer);
				logger.log(Level.WARNING, "Invalid HANDSHAKE" , e);
				throw e;
			}
			if (read != HANDSHAKE_LENGTH)
			{
				PeerConnectionException e = new PeerConnectionException("Invalid handshake length from " + peer);
				logger.log(Level.WARNING, "Invalid HANDSHAKE" , e);
				throw e;
			}
			if (message.get() != PSTRLEN)
			{
				PeerConnectionException e = new PeerConnectionException("Invalid pstrlen from " + peer);
				logger.log(Level.WARNING, "Invalid HANDSHAKE" , e);
				throw e;
			}
			message.limit(PSTRLEN_LENGTH+PSTRLEN);
			if (message.compareTo(ByteBuffer.wrap(PSTR)) != 0)
			{
				PeerConnectionException e = new PeerConnectionException("Invalid protocol identifier from " + peer);
				logger.log(Level.WARNING, "Invalid HANDSHAKE" , e);
				throw e;
			}
			message.limit(PSTRLEN_LENGTH+PSTRLEN+RESERVED_LENGTH+INFOHASH_LENGTH).position(PSTRLEN_LENGTH+PSTRLEN+RESERVED_LENGTH);
			if (message.compareTo(infoHash) != 0)
			{
				PeerConnectionException e = new PeerConnectionException("Invalid infohash from " + peer);
				logger.log(Level.WARNING, "Invalid HANDSHAKE" , e);
				throw e;
			}
			message.limit(HANDSHAKE_LENGTH).position(PSTRLEN_LENGTH+PSTRLEN+RESERVED_LENGTH+INFOHASH_LENGTH);
			ByteBuffer peerId = ByteBuffer.allocate(PEERID_LENGTH);
			peerId.put(message).rewind();
			
			peer.getSocketChannel().configureBlocking(blocking);	//Reset the channel to whatever blocking-mode it was on before
			logger.log(Level.FINER, "Handshake successfully received from " + peer);
			return peerId;
		}
	}
}
