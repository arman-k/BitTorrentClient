/**
 * This class represent the metainfo in a .torrent metafile.
 * A valid Metainfo must have an InfoDictionary and an announce URL.
 */

package metainfo;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;

public class Metainfo 
{
	private InfoDictionary info;
	private String announce;
	private List<Object> announceList;
	private LocalDateTime creationDate;
	private String comment;
	private String createdBy;
	private String encoding;
	private ByteBuffer infoHash;
	
	/**
	 * Returns a Metainfo object that can be used by a Torrent Client.
	 * @param info			A dictionary that describes the file(s) of the torrent. There are two possible forms: one for the case of a 'single-file' torrent with no directory structure, and one for the case of a 'multi-file' torrent
	 * @param announce		The announce URL of the tracker
	 * @param announceList	(Optional) This is an extension to the official specification, offering backwards-compatibility
	 * @param creationDate	(Optional) The creation time of the torrent
	 * @param comment		(Optional) Free-form textual comments of the author
	 * @param createdBy		(Optional) Name and version of the program used to create the .torrent metafile
	 * @param encoding		(Optional) The string encoding format used to generate the pieces part of the info dictionary in the .torrent metafile
	 */
	public Metainfo(InfoDictionary info, String announce, 
			List<Object> announceList, LocalDateTime creationDate,
			String comment, String createdBy, String encoding, ByteBuffer infoHash)
	{
		this.info = info;
		this.announce = announce;
		this.announceList = announceList;
		this.creationDate = creationDate;
		this.comment = comment;
		this.createdBy = createdBy;
		this.encoding = encoding;
		this.infoHash = infoHash;
	}

	public InfoDictionary getInfo() {
		return info;
	}

	public void setInfo(InfoDictionary info) {
		this.info = info;
	}

	public String getAnnounce() {
		return announce;
	}

	public void setAnnounce(String announce) {
		this.announce = announce;
	}

	public List<Object> getAnnounceList() {
		return announceList;
	}

	public void setAnnounceList(List<Object> announceList) {
		this.announceList = announceList;
	}

	public LocalDateTime getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(LocalDateTime creationDate) {
		this.creationDate = creationDate;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	

	public ByteBuffer getInfoHash() {
		return infoHash;
	}

	public void setInfoHash(ByteBuffer infoHash) {
		this.infoHash = infoHash;
	}
}
