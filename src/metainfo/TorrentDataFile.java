/**
 * This class represents a file in the Multiple file mode of InfoDictionary.
 * A valid file must have a file length and a path.
 */

package metainfo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.LoggingClient;

public class TorrentDataFile
{
	private Long length;
	private String path;
	private String md5sum;
	private Long offset;
	private RandomAccessFile backingFile;
	private FileChannel fileChannel;
	Logger logger = LoggingClient.getInstance().logger;
	
	/**
	 * Returns a File object that can be stored in the files list in MultiFileInfoDict
	 * @param length	The length of the file in bytes
	 * @param path		The path of the file (including filename)
	 * @param md5sum	(Optional) The md5sum of the file
	 * @param offset	The offset in the flat torrent byte-stream this file starts at
	 * @see MultiFileInfoDict
	 */
	public TorrentDataFile(Long length, String path, String md5sum, Long offset)
	{
		this.length = length;
		this.path = path;
		this.md5sum = md5sum;
		this.offset = offset;
	}
	
	/*
	 * Open this data file in baseDirectory
	 */
	public void open(String baseDirectory) throws FileNotFoundException 
	{
		setBackingFile(new RandomAccessFile(baseDirectory + "/" + getPath(), "rw"));
		setFileChannel(getBackingFile().getChannel());
		logger.log(Level.FINE, "Opened " + this);
	}
	
	/*
	 * Close this file's resources
	 */
	public void close() throws IOException 
	{
		getFileChannel().close();
		getBackingFile().close();
		logger.log(Level.FINE, "Closed " + this);
	}
	
	/*
	 * Read from offsetReq in disk into block
	 * Returns number of bytes actually read
	 */
	public Long read(ByteBuffer block, Long offsetReq) throws IOException
	{
		int lengthReq = block.remaining();
		
		if (offsetReq + lengthReq > getLength())
		{
			TorrentDataFileException e = new TorrentDataFileException("Requested offset+length overflows the file while reading " + this);
			logger.log(Level.WARNING, "Was about to buffer-overread", e);
			throw e;
		}
		
		Long read = (long) getFileChannel().read(block, offsetReq);
		if (read < lengthReq)
		{
			TorrentDataFileException e = new TorrentDataFileException("Failed to read " + lengthReq + " byte(s) from file " + this);
			logger.log(Level.WARNING, "Couldn't read number of requested bytes", e);
			throw e;
		}
		
		return read;
	}
	
	/*
	 * Write block into disk from offsetReq
	 * Returns number of bytes actually written
	 */
	public Long write(ByteBuffer block, Long offsetReq) throws IOException
	{
		int lengthReq = block.remaining();
		
		if (offsetReq + lengthReq > getLength())
		{
			TorrentDataFileException e = new TorrentDataFileException("Requested offset+length overflows the file while writing " + this);
			logger.log(Level.WARNING, "Was about to buffer-overwrite", e);
			throw e;
		}
			
		Long written = (long) getFileChannel().write(block, offsetReq);
		if (written < lengthReq)
		{
			TorrentDataFileException e = new TorrentDataFileException("Failed to write " + lengthReq + " byte(s) to file " + this);
			logger.log(Level.WARNING, "Couldn't write number of requested bytes", e);
			throw e;
		}
			
		return written;
	}
	
	public String toString()
	{
		return "{TorrentDataFile->"+"path:"+getPath()+",length:"+getLength()+",offset:"+getOffset()+"}";
	}
	
	public Long getLength() {
		return length;
	}

	public void setLength(Long length) {
		this.length = length;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getMd5sum() {
		return md5sum;
	}

	public void setMd5sum(String md5sum) {
		this.md5sum = md5sum;
	}

	public Long getOffset() {
		return offset;
	}

	public void setOffset(Long offset) {
		this.offset = offset;
	}

	public RandomAccessFile getBackingFile() {
		return backingFile;
	}

	public void setBackingFile(RandomAccessFile backingFile) {
		this.backingFile = backingFile;
	}

	public FileChannel getFileChannel() {
		return fileChannel;
	}

	public void setFileChannel(FileChannel fileChannel) {
		this.fileChannel = fileChannel;
	}

	private class TorrentDataFileException extends IOException
	{
		private static final long serialVersionUID = 1L;
		
		public TorrentDataFileException(String message)
		{
			super("TorrentDataFile exception: " + message);
		}
	}
}
