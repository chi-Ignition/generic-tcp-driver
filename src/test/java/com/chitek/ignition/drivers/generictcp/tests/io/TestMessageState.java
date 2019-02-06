package com.chitek.ignition.drivers.generictcp.tests.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.folder.MessageDataWrapper;
import com.chitek.ignition.drivers.generictcp.folder.MessageHeader;
import com.chitek.ignition.drivers.generictcp.io.IMessageHandler;
import com.chitek.ignition.drivers.generictcp.io.MessageState;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.tests.DriverTestSuite;
import com.chitek.ignition.drivers.generictcp.tests.MockExecutionManager;
import com.chitek.ignition.drivers.generictcp.tests.TestUtils;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.util.VariantByteBuffer;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

public class TestMessageState {

	Logger log;

	MessageHeader messageHeader;
	DriverSettings driverSettings;
	DriverConfig driverConfig;

	InetSocketAddress remoteSocket;
	IMessageHandler messageHandler;
	int messageId;
	byte[] messageDataRaw;
	byte[] messageData;
	byte[] handshakeData;
	MessageDataWrapper dataWrapper = new MessageDataWrapper();

	@Before
	public void setup() throws Exception {
		log = DriverTestSuite.getLogger();

		driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.UInt16);
		driverConfig = new DriverConfig();
		driverConfig.setMessageIdType(driverSettings.getMessageIdType());

		HeaderConfig headerConfig = TestUtils.readHeaderConfig("/testHeaderConfig.xml");
		messageHeader = new MessageHeader(headerConfig, ByteOrder.BIG_ENDIAN);

		remoteSocket = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), 1999);

		messageHandler = new IMessageHandler() {
			@Override
			public void messageReceived(InetSocketAddress remoteSocket, final int id, byte[] data, byte[] handshake) {
				messageDataRaw = data;
				messageId = id;
				handshakeData = handshake;

				VariantByteBuffer buffer = new VariantByteBuffer(data).order(ByteOrder.BIG_ENDIAN);
				int dataLength = dataWrapper.evaluateData(buffer);
				messageData = new byte[dataLength];
				buffer.get(messageData);
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

	@Test(timeout=1000)
	public void testInvalidHeaderShouldReportPacketSize() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 6, 0, 2 });
		state.addData(data);
		assertFalse("Header should be invalid (fixed value mismatch)", state.isHeaderValid());
		assertEquals("PacketSize", 2, state.getPendingBytes());
	}
	
	@Test
	public void testPacketSize() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { (byte) 1, (byte) 100, 0, 2 });
		state.addData(data);
		assertFalse("Header should be invalid (fixed value mismatch)", state.isHeaderValid());
		assertEquals("PacketSize", 356 - 4, state.getPendingBytes());
	}
	
	@Test
	public void testPacketSizeUnsigned() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { (byte) 255, (byte) 255, 0, 2 });
		state.addData(data);
		assertFalse("Header should be invalid (fixed value mismatch)", state.isHeaderValid());
		assertEquals("PacketSize", 65535 - 4, state.getPendingBytes());
	}
	
	@Test
	public void testFixedWord() throws Exception {
		HeaderConfig headerConfig = TestUtils.readHeaderConfig("/testHeaderConfig.xml");
		headerConfig.getTags().get(1).setRawValue("31399");
		messageHeader = new MessageHeader(headerConfig, ByteOrder.BIG_ENDIAN);
		
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 8, (byte) 0x7a, (byte) 0xa7 });
		state.addData(data);
		assertTrue("Header should be valid", state.isHeaderValid());
		assertEquals("PacketSize", 4, state.getPendingBytes());
	}	

	@Test
	public void testFixedByte() throws Exception {
		HeaderConfig headerConfig = TestUtils.readHeaderConfig("/testHeaderConfigByte.xml");
		messageHeader = new MessageHeader(headerConfig, ByteOrder.BIG_ENDIAN);
		
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 6, (byte) 160 });
		state.addData(data);
		assertTrue("Header should be valid", state.isHeaderValid());
		assertEquals("PacketSize", 3, state.getPendingBytes());
	}	
	
	@Test(timeout=1000)
	public void testHeaderWithoutData() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 4 });
		state.addData(data);
		assertFalse("Header without data should be invalid", state.isHeaderValid());
	}

	@Test(timeout=1000)
	public void testValidHeader() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 8, 0, (byte) 0xff });
		state.addData(data);
		assertTrue("Header without data should be valid", state.isHeaderValid());
		assertEquals("Count of pending bytes", 4, state.getPendingBytes());
	}

	@Test(timeout=1000)
	public void testInvalidHeaderShouldDiscardPacket() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 6, 0, 2 });
		state.addData(data);
		assertFalse("Header should be invalid", state.isHeaderValid());
		assertEquals("Count of bytes to discard", 2, state.getPendingBytes());

		data = ByteBuffer.wrap(new byte[] { 0, 6 });
		state.addData(data);
		assertEquals("Count of pending bytes", 0, state.getPendingBytes());
		assertFalse("No pending data", state.isMessagePending());
	}
	
	@Test(timeout=1000)
	public void testPacketWithTwoHeaders() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 6, 0, 2, 0, 0, 0, 6 });
		state.addData(data);

		assertEquals("Count of pending bytes", 0, state.getPendingBytes());
		assertFalse("Haeder should not be complete", state.isHeaderReceived());
		assertFalse("Header should not be valid", state.isHeaderValid());
	}

	@Test(timeout=1000)
	public void testSplittedHeader() throws Exception {
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 8 });
		state.addData(data);

		assertEquals("Count of pending bytes", 0, state.getPendingBytes());
		assertFalse("Haeder should not be complete", state.isHeaderReceived());
		assertFalse("Header should not be valid", state.isHeaderValid());

		// Send the missing header bytes
		data = ByteBuffer.allocate(10).order(driverSettings.getByteOrder());
		data.putShort((short) 0xff);
		data.flip();
		state.addData(data);
		assertEquals("Count of pending bytes", 4, state.getPendingBytes());
		assertTrue("Haeder should be complete", state.isHeaderReceived());
		assertTrue("Header should be valid", state.isHeaderValid());
	}


	@Test(timeout=100000)
	public void testSimpleMessage() throws Exception {
		messageHeader = new MessageHeader(new HeaderConfig(), ByteOrder.BIG_ENDIAN);

		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);

		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 1, 0, 1, 0, 2 });
		state.addData(data);

		assertFalse("Message should be complete", state.isMessagePending());
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 4, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 0, 2}, messageData);
	}

	@Test
	public void testSimpleStringMessage() throws Exception {

		// Create settings with message id type = None
		driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		driverConfig = new DriverConfig();
		driverConfig.setMessageIdType(driverSettings.getMessageIdType());

		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfigSimple.xml"));
		MessageState state = new MessageState(remoteSocket, null, null, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		// testMessageConfigSimple defines ID 0 with one String tag
		ByteBuffer data = ByteBuffer.allocate(20).order(driverSettings.getByteOrder());

		byte[] msg = new byte[]{0, 1};	// Simple two byte message
		data.put(msg);
		data.flip();

		state.addData(data);
		assertEquals("MessageId", 0, messageId);
		assertEquals("Message length including timestamps", 2*8 + 2, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{0,1}, messageData);
		assertEquals("Message should be complete", 0, state.getPendingBytes());
		assertFalse("No pending message", state.isMessagePending());
	}

	@Test
	public void testSplittedSimpleMessage() throws Exception {
		messageHeader = new MessageHeader(new HeaderConfig(), ByteOrder.BIG_ENDIAN);

		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);

		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 1, 0 });
		state.addData(data);

		assertTrue("Message should be incomplete", state.isMessagePending());

		data = ByteBuffer.wrap(new byte[] { 1, 0, 2 });
		state.addData(data);

		assertFalse("Message should be complete", state.isMessagePending());
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2 * 8 + 4, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[] { 0, 1, 0, 2 }, messageData);
	}

	@Test(timeout=1000)
	public void testInvalidMessageId_shouldDiscardPacket() throws Exception {
		messageHeader = new MessageHeader(new HeaderConfig(), ByteOrder.BIG_ENDIAN);

		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		// testMessageConfig defines ID 1 with two UInt16 tags - ID 4 is invalid
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 2, 0, 4, 5, 6, 8, 9 });
		state.addData(data);

		assertFalse("No pending message", state.isMessagePending());
		assertEquals("There should be no delivered message", -1, messageId);

		// The next message should evaluate ok
		data = ByteBuffer.wrap(new byte[] { 0, 1, 0, 1, 0, 2 });
		state.addData(data);

		assertFalse("Message should be complete", state.isMessagePending());
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 4, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 0, 2}, messageData);
	}

	@Test (timeout=1000)
	public void testHeaderWithMessages() throws Exception {
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.allocate(20).order(driverSettings.getByteOrder());
		data.putShort((short) 16);	 // The packet size (Header + 2 messages)
		data.putShort((short) 0xff); // The fixed word

		byte[] msg = new byte[]{0, 1, 0, 2, 0, 3};	// Message ID1
		data.put(msg);
		data.flip();

		state.addData(data);
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 4+2*8, messageDataRaw.length);
		log.info("MessageData: " + ByteUtilities.toString(messageData));
		assertArrayEquals("Message data", new byte[]{0,2,0,3}, messageData);
		assertEquals("One message is pending", 6, state.getPendingBytes());

		// The next message should evaluate ok
		messageId = -1;	// Reset id for next check
		msg = new byte[]{0, 1, 1, 1, 3, 3};	// Message ID1
		data.clear();
		data.put(msg);
		data.flip();
		state.addData(data);

		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps + remote socket info", 4+2*8, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{1,1,3,3}, messageData);
		assertEquals("Packet should be complete", 0, state.getPendingBytes());
	}

	@Test (timeout=1000)
	public void testHeaderWithMessages_sizeWithoutHeader() throws Exception {
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));

		HeaderConfig headerConfig = TestUtils.readHeaderConfig("/testHeaderConfig.xml");
		headerConfig.setSizeIncludesHeader(false);
		messageHeader = new MessageHeader(headerConfig, ByteOrder.BIG_ENDIAN);
		
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.allocate(20).order(driverSettings.getByteOrder());
		data.putShort((short) 12);	 // The packet size (2 messages)
		data.putShort((short) 0xff); // The fixed word

		byte[] msg = new byte[]{0, 1, 0, 2, 0, 3};	// Message ID1
		data.put(msg);
		data.flip();

		state.addData(data);
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 4+2*8, messageDataRaw.length);
		log.info("MessageData: " + ByteUtilities.toString(messageData));
		assertArrayEquals("Message data", new byte[]{0,2,0,3}, messageData);
		assertEquals("One message is pending", 6, state.getPendingBytes());

		// The next message should evaluate ok
		messageId = -1;	// Reset id for next check
		msg = new byte[]{0, 1, 1, 1, 3, 3};	// Message ID1
		data.clear();
		data.put(msg);
		data.flip();
		state.addData(data);

		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 4+2*8, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{1,1,3,3}, messageData);
		assertEquals("Packet should be complete", 0, state.getPendingBytes());
	}
	
	@Test (timeout=1000)
	public void testValidHeaderWithInvalidMessage() throws Exception {
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		
		ByteBuffer data = ByteBuffer.allocate(50).order(driverSettings.getByteOrder());
		data.putShort((short) 16);	 // The packet size (Header + 2 messages)
		data.putShort((short) 0xff); // The fixed word
		data.put(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}); // Invalid message id
		data.put(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}); // Some more invalid data
		data.flip();
		state.addData(data);
	}	
	
	@Test (timeout=1000)
	public void testValidHeaderWithInvalidMessage2() throws Exception {
		log.setLevel(Level.TRACE);
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		
		// Use a header without fixed values
		HeaderConfig headerConfig = TestUtils.readHeaderConfig("/testHeaderConfigSimple.xml");
		messageHeader = new MessageHeader(headerConfig, ByteOrder.BIG_ENDIAN);
		
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		
		ByteBuffer data = ByteBuffer.allocate(50).order(driverSettings.getByteOrder());
		data.putShort((short) 12);	 // The packet size - does not include header here (2 messages)
		data.put(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}); // Invalid message id
		data.put(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}); // Some more invalid data
		data.flip();
		state.addData(data);
	}
	
	@Test (timeout=1000)
	public void testMessagesWithoutHeader() throws Exception {
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		MessageState state = new MessageState(remoteSocket, null, null, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.allocate(20).order(driverSettings.getByteOrder());

		byte[] msg = new byte[]{0, 1, 0, 2, 0, 3};	// Message ID1
		data.put(msg);
		data.flip();

		state.addData(data);
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 4+2*8, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{0,2,0,3}, messageData);
		assertEquals("Message should be complete", 0, state.getPendingBytes());
		assertFalse("No pending message", state.isMessagePending());

		// The next message should evaluate ok
		messageId = -1;	// Reset id for next check
		msg = new byte[]{0, 1, 1, 1, 3, 3};	// Message ID1
		data.clear();
		data.put(msg);
		data.flip();
		state.addData(data);

		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 4+2*8, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{1,1,3,3}, messageData);
		assertEquals("Message should be complete", 0, state.getPendingBytes());
		assertFalse("No pending message", state.isMessagePending());
	}

	@Test (timeout=1000)
	public void testMessagesWithMessageAge() throws Exception {
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfigWithAge.xml"));
		MessageState state = new MessageState(remoteSocket, null, null, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		messageId = -1;
		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.allocate(20).order(driverSettings.getByteOrder());

		byte[] msg = new byte[]{0, 1, 0, 2, 0, 3, 0, 0, 0, 10};	// Message ID1, Age 10ms
		data.put(msg);
		data.flip();

		state.addData(data);
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 8+2*8, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{0,2,0,3,0,0,0,10}, messageData);
		assertEquals("Message should be complete", 0, state.getPendingBytes());
		assertFalse("No pending message", state.isMessagePending());
	}
	
	@Test(timeout=1000)
	public void testHandshake() throws Exception {
		messageHeader = new MessageHeader(TestUtils.readHeaderConfig("/testHeaderConfigHandshake.xml"), ByteOrder.BIG_ENDIAN);
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		MessageState state = new MessageState(remoteSocket, null, messageHeader, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);

		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.allocate(20).order(driverSettings.getByteOrder());
		data.putShort((short) 16);	 // The packet size (Header + 2 messages)
		data.putShort((short) 0xff); // The fixed word
		byte[] msg = new byte[]{0, 1, 0, 2, 0, 3};	// Message ID1
		data.put(msg);
		data.flip();

		messageId=-1;
		state.addData(data);
		assertEquals("MessageId", 1, messageId);

		// The next message should send the handshake
		messageId = -1;	// Reset id for next check
		handshakeData = null;
		msg = new byte[]{0, 1, 1, 1, 3, 3};	// Message ID1
		data.clear();
		data.put(msg);
		data.flip();
		state.addData(data);

		assertNotNull("Handshake message should be there", handshakeData);
		assertArrayEquals("Handshake message", new byte[]{0, 4, (byte) 0xff, (byte) 0xfe}, handshakeData);
	}
	
	@Test(timeout=1000)
	public void testTimeout() throws Exception {
		// The timeout handler should not be started for a fixed length message
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfig.xml"));
		
		MockExecutionManager executor = new MockExecutionManager();
		
		MessageState state = new MessageState(remoteSocket, executor, null, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		
		// testMessageConfig defines ID 1 with two UInt16 tags
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 1, 0, 1, 0, 2 });
		state.addData(data);

		assertEquals("Timeout handler should not be started", 0, executor.getScheduledCount());
		assertFalse("Message should be complete", state.isMessagePending());
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 4, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 0, 2}, messageData);
		
		// send an incomplete message
		data = ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 });
		state.addData(data);
		
		assertEquals("Timeout handler should not be started", 0, executor.getScheduledCount());
		assertTrue("Message should be pending", state.isMessagePending());
	}

	@Test
	public void testTimeoutPacketBased() throws Exception {
		driverConfig.addMessageConfig(TestUtils.readMessageConfig("/testMessageConfigPacketBased.xml"));
		
		MockExecutionManager executor = new MockExecutionManager();
		
		MessageState state = new MessageState(remoteSocket, executor, null, driverConfig, driverSettings);
		state.setMessageHandler(messageHandler);
		
		// testMessageConfigPacketBased defines one Int16 tag and then a variable length String with minimal length 3
		ByteBuffer data = ByteBuffer.wrap(new byte[] { 0, 1, 0, 1, 'a', 'b', 'c', 'd' });
		state.addData(data);

		assertEquals("Timeout handler should be started", 1, executor.getScheduledCount());
		assertTrue("Message should be pending", state.isMessagePending());
		
		// execute the timeout handler
		executor.runCommand();
		
		// Message should be delivered now
		assertEquals("MessageId", 1, messageId);
		assertEquals("Message length including timestamps", 2*8 + 6, messageDataRaw.length);
		assertArrayEquals("Message data", new byte[]{0, 1, 'a', 'b', 'c', 'd'}, messageData);
	}
	
}
