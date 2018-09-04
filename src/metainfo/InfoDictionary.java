/**
 * This abstract class represents an InfoDictionary. It must be inherited by either SingleFileInfoDict or MultiFileInfoDict.
 * A valid InfoDictionary must have a piece length, a string of pieces and other fields as necessitated by its child class. 
 */

package metainfo;

import java.nio.ByteBuffer;

public abstract class InfoDictionary 
{
	protected Long pieceLength;
	protected ByteBuffer pieces;
	protected Boolean noExternalPeerSource;
	
	/**
	 * Returns an InfoDictionary object that can be stored in the info field of Metainfo
	 * Must be sub-classed by either SingleFileInfoDict or MultiFileInfoDict
	 * @param pieceLength			The number of bytes in each piece
	 * @param pieces				The string consisting of the concatenation of all 20-byte SHA1 hash values, one per piece
	 * @param noExternalPeerSource	(Optional) Denotes if external peer source is allowed
	 */
	public InfoDictionary(Long pieceLength, ByteBuffer pieces, Boolean noExternalPeerSource)
	{
		this.pieceLength = pieceLength;
		this.pieces = pieces;
		this.noExternalPeerSource = noExternalPeerSource;
	}
	
	public Long getPieceLength() {
		return pieceLength;
	}
	
	public void setPieceLength(Long pieceLength) {
		this.pieceLength = pieceLength;
	}
	
	public ByteBuffer getPieces() {
		return pieces;
	}	
	
	public void setPieces(ByteBuffer pieces)
	{
		this.pieces = pieces;
	}
	
	public Boolean getNoExternalPeerSource() {
		return noExternalPeerSource;
	}
	
	public void setNoExternalPeerSource(Boolean noExternalPeerSource) {
		this.noExternalPeerSource = noExternalPeerSource;
	}
}
