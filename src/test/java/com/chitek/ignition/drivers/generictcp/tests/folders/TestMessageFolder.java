package com.chitek.ignition.drivers.generictcp.tests.folders;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.chitek.ignition.drivers.generictcp.folder.IndexMessageFolder;
import com.chitek.ignition.drivers.generictcp.folder.MessageDataWrapper;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.redundancy.StateUpdate;
import com.chitek.ignition.drivers.generictcp.tests.MockDriverContext;
import com.chitek.ignition.drivers.generictcp.tests.TestUtils;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.QueueMode;
import com.chitek.ignition.drivers.generictcp.util.VariantByteBuffer;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.util.TagTree.TagTreeNode;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

public class TestMessageFolder {

	private static final String DEVICE_NAME = "DeviceName";

	private MockDriverContext driverContext;

    @ClassRule
    public static TemporaryFolder testFolder = new TemporaryFolder();
	
	@Before
	public void setup() throws Exception {
		driverContext = new MockDriverContext(DEVICE_NAME);
		driverContext.setDiskPath(testFolder.newFolder().getAbsolutePath());
	}

	@Test
	public void testMessageDataWrapper() throws Exception {
		MessageDataWrapper wrapper = new MessageDataWrapper();
		byte[] data = MessageDataWrapper.wrapMessage(1000, 2499, (short)2, new byte[]{1,2,3,4},ByteOrder.BIG_ENDIAN);
		int payloadLength = wrapper.evaluateData(new VariantByteBuffer(data));
		assertEquals(4, payloadLength);
		assertEquals(1000, wrapper.getTimeReceived());
		assertEquals(2, wrapper.getSequenceId());
		assertEquals(2499, wrapper.getHeaderTimestamp());
	}
	
	@Test
	public void testMessageFolder() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		assertEquals(folder.getDriverContext().getDeviceName(), DEVICE_NAME);

		// Check the browse tree
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/Data1"));
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/_MessageCount"));
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/_Timestamp"));

		// Check the nodes
		org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode nodeData1 = (VariableNode) driverContext.getNode(buildNodeId("Alias1/Data1"));
		assertNotNull(nodeData1);
		assertEquals(new org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText("Data1"), nodeData1.getDisplayName());
		assertEquals(BuiltinDataType.String.getNodeId(), nodeData1.getDataType());
		assertEquals(Integer.valueOf(ValueRank.Scalar.getValue()), nodeData1.getValueRank());

		// Test read
		MockReadItem itemData1 = new MockReadItem("Alias1/Data1");
		List<ReadItem> items = new ArrayList<ReadItem>();
		items.add(itemData1);
		folder.readItems(items);
		assertNotNull(itemData1.getValue());

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,66}, null); // 65,66 == 'AB'

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated
		folder.readItems(items);
		DataValue data1Value = itemData1.getValue();
		assertNotNull(data1Value);
		assertEquals("AB", data1Value.getValue().getValue());
	}

	@Test
	public void testMessageFolderWithId() throws Exception {

		// Create settings with message id type = UInt16
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.UInt16);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		assertEquals(folder.getDriverContext().getDeviceName(), DEVICE_NAME);

		// Check the browse tree
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/Data1"));
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/_MessageCount"));
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/_Timestamp"));

		// Check the nodes
		VariableNode nodeData1 = (VariableNode) driverContext.getNode(buildNodeId("Alias1/Data1"));
		assertNotNull(nodeData1);
		assertEquals(new LocalizedText("Data1"), nodeData1.getDisplayName());
		assertEquals(BuiltinDataType.String.getNodeId(), nodeData1.getDataType());
		assertEquals(Integer.valueOf(ValueRank.Scalar.getValue()), nodeData1.getValueRank());

		// Test read
		MockReadItem itemData1 = new MockReadItem("Alias1/Data1");
		List<ReadItem> items = new ArrayList<ReadItem>();
		items.add(itemData1);
		folder.readItems(items);
		assertNotNull(itemData1.getValue());

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,65,66}, null); // 65,66 == 'AB'

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated
		folder.readItems(items);
		DataValue data1Value = itemData1.getValue();
		assertNotNull(data1Value);
		assertEquals("AB", data1Value.getValue().getValue());
	}
	
	@Test
	public void testMessageFolderWithMessageAge() throws Exception {

		// Create settings with message id type = UInt16
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.UInt16);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigWithAge.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		assertEquals(folder.getDriverContext().getDeviceName(), DEVICE_NAME);

		// Check the browse tree
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/Data1"));
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/_MessageCount"));
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/_Timestamp"));
		assertNotNull(driverContext.getBrowseTree().findTag("Alias1/_MessageAge"));

		// Check the nodes
		VariableNode nodeData1 = (VariableNode) driverContext.getNode(buildNodeId("Alias1/Data1"));
		assertNotNull(nodeData1);
		assertEquals(new LocalizedText("Data1"), nodeData1.getDisplayName());
		assertEquals(BuiltinDataType.Int16.getNodeId(), nodeData1.getDataType());
		assertEquals(Integer.valueOf(ValueRanks.Scalar), nodeData1.getValueRank());
		
		VariableNode nodeAge = (VariableNode) driverContext.getNode(buildNodeId("Alias1/_MessageAge"));
		assertNotNull(nodeAge);
		assertEquals(new LocalizedText("_MessageAge"), nodeAge.getDisplayName());
		assertEquals(BuiltinDataType.UInt32.getNodeId(), nodeAge.getDataType());
		assertEquals(Integer.valueOf(ValueRanks.Scalar), nodeAge.getValueRank());

		// Test read
		MockReadItem itemData1 = new MockReadItem("Alias1/Data1");
		MockReadItem itemAge = new MockReadItem("Alias1/_MessageAge");
		List<ReadItem> items = new ArrayList<ReadItem>();
		items.add(itemData1);
		items.add(itemAge);
		folder.readItems(items);
		assertNotNull(itemData1.getValue());

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,20, 0,1,0,2,0,0,0,10}, null); // Data1=1, Data2=2, Age=10ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated
		folder.readItems(items);
		DataValue data1Value = itemData1.getValue();
		assertNotNull(data1Value);
		assertEquals((short)1, data1Value.getValue().getValue());
		
		folder.readItems(items);
		DataValue ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", uint(10), ageValue.getValue().getValue());
	}

	@Test
	public void testMessageFolderWithMessageAgeOverflow() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigWithAge.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		assertEquals(folder.getDriverContext().getDeviceName(), DEVICE_NAME);
		
		// Test read
		MockReadItem itemData1 = new MockReadItem("Alias1/Data1");
		MockReadItem itemAge = new MockReadItem("Alias1/_MessageAge");
		List<ReadItem> items = new ArrayList<ReadItem>();
		items.add(itemData1);
		items.add(itemAge);
		folder.readItems(items);
		assertNotNull(itemData1.getValue());

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,10, 0,1,0,2, (byte) 0xff,(byte) 0xff,(byte) 0xff,(byte) 0xf6}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		DataValue ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", uint(20), ageValue.getValue().getValue());
	}
	
	@Test
	public void testMessageFolderWithMessageAgeOverflow2() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, 2147483647, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigWithAge.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		assertEquals(folder.getDriverContext().getDeviceName(), DEVICE_NAME);
		
		// Test read
		MockReadItem itemData1 = new MockReadItem("Alias1/Data1");
		MockReadItem itemAge = new MockReadItem("Alias1/_MessageAge");
		List<ReadItem> items = new ArrayList<ReadItem>();
		items.add(itemData1);
		items.add(itemAge);
		folder.readItems(items);
		assertNotNull(itemData1.getValue());

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,10, 0,1,0,2, (byte) 0x7f,(byte) 0xff,(byte) 0xff,(byte) 0xff}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		DataValue ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", uint(11), ageValue.getValue().getValue());
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,10, 0,1,0,2, 0,0,0,0}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", uint(10), ageValue.getValue().getValue());
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,1,0,2, (byte) 0x7f,(byte) 0xff,(byte) 0xff,(byte) 0xff}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", uint(1), ageValue.getValue().getValue());
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,(byte) 0x7f,(byte) 0xff,(byte) 0xff,(byte) 0xf0, 0,1,0,2, (byte) 0x7f,(byte) 0xff,(byte) 0xff,(byte) 0xe0}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", uint(16), ageValue.getValue().getValue());
	}
	
	@Test
	public void testFolderWithHandshake() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		messageConfig.setQueueMode(QueueMode.HANDSHAKE);

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);

		byte[] handshakeMessage = new byte[]{1,2,3,4};

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,67}, handshakeMessage); // 65,67 == 'AC'

		assertArrayEquals(handshakeMessage, driverContext.getLastWrittenMessage());
		
		folder.shutdown();
	}
	
	@Test
	public void testFolderWithHandshakeNoQueue() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		messageConfig.setQueueMode(QueueMode.NONE);

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);

		byte[] handshakeMessage = new byte[]{1,2,3,4};

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,67}, handshakeMessage); // 65,67 == 'AC'
		
		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		assertArrayEquals(handshakeMessage, driverContext.getLastWrittenMessage());
	}
	
	@Test
	public void testQueueMode() throws Exception {
		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigQueue.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		VariableNode nodeQueueSize = (VariableNode) driverContext.getNode(buildNodeId("Alias1/_QueueSize"));
		assertNotNull("Folder in Queue mode should have a _QueueSize tag", nodeQueueSize);
		
		byte[] message = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,66};
		folder.messageArrived(message, null); // 65,66 == 'AB'
		
		// The folder is not active, so it should not have tried to evaluate the message
		assertEquals(0, driverContext.getExecutor().getScheduledCount());
		
		// Add a second message
		message = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,67,68};
		folder.messageArrived(message, null); // 67,68 == 'CD'
		
		// QueueSize should be 2 now
		DataValue queueSize = FolderTestUtils.readValue(folder,"Alias1/_QueueSize");
		assertEquals(ushort(2), queueSize.getValue().getValue());
		
		// Now activate the folder
		folder.activityLevelChanged(true);
		
		// The folder should evaluate the first queued message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		DataValue value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals("AB", value.getValue().getValue());
			
		// MessageCount should be 1
		DataValue messageCount = FolderTestUtils.readValue(folder,"Alias1/_MessageCount");
		assertEquals(uint(1), messageCount.getValue().getValue());
		// Handshake should be 1
		DataValue handshake = FolderTestUtils.readValue(folder,"Alias1/_Handshake");
		assertEquals(uint(1), handshake.getValue().getValue());		
		
		// Now set the Handshake
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(0));		
		
		// QueueSize should be 1 now
		queueSize = FolderTestUtils.readValue(folder,"Alias1/_QueueSize");
		assertEquals(ushort(1), queueSize.getValue().getValue());
		
		// The folder should evaluate the next queued message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals("CD", value.getValue().getValue());

		// MessageCount should be 2
		messageCount = FolderTestUtils.readValue(folder,"Alias1/_MessageCount");
		assertEquals(uint(2), messageCount.getValue().getValue());
		
		// Now set the Handshake again
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(0));
		
		// Folder should try to evaluate another message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		folder.shutdown();
	}
	
	@Test
	public void testQueueModePersistant() throws Exception {
		
		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigPersistant.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		VariableNode nodeQueueSize = (VariableNode) driverContext.getNode(buildNodeId("Alias1/_QueueSize"));
		assertNotNull("Folder in Queue mode should have a _QueueSize tag", nodeQueueSize);
		
		byte[] message = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,66};
		folder.messageArrived(message, null); // 65,66 == 'AB'
		
		// The folder is not active, so it should not have tried to evaluate the message
		assertEquals(0, driverContext.getExecutor().getScheduledCount());
		
		// The file should exist
		String path = driverContext.getDiskPath() + String.format("%s%d%s", IndexMessageFolder.QUEUE_FILE_PREFIX, folder.getFolderId(), IndexMessageFolder.QUEUE_FILE_EXTENSION);

		Path file = Paths.get(path);
		assertTrue("Queue file has not been created",Files.exists(file));
		assertEquals(124,Files.size(file));
		
		// Add a second message
		message = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,67,68};
		folder.messageArrived(message, null); // 67,68 == 'CD'
		
		// QueueSize should be 2 now
		DataValue queueSize = FolderTestUtils.readValue(folder,"Alias1/_QueueSize");
		assertEquals(ushort(2), queueSize.getValue().getValue());
		
		assertEquals(166,Files.size(file));
		
		// Shutdown the folder
		folder.shutdown();
		
		// Reinitialize the folder - messages should be read from queue file
		folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		// QueueSize should be 2 now
		queueSize = FolderTestUtils.readValue(folder,"Alias1/_QueueSize");
		assertEquals("Wrong message count read from queue file",ushort(2), queueSize.getValue().getValue());
		
		// Now activate the folder
		folder.activityLevelChanged(true);
		
		// The folder should evaluate the first queued message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		DataValue value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals("AB", value.getValue().getValue());
			
		// MessageCount should be 1
		DataValue messageCount = FolderTestUtils.readValue(folder,"Alias1/_MessageCount");
		assertEquals(uint(1), messageCount.getValue().getValue());
		// Handshake should be 1
		DataValue handshake = FolderTestUtils.readValue(folder,"Alias1/_Handshake");
		assertEquals(uint(1), handshake.getValue().getValue());		
		
		// Now set the Handshake
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(0));		
		
		// QueueSize should be 1 now
		queueSize = FolderTestUtils.readValue(folder,"Alias1/_QueueSize");
		assertEquals(ushort(1), queueSize.getValue().getValue());
		
		// The folder should evaluate the next queued message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals("CD", value.getValue().getValue());

		// MessageCount should be 2
		messageCount = FolderTestUtils.readValue(folder,"Alias1/_MessageCount");
		assertEquals(uint(2), messageCount.getValue().getValue());
		
		// Now set the Handshake again
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(0));
		
		// Folder should try to evaluate another message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		folder.shutdown();
	}
	
	@Test
	public void testRedundancy() throws Exception {
		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigQueue.xml");
		List<StateUpdate> stateUpdates = new LinkedList<StateUpdate>();

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		folder.activityLevelChanged(true);
		// Changing activity level starts a queue evaluation
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();		
		
		byte[] message = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,66};
		folder.messageArrived(message, null); // 65,66 == 'AB'
		
		// folder should have posted a state update
		StateUpdate stateUpdate = driverContext.getLastStateUpdate();
		byte[] msg = (byte[]) getFolderUpdateStateField(stateUpdate, "message");
		assertArrayEquals(message, msg);
		stateUpdates.add(stateUpdate);
		
		// Evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		// Now set the Handshake
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(false));
		
		// Folder should post a remove update
		stateUpdate = driverContext.getLastStateUpdate();
		msg = (byte[]) getFolderUpdateStateField(stateUpdate, "message");
		assertArrayEquals(Arrays.copyOf(message, 8), msg);
		stateUpdates.add(stateUpdate);		
		
		// A second message
		message = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,67,68};
		folder.messageArrived(message, null);
		
		// folder should have posted another state update
		stateUpdate = driverContext.getLastStateUpdate();
		msg = (byte[]) getFolderUpdateStateField(stateUpdate, "message");
		assertArrayEquals(message, msg);
		stateUpdates.add(stateUpdate);
		
		// Create a new folder and transfer the states back
		driverContext.getExecutor().clear(); // There's still 1 command pending - remove it
		IndexMessageFolder backupFolder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		for (StateUpdate state : stateUpdates) {
			backupFolder.updateRuntimeState(state);
		}
		
		// Now make the backup folder active
		backupFolder.activityLevelChanged(true);
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		// We acknowledged the first message, so the backup should start with the second message
		DataValue value = FolderTestUtils.readValue(backupFolder,"Alias1/Data1");
		assertEquals("CD", value.getValue().getValue());
		
		folder.shutdown();
	}
	
	@Test
	public void testFullStateTransfer() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigQueue.xml");

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		folder.activityLevelChanged(true);
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,65,66}, null); // 65,66 == 'AB'
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,67,68}, null); // 'CD'
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,67,68}, null); // 'EF'
		
		StateUpdate fullState = folder.getFullState();
		
		// Create a new folder and set the full state
		driverContext.getExecutor().clear(); // There's still commands pending - remove them
		IndexMessageFolder backupFolder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		backupFolder.setFullState(fullState);
		
		// Now make the backup folder active
		backupFolder.activityLevelChanged(true);
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		// We acknowledged the first message, so the backup should start with the second message
		DataValue value = FolderTestUtils.readValue(backupFolder,"Alias1/Data1");
		assertEquals("AB", value.getValue().getValue());
		
		folder.shutdown();
	}
	
	@Test
	public void testBrowseTree() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		messageConfig.setQueueMode(QueueMode.HANDSHAKE);
		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
	
		TagTreeNode<String> rootNode = driverContext.getBrowseTree().findTag("Alias1");
		assertNotNull(rootNode);
		assertNotNull(rootNode.getTag());
		
		String[] expectedNodes = new String[]{"Alias1/Data1", "Alias1/_Timestamp", "Alias1/_MessageCount", "Alias1/_Handshake", "Alias1/_QueueSize"};
		List<String> browseNodes = new ArrayList<String>();
		for (TagTreeNode<String> childNode : rootNode.getChildren()) {
			// TagTreeNode.Address contains a modified address to use the TagTree with Arrays
			// See 'addTagToBrowseTree'
			// TagTreeNode.Tag contains the real address of the tag
			browseNodes.add(childNode.getTag());
			System.out.println("BrowseNode: " + childNode.getAddress() + " Tag:" + childNode.getTag());
		}
		assertArrayEquals(expectedNodes, browseNodes.toArray());
		
		folder.shutdown();
	}
	
	@Test
	public void testInvalidMessage() throws Exception {
		// Here we simply test that no Exception is thrown
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65}, null); // Message is 1 byte too short

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		folder.shutdown();
	}
	
	@Test
	public void testDataTypes() throws Exception {
		// Here we simply test that no Exception is thrown
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigTypes.xml");
		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		ByteBuffer buffer = ByteBuffer.allocate(100);
		buffer.put(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0});	// Timestamps
		buffer.put(" bcde".getBytes()); 	// Data 1 - String
		buffer.put(new byte[]{1,2,0,(byte)254,(byte)255});	// Data 2 - ByteString
		
		buffer.flip();
		byte[]data = new byte[buffer.remaining()];
		buffer.get(data);
		folder.messageArrived(data, null);

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		DataValue value1 = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals("Result String should be trimmed", "bcde", value1.getValue().getValue());
		DataValue value2 = FolderTestUtils.readValue(folder,"Alias1/Data2");
		char[] charvalue = new char[5];
		((String)value2.getValue().getValue()).getChars(0,5,charvalue,0);
		assertArrayEquals(new char[]{1,2,0,254,255}, charvalue);
		
		folder.shutdown();
	}
	
	@Test
	public void testPackeBasedMessageMinLength() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigPacketBased.xml");
		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		assertEquals(5, messageConfig.getMessageLength());
		
		// Message config defined one Int16 tag and then a variable length String with minimal length 3
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 1,1, 'a','b','c'}, null); // Message is 1 byte too short

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		DataValue value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals((short)257, value.getValue().getValue());
		DataValue value2 = FolderTestUtils.readValue(folder,"Alias1/Data2");
		assertEquals("abc", value2.getValue().getValue());
		
		folder.shutdown();
	}	
	
	@Test
	public void testPackeBasedMessage() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigPacketBased.xml");
		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		assertEquals(5, messageConfig.getMessageLength());
		
		// Message config defined one Int16 tag and then a variable length String with minimal length 3
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 1,1, 'a','b','c','d','e'}, null); // Message is 1 byte too short

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		
		DataValue value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals((short)257, value.getValue().getValue());
		DataValue value2 = FolderTestUtils.readValue(folder,"Alias1/Data2");
		assertEquals("abcde", value2.getValue().getValue());
		
		folder.shutdown();
	}	
	
	private NodeId buildNodeId(String address) {
		return new NodeId(1, String.format("[%s]%s", DEVICE_NAME, address));
	}
		
	/**
	 * FolderUpdateState is a private class, we have to acces the fields using refelction.
	 *
	 * @param stateUpdate
	 * @param fieldName
	 * @return
	 * @throws Exception
	 */
	private Object getFolderUpdateStateField(StateUpdate stateUpdate, String fieldName) throws Exception {

			Field field = stateUpdate.getClass().getDeclaredField(fieldName);
			if (field != null) {
				field.setAccessible(true);
				return field.get(stateUpdate);
			}
		
		return null;
	}
}
