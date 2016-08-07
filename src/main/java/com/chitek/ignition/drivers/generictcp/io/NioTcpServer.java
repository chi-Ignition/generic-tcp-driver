package com.chitek.ignition.drivers.generictcp.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class NioTcpServer implements Runnable, NioServer {

	private final Logger log;
	private final InetSocketAddress hostAddress;
	private IIoEventHandler eventHandler;

	private ServerSocketChannel serverChannel;
	private Selector selector;
	private volatile Map<InetSocketAddress, SocketChannel> clientMap = new HashMap<InetSocketAddress, SocketChannel>();
	// A list of SocketChannels to put into write state
	private final List<SocketChannel> writeInterest = new LinkedList<SocketChannel>();
	// Maps a SocketChannel to a list of ByteBuffer instances
	private final Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<SocketChannel, List<ByteBuffer>>();
	// Timeout supervision
	private long timeout = 1000*60*120;	// 120 minutes default
	private TimeoutHandler timeoutHandler;

	private boolean running;

	// Buffer for incoming data
	private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);

	public NioTcpServer(InetSocketAddress hostAddress, Logger log) throws IOException {
		this.hostAddress = hostAddress;
		this.log = log;
	}

	public void start() {
		if (eventHandler == null) {
			log.error("EventHandler is not set.");
			return;
		}

		timeoutHandler=new TimeoutHandler(timeout);
		running = true;
		try {
			createSocketSelector();
			new Thread(this).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void stop() {
		running = false;
		try {
			if (selector != null && selector.isOpen())
				selector.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		selector = null;
		try {
			serverChannel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (Iterator<Map.Entry<InetSocketAddress, SocketChannel>> it = clientMap.entrySet().iterator(); it.hasNext();) {
			Entry<InetSocketAddress, SocketChannel> client = it.next();
			try {
				if (client.getValue().isOpen())
					client.getValue().close();
			} catch (IOException e) {
			}
		}
		clientMap.clear();
	}

	public synchronized void setEventHandler(IIoEventHandler eventHandler) {
		this.eventHandler = eventHandler;
	}

	/**
	 * Set the timeout for client connections. The timeout should be set before calling start().
	 * 
	 * @param timeout
	 * 	The timeout in milliseconds.
	 */
	public void setTimeout(long timeout) {
		log.debug(String.format("Timeout set to %s ms", timeout));
		this.timeout = timeout;
	}
	
	/**
	 * Send the given ByteBuffer to a remote client.
	 * 
	 * @param remoteSocketAddress
	 * 		The remote socket to send to.
	 * @param data
	 * 		Data to send.
	 */
	public void write(InetSocketAddress remoteSocketAddress, ByteBuffer data) {
		synchronized (this.writeInterest) {

			// Get the SocketChannel for the given remote address
			SocketChannel socketChannel = clientMap.get(remoteSocketAddress);
			if (socketChannel == null) {
				log.error(String.format("Attempt to send to a not connected client: %s", remoteSocketAddress));
				return;
			}

			// Mark this SocketChannel to be switched to WriteInterest
			// We don't change the interestOps directly, because it is not clear how the Selector reacts
			// when interestOps is changed during the blocking select() call.
			writeInterest.add(socketChannel);

			// And queue the data we want written
			synchronized (this.pendingData) {
				List<ByteBuffer> queue = this.pendingData.get(socketChannel);
				if (queue == null) {
					queue = new ArrayList<ByteBuffer>();
					pendingData.put(socketChannel, queue);
				}
				queue.add(data);
			}
		}

		// Finally, wake up our selecting thread so it can change the channels SelectionKey
		this.selector.wakeup();
	}

	private void createSocketSelector() throws Exception {

		// Create a non-blocking server
		this.selector = SelectorProvider.provider().openSelector();
		this.serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		// Bind the server socket to the specified address and port
		serverChannel.socket().bind(hostAddress);

		// Register the server socket channel, indicating an interest in accepting new connections
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);

		if (log.isDebugEnabled())
			log.debug(String.format("Created ServerSocket listening on %s:%s.", hostAddress.getAddress(), hostAddress.getPort()));
	}

	@Override
	public void run() {
		log.debug("NioServer main loop started.");
		
		int keys=0;
		while (running) {
			try {
				// Switch marked SocketChannels to Write state
				synchronized (this.writeInterest) {
					Iterator<SocketChannel> it = writeInterest.iterator();
					while (it.hasNext()) {
						SocketChannel socketChannel = it.next();
						try {
							socketChannel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
						} catch (CancelledKeyException e) {
							// The connections might have been closed
							pendingData.remove(socketChannel);
						}
					}
					writeInterest.clear();
				}
			
				// Wait for an event one of the registered channels
				keys = selector.select(timeoutHandler.getTimeToTimeout());

				if (keys == 0) {
					// No updated keys - timeout expired or wakeup called
					log.debug("NioServer main loop: select timeout expired or wakeup");
					handleTimeout();
				} else {
					// Iterate over the set of keys for which events are available
					Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
					while (selectedKeys.hasNext()) {
						SelectionKey key = selectedKeys.next();
						selectedKeys.remove();

						if (!key.isValid()) {
							continue;
						}

						// Check what event is available and deal with it
						if (key.isAcceptable()) {
							this.accept(key);
						} else if (key.isReadable()) {
							this.readFromSocket(key);
						} else if (key.isWritable()) {
							this.writeToSocket(key);
						}
					}
				}
			} catch (ClosedSelectorException e) {
				log.debug("NioServer main loop ended: Selector closed");
			} catch (Exception e) {
				log.error("Exception in NioServer run() method.", e);
			}
		}
		log.debug("NioServer main loop ended.");
	}

	/**
	 * @return
	 * 	The count of connected client sockets.
	 */
	public int getConnectedClientCount() {
		return clientMap.size();
	}
	
	/**
	 * @return
	 * 	The SocketAddress of the server
	 */
	public SocketAddress getLocalAddress() {
		return serverChannel.socket().getLocalSocketAddress();
	}

	private void accept(SelectionKey key) throws IOException {
		// This cast is safe, because only ServerSocketChannels can have accepts pending
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

		// Accept the connection and make it non-blocking
		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);

		InetSocketAddress remoteSocket = (InetSocketAddress) socketChannel.getRemoteAddress();
		
		// Check if there is already a connection from this address
		for (Entry<InetSocketAddress, SocketChannel> entry : clientMap.entrySet()) {
			if (entry.getKey().getAddress().equals(remoteSocket.getAddress())) {
				log.debug(String.format("New connection from client %s. Replacing existing connection.", remoteSocket));
				disposeClientChannel(entry.getKey());
				break;
			}
		}
		
		// Register the new SocketChannel with our Selector, indicating
		// we'd like to be notified when there's data waiting to be read
		socketChannel.register(this.selector, SelectionKey.OP_READ);

		clientMap.put(remoteSocket, socketChannel);
		timeoutHandler.dataReceived(remoteSocket);
		log.debug(String.format("Remote client %s connected.", remoteSocket));

		boolean accept = eventHandler.clientConnected(remoteSocket);
		if (!accept) {
			disposeClientChannel(remoteSocket);
			log.debug(String.format("Remote client %s disconnected.", remoteSocket));
		}
	}

	private void readFromSocket(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		InetAddress remoteAddress = socketChannel.socket().getInetAddress();
		InetSocketAddress remoteSocket = new InetSocketAddress(remoteAddress, socketChannel.socket().getPort());

		// Clear out our read buffer so it's ready for new data
		readBuffer.clear();

		// Attempt to read off the channel
		int numRead;
		try {
			numRead = socketChannel.read(readBuffer);
		} catch (IOException e) {
			// The remote forcibly closed the connection, cancel
			// the selection key and close the channel.
			disposeClientChannel(remoteSocket);
			log.debug(String.format("Remote client %s closed connection forcibly.", remoteSocket));
			return;
		}

		if (numRead == -1) {
			// Remote entity shut the socket down cleanly. Do the
			// same from our end and cancel the channel.
			disposeClientChannel(remoteSocket);
			log.debug(String.format("Remote client %s closed connection.", remoteSocket));
			return;
		}
		
		// reset the timeout for this connection
		timeoutHandler.dataReceived(remoteSocket);
		
		// Hand the data off to our worker thread
		readBuffer.flip();
		eventHandler.dataArrived(remoteSocket, readBuffer, numRead);
	}

	private void writeToSocket(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		synchronized (this.pendingData) {
			List<ByteBuffer> queue = pendingData.get(socketChannel);

			// Write until there's not more data ...
			while (!queue.isEmpty()) {
				ByteBuffer buf = queue.get(0);
				socketChannel.write(buf);
				if (buf.remaining() > 0) {
					// ... or the socket's buffer fills up
					break;
				}
				queue.remove(0);
			}

			if (queue.isEmpty()) {
				// We wrote away all data, so we're no longer interested
				// in writing on this socket. Switch back to waiting for
				// data.
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}
	
	private void disposeClientChannel(InetSocketAddress remoteSocket) {

		timeoutHandler.removeAddress(remoteSocket);
		
		SocketChannel socketChannel = clientMap.remove(remoteSocket);
		socketChannel.keyFor(selector).cancel();
		try {
			socketChannel.close();
		} catch (IOException e) {
		}
		synchronized (this.pendingData) {
			List<ByteBuffer> pending = pendingData.remove(socketChannel);
			if (pending != null) {
				pending.clear();
			}
		}
		eventHandler.connectionLost(remoteSocket);
	}
	
	/**
	 * Close client connection after a timeout.
	 * This method is not synchronized and must only be called from the main loop!
	 */
	private void handleTimeout() {
		if (timeoutHandler.isTimeoutExpired()) {
			SocketChannel channel = clientMap.get(timeoutHandler.getTimeoutAddress());
			InetSocketAddress address = (InetSocketAddress) channel.socket().getRemoteSocketAddress();
			log.warn(String.format("Timeout for client connection from %s expired. Closing connection.", address));
			disposeClientChannel(address);	
		}
	}
}
