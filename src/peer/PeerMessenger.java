/*
 * Peer messenger is a message carrier and receiver
 * It wraps around Java NIO for ease of use
 */
package peer;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.LoggingClient;

import java.nio.channels.SocketChannel;

import tools.Util;

public class PeerMessenger
{
	private Peer peer;
	private LinkedBlockingQueue<ByteBuffer> messageQueue;
	private Thread sender;
	private Thread receiver;
	private volatile boolean running = false;	//http://tutorials.jenkov.com/java-concurrency/volatile.html	
	private final static long POLL_TIMEOUT = 120l;
	Logger logger = LoggingClient.getInstance().logger;
	
	public PeerMessenger(Peer peer)
	{
		this.peer = peer;
		this.messageQueue = new LinkedBlockingQueue<ByteBuffer>();
		this.sender = new Thread(new MessageSender());
		this.sender.setDaemon(true);
		this.receiver = new Thread(new MessageReceiver());
		this.receiver.setDaemon(true);
	}

	/*
	 * Start sender and receiver threads if not running already
	 */
	public void start()
	{
		if (!isRunning())
		{
			setRunning(true);
			sender.start();
			receiver.start();
			logger.log(Level.INFO, "Sender and receiver threads started for " + getPeer());
		}
	}
	
	/*
	 * Join sender and receiver threads
	 */
	public void stop()	//TODO the logic is too complex, see if it can be simplified
	{
		if (isRunning())
		{
			setRunning(false);
			logger.log(Level.FINEST, "Stopping peer messenger. Sender and receiver threads for " + getPeer() + " should return now.");
		}			
	}
	
	/*
	 * Put message in message queue
	 */
	public void send(ByteBuffer message)
	{
		try
		{
			getMessageQueue().put(message);
		} catch(InterruptedException e)
		{
			logger.log(Level.WARNING, "Sender thread for " + getPeer() + " interrupted while trying to queue a message", e);
		}
	}
	
	/*
	 * Message receiver thread
	 * It only reads raw messages from peer and fires a message received event for rest of the processing
	 */
	private class MessageReceiver implements Runnable
	{
		public void run()
		{
			//http://blog.asquareb.com/blog/2015/06/05/java-direct-bytebuffer-performance-advantages-and-considerations/
			//OS gives us only cheap virtual memory, physical memory will only be accessed when required,
			//so don't be shocked at this large allocation
			ByteBuffer buf = ByteBuffer.allocateDirect(1*Util.SIZE_MB);	
			try 
			{
				Selector selector = Selector.open();
				getPeer().getSocketChannel().configureBlocking(false)
						.register(selector, SelectionKey.OP_READ);
				
				while(isRunning())
				{
					buf.rewind().mark().limit(PeerMessage.LENGTH_SIZE);
					
					while (isRunning() && buf.hasRemaining())
						this.read(selector, buf);
					buf.reset();
					if (!isRunning())
						return;
					int messageLength = buf.getInt();
					buf.limit(PeerMessage.LENGTH_SIZE + messageLength).mark();
					
					while (isRunning() && buf.hasRemaining())
						this.read(selector, buf);
					buf.reset();
					if (!isRunning())
						return;
					
					//Forward the message starting after the length field
					//The buffer has already been 'limited' up to length
					//The handler can therefore use buf.remaining() to find the length of the buffer
					getPeer().onMessageReceived(buf);
				}
			} catch (IOException e) 
			{
				logger.log(Level.WARNING, "Failed to receive message from " + getPeer(), e);
				setRunning(false);
				logger.log(Level.FINER, "Stopping peer messenger for " + getPeer() + " and abruptly disconnecting");
				getPeer().abruptDisconnect();
			}
		}
		
		public void read(Selector selector, ByteBuffer buf) throws IOException
		{
			if (selector.select() == 0)
				return;
			
			Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
			while (isRunning() && keyIterator.hasNext())
			{
				SelectionKey key = keyIterator.next();
				if (key.isReadable())
					_read((SocketChannel) key.channel(), buf);
				keyIterator.remove();
			}
		}
		
		public void _read(SocketChannel channel, ByteBuffer buf) throws IOException
		{
			int read = 0;
			if (isRunning())
				read = channel.read(buf);
			if (read == -1)
			{
				EOFException e = new EOFException("Reached EOF while reading from peer " + getPeer());
				logger.log(Level.WARNING, "EOF ERROR", e);
				throw e;
			}				
		}
	}
	
	/*
	 * Message sender thread
	 * It polls the message queue, waiting for upto POLL_TIMEOUT seconds
	 * If a message is available, it sends the message to peer
	 * Otherwise it sends a keep-alive message
	 */
	private class MessageSender implements Runnable
	{
		public void run()
		{
			while(isRunning())
			{
				try 
				{
					ByteBuffer message = messageQueue.poll(POLL_TIMEOUT, TimeUnit.SECONDS);
					if (message == null)
					{
						message = PeerMessage.encodeKeepAlive();
						if (isRunning())
							logger.log(Level.INFO, "Sending keep-alive message. No messages queued for " + getPeer());
					}
					this.write(message);
				} catch (InterruptedException e) 
				{
					logger.log(Level.WARNING, "Sender thread for " + getPeer() + " interrupted while trying to write a message", e);
				} catch (IOException e) 
				{
					logger.log(Level.WARNING, "Failed to send message to " + getPeer(), e);
					setRunning(false);
					logger.log(Level.FINER, "Stopping peer messenger for " + getPeer() + " and abruptly disconnecting");
					getPeer().abruptDisconnect();
				}
			}
		}
		
		public void write(ByteBuffer message) throws IOException
		{
			while (isRunning() && message.hasRemaining())
				getPeer().getSocketChannel().write(message);
		}
	}

	public Peer getPeer() {
		return peer;
	}

	public void setPeer(Peer peer) {
		this.peer = peer;
	}

	public LinkedBlockingQueue<ByteBuffer> getMessageQueue() {
		return messageQueue;
	}

	public void setMessageQueue(LinkedBlockingQueue<ByteBuffer> messageQueue) {
		this.messageQueue = messageQueue;
	}

	public Thread getSender() {
		return sender;
	}

	public void setSender(Thread sender) {
		this.sender = sender;
	}

	public Thread getReceiver() {
		return receiver;
	}

	public void setReceiver(Thread receiver) {
		this.receiver = receiver;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}	
}
