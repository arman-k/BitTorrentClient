/**
 * Peer message encoder
 */
package peer;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class PeerMessage
{
	public final static byte CHOKE_ID = 0;
	public final static byte UNCHOKE_ID = 1;
	public final static byte INTERESTED_ID = 2;
	public final static byte NOT_INTERESTED_ID = 3;
	public final static byte HAVE_ID = 4;
	public final static byte BITFIELD_ID = 5;
	public final static byte REQUEST_ID = 6;
	public final static byte PIECE_ID = 7;
	public final static byte CANCEL_ID = 8;
	public final static byte PORT_ID = 9;
	public final static int BYTE_SIZE = 1;
	public final static int SHORT_SIZE = 2;
	public final static int INT_SIZE = 4;
	public final static int LENGTH_SIZE = INT_SIZE;
	public final static int TYPE_SIZE = BYTE_SIZE;
	public final static int BASE_SIZE = LENGTH_SIZE + TYPE_SIZE;
	public final static int KEEP_ALIVE_BASE_SIZE = LENGTH_SIZE;
	public final static int CHOKE_BASE_SIZE = BASE_SIZE;
	public final static int UNCHOKE_BASE_SIZE = BASE_SIZE;
	public final static int INTERESTED_BASE_SIZE = BASE_SIZE;
	public final static int NOT_INTERESTED_BASE_SIZE = BASE_SIZE;
	public final static int HAVE_BASE_SIZE = BASE_SIZE + INT_SIZE;
	public final static int BITFIELD_BASE_SIZE = BASE_SIZE;
	public final static int REQUEST_BASE_SIZE = BASE_SIZE + 3*INT_SIZE;
	public final static int PIECE_BASE_SIZE = BASE_SIZE + 2*INT_SIZE;
	public final static int CANCEL_BASE_SIZE = BASE_SIZE + 3*INT_SIZE;
	public final static int PORT_BASE_SIZE = BASE_SIZE + SHORT_SIZE;
	
	
	public static ByteBuffer encodeKeepAlive()
	{
		return (ByteBuffer) ByteBuffer.allocate(KEEP_ALIVE_BASE_SIZE)
				.putInt(0).rewind();
	}
	
	public static ByteBuffer encodeChoke()
	{
		return (ByteBuffer) ByteBuffer.allocate(CHOKE_BASE_SIZE)
				.putInt(BYTE_SIZE).put(CHOKE_ID).rewind();
	}
	
	public static ByteBuffer encodeUnchoke()
	{
		return (ByteBuffer) ByteBuffer.allocate(UNCHOKE_BASE_SIZE)
				.putInt(BYTE_SIZE).put(UNCHOKE_ID).rewind();
	}
	
	public static ByteBuffer encodeInterested()
	{
		return (ByteBuffer) ByteBuffer.allocate(INTERESTED_BASE_SIZE)
				.putInt(BYTE_SIZE).put(INTERESTED_ID).rewind();
	}
	
	public static ByteBuffer encodeNotInterested()
	{
		return (ByteBuffer) ByteBuffer.allocate(NOT_INTERESTED_BASE_SIZE)
				.putInt(BYTE_SIZE).put(NOT_INTERESTED_ID).rewind();
	}
	
	public static ByteBuffer encodeHave(int index)
	{
		return (ByteBuffer) ByteBuffer.allocate(HAVE_BASE_SIZE)
				.putInt(BYTE_SIZE+INT_SIZE).put(HAVE_ID).putInt(index).rewind();
	}
	
	public static ByteBuffer encodeBitfield(BitSet bitfield)
	{
		byte[] bitfieldArray = new byte[(bitfield.size()+7)/8];
		
		for (int bitIndex = bitfield.nextSetBit(0); 
				bitIndex >=0; bitIndex = bitfield.nextSetBit(bitIndex+1))	//TODO move to Util
		{
			bitfieldArray[bitIndex/8] |= (1 << (7 - (bitIndex%8)));	//Little-endian bits to big-endian bits
		}
		
		return (ByteBuffer) ByteBuffer.allocate(BITFIELD_BASE_SIZE + bitfieldArray.length)
				.putInt(BYTE_SIZE+bitfieldArray.length).put(BITFIELD_ID)
				.put(ByteBuffer.wrap(bitfieldArray)).rewind();
	}
	
	public static ByteBuffer encodeRequest(int index, int begin, int length)
	{
		return (ByteBuffer) ByteBuffer.allocate(REQUEST_BASE_SIZE)
				.putInt(BYTE_SIZE+INT_SIZE*3).put(REQUEST_ID).putInt(index)
				.putInt(begin).putInt(length).rewind();
	}
	
	public static ByteBuffer encodePiece(int index, int begin, ByteBuffer block)
	{
		return (ByteBuffer) ByteBuffer.allocate(PIECE_BASE_SIZE + block.array().length)
				.putInt(BYTE_SIZE+INT_SIZE*2+block.array().length).put(PIECE_ID)
				.putInt(index).putInt(begin).put(block).rewind();
	}
	
	public static ByteBuffer encodeCancel(int index, int begin, int length)
	{
		return (ByteBuffer) ByteBuffer.allocate(CANCEL_BASE_SIZE)
				.putInt(BYTE_SIZE+INT_SIZE*3).put(CANCEL_ID).putInt(index)
				.putInt(begin).putInt(length).rewind();
	}
	
	public static ByteBuffer encodePort(int port)
	{
		return (ByteBuffer) ByteBuffer.allocate(PORT_BASE_SIZE)
				.putInt(BYTE_SIZE+SHORT_SIZE).put(PORT_ID).putInt(port).rewind();
	}
	
	public static class PeerMessageException extends IllegalArgumentException
	{
		private static final long serialVersionUID = 1L;
		
		public PeerMessageException(String msg)
		{
			super("Peer message exception: " + msg);
		}
	}
}
