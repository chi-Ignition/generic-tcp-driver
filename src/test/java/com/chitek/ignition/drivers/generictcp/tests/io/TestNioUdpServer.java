package com.chitek.ignition.drivers.generictcp.tests.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.io.IIoEventHandler;
import com.chitek.ignition.drivers.generictcp.io.NioUdpServer;
import com.chitek.ignition.drivers.generictcp.tests.DriverTestSuite;

public class TestNioUdpServer {

	private Logger log;
	private IIoEventHandler eventHandler;

	private CountDownLatch connectLatch;
	private CountDownLatch disconnectLatch;
	private CountDownLatch dataLatch;

	@Before
	public void setup() throws Exception {

		connectLatch = new CountDownLatch(1);
		disconnectLatch = new CountDownLatch(1);
		dataLatch = new CountDownLatch(1);

		log = DriverTestSuite.getLogger();

		eventHandler = new IIoEventHandler() {

			@Override
			public boolean clientConnected(InetSocketAddress remoteSocket) {
				log.debug("Client connected");
				connectLatch.countDown();
				return true;
			}

			@Override
			public void connectionLost(InetSocketAddress remoteAddress) {
				log.debug(String.format("Client %s disconnected", remoteAddress));
				disconnectLatch.countDown();
			}

			@Override
			public void dataArrived(InetSocketAddress remoteAddress, ByteBuffer data, int bytesRead) {
				log.debug("Data arrived");
				dataLatch.countDown();
			}

		};
	}

	@Test(timeout = 200)
	public void testConnectAndReceive() throws Exception {

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
		NioUdpServer server = new NioUdpServer(address, log);
		server.setEventHandler(eventHandler);
		server.start();

		// Send data and wait for the server to call the event handler
		byte[] bytes = new byte[] { 1, 2, 3, 4 };
		DatagramSocket socket1 = connect((InetSocketAddress) server.getLocalAddress());
		sendData(socket1, bytes, (InetSocketAddress) server.getLocalAddress());
		// The UDP server calls clientConnected when receiving data for the first time
		if (!connectLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received");
		}
		if (!dataLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received from first send");
		}
		

		dataLatch = new CountDownLatch(1);
		sendData(socket1, bytes, (InetSocketAddress) server.getLocalAddress());
		if (!dataLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received from second send");
		}
		

		// Disconnect and wait for the server to call the event handler
		disconnect(socket1);

		server.stop();
	}
	
	@Test(timeout = 200)
	public void testWrite() throws Exception {

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
		NioUdpServer server = new NioUdpServer(address, log);
		server.setEventHandler(eventHandler);
		server.start();

		// Send data and wait for the server to call the event handler
		byte[] bytes = new byte[] { 1, 2, 3, 4 };
		DatagramSocket socket = connect((InetSocketAddress) server.getLocalAddress());
		sendData(socket, bytes, (InetSocketAddress) server.getLocalAddress());
		// The UDP server calls clientConnected when receiving data for the first time
		if (!connectLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received");
		}

		assertEquals("Number of connected clients", 1, server.getConnectedClientCount());

		ByteBuffer data = ByteBuffer.wrap(bytes);
		server.write(new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort()), data);

		try {
			socket.setSoTimeout(50);
			try {
				byte[] recBuffer = new byte[4];
				DatagramPacket packet = new DatagramPacket(recBuffer, recBuffer.length);
				socket.receive(packet);
				assertArrayEquals("Received data should match sent data", bytes, recBuffer);
			} catch (Exception e) {
				fail("No data received");
			}

			// Disconnect and wait for the server to call the event handler
			disconnect(socket);

		} finally {
			server.stop();
		}

	}

	@Test(timeout = 200)
	public void testConnectionFromSameClient() throws Exception {

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
		NioUdpServer server = new NioUdpServer(address, log);
		server.setEventHandler(eventHandler);
		server.start();

		// Send data and wait for the server to call the event handler
		byte[] bytes = new byte[] { 1, 2, 3, 4 };
		DatagramSocket socket1 = connect((InetSocketAddress) server.getLocalAddress());
		sendData(socket1, bytes, (InetSocketAddress) server.getLocalAddress());
		// The UDP server calls clientConnected when receiving data for the first time
		if (!connectLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received");
		}
		if (!dataLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received from first connection");
		}
		
		// Connect another and wait for the server to call the event handler
		// The new connection should replace the existing one
		dataLatch = new CountDownLatch(1);
		DatagramSocket socket2 = connect((InetSocketAddress) server.getLocalAddress());
		sendData(socket1, bytes, (InetSocketAddress) server.getLocalAddress());
		
		if (!dataLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received from second connection");
		}
		
		// Second connection should not increase client count
		assertEquals("Number of connected clients", 1, server.getConnectedClientCount());

		// Disconnect and wait for the server to call the event handler
		disconnect(socket1);
		disconnect(socket2);

		server.stop();
	}

	@Test(timeout = 200)
	public void testTimeout() throws Exception {

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
		NioUdpServer server = new NioUdpServer(address, log);
		server.setEventHandler(eventHandler);
		server.setTimeout(25);
		server.start();

		// Connect and wait for the server to call the event handler
		DatagramSocket socket = connect((InetSocketAddress) server.getLocalAddress());
		byte[] bytes = new byte[] { 1, 2, 3, 4 };
		sendData(socket, bytes, (InetSocketAddress) server.getLocalAddress());
		if (!connectLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("No data received");
		}
		assertEquals("Number of connected clients", 1, server.getConnectedClientCount());

		if (!disconnectLatch.await(100, TimeUnit.MILLISECONDS)) {
			fail("Disconnect not called");
		}
		assertEquals("Number of connected clients", 0, server.getConnectedClientCount());

		// Close this end of the connection
		disconnect(socket);

		server.stop();

	}

	private DatagramSocket connect(InetSocketAddress address) {
		DatagramSocket socket;
		try {
			socket = new DatagramSocket(null);
			socket.bind(null);
			socket.connect(address);
			return socket;
		} catch (SocketException e1) {
			e1.printStackTrace();
		}

		return null;
	}

	private void disconnect(DatagramSocket socket) {
		socket.close();
		socket = null;
	}

	private void sendData(DatagramSocket socket, byte[] data, InetSocketAddress serverAddr) {
		DatagramPacket packet = new DatagramPacket(data, data.length);
		packet.setSocketAddress(serverAddr);
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
