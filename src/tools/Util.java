/**
 * A utility class that contains methods to handle common tasks that doesn't fall into any other category.
 */

package tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.TimeZone;

public class Util 
{
	public static final int SIZE_KB = 1024;
	public static final int SIZE_MB = 1024*SIZE_KB;
	/**
	 * Converts a long value in standard UNIX epoch format into a LocalDateTime object
	 * @param unixEpoch		The Unix Epoch value to convert
	 * @return				A LocalDateTime object representing the Unix Epoch
	 */
	public static LocalDateTime unixEpochToLDT(Object unixEpoch)
	{
		if (unixEpoch == null)
			return null;
		Instant instant = Instant.ofEpochSecond((Long) unixEpoch);
		return LocalDateTime.ofInstant(instant, TimeZone.getDefault().toZoneId());
	}
	
	/**
	 * Converts a LocalDateTime object into standard UNIX epoch format
	 * @param ldt	The LocalDateTime object to convert
	 * @return		A Unix Epoch value representing the LocalDateTime
	 */
	public static Long ldtToUnixEpoch(LocalDateTime ldt)
	{
		if (ldt == null)
			return null;
		return ldt.atZone(TimeZone.getDefault().toZoneId()).toEpochSecond();
	}
	
	/**
	 * Converts a Boolean object wrapper into primitive boolean value
	 * @param obj		The Boolean object to convert
	 * @return			The converted boolean value
	 */
	public static boolean booleanObjectToPrimitive(Boolean obj)
	{
		if (obj == null)
			return false;
		return obj.booleanValue();
	}
	
	/**
	 * Converts a String into ByteBuffer
	 * @param input		The String to convert
	 * @return			The converted ByteBuffer
	 */
	public static ByteBuffer stringToByteBuffer(String input) 
	{
		if (input == null)
			return null;
        return ByteBuffer.wrap(input.getBytes(Charset.forName("UTF-8")));
    }
	
	/**
	 * Converts a ByteBuffer into String
	 * @param input		The ByteBuffer to convert
	 * @return			The converted String
	 */
	public static String byteBufferToString(Object input) 
	{
		if (input == null)
			return null;
		return new String(((ByteBuffer) input).array());
    }
	
	/*
	 * Encode byte-array input in URL-safe format
	 */
	public static String URLEncode(byte[] input)
	{
		if (input == null)
			return null;
		final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
		StringBuilder output = new StringBuilder();
		int unsignedByte;
		
		for (int i = 0; i < input.length; i++)
		{
			if ((input[i] >= '0' && input[i] <= '9')
					|| (input[i] >= 'a' && input[i] <= 'z')
					|| (input[i] >= 'A' && input[i] <= 'Z')
					|| (input[i] == '.')
					|| (input[i] == '-')
					|| (input[i] == '_')
					|| (input[i] == '~'))
			{
				output.append((char) input[i]);
			}
			else
			{
				unsignedByte = input[i] & 0xFF;
				output.append('%');
				output.append(HEX_DIGITS[unsignedByte >>> 4]);
				output.append(HEX_DIGITS[unsignedByte & 0x0F]);
			}			
		}
				
		return output.toString();
	}
	
	/*
	 * Encode string input in URL-safe format
	 */
	public static String URLEncode(String input)
	{
		if (input == null)
			return null;
		
		return URLEncode(input.getBytes(Charset.forName("UTF-8")));
	}
	
	/*
	 * Build hash of infoDictionary
	 */
	public static byte[] buildHash(HashMap<ByteBuffer, Object> infoDict, String algorithm) throws IOException, NoSuchAlgorithmException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BencodeWriter writer = new BencodeWriter(out);
		writer.write(infoDict);
		writer.flush();
		
		return buildHash(out.toByteArray(), algorithm);
	}
	
	/*
	 * Build hash of data in ByteBuffer format with algorithm
	 */
	public static byte[] buildHash(ByteBuffer data, String algorithm) throws NoSuchAlgorithmException
	{
		return buildHash(data.array(), algorithm);
	}
	
	/*
	 * Build hash of data in byte-array format with SHA-1 defualt algorithm
	 */
	public static byte[] buildHash(byte[] data, String algorithm) throws NoSuchAlgorithmException
	{
		if (algorithm == null)
			algorithm = "SHA-1";
		MessageDigest md = MessageDigest.getInstance(algorithm);
		md.reset();
		
		return md.digest(data);
	}
	
	/*
	 * Convert an IP from network-byte order integer to dot-decimal notation
	 */
	public static String intToIPString(int IP)
	{
		return String.format("%d.%d.%d.%d", 
				((IP >>> 24) & 0xff), ((IP >>> 16) & 0xff),
				((IP >>> 8) & 0xff), (IP & 0xff));
	}
	
	/**
	 * Returns a boolean value, converting from an integer representing the 'private' field in an info dictionary.
	 * It is intended to be invoked by buildInfoDictionary().
	 * @param priv	An integer representing the 'private' field of an info dictionary.
	 * @return		The corresponding boolean value of priv that can be stored in the 'noExternalPeerSource' field of an InfoDictionary.
	 * @see Metainfo, InfoDictionary, buildInfoDictionary()
	 */
	public static Integer boolToInt(Object priv)
	{
		if (priv == null)
			return null;
		if ((Boolean) priv)
			return 1;
		return 0;
	}
}
