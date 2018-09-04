/**
 * This class represents an InfoDictionary in the Single File Mode.
 * A valid MultiFileInfoDict must have a file name and a file length.
 */

package metainfo;

import java.nio.ByteBuffer;

public class SingleFileInfoDict extends InfoDictionary
{
	private TorrentDataFile file;
	
	/**
	 * Returns a SingleFileInfoDict object that can be stored in the info field of Metainfo
	 * @param pieceLength			The number of bytes in each piece
	 * @param pieces				The string consisting of the concatenation of all 20-byte SHA1 hash values, one per piece
	 * @param noExternalPeerSource	(Optional) Denotes if external peer source is allowed
	 */
	public SingleFileInfoDict(Long pieceLength, ByteBuffer pieces, Boolean noExternalPeerSource, TorrentDataFile file)
	{
		super(pieceLength, pieces, noExternalPeerSource);
		this.file = file;
	}

	public TorrentDataFile getFile() {
		return file;
	}

	public void setFile(TorrentDataFile file) {
		this.file = file;
	}
}
