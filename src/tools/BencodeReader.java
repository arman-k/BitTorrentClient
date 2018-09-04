/**
 * This class is intended to be a parser/decoder for Bencoded data.
 * Bencoding is a way to specify and organize data in a terse format.
 * It supports the following types: byte strings, integers, lists and dictionaries.
 */

package tools;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class BencodeReader
{
	private final PushbackInputStream in;
	
	/**
	 * Returns a BencodeReader object that can be used to parse bencoded data types.
	 * InputStream is wrapped with BufferedInputStream for efficient I/O.
	 * This is then wrapped with PushbackInputStream to be able to push back a byte and provide a 'peek' primitive on the stream.
	 * @param input	The InputStream to read from
	 */
	public BencodeReader(InputStream input)
	{
		this.in = new PushbackInputStream(new BufferedInputStream(input));
	}
	
	/**
	 * Returns a dictionary (Map) representing bencoded metainfo that can be used to construct a torrent Metainfo object.
	 * This is just a wrapper around parseDictionary(). It is recommended to call this method instead,
	 * if you want to parse out an entire bencoded metainfo file such as .torrent.
	 * @return	a Map of decoded metainfo
	 * @throws IOException	If the file is not a dictionary
	 * @see metainfo.Metainfo
	 */
	public HashMap<ByteBuffer, Object> read() throws IOException
	{
		int delimiter;		
		/*
		 * You will find the following primitive: 
		 * "assertNotEOF((delimiter = in.read()), msg)
		 * if (delimiter != '%c') throw new Exception()
		 * in.unread(delimiter)"
		 * throughout this class.
		 * This provides 'peek' behavior on the input stream.
		 * By peeking the delimiter, we can figure out what data type is up next and invoke the corresponding parser.
		 */
		
		//A bencoded metainfo file must be a dictionary (start with a 'd')
		assertNotEOF((delimiter = in.read()), "Unexpected EOF while reading metaInfo file");		
		if (delimiter != 'd')
			throw new BencodeException("The metainfo file is not a properly encoded dictionary");
		in.unread(delimiter);
		
		return readDictionary();
	}
	
	/**
	 * Returns a ByteBuffer representing a bencoded String
	 * Calls readStringLength() to parse out the length and then consume that many bytes from the stream to construct a ByteBuffer.
	 * ByteBuffer is used because Java Strings for low-level I/O is a pain in the ass.
	 * @return a decoded String
	 * @throws IOException	If the bencoded String is invalid
	 */
	public ByteBuffer readString() throws IOException
	{
		int len;
		
		//The length field in a bencoded string must be valid
		if ((len = readStringLength()) == -1)
			throw new BencodeException("Token not a string");
		byte buf[] = new byte[len];
		
		//This could possibly be made much faster
		for (int i = 0; i < len; i++)
		{
			int temp = in.read();
			assertNotEOF(temp, "Unexpected EOF while reading string");
			buf[i] = (byte) temp;
		}
		
		return ByteBuffer.wrap(buf);
	}
	
	/**
	 * Returns the length of the bencoded String. Intended to be used by readString()
	 * @return an integer representing the String length
	 * @throws IOException	If the string length is invalid
	 * @see readString()
	 */
	private int readStringLength() throws IOException
	{
		int digit, len = 0;
		
		//The length must start with a digit
		assertNotEOF((digit = in.read()), "Unexpected EOF while reading string length");
		in.unread(digit);
		if (!Character.isDigit(digit)) 
			return -1;
		
		//The ascii digits upto ':' represent the length
		while ((digit = in.read()) != ':') 
		{
			assertNotEOF(digit, "Unexpected EOF while reading string length");
			if (!Character.isDigit(digit))
				throw new BencodeException("Unexpected non-digit character '" + digit + "' in string length");
			len = (len * 10) + (digit - '0');			
		}
		
		return len;
	}
	
	/**
	 * Returns a Long representing a bencoded Integer.
	 * @return a decoded long value
	 * @throws IOException	If the bencoded Integer is invalid
	 */
	public Long readInteger() throws IOException
	{
		int digit;
		Long number = 0l;
		boolean isNumber = false, isNegative = false;
		
		//A bencoded Integer must start with 'i'
		assertNotEOF((digit = in.read()), "Unexpected EOF while reading integer");
		if (digit != 'i')
			throw new BencodeException("Token not an integer");
		
		//Set isNegative to true if '-' is prepended to the bencoded Integer
		if ((digit = in.read()) == '-')	
			isNegative = true;
		else 
		{
			assertNotEOF(digit, "Unexpected EOF while reading integer");
			in.unread(digit);
		}
		
		//A bencoded Integer must end with 'e'
		while ((digit = in.read()) != 'e')
		{
			assertNotEOF(digit, "Unexpected EOF while reading integer");
			if (!Character.isDigit(digit))
				throw new BencodeException("Unexpected character '" + digit + "' while reading integer");
			isNumber = true;
			number = (number * 10) + (digit - '0');
			/*
			 * A bencoded Integer cannot contain any leading zero. For example, 03 is invalid.
			 * If a bencoded Integer is 0, it must end immediately.
			 */
			if (number == 0)
			{
				digit = in.read();
				assertNotEOF(digit, "Unexpected EOF while reading integer");
				if (digit != 'e')
					throw new BencodeException("Invalid encoding of integer - contains leading zero");
				else
					in.unread(digit);
			}	
		}
		
		if (isNumber) //A bencoded Integer must contain at least one digit
		{
			if (isNegative)
			{
				if (number != 0) //A bencoded Integer cannot be negative zero
					return -number; 
				else
					throw new BencodeException("Invalid encoding of integer - contains negative zero");
			}	
			else
				return number;
		}
		else
			throw new BencodeException("Unexpected end of integer - integer contains no digit");		
	}
	
	/**
	 * Returns a List representing a bencoded List. Valid values include bencoded Strings, Integers, Lists or Dictionaries
	 * @return a decoded List
	 * @throws IOException	If the bencoded List is invalid
	 */
	public List<Object> readList() throws IOException
	{
		int delimiter;
		ArrayList<Object> list = new ArrayList<Object>();
		
		//A bencoded List must start with 'l'
		assertNotEOF((delimiter = in.read()), "Unexpected EOF while reading list");		
		if (delimiter != 'l')
			throw new BencodeException("Token not a list");	
		
		//A bencoded List must end with 'e'
		while ((delimiter = in.read()) != 'e')
		{
			in.unread(delimiter);
			list.add(readToken()); //Parse the token and add it to the list
		}
			
		
		return list;
	}
	
	/**
	 * Returns a HashMap representing a bencoded Dictionary. Keys must be bencoded Strings. 
	 * Valid values include bencoded Strings, Integers, Lists or Dictionaries
	 * @return a decoded HashMap
	 * @throws IOException	If the bencoded Dictionary is invalid
	 */
	public HashMap<ByteBuffer, Object> readDictionary() throws IOException
	{
		int delimiter;
		LinkedHashMap<ByteBuffer, Object> dict = new LinkedHashMap<ByteBuffer, Object>();
		
		//A bencoded Dictionary must start with 'd'
		assertNotEOF((delimiter = in.read()), "Unexpected EOF while reading dictionary");		
		if (delimiter != 'd')
			throw new BencodeException("Token not a dictionary");
		
		//A bencoded Dictionary must end with 'e'
		while ((delimiter = in.read()) != 'e')
		{
			assertNotEOF(delimiter, "Unexpected EOF while reading dictionary");
			in.unread(delimiter);
			if (!Character.isDigit(delimiter)) //Key must be a bencoded String (which starts with a digit in the length field)
				throw new BencodeException("Unexpected delimiter '" + delimiter + "' while reading dictionary - key is not a string");
			else
				dict.put(readString(), readToken()); //Pass the String as key. Parse the next token and pass it as value.
		}
		
		return dict;
	}
	
	/**
	 * Returns a token representing a bencoded data type. This includes bencoded Strings, Integers, Lists and Dictionaries.
	 * @return a decoded Token
	 * @throws IOException	If the bencoded Token is invalid
	 * @see readString(), readInteger(), readList(), readDictionary()
	 */
	private Object readToken() throws IOException
	{
		int delimiter = in.read();
		assertNotEOF(delimiter, "Unexpected EOF while reading token");
		in.unread(delimiter);
		
		if (Character.isDigit(delimiter))
			return readString();
		else if (delimiter == 'i')
			return readInteger();			
		else if (delimiter == 'l')
			return readList();
		else if (delimiter == 'd')
			return readDictionary();			
		else
			throw new BencodeException("Unexpected delimiter '" + delimiter + "' while reading token");
	}
	
	/**
	 * Checks for EOF (End of File) in the input stream. 
	 * Is intended to be called by other methods in this class to check for EOF while consuming a byte
	 * @param nextByte	the byte to check for EOF
	 * @param message	the message to be passed to EOFException
	 * @throws IOException	If the next byte is EOF
	 */
	private void assertNotEOF(int nextByte, String message) throws IOException
	{
		if (nextByte == -1) //EOF is represented by -1
			throw new EOFException(message);			
	}
}
