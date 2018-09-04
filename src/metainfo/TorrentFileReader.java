/**
 * This class is intended to be a parser/reader for the metainfo in a .torrent metafile.
 * A valid TorrentFileReader must have a InputStream to read from.
 */

package metainfo;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import tools.BencodeReader;
import tools.Util;
import static tools.Util.stringToByteBuffer;
import static tools.Util.byteBufferToString;

public class TorrentFileReader 
{
	final static String message = "Metainfo is not properly constructed - "; //A prepended template for all Exception messages in this class
	private final InputStream in;
	private HashMap<ByteBuffer, Object> rawMetainfo;
	private Metainfo metainfo;

	/**
	 * Returns a TorrentFileReader object that can be used to read the metainfo from a .torrent metafile.
	 * @param filename			The name of the file to read from (must be a .torrent metafile)
	 * @throws FileNotFoundException	If file is not found
	 */
	public TorrentFileReader(String filename) throws FileNotFoundException
	{
		this.in = new FileInputStream(filename);
	}

	/**
	 * Returns a TorrentFileReader object that can be used to read the metainfo from a .torrent metafile.
	 * @param stream	An InputStream to read from (must be a .torrent metafile stream)
	 */
	public TorrentFileReader(InputStream stream)
	{
		this.in = stream;
	}
	
	/**
	 * Returns a Metainfo object that represents the metainfo in the .torrent metafile stream.
	 * A Torrent Client should invoke this method to craft a Metainfo object out of a .torrent metafile.
	 * @return		A Metainfo object that can be used by a Torrent Client
	 * @throws IOException	If the file is not properly encoded
	 * @see Metainfo, BencodeReader, buildMetainfo()
	 */
	public Metainfo readMetainfo() throws IOException 
	{
		read();				
		return metainfo;
	}
	
	/**
	 * Returns a raw Map (dictionary) that represents the metainfo in the .torrent metafile stream.
	 * A Torrent Client can also invoke this method to handle the metainfo, but validity of the file cannot be guaranteed.
	 * This should be used if the order of the dictionary key-value pairs matters. 
	 * The raw version of metainfo should also be used to calculate infohash.
	 * @return		A Metainfo object that can be used by a Torrent Client
	 * @throws IOException	If the file is not properly encoded
	 * @see Metainfo, BencodeReader, buildMetainfo()
	 */
	public HashMap<ByteBuffer, Object> readRawMetainfo() throws IOException 
	{
		read();		
		return rawMetainfo;
	}
	
	
	/**
	 * Constructs a BencodeReader object and invokes read() on it to read from the input file.
	 * It builds a Metainfo object out of the returned Map and keeps both versions of it.
	 * @throws IOException	If the file is not properly encoded
	 * @see Metainfo, BencodeReader, buildMetainfo()
	 */
	private void read() throws IOException
	{
		if (rawMetainfo == null)
			rawMetainfo = new BencodeReader(in).read(); //Read the raw metainfo from the stream
		if (metainfo == null)
			metainfo = buildMetainfo(rawMetainfo);	//Build a Metainfo object out of the raw metainfo
	}
	
	
	/**
	 * Returns a Metainfo object, built out of raw metainfo decoded from a .torrent metafile stream.
	 * It is intended to be invoked by read().
	 * @param rawMetainfo			A Map of raw metainfo
	 * @return						A Metainfo object that can be used by a Torrent Client
	 * @throws IOException 
	 * @throws NoSuchAlgorithmException 
	 * @see Metainfo, read()
	 */
	@SuppressWarnings("unchecked")
	private Metainfo buildMetainfo(HashMap<ByteBuffer, Object> rawMetainfo) throws IOException
	{
		HashMap<ByteBuffer, Object> rawInfo = (HashMap<ByteBuffer, Object>) rawMetainfo.get(stringToByteBuffer("info"));
		InfoDictionary info = buildInfoDictionary(rawInfo);	//Build an info dictionary from the 'info' field
		String announce = byteBufferToString(rawMetainfo.get(stringToByteBuffer("announce")));
		List<Object> announceList = (List<Object>) rawMetainfo.get(stringToByteBuffer("announce-list"));
		LocalDateTime creationDate = Util.unixEpochToLDT(rawMetainfo.get(stringToByteBuffer("creation date")));	//Convert unix epoch to LocalDateTime
		String comment = byteBufferToString(rawMetainfo.get(stringToByteBuffer("comment")));
		String createdBy = byteBufferToString(rawMetainfo.get(stringToByteBuffer("created by")));
		String encoding = byteBufferToString(rawMetainfo.get(stringToByteBuffer("encoding")));
		final String hashingAlgorithm = "SHA-1";
		ByteBuffer infoHash = null;
		
		//Metainfo must have an info and announce field
		if (info == null)
			throw new MetainfoException(message + "missing 'info'");
		if (announce == null)
			throw new MetainfoException(message + "missing 'announce'");
		
		try	{
			infoHash = ByteBuffer.wrap(Util.buildHash(rawInfo, hashingAlgorithm));
		} catch (NoSuchAlgorithmException e) {
			System.err.println(e + ": invalid algorithm - " + hashingAlgorithm);
		}
		
		return new Metainfo(info, announce, announceList, creationDate, comment, createdBy, encoding, infoHash);
	}
	
	/**
	 * Returns an InfoDictionary, built out of the dictionary value referred to by the 'info' key in raw metainfo.
	 * It is either a SingleFileInfoDict or MultiFileInfoDict (depending on the mode).
	 * It is intended to be invoked by buildMetainfo().
	 * @param info					A Map representing a raw Info Dictionary
	 * @return						An InfoDictionary intended to be stored in the info field of Metainfo
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, SingleFileInfoDict, MultiFileInfoDict, buildMetainfo()
	 */
	private static InfoDictionary buildInfoDictionary(HashMap<ByteBuffer, Object> info) throws MetainfoException
	{
		if (info == null)
			return null;
		
		Long pieceLength = (Long) info.get(stringToByteBuffer("piece length"));
		//This little fucker made us replace String with ByteBuffer in BencodeReader/Writer
		ByteBuffer pieces = (ByteBuffer) info.get(stringToByteBuffer("pieces"));
		Boolean noExternalPeerSource = privateIntToBool(info.get(stringToByteBuffer("private")));	//Convert the private integer field to a Boolean
		
		/*
		 * InfoDictionary must have a piece length and a pieces String.
		 * The length of pieces must be a multiple of 20 as this is just a concatenation of 20-byte SHA1 hash values
		 */
		if (pieceLength == null)
			throw new MetainfoException(message + "missing 'piece length' in 'info'");
		if (pieces == null)
			throw new MetainfoException(message + "missing 'pieces' in 'info'");
		if (pieces.array().length % 20 != 0)
			throw new MetainfoException(message + "length of pieces is not a multiple of 20");
		
		/*
		ByteBuffer pieces[] = new ByteBuffer[hashBuffer.array().length / 20];
		for (int i = 0; i < pieces.length; i++)
		{
			pieces[i] = ByteBuffer.allocate(20);
			for (int j = 0; j < 20; j++)
			{				
				pieces[i].put(j, hashBuffer.get(i*20+j));
			}
		}*/
		
		//If InfoDictionary contains the 'files' key, it is in Multiple File mode. Otherwise it is in Single File mode.
		if (info.containsKey(stringToByteBuffer("files")))
			return buildMultiFileInfoDict(info, pieceLength, pieces, noExternalPeerSource);
		return buildSingleFileInfoDict(info, pieceLength, pieces, noExternalPeerSource);
	}
	
	/**
	 * Returns a SingleFileInfoDict, built out of the Single File mode dictionary value referred to by the 'info' key in raw metainfo.
	 * It is intended to be invoked by buildInfoDictionary().
	 * @param info					A Map representing a raw Info Dictionary in Single File mode
	 * @param pieceLength			The number of bytes in each piece
	 * @param pieces				The string consisting of the concatenation of all 20-byte SHA1 hash values, one per piece
	 * @param noExternalPeerSource	Denotes if external peer source is allowed
	 * @return						A SingleFileInfoDict object that can be stored in the 'info' field of Metainfo
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, buildInfoDictionary()
	 */
	private static SingleFileInfoDict buildSingleFileInfoDict(
			HashMap<ByteBuffer, Object> info, Long pieceLength, ByteBuffer pieces, Boolean noExternalPeerSource) 
					throws MetainfoException
	{
		String fileName = byteBufferToString(info.get(stringToByteBuffer("name")));		
		Long length = (Long) info.get(stringToByteBuffer("length"));		
		String md5sum = byteBufferToString(info.get(stringToByteBuffer("md5sum")));
		
		///SingleFileInfoDict must have a file name and a file length
		if (fileName == null)
			throw new MetainfoException(message + "missing 'name' in single-file mode 'info'");
		if (length == null)
			throw new MetainfoException(message + "missing 'length' in 'info'");
		
		return new SingleFileInfoDict(pieceLength, pieces, noExternalPeerSource, new TorrentDataFile(length, fileName, md5sum, 0l));
	}
	
	/**
	 * Returns a MultiFileInfoDict, built out of the Multiple File mode dictionary value referred to by the 'info' key in raw metainfo.
	 * It is intended to be invoked by buildInfoDictionary().
	 * @param info					A Map representing a raw Info Dictionary in Multiple File mode
	 * @param pieceLength			The number of bytes in each piece
	 * @param pieces				The string consisting of the concatenation of all 20-byte SHA1 hash values, one per piece
	 * @param noExternalPeerSource	Denotes if external peer source is allowed
	 * @return						A MultiFileInfoDict object that can be stored in the 'info' field of Metainfo
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, SingleFileInfoDict, buildInfoDictionary()
	 */
	private static MultiFileInfoDict buildMultiFileInfoDict(
			HashMap<ByteBuffer, Object> info, Long pieceLength, ByteBuffer pieces, Boolean noExternalPeerSource) 
					throws MetainfoException
	{
		String directoryName = byteBufferToString(info.get(stringToByteBuffer("name")));		
		List<TorrentDataFile> files = dictsToFiles(info.get(stringToByteBuffer("files")));	//Convert the dictionary of raw file representations to a List of File objects
		
		//MultiFileInfoDict must have a directory name and a list of files
		if (directoryName == null)
			throw new MetainfoException(message + "missing 'name' in multiple-file mode 'info'");
		if (files == null)
			throw new MetainfoException(message + "'files' in 'info'");
		
		return new MultiFileInfoDict(pieceLength, pieces, noExternalPeerSource, directoryName, files);
	}
	
	/**
	 * Returns a list of Files, built out of the dictionaries in the 'files' field of a Multiple File mode dictionary.
	 * It is intended to be invoked by buildMultiFileInfoDict().
	 * @param dicts					A list of dictionaries representing the files of the torrent.
	 * @return						A list of File objects that can be stored in the 'files' list of MultiFileInfoDict
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, File, buildMultiFileInfoDict()
	 */
	@SuppressWarnings("unchecked")
	private static List<TorrentDataFile> dictsToFiles(Object dicts) throws MetainfoException
	{
		if (dicts == null)
			return null;
		ArrayList<TorrentDataFile> files = new ArrayList<TorrentDataFile>();
		Long offset = 0l;
		
		for (HashMap<ByteBuffer, Object> element : (List<HashMap<ByteBuffer, Object>>) dicts)
		{
			TorrentDataFile file = buildFile(element, offset);	//Grab each dictionary and build a File out of it			
			if (file == null)
				throw new MetainfoException(message + "missing 'file' in 'files' in 'info'");
			offset += file.getLength();
			files.add(file);	//Add the File to the list of Files
		}
		
		return files;
	}
	
	/**
	 * Returns a File object, built out of one file in the Files list of a Multiple File mode dictionary.
	 * It is intended to be invoked by dictsToFiles().
	 * @param file					A dictionary representing one file of the torrent.
	 * @return						A File object that can be a part of the 'files' list in MultiFileInfoDict
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, File, dictsToFiles()
	 */
	private static TorrentDataFile buildFile(HashMap<ByteBuffer, Object> file, Long offset) throws MetainfoException
	{
		if (file == null)
			return null;
		Long length = (Long) file.get(stringToByteBuffer("length"));
		String md5sum = byteBufferToString(file.get(stringToByteBuffer("md5sum")));
		String path = buildPath(file.get(stringToByteBuffer("path")));
		
		//A File must have a length and a path.
		if (length == null)
			throw new MetainfoException(message + "missing 'length' in 'file' in 'files' in 'info'");
		if (path == null)
			throw new MetainfoException(message + "missing 'path' in 'file' in 'files' in 'info'");
		
		return new TorrentDataFile(length, path, md5sum, offset);
	}
	
	/**
	 * Returns a file path (including the filename), built by concatenating the list of strings in the 'path' field of a file.
	 * It is intended to be invoked by buildFile().
	 * @param pathList	The list of strings that represent the path of the file
	 * @return			The concatenated path (including the filename)
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, File, buildFile()
	 */
	@SuppressWarnings("unchecked")
	private static String buildPath(Object pathList)
	{
		if (pathList == null)
			return null;
		StringBuilder path = new StringBuilder();
		for (ByteBuffer element : (List<ByteBuffer>) pathList)
		{
			path.append(byteBufferToString(element));
			path.append("/");
		}
		path.deleteCharAt(path.length()-1);
			
		return path.toString();
	}
	
	/**
	 * Returns a boolean value, converting from an integer representing the 'private' field in an info dictionary.
	 * It is intended to be invoked by buildInfoDictionary().
	 * @param priv	An integer representing the 'private' field of an info dictionary.
	 * @return		The corresponding boolean value of priv that can be stored in the 'noExternalPeerSource' field of an InfoDictionary.
	 * @see Metainfo, InfoDictionary, buildInfoDictionary()
	 */
	private static Boolean privateIntToBool(Object priv)
	{
		if (priv == null)
			return null;
		if ((Long) priv == 1)
			return true;
		return false;
	}
}
