/**
 * An abstraction layer over the flat torrent byte storage
 */

package torrent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.Environment;
import client.LoggingClient;
import metainfo.InfoDictionary;
import metainfo.SingleFileInfoDict;
import metainfo.MultiFileInfoDict;
import metainfo.TorrentDataFile;

public class TorrentFileSystem
{
	private final InfoDictionary infoDict;
	private final List<TorrentDataFile> files;
	private Long size = 0l;
	Logger logger = LoggingClient.getInstance().logger;

	public TorrentFileSystem(Torrent torrent)
	{
		this.infoDict = torrent.getMetainfo().getInfo();
		this.files = new ArrayList<TorrentDataFile>();
	}
	
	/*
	 * Initialize the file system
	 * Create all necessary files and folders in root download directory
	 */
	public void init() throws FileNotFoundException
	{
		if (infoDict instanceof SingleFileInfoDict)
			openFile(((SingleFileInfoDict) infoDict).getFile(), Environment.getInstance().getRootDownloadDirectory());
		else
		{
			String directoryName = ((MultiFileInfoDict) infoDict).getDirectoryName();
			String baseDirectory = Environment.getInstance().getRootDownloadDirectory() + "/" + directoryName;
			for (TorrentDataFile torrentDataFile : ((MultiFileInfoDict) infoDict).getFiles())
			{				
				String path = baseDirectory + "/" + torrentDataFile.getPath();
				new File(new File(path).getParent()).mkdirs();	//Create all parent directories of the file
				openFile(torrentDataFile, baseDirectory);
			}
		}
		logger.log(Level.INFO, "Initialized torrent file system!");
	}
	
	/*
	 * Open the file and add its size to total size
	 */
	private void openFile(TorrentDataFile torrentDataFile, String baseDirectory) throws FileNotFoundException
	{
		torrentDataFile.open(baseDirectory);
		this.files.add(torrentDataFile);
		this.size += torrentDataFile.getLength();
	}
	
	/*
	 * Read from the filesystem at offsetReq into block
	 * TODO Need to give clearer comments
	 */
	public Long read(ByteBuffer block, Long offsetReq) throws IOException
	{
		Long lengthReq = (long) block.remaining();
		Long readSoFar = 0l;
		Long sumOfLengthAcrossFiles = 0l;
		
		if (offsetReq + lengthReq > getSize())
		{
			TorrentFileSystemException e = new TorrentFileSystemException("Requested offset+length overflows the storage of the TorrentFileSystem");
			logger.log(Level.WARNING, "Was about to buffer-overread", e);
			throw e;
		}
		
		for (TorrentDataFile torrentDataFile : this.files)
		{
			if (torrentDataFile.getOffset() >= offsetReq + lengthReq)	//Break if the length of block has already been traversed across the flat byte storage
				break;
			if (torrentDataFile.getOffset() + torrentDataFile.getLength() < offsetReq)	//Continue if we haven't reached the offset yet in the flat byte storage
				continue;
			
			Long segmentOffset = offsetReq - torrentDataFile.getOffset();
			segmentOffset = segmentOffset > 0 ? segmentOffset : 0;	//Find segment offset within file found
			Long segmentLength = torrentDataFile.getLength() - segmentOffset;
			if (lengthReq - sumOfLengthAcrossFiles < segmentLength)
				segmentLength = lengthReq - sumOfLengthAcrossFiles;	//Find segment length within file found
			sumOfLengthAcrossFiles += segmentLength;	//TODO I guess there is no need of this, replace with readSoFar
			block.limit((int) (readSoFar + segmentLength));
			readSoFar += torrentDataFile.read(block, segmentOffset);	//Read from segmentOffset in file found
			logger.log(Level.FINE, "Read bytes from " + torrentDataFile + ", segment-length: " + segmentLength + ", segment-offset: " + segmentOffset + ", read so far: " + readSoFar);
		}
		
		if (readSoFar < lengthReq)
		{
			TorrentFileSystemException e = new TorrentFileSystemException("Failed to read " + lengthReq + " byte(s) from the TorrentFileSystem");
			logger.log(Level.WARNING, "Buffer under-read", e);
			throw e;
		}
		
		return readSoFar;
	}	
	
	/*
	 * Write the block into the filesystem at offsetReq
	 */
	public Long write(ByteBuffer block, Long offsetReq) throws IOException
	{
		Long lengthReq = (long) block.remaining();
		Long writtenSoFar = 0l;
		Long sumOfLengthAcrossFiles = 0l;
		
		if (offsetReq + lengthReq > getSize())
		{
			TorrentFileSystemException e = new TorrentFileSystemException("Requested offset+length overflows the storage of the TorrentFileSystem");
			logger.log(Level.WARNING, "Was about to buffer-overwrite", e);
			throw e;
		}
		
		for (TorrentDataFile torrentDataFile : this.files)
		{
			//System.err.println("FILESYSTEM write (current file traversing): " + torrentDataFile.getPath());
			//System.err.println("FILESYSTEM write (offset): " + torrentDataFile.getOffset() + ", (length): " + torrentDataFile.getLength());
			//System.err.println("FILESYSTEM write (lengthReq): " + lengthReq + ", (offsetReq): " + offsetReq + ", (sumOfLengthAcrossFiles): " + sumOfLengthAcrossFiles);
			if (torrentDataFile.getOffset() >= offsetReq + lengthReq)	//Break if the length of block has already been traversed across the flat byte storage
				break;
			if (torrentDataFile.getOffset() + torrentDataFile.getLength() < offsetReq)	//Continue if we haven't reached the offset yet in the flat byte storage
				continue;
			
			Long segmentOffset = offsetReq - torrentDataFile.getOffset();
			segmentOffset = segmentOffset > 0 ? segmentOffset : 0;	//Find segment offset within file found
			Long segmentLength = torrentDataFile.getLength() - segmentOffset;
			if (lengthReq - sumOfLengthAcrossFiles < segmentLength)
				segmentLength = lengthReq - sumOfLengthAcrossFiles;	//Find segment length within file found
			sumOfLengthAcrossFiles += segmentLength;
			block.limit((int) (writtenSoFar + segmentLength));
			writtenSoFar += torrentDataFile.write(block, segmentOffset);	//Write block at segmentOffset in file found			
			logger.log(Level.FINE, "Wrote bytes to " + torrentDataFile + ", segment-length: " + segmentLength + ", segment-offset: " + segmentOffset + ", written so far: " + writtenSoFar);
		}
		
		if (writtenSoFar < lengthReq)
		{
			TorrentFileSystemException e = new TorrentFileSystemException("Failed to write " + lengthReq + " byte(s) to the TorrentFileSystem");
			logger.log(Level.WARNING, "Buffer under-write", e);
			throw e;
		}
				
		return writtenSoFar;
	}
	
	public void onTorrentDownloadStopped()	//TODO wait wut?
	{
		onTorrentDownloadCompleted();
	}
	
	public void onTorrentDownloadCompleted()
	{
		logger.log(Level.FINE, "Torrent Download Completed event");
		try
		{
			for (TorrentDataFile file : this.files)
				file.getFileChannel().force(true);
			logger.log(Level.FINER, "Forced wrote unwritten bytes");
			shutdownFileSystem();
		} catch (IOException e)
		{
			logger.log(Level.WARNING, "Failed to shutdown file system or force-writing unwritten bytes", e);
		}		
	}
	
	/*
	 * Close all files
	 */
	public void shutdownFileSystem() throws IOException
	{
		for (TorrentDataFile file : this.files)
			file.close();
		logger.log(Level.FINER, "File system shutdown successful!");
	}
	
	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}
	
	private class TorrentFileSystemException extends IOException
	{
		private static final long serialVersionUID = 1L;
		
		public TorrentFileSystemException(String message)
		{
			super("TorrentFileSystem exception: " + message);
		}
	}
}
