/**
 * This class represents an InfoDictionary in the Multiple File Mode.
 * A valid MultiFileInfoDict must have a directory name and a list of Files.
 */

package metainfo;

import java.nio.ByteBuffer;
import java.util.List;

public class MultiFileInfoDict extends InfoDictionary
{
	private String directoryName;
	private List<TorrentDataFile> files;
	
	/**
	 * Returns a MultiFileInfoDict object that can be stored in the info field of Metainfo
	 * @param pieceLength			The number of bytes in each piece
	 * @param pieces				The string consisting of the concatenation of all 20-byte SHA1 hash values, one per piece
	 * @param noExternalPeerSource	(Optional) Denotes if external peer source is allowed
	 * @param directoryName			The name of the root directory
	 * @param files					The list of files
	 * @see InfoDictionary, SingleFileInfoDict, Metainfo
	 */
	public MultiFileInfoDict(Long pieceLength, ByteBuffer pieces, Boolean noExternalPeerSource, String directoryName, List<TorrentDataFile> files)
	{
		super(pieceLength, pieces, noExternalPeerSource);
		this.directoryName = directoryName;
		this.files = files;
	}
	
	public String getDirectoryName() {
		return directoryName;
	}

	public void setDirectoryName(String directoryName) {
		this.directoryName = directoryName;
	}
	
	public List<TorrentDataFile> getFiles() {
		return files;
	}

	public void setFiles(List<TorrentDataFile> files) {
		this.files = files;
	}
}
