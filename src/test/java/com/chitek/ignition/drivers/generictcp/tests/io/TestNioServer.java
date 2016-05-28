package com.chitek.ignition.drivers.generictcp.tests.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.io.IIoEventHandler;
import com.chitek.ignition.drivers.generictcp.io.NioTcpServer;
import com.chitek.ignition.drivers.generictcp.tests.DriverTestSuite;

public class TestNioServer {

	private Logger log;
	private IIoEventHandler eventHandler;
	
	private CountDownLatch connectLatch;
	private CountDownLatch disconnectLatch;
	
	@Before
	public void setup() throws Exception {
		
		connectLatch = new CountDownLatch(1);
		disconnectLatch = new CountDownLatch(1);
		
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
								
			}
			
		};
	}
	
	@Test(timeout = 200) 
	public void testSimpleConnection() throws Exception {

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
		NioTcpServer server = new NioTcpServer(address, log);
		server.setEventHandler(eventHandler);
		server.start();
		
		// Connect and wait for the server to call the event handler
		Socket socket = connect((InetSocketAddress) server.getLocalAddress());
		connectLatch.await();	
		assertEquals("Number of connected clients", 1, server.getConnectedClientCount());
		
		// Disconnect and wait for the server to call the event handler
		disconnect(socket);
		disconnectLatch.await();
		assertEquals("Number of connected clients", 0, server.getConnectedClientCount());
		
		server.stop();

	}
	
	@Test(timeout = 200) 
	public void testConnectionFromSameClient() throws Exception {

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
		NioTcpServer server = new NioTcpServer(address, log);
		server.setEventHandler(eventHandler);
		server.start();
		
		// Connect and wait for the server to call the event handler
		Socket socket1 = connect((InetSocketAddress) server.getLocalAddress());
		connectLatch.await();	
		assertEquals("Number of connected clients", 1, server.getConnectedClientCount());
		
		// Connect another and wait for the server to call the event handler
		// The new connection should replace the existing one
		connectLatch = new CountDownLatch(1);
		Socket socket2 = connect((InetSocketAddress) server.getLocalAddress());
		connectLatch.await();	
		assertEquals("Number of connected clients", 1, server.getConnectedClientCount());
		
		// Disconnect and wait for the server to call the event handler
		disconnect(socket1);
		disconnectLatch = new CountDownLatch(1);
		disconnect(socket2);
		disconnectLatch.await();
		assertEquals("Number of connected clients", 0, server.getConnectedClientCount());
		
		server.stop();
	}
	
	@Test(timeout = 200) 
	public void testWrite() throws Exception {

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
		NioTcpServer server = new NioTcpServer(address, log);
		server.setEventHandler(eventHandler);
		server.start();
		
		// Connect and wait for the server to call the event handler
		Socket socket = connect((InetSocketAddress) server.getLocalAddress());
		connectLatch.await();	

		byte[] bytes = new byte[]{1,2,3,4};
		ByteBuffer data = ByteBuffer.wrap(bytes);
		server.write((InetSocketAddress) socket.getLocalSocketAddress(), data);
		
		byte[] buffer = new byte[4];
		socket.setSoTimeout(50);
		try {
			socket.getInputStream().read(buffer);
			assertArrayEquals("Received data should match sent data", bytes, buffer);
		} catch (IOException e) {
			fail("No data received");
		} finally {
			// Disconnect and wait for the server to call the event handler
			disconnect(socket);
			disconnectLatch.await();
			
			server.stop();
		}
	}
	
	private Socket connect(InetSocketAddress address) {
		Socket socket=new Socket();
		try {
			socket.connect(address);
			return socket;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private void disconnect(Socket socket) {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		socket=null;
	}
}
