package com.chitek.ignition.drivers.generictcp.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
	private volatile Map<InetSocketAddress, InetSocketAddress> clientMap = new HashMap<InetSocketAddress, InetSocketAddress>();
	// Maps a Client connection to a list of ByteBuffer instances
	private final Map<SocketAddress, List<ByteBuffer>> pendingData = new HashMap<SocketAddress, List<ByteBuffer>>();
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
		

		// Get the SocketChannel for the given remote address
		InetSocketAddress address = clientMap.get(remoteSocketAddress);
		if (address == null) {
			log.error(String.format("Attempt to send to a not connected client: %s", remoteSocketAddress));
			return;
		}

		// And queue the data we want written
		synchronized (this.pendingData) {
			List<ByteBuffer> queue = this.pendingData.get(address);
			if (queue == null) {
				queue = new ArrayList<ByteBuffer>();
				pendingData.put(address, queue);
			}
			queue.add(data);
		}
		

		// Finally, wake up our selecting thread so it can change the channels SelectionKey
		serverChannel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
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
				// Wait for an event one of the registered channels
				keys = this.selector.select(timeoutHandler.getTimeToTimeout());

				if (keys == 0) {
					// No updated keys - timeout expired or wakeup called
					log.debug("NioServer main loop: select timeout expired or wakeup");
					handleTimeout();
				} else {
					log.debug("NioServer main loop: select returned with keys");
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
		synchronized(clientMap) {
			return clientMap.size();
		}
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

		if (log.isTraceEnabled()) {
			log.trace(String.format("%d bytes of data received from %s", readBuffer.position(), remoteSocket));
		}
		
		// Check if we already know this client
		if (!clientMap.containsKey(remoteSocket)) {
			synchronized (clientMap) {
				// Check if there is already a connection from this remote address
				for (InetSocketAddress existingSocket : clientMap.keySet()) {
					if (existingSocket.getAddress().equals(remoteSocket.getAddress())) {
						log.debug(String.format("New connection from client %s. Replacing existing connection.", remoteSocket));
						disposeClientChannel(existingSocket);
						break;
					}
				}

				clientMap.put(remoteSocket, remoteSocket);
				boolean accept = eventHandler.clientConnected(remoteSocket);
				if (accept) {
					log.debug(String.format("Remote client %s connected.", remoteSocket));
				} else {
					clientMap.remove(remoteSocket);
					log.debug(String.format("Remote client %s not accepted.", remoteSocket));
					return;
				}
			}
		}

		// reset the timeout for this connection
		timeoutHandler.dataReceived(remoteSocket);
		
		// Hand the data off to our worker thread
		int numRead = readBuffer.position();
		readBuffer.flip();
		eventHandler.dataArrived(remoteSocket, readBuffer, numRead);
	}

	private void writeToSocket(SelectionKey key) throws IOException {
		DatagramChannel channel = (DatagramChannel) key.channel();

		synchronized (this.pendingData) {
			for (Entry<SocketAddress, List<ByteBuffer>> entry : pendingData.entrySet()) {

				List<ByteBuffer> queue = entry.getValue();

				try {
					// Write until there's not more data ...
					while (!queue.isEmpty()) {
						
						ByteBuffer buf = queue.get(0);
						channel.send(buf, entry.getKey());
						if (buf.remaining() > 0) {
							// do not remove buffer from queue if the socket's buffer fills up
							break;
						}
						queue.remove(0);
					}
				} catch (Exception ex) {
					log.error("Unexpected Exception in NioUDPServer.writeToSocket!", ex);
				}
			}
		}
		key.interestOps(SelectionKey.OP_READ);
	}

	private void disposeClientChannel(InetSocketAddress remoteAddress) {
		
		timeoutHandler.removeAddress(remoteAddress);
		
		InetSocketAddress channel = clientMap.remove(remoteAddress);
		synchronized (this.pendingData) {
			List<ByteBuffer> pending = pendingData.remove(channel);
			if (pending != null) {
				pending.clear();
			}
		}
		
		eventHandler.connectionLost(remoteAddress);
	}

	/**
	 * Close client connection after a timeout. This method is not synchronized and must only be called from the main
	 * loop!
	 */
	private void handleTimeout() {
		if (timeoutHandler.isTimeoutExpired()) {
			InetSocketAddress remoteAddress = clientMap.get(timeoutHandler.getTimeoutAddress());
			log.warn(String.format("Timeout for client connection from %s expired. Closing connection.", remoteAddress));
			disposeClientChannel(remoteAddress);
		} else {
			log.debug("No timeout");
		}
	}
}
