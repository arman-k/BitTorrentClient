/**
 * This class is intended to be a parser/writer for the metainfo in a .torrent metafile.
 * A valid TorrentFileWriter must have a OutputStream to write to.
 */

package metainfo;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static tools.Util.stringToByteBuffer;

import tools.BencodeWriter;
import tools.Util;

public class TorrentFileWriter 
{
	final static String message = "Metainfo is not properly constructed - "; //A prepended template for all Exception messages in this class
	private final OutputStream out;
	
	/**
	 * Returns a TorrentFileWriter object that can be used to write metainfo to a .torrent metafile.
	 * @param filename			The name of the file to write to (must be a .torrent metafile)
	 * @throws FileNotFoundException	If file is not found
	 */
	public TorrentFileWriter(String filename) throws FileNotFoundException
	{
		this.out = new FileOutputStream(filename);
	}
	
	/**
	 * Returns a TorrentFileWriter object that can be used to write metainfo to a .torrent metafile.
	 * @param stream	An OutputStream to write to (must be a .torrent metafile stream)
	 */
	public TorrentFileWriter(OutputStream stream)
	{
		this.out = stream;
	}
	
	/* 
	 * This is the only method properly tested here
	 * And the only one in actual use I believe
	 * The rest is needed if a torrent file generator is actually implemented
	 */
	/**
	 * Writes a raw metainfo Map that represents the metainfo in the .torrent metafile stream.
	 * A Torrent Client could invoke this method to write metainfo into a .torrent metafile.
	 * This should be used to write the info dictionary which is subsequently used to calculate infohash.
	 * @param rawMetainfo	The Map (raw metainfo/dictionary) to write
	 * @throws IOException	If the file is not properly encoded
	 * @see Metainfo, BencodeWriter, BencodeReader
	 */
	public void writeRawMetainfo(HashMap<ByteBuffer, Object> rawMetainfo) throws IOException
	{
		BencodeWriter writer = new BencodeWriter(out);
		writer.write(rawMetainfo);
		writer.flush();
	}
	
	/**
	 * Decomposes and writes a Metainfo object that represents the metainfo in the .torrent metafile stream.
	 * A Torrent Client could invoke this method to write metainfo to a .torrent metafile.
	 * @param metainfo	The metainfo object to decompose and write
	 * @throws IOException	If the file is not properly encoded
	 * @see Metainfo, BencodeWriter, decomposeMetainfo()
	 */
	public void writeMetainfo(Metainfo metainfo) throws IOException
	{
		BencodeWriter writer = new BencodeWriter(out);
		writer.write(decomposeMetainfo(metainfo));
		writer.flush();
	}
	
	/**
	 * Decomposes a Metainfo object and writes the metainfo to a .torrent metafile stream.
	 * It is intended to be invoked by writeMetainfo().
	 * @return 	Map (dictionary) that represents the metainfo 
	 * @throws IOException	If the file is not properly encoded
	 * @see Metainfo, BencodeWriter, writeMetainfo()
	 */
	private HashMap<ByteBuffer, Object> decomposeMetainfo(Metainfo metainfo) throws IOException
	{
		HashMap<ByteBuffer, Object> rawMetainfo = new HashMap<ByteBuffer, Object>();
		InfoDictionary info = metainfo.getInfo();
		ByteBuffer announce = stringToByteBuffer(metainfo.getAnnounce());
		List<Object> announceList = metainfo.getAnnounceList();
		Long creationDate = Util.ldtToUnixEpoch(metainfo.getCreationDate());	//Convert LocalDateTime to UnixEpoch
		ByteBuffer comment = stringToByteBuffer(metainfo.getComment());
		ByteBuffer createdBy = stringToByteBuffer(metainfo.getCreatedBy());
		ByteBuffer encoding = stringToByteBuffer(metainfo.getEncoding());
		
		//Metainfo must have an info and announce field
		if (info == null)
			throw new MetainfoException(message + "missing 'info'");
		if (announce == null)
			throw new MetainfoException(message + "missing 'announce'");
		
		rawMetainfo.put(stringToByteBuffer("info"), decomposeInfoDictionary(info));	//Decompose InfoDictionary and write to the 'info' field
		rawMetainfo.put(stringToByteBuffer("announce"), announce);
		if (announceList != null)
			rawMetainfo.put(stringToByteBuffer("announce-list"), announceList);
		if (creationDate != null)
			rawMetainfo.put(stringToByteBuffer("creation date"), creationDate);
		if (comment != null)
			rawMetainfo.put(stringToByteBuffer("comment"), comment);
		if (createdBy != null)
			rawMetainfo.put(stringToByteBuffer("created by"), createdBy);
		if (encoding != null)
			rawMetainfo.put(stringToByteBuffer("encoding"), encoding);
				
		return rawMetainfo;
	}
	
	/**
	 * Returns a Map (dictionary), decomposed from an InfoDictionary.
	 * It can be either Single-File or Multi-File mode.
	 * It is intended to be invoked by decomposeMetainfo().
	 * @param info					An InfoDictionary
	 * @return						A Map representing an Info Dictionary
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, SingleFileInfoDict, MultiFileInfoDict, decomposeMetainfo()
	 */
	private HashMap<ByteBuffer, Object> decomposeInfoDictionary(InfoDictionary info) throws IOException
	{
		HashMap<ByteBuffer, Object> infoDict = new HashMap<ByteBuffer, Object>();
		Long pieceLength = info.getPieceLength();
		//This little fucker made us replace String with ByteBuffer in BencodeReader/Writer
		ByteBuffer pieces = info.getPieces();
		Long noExternalPeerSource = privateBoolToInt(info.getNoExternalPeerSource());
		
		/*
		 * InfoDictionary must have a piece length and a pieces String.
		 * The length of pieces must be a multiple of 20 as this is just a concatenation of 20-byte SHA1 hash values
		 */
		if (pieceLength == null)
			throw new MetainfoException(message + "missing 'piece length' in 'info'");
		if (pieces == null)
			throw new MetainfoException(message + "missing 'pieces' in 'info'");
		
		infoDict.put(stringToByteBuffer("piece length"), pieceLength);
		infoDict.put(stringToByteBuffer("pieces"), pieces);
		if (noExternalPeerSource != null)
			infoDict.put(stringToByteBuffer("private"), noExternalPeerSource);
		
		if (info instanceof SingleFileInfoDict)
			return decomposeSingleFileInfoDict((SingleFileInfoDict) info, infoDict);
		return decomposeMultiFileInfoDict((MultiFileInfoDict) info, infoDict);
	}
	
	/**
	 * Returns a Map (dictionary), decomposed from a SingleFileInfoDict.
	 * It is intended to be invoked by decomposeInfoDictionary().
	 * @param info					A SingleFileInfoDict object
	 * @param infoDict				The Map (dictionary) that was partially filled
	 * @return						A Map (dictionary) representing a Single File mode InfoDictionary
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, decomposeInfoDictionary()
	 */
	private HashMap<ByteBuffer, Object> decomposeSingleFileInfoDict(SingleFileInfoDict info, HashMap<ByteBuffer, Object> infoDict) throws IOException
	{
		ByteBuffer fileName = stringToByteBuffer(info.getFile().getPath());		
		Long length = info.getFile().getLength();		
		ByteBuffer md5sum = stringToByteBuffer(info.getFile().getMd5sum());
		
		///SingleFileInfoDict must have a file name and a file length
		if (fileName == null)
			throw new MetainfoException(message + "missing 'name' in single-file mode 'info'");
		if (length == null)
			throw new MetainfoException(message + "missing 'length' in 'info'");
		
		infoDict.put(stringToByteBuffer("name"), fileName);
		infoDict.put(stringToByteBuffer("length"), length);
		if (md5sum != null)
			infoDict.put(stringToByteBuffer("md5sum"), md5sum);
		
		return infoDict;
	}
	
	/**
	 * Returns a Map (dictionary), decomposed from a MultiFileInfoDict.
	 * It is intended to be invoked by decomposeInfoDictionary().
	 * @param info					A MultiFileInfoDict object
	 * @param infoDict				The Map (dictionary) that was partially filled
	 * @return						A Map (dictionary) representing a Multiple File mode InfoDictionary
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, SingleFileInfoDict, decomposeInfoDictionary()
	 */
	private HashMap<ByteBuffer, Object> decomposeMultiFileInfoDict(MultiFileInfoDict info, HashMap<ByteBuffer, Object> infoDict) throws IOException
	{
		ByteBuffer directoryName = stringToByteBuffer(info.getDirectoryName());		
		Object files = filesToDicts(info.getFiles());
		
		//MultiFileInfoDict must have a directory name and a list of files
		if (directoryName == null)
			throw new MetainfoException(message + "missing 'name' in multiple-file mode 'info'");
		if (files == null)
			throw new MetainfoException(message + "'files' in 'info'");
		
		infoDict.put(stringToByteBuffer("name"), directoryName);
		infoDict.put(stringToByteBuffer("files"), files);
		
		return infoDict;
	}
	
	/**
	 * Returns a list of Maps (dictionaries), decomposed from a List of Files.
	 * It is intended to be invoked by decomposeMultiFileInfoDict().
	 * @param files					A list of Files representing the files of the torrent.
	 * @return						A list of Maps (dictionaries)
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, File, decomposeMultiFileInfoDict()
	 */
	private static Object filesToDicts(List<TorrentDataFile> files) throws MetainfoException
	{
		if (files == null)
			return null;
		
		List<HashMap<ByteBuffer, Object>> dicts = new ArrayList<HashMap<ByteBuffer, Object>>();
		
		for (TorrentDataFile file : files)
		{
			HashMap<ByteBuffer, Object> dict = decomposeFile(file);	//Grab each File and decompose it into a Map (dictionary)
			if (dict == null)
				throw new MetainfoException(message + "missing 'file' in 'files' in 'info'");
			dicts.add(dict);	//Add the Map (dictionary) to the list of Maps
		}
		
		return dicts;
	}
	
	/**
	 * Returns a Map (dictionary), decomposed from a File in the Files list of a Multiple File mode dictionary.
	 * It is intended to be invoked by filesToDicts().
	 * @param file					A File representing one file of the torrent.
	 * @return						A Map (dictionary)
	 * @throws MetainfoException	If the metainfo is not properly encoded
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, File, filesToDicts()
	 */
	private static HashMap<ByteBuffer, Object> decomposeFile(TorrentDataFile file) throws MetainfoException
	{
		if (file == null)
			return null;
		
		HashMap<ByteBuffer, Object> dict = new HashMap<ByteBuffer, Object>();
		Long length = file.getLength();
		ByteBuffer md5sum = stringToByteBuffer(file.getMd5sum());
		List<ByteBuffer> path = decomposePath(file.getPath());		
		
		//A File must have a length and a path.
		if (length == null)
			throw new MetainfoException(message + "missing 'length' in 'file' in 'files' in 'info'");
		if (path == null)
			throw new MetainfoException(message + "missing 'path' in 'file' in 'files' in 'info'");
		
		dict.put(stringToByteBuffer("length"), length);
		if (md5sum != null)
			dict.put(stringToByteBuffer("md5sum"), md5sum);
		dict.put(stringToByteBuffer("path"), path);
		
		return dict;
	}
	
	/**
	 * Returns a List of Strings that represent the file path (including the filename), separated by a '/'
	 * It is built by decomposing the concatenated 'path' field in File
	 * It is intended to be invoked by decomposeFile().
	 * @param pathString	A string representing the concatenated path of the file
	 * @return			A List of Strings representing the path of the file
	 * @see Metainfo, InfoDictionary, MultiFileInfoDict, File, decomposeFile()
	 */
	private static List<ByteBuffer> decomposePath(String pathString)
	{
		if (pathString == null)
			return null;
		String[] paths = pathString.split("/");
		ArrayList<ByteBuffer> pathList = new ArrayList<ByteBuffer>();
		for (String path : paths)
			pathList.add(stringToByteBuffer(path));
		
		return pathList;
	}
	
	/**
	 * Returns an integer, converting from a Boolean representing the 'private' field in an info dictionary.
	 * It is intended to be invoked by decomposeInfoDictionary().
	 * @param priv	A Boolean representing the 'private' field of an info dictionary.
	 * @return		The corresponding integer value of priv
	 * @see Metainfo, InfoDictionary, decomposeInfoDictionary()
	 */
	private static Long privateBoolToInt(Object priv)
	{
		if (priv == null)
			return null;
		if ((Boolean) priv)
			return 1l;
		return 0l;
	}
}
