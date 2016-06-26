package com.chitek.ignition.drivers.generictcp.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class NioUdpServer implements Runnable, NioServer {

	private final Logger log;
	private final InetSocketAddress hostAddress;
	private IIoEventHandler eventHandler;

	private DatagramChannel serverChannel;
	private Selector selector;
	private volatile Map<InetAddress, DatagramChannel> clientMap = new HashMap<InetAddress, DatagramChannel>();
	// A list of SocketChannels to put into write state
	private final List<DatagramChannel> writeInterest = new LinkedList<DatagramChannel>();
	// Maps a SocketChannel to a list of ByteBuffer instances
	private final Map<DatagramChannel, List<ByteBuffer>> pendingData = new HashMap<DatagramChannel, List<ByteBuffer>>();
	// Timeout supervision
	private long timeout = 1000 * 60 * 120; // 120 minutes default
	private TimeoutHandler timeoutHandler;

	private boolean running;

	// Buffer for incoming data
	private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);

	public NioUdpServer(InetSocketAddress hostAddress, Logger log) throws IOException {
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

		for (Iterator<Map.Entry<InetAddress, DatagramChannel>> it = clientMap.entrySet().iterator(); it.hasNext();) {
			Entry<InetAddress, DatagramChannel> client = it.next();
			try {
				if (client.getValue().isOpen())
					client.getValue().close();
			} catch (IOException e) {
			}
		}

		clientMap.clear();
		timeoutHandler=null;
	}

	public synchronized void setEventHandler(IIoEventHandler eventHandler) {
		this.eventHandler = eventHandler;
	}

	/**
	 * Set the timeout for client connections. The timeout should be set before calling start().
	 * 
	 * @param timeout
	 *            The timeout in milliseconds.
	 */
	public void setTimeout(long timeout) {
		log.debug(String.format("Timeout set to %s ms", timeout));
		this.timeout = timeout;
	}

	/**
	 * Send the given ByteBuffer to a remote client.
	 * 
	 * @param remoteSocketAddress
	 *            The remote socket to send to.
	 * @param data
	 *            Data to send.
	 */
	public void write(InetSocketAddress remoteSocketAddress, ByteBuffer data) {
		synchronized (this.writeInterest) {

			// Get the SocketChannel for the given remote address
			DatagramChannel channel = clientMap.get(remoteSocketAddress.getAddress());
			if (channel == null) {
				log.error(String.format("Attempt to send to a not connected client: %s", remoteSocketAddress));
				return;
			}

			// Mark this SocketChannel to be switched to WriteInterest
			// We don't change the interestOps directly, because it is not clear how the Selector reacts
			// when interestOps is changed during the blocking select() call.
			writeInterest.add(channel);

			// And queue the data we want written
			synchronized (this.pendingData) {
				List<ByteBuffer> queue = this.pendingData.get(channel);
				if (queue == null) {
					queue = new ArrayList<ByteBuffer>();
					pendingData.put(channel, queue);
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
		this.serverChannel = DatagramChannel.open();
		serverChannel.configureBlocking(false);

		// Bind the server socket to the specified address and port
		serverChannel.socket().bind(hostAddress);

		// Register the udp socket channel, indicating an interest in reading
		serverChannel.register(selector, SelectionKey.OP_READ);

		if (log.isDebugEnabled())
			log.debug(String.format("Created DatagramSocket listening on %s:%s.", hostAddress.getAddress(), hostAddress.getPort()));
	}

	@Override
	public void run() {
		log.debug("NioServer main loop started.");

		int keys = 0;
		while (running) {
			try {
				// Switch marked SocketChannels to Write state
				synchronized (this.writeInterest) {
					Iterator<DatagramChannel> it = writeInterest.iterator();
					while (it.hasNext()) {
						DatagramChannel channel = it.next();
						channel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
					}
					writeInterest.clear();
				}

				// Wait for an event one of the registered channels
				keys = this.selector.select(timeoutHandler.getTimeToTimeout());

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
						if (key.isReadable()) {
							this.readFromSocket(key);
						} else if (key.isWritable()) {
							this.writeToSocket(key);
						}
					}
				}
			} catch (ClosedSelectorException e) {
				log.debug("Selector closed");
			} catch (Exception e) {
				log.error("Exception in NioServer run() method.", e);
			}
		}
		log.debug("NioServer main loop ended.");
	}

	/**
	 * @return The count of connected client sockets.
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
	
	private void readFromSocket(SelectionKey key) throws IOException {
		DatagramChannel channel = (DatagramChannel) key.channel();

		// For a datagram socket, we have to call receive to get the remote address
		readBuffer.clear();
		InetSocketAddress remoteSocket = (InetSocketAddress) channel.receive(readBuffer);

		// Check if we already know this client
		if (!clientMap.containsKey(remoteSocket.getAddress())) {
			boolean accept = eventHandler.clientConnected(remoteSocket);
			if (accept) {
				clientMap.put(remoteSocket.getAddress(), channel);
				channel.connect(remoteSocket);
				log.debug(String.format("Remote client %s connected.", remoteSocket));
			} else {
				log.debug(String.format("Remote client %s not accepted.", remoteSocket));
				return;
			}
		}

		// reset the timeout for this connection
		timeoutHandler.dataReceived(remoteSocket.getAddress());
		
		// Hand the data off to our worker thread
		int numRead = readBuffer.position();
		readBuffer.flip();
		eventHandler.dataArrived(remoteSocket, readBuffer, numRead);
	}

	private void writeToSocket(SelectionKey key) throws IOException {
		DatagramChannel channel = (DatagramChannel) key.channel();

		synchronized (this.pendingData) {
			List<ByteBuffer> queue = pendingData.get(channel);
			
			try {
				// Write until there's not more data ...
				while (!queue.isEmpty()) {
					ByteBuffer buf = queue.get(0);
					channel.write(buf);
					if (buf.remaining() > 0) {
						// ... or the socket's buffer fills up
						break;
					}
					queue.remove(0);
				}
			} catch (NotYetConnectedException ex) {
				log.error("Unexpected Exception in NioUDPServer.writeToSocket: Channel not yet connected!");
			} finally {
				// We wrote away all data, so we're no longer interested
				// in writing on this socket. Switch back to waiting for
				// data.
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}

	private void disposeClientChannel(InetSocketAddress remoteSocket) {
		
		timeoutHandler.removeAddress(remoteSocket.getAddress());
		
		DatagramChannel channel = clientMap.remove(remoteSocket.getAddress());
		synchronized (this.pendingData) {
			List<ByteBuffer> pending = pendingData.remove(channel);
			if (pending != null) {
				pending.clear();
			}
		}
		eventHandler.connectionLost(remoteSocket);
	}

	/**
	 * Close client connection after a timeout. This method is not synchronized and must only be called from the main
	 * loop!
	 */
	private void handleTimeout() {
		if (timeoutHandler.isTimeoutExpired()) {
			DatagramChannel channel = clientMap.get(timeoutHandler.getTimeoutAddress());
			InetSocketAddress address = (InetSocketAddress) channel.socket().getRemoteSocketAddress();
			log.warn(String.format("Timeout for client connection from %s expired. Closing connection.", address));
			disposeClientChannel(address);
		} else {
			log.debug("No timeout");
		}
	}
}