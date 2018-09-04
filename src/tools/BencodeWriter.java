/**
 * This class is intended to be a parser/encoder for Bencoded data.
 * Bencoding is a way to specify and organize data in a terse format.
 * It supports the following types: byte strings, integers, lists and dictionaries.
 */

package tools;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

public class BencodeWriter
{
	private final BufferedOutputStream out;
	
	/**
	 * Returns a BencodeWriter object that can be used to write bencoded data types.
	 * OutputStream is wrapped with BufferedOutputStream for efficient I/O.
	 * @param output	The OutputStream to read from
	 */
	public BencodeWriter(OutputStream output)
	{
		this.out = new BufferedOutputStream(output);
	}
	
	/**
	 * Writes bencoded metainfo by parsing a raw metainfo Map or Metainfo object.
	 * This is just a wrapper around writeDictionary(). It is recommended to call this method instead,
	 * if you want to write an entire bencoded metainfo file such as .torrent.
	 * @param data	The data to bencode and write
	 * @throws IOException	If data is not a dictionary (Map)
	 * @see metainfo.Metainfo
	 */
	@SuppressWarnings("unchecked")
	public void write(Object data) throws IOException
	{
		//The data to be written must be a dictionary (Map)
		if (data instanceof HashMap<?,?>)
			writeDictionary((HashMap<String, Object>) data);
		else
			throw new BencodeException("Data is not a dictionary.");
	}
	
	/**
	 * Writes a ByteBuffer representing a bencoded String
	 * ByteBuffer is used because Java Strings for low-level I/O is a pain in the ass.
	 * @param str	The String to bencode and write
	 * @throws IOException	If the bencoded String is invalid
	 */
	public void writeString(ByteBuffer str) throws IOException
	{
		byte array[] = str.array();	//Fetch the byte array that backs the ByteBuffer
		out.write(Integer.toString(array.length).getBytes(Charset.forName("UTF-8")));	//Convert the length of the byte array to a String and fetch the raw decimal characters
		out.write(':');	//Write a colon to separate the length of the string and the actual string
		for (int i = 0; i < array.length; i++)	//Write out the string
		{
			out.write(array[i]);
		}
	}
	
	/**
	 * Writes a Long representing a bencoded Integer.
	 * @param value	The Integer to bencode and write
	 * @throws IOException	If the bencoded Integer is invalid
	 */
	public void writeInteger(Long value) throws IOException
	{
		out.write('i');	//A bencoded Integer must start with 'i'
		out.write(Long.toString(value).getBytes(Charset.forName("UTF-8")));	//Convert the value to a String and fetch the raw decimal characters
		out.write('e');	//A bencoded Integer must end with 'e'
	}
	
	/**
	 * Writes a List representing a bencoded List. Valid values include bencoded Strings, Integers, Lists or Dictionaries
	 * @param list	The List to bencode and write
	 * @throws IOException	If the bencoded List is invalid
	 */
	public void writeList(List<Object> list) throws IOException
	{
		out.write('l');	//A bencoded List must start with 'l'
		for (Object e : list)
			writeToken(e);
		out.write('e');	//A bencoded List must end with 'e'
	}
	
	/**
	 * Writes a Map (dictionary) representing a bencoded Dictionary. Keys must be bencoded Strings. 
	 * Valid values include bencoded Strings, Integers, Lists or Dictionaries
	 * @param dict	The dictionary (Map) to bencode and write
	 * @throws IOException	If the bencoded Dictionary is invalid
	 */
	@SuppressWarnings("unchecked")
	public void writeDictionary(HashMap<String, Object> dict) throws IOException
	{
		out.write('d');	//A bencoded Dictionary must start with 'd'
		for (Object elem : dict.entrySet())
		{
			HashMap.Entry<ByteBuffer, Object> entry = (HashMap.Entry<ByteBuffer, Object>) elem;	//Get every entry from the Map through the entrySet() view
			writeString((ByteBuffer) entry.getKey());
			writeToken(entry.getValue());
		}			
		out.write('e');	//A bencoded Dictionary must end with 'e'
	}
	
	/**
	 * Writes a token representing a bencoded data type. This includes bencoded Strings, Integers, Lists and Dictionaries.
	 * @param token	The token to bencode and write
	 * @throws IOException	If the bencoded Token is invalid
	 * @see readString(), readInteger(), readList(), readDictionary()
	 */
	@SuppressWarnings("unchecked")
	public void writeToken(Object token) throws IOException
	{
		if (token instanceof ByteBuffer)
			writeString((ByteBuffer) token);
		else if (token instanceof Long)
			writeInteger((Long) token);
		else if (token instanceof List<?>)
			writeList((List<Object>) token);
		else if (token instanceof HashMap<?,?>)
			writeDictionary((HashMap<String, Object>) token);
		else
			throw new BencodeException("Unexpected data type while writing token " + token.toString());
	}
	
	/**
	 * Flushes the BufferedOutputStream to flush out the remaining bytes at the end of the stream.
	 * @throws IOException	If the underlying stream fails to flush
	 */
	public void flush() throws IOException
	{
		out.flush();
	}
}
