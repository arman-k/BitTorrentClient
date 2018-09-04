package metainfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.LoggingClient;
import tools.Util;
import torrent.TorrentFileSystem;

public class Piece
{
	private ByteBuffer hash;
	private ByteBuffer data;
	private Long index;
	private Long offset;
	private Long length;
	private TorrentFileSystem torrentFileSystem;
	private BitSet bytesAvailable;
	private boolean available = false;
	private final String hashingAlgorithm = "SHA-1";
	Logger logger = LoggingClient.getInstance().logger;
	
	public Piece(TorrentFileSystem torrentFileSystem, ByteBuffer hash, Long index, Long length, Long offset)
	{
		this.torrentFileSystem = torrentFileSystem;
		this.hash = hash;
		this.index = index;
		this.length = length;
		this.offset = offset;
	}
	
	/*
	 * Read data from this piece from offsetReq upto offsetReq+lengthReq
	 */
	public ByteBuffer read(Long offsetReq, Long lengthReq) throws IOException
	{
		if (!isAvailable())
		{
			IOException e = new IOException(this + " is not available yet!");
			logger.log(Level.WARNING, "Tried to read unavailable piece", e);
			throw e;
		}
			
		if (offsetReq + lengthReq > getLength())
		{
			IllegalArgumentException e = new IllegalArgumentException("Requested offset+length overflows the length of " + this);
			logger.log(Level.WARNING, "Was about to buffer-overread", e);
			throw e;
		}
		
		ByteBuffer block = ByteBuffer.allocate(lengthReq.intValue());
		Long read = getTorrentFileSystem().read(block, getOffset() + offsetReq);	//Read from disk
		if (read < lengthReq)
		{
			IOException e = new IOException("Failed to read " + lengthReq + " byte(s) from " + this);
			logger.log(Level.WARNING, "Couldn't read number of requested bytes", e);
			throw e;
		}
			
		block.rewind();
		//block.limit(read.intValue()); //TODO no need for this
		
		return block;
	}
	
	/*
	 * Buffered-write the block into this piece from offsetReq
	 * Data is flushed to disk when all the blocks are available and hash is verified
	 */
	public synchronized void write(ByteBuffer block, Long offsetReq) throws IOException
	{
		if (getData() == null)
		{
			setData(ByteBuffer.allocate(getLength().intValue()));
			setBytesAvailable(new BitSet(getLength().intValue()));
			logger.log(Level.INFO, "Initialized data buffer for " + this);
		}
		if (offsetReq + block.remaining() > getLength())
		{
			IOException e = new IOException("Requested length+offset overflows the size of " + this);
			logger.log(Level.WARNING, "Was about to buffer-overwrite", e);
			throw e;
		}
		
		block.mark();
		getData().position(offsetReq.intValue());
		getData().put(block);
		block.reset();
		getBytesAvailable().set(offsetReq.intValue(), ((Long) (offsetReq + block.remaining())).intValue());
		
		logger.log(Level.FINER, "Writing to offset " + offsetReq + " of length " + block.remaining() + " at " + this);
		if (getBytesAvailable().cardinality() == getLength())	//If all the bytes of the piece has been downloaded
		{
			logger.log(Level.FINE, this + " download completed!");
			getData().rewind();
			if (verifyHash())
			{
				logger.log(Level.FINE, this + " hash verified!");
				Long written = getTorrentFileSystem().write(getData(), getOffset());	//Flush data to disk
				if (written < getLength())
				{
					IOException e = new IOException("Failed to write " + getLength() + " byte(s) to " + this);
					logger.log(Level.WARNING, "Couldn't write number of requested bytes", e);
					throw e;
				}
				setAvailable(true);	//Set piece availability to true
			}
			setData(null);	//Discard buffer for garbage-collection
			setBytesAvailable(null);
		}
	}
	
	public boolean verifyHash() throws IOException
	{
		if (isAvailable())
		{
			logger.log(Level.FINE, this + " hash already verified!");
			return true;
		}
		
		try	{
			return Arrays.equals(
					Util.buildHash(getData(), this.hashingAlgorithm), 
					getHash().array());
		} catch (NoSuchAlgorithmException e) {
			logger.log(Level.WARNING, "Invalid hashing algorithm - " + this.hashingAlgorithm, e);
			return false;
		}
	}
	
	public String toString()
	{
		return "{Piece->index:"+getIndex()+",offset:"+getOffset()+",length:"+getLength()+"}";
	}

	public ByteBuffer getData() {
		return data;
	}

	public void setData(ByteBuffer data) {
		this.data = data;
	}

	public ByteBuffer getHash() {
		return hash;
	}

	public void setHash(ByteBuffer hash) {
		this.hash = hash;
	}

	public Long getIndex() {
		return index;
	}

	public void setIndex(Long index) {
		this.index = index;
	}

	public Long getOffset() {
		return offset;
	}

	public void setOffset(Long offset) {
		this.offset = offset;
	}

	public Long getLength() {
		return length;
	}

	public void setLength(Long length) {
		this.length = length;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public BitSet getBytesAvailable() {
		return bytesAvailable;
	}

	public void setBytesAvailable(BitSet bytesAvailable) {
		this.bytesAvailable = bytesAvailable;
	}

	public TorrentFileSystem getTorrentFileSystem() {
		return torrentFileSystem;
	}

	public void setTorrentFileSystem(TorrentFileSystem torrentFileSystem) {
		this.torrentFileSystem = torrentFileSystem;
	}
}
