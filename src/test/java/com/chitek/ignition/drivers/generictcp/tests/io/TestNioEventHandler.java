package com.chitek.ignition.drivers.generictcp.tests.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.folder.MessageHeader;
import com.chitek.ignition.drivers.generictcp.io.IMessageHandler;
import com.chitek.ignition.drivers.generictcp.io.NioEventHandler;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.tests.DriverTestSuite;
import com.chitek.ignition.drivers.generictcp.tests.MockExecutionManager;
import com.chitek.ignition.drivers.generictcp.tests.TestUtils;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;

public class TestNioEventHandler {

	Logger log;

	MessageHeader messageHeader;
	DriverSettings driverSettings;
	DriverConfig driverConfig;

	IMessageHandler messageHandler;
	int messageId=-1;
	byte[] messageData;
	byte[] handshakeData;
	InetSocketAddress receivedRemoteSocket;


	@Before
	public void setup() throws Exception {
		log = DriverTestSuite.getLogger();

		driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.UInt16);
		driverConfig = new DriverConfig();
		driverConfig.setMessageIdType(driverSettings.getMessageIdType());

		HeaderConfig headerConfig = TestUtils.readHeaderConfig("/testHeaderConfig.xml");
		messageHeader = new MessageHeader(headerConfig, ByteOrder.BIG_ENDIAN);

		messageHandler = new IMessageHandler() {
			@Override
			public void messageReceived(InetSocketAddress remoteSocket, final int id, byte[] data, byte[] handshake) {
				messageId = id;
				messageData = data;
				handshakeData = handshake;
				receivedRemoteSocket = remoteSocket;
			}

			@Override
			public boolean clientConnected(InetSocketAddress remoteSocket) {
				return true;
			}

			@Override
			public void clientDisconnected(InetSocketAddress remoteSocket) {

			}
			
			@Override
			public void clientReadTimeout(InetSocketAddress remoteSocket) {

			}
		};
	}

	@Test
	public void testSimpleMessage() throws Exception {

		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));

		NioEventHandler handler = new NioEventHandler(log, null, driverConfig, driverSettings, messageHeader, messageHandler);
		ByteBuffer data = ByteBuffer.allocate(20);
		data.putShort((short) 10);	 // The packet size (Header + 1 message)
		data.putShort((short) 0xff); // The fixed word
		data.put(new byte[] { 0, 1, 0, 1, 0, 2 });	// Message ID 1
		data.flip();
		InetSocketAddress remoteSocket = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), 1999);
		handler.dataArrived(remoteSocket, data, 0);
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 4, messageData.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 0, 2}, Arrays.copyOfRange(messageData, 16, 20));
		assertEquals(remoteSocket, receivedRemoteSocket);
	}

	@Test
	public void testPacketBasedMessage() throws Exception {

		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfigPacketBased.xml"));

		MockExecutionManager executor = new MockExecutionManager();
		NioEventHandler handler = new NioEventHandler(log, executor, driverConfig, driverSettings, null, messageHandler);
		ByteBuffer data = ByteBuffer.allocate(20);
		data.put(new byte[] { 0, 1, 0, 1, 'a', 'b', 'c', 'd' });	// Message ID 1
		data.flip();
		InetSocketAddress remoteSocket = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), 1999);
		handler.dataArrived(remoteSocket, data, 0);
		
		assertEquals("Timeout handler should be started", 1, executor.getScheduledCount());	
		// execute the timeout handler
		executor.runCommand();
		
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 6, messageData.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 'a', 'b', 'c', 'd'}, Arrays.copyOfRange(messageData, 16, 22));
		assertEquals(remoteSocket, receivedRemoteSocket);
	}
	
	@Test
	public void testMultipleClients() throws Exception {

		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));

		NioEventHandler handler = new NioEventHandler(log, null, driverConfig, driverSettings, messageHeader, messageHandler);
		ByteBuffer data1 = ByteBuffer.allocate(20);
		data1.putShort((short) 10);	 // The packet size (Header + 1 message)
		data1.putShort((short) 0xff); // The fixed word
		data1.flip();

		ByteBuffer data2 = ByteBuffer.allocate(20);
		data2.put(new byte[] { 0, 1, 0, 1, 0, 2 });	// Message ID 1
		data2.flip();
		InetSocketAddress remoteSocket1 = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), 1999);
		InetSocketAddress remoteSocket2 = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,2}), 1999);

		// Send the first part of the packet
		handler.dataArrived(remoteSocket1, data1, 0);
		data1.flip();
		handler.dataArrived(remoteSocket2, data1, 0);

		// Complete packet from client 1
		handler.dataArrived(remoteSocket1, data2, 0);
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 4, messageData.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 0, 2}, Arrays.copyOfRange(messageData, 16, 20));
		assertEquals(remoteSocket1, receivedRemoteSocket);

		// Complete packet from client 2
		messageId = -1;
		data2.flip();
		handler.dataArrived(remoteSocket2, data2, 0);
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 4, messageData.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 0, 2}, Arrays.copyOfRange(messageData, 16, 20));
		assertEquals(remoteSocket2, receivedRemoteSocket);
	}

}
