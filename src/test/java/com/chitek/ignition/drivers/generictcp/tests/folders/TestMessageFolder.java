package com.chitek.ignition.drivers.generictcp.tests.folders;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
import com.inductiveautomation.opcua.nodes.VariableNode;
import com.inductiveautomation.opcua.types.DataType;
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.LocalizedText;
import com.inductiveautomation.opcua.types.NodeId;
import com.inductiveautomation.opcua.types.UInt16;
import com.inductiveautomation.opcua.types.UInt32;
import com.inductiveautomation.opcua.types.ValueRank;
import com.inductiveautomation.opcua.types.Variant;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.util.TagTree.TagTreeNode;

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
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
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
		assertEquals(DataType.String.getNodeId(), nodeData1.getDataTypeId());
		assertEquals(ValueRank.Scalar, nodeData1.getValueRank());

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
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.UInt16);
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
		assertEquals(DataType.String.getNodeId(), nodeData1.getDataTypeId());
		assertEquals(ValueRank.Scalar, nodeData1.getValueRank());

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
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.UInt16);
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
		assertEquals(DataType.Int16.getNodeId(), nodeData1.getDataTypeId());
		assertEquals(ValueRank.Scalar, nodeData1.getValueRank());
		
		VariableNode nodeAge = (VariableNode) driverContext.getNode(buildNodeId("Alias1/_MessageAge"));
		assertNotNull(nodeAge);
		assertEquals(new LocalizedText("_MessageAge"), nodeAge.getDisplayName());
		assertEquals(DataType.UInt32.getNodeId(), nodeAge.getDataTypeId());
		assertEquals(ValueRank.Scalar, nodeAge.getValueRank());

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
		assertEquals("Message Age", new UInt32(10), ageValue.getValue().getValue());
	}

	@Test
	public void testMessageFolderWithMessageAgeOverflow() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
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
		assertEquals("Message Age", new UInt32(20), ageValue.getValue().getValue());
	}
	
	@Test
	public void testMessageFolderWithMessageAgeOverflow2() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, 2147483647, OptionalDataType.None);
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
		assertEquals("Message Age", new UInt32(11), ageValue.getValue().getValue());
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,10, 0,1,0,2, 0,0,0,0}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", new UInt32(10), ageValue.getValue().getValue());
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,1,0,2, (byte) 0x7f,(byte) 0xff,(byte) 0xff,(byte) 0xff}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", new UInt32(1), ageValue.getValue().getValue());
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,(byte) 0x7f,(byte) 0xff,(byte) 0xff,(byte) 0xf0, 0,1,0,2, (byte) 0x7f,(byte) 0xff,(byte) 0xff,(byte) 0xe0}, null); // Data1=1, Data2=2, Age=20ms

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();

		// Test read after message is evaluated	
		folder.readItems(items);
		ageValue = itemAge.getValue();
		assertNotNull(ageValue);
		assertEquals("Message Age", new UInt32(16), ageValue.getValue().getValue());
	}
	
	@Test
	public void testFolderWithHandshake() throws Exception {

		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		messageConfig.setQueueMode(QueueMode.HANDSHAKE);

		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);

		byte[] handshakeMessage = new byte[]{1,2,3,4};

		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,67}, handshakeMessage); // 65,67 == 'AC'

		assertArrayEquals(handshakeMessage, driverContext.getLastWrittenMessage());
	}
	
	@Test
	public void testQueueMode() throws Exception {
		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
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
		assertEquals(new UInt16(2), queueSize.getValue().getValue());
		
		// Now activate the folder
		folder.activityLevelChanged(true);
		
		// The folder should evaluate the first queued message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		DataValue value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals("AB", value.getValue().getValue());
			
		// MessageCount should be 1
		DataValue messageCount = FolderTestUtils.readValue(folder,"Alias1/_MessageCount");
		assertEquals(new UInt32(1), messageCount.getValue().getValue());
		// Handshake should be false
		DataValue handshake = FolderTestUtils.readValue(folder,"Alias1/_Handshake");
		assertEquals(false, handshake.getValue().getValue());		
		
		// Now set the Handshake
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(true));
		
		// Handshake should immediately reset as there is another message in the queue
		handshake = FolderTestUtils.readValue(folder,"Alias1/_Handshake");
		assertEquals(false, handshake.getValue().getValue());			
		
		// QueueSize should be 1 now
		queueSize = FolderTestUtils.readValue(folder,"Alias1/_QueueSize");
		assertEquals(new UInt16(1), queueSize.getValue().getValue());
		
		// The folder should evaluate the next queued message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		value = FolderTestUtils.readValue(folder,"Alias1/Data1");
		assertEquals("CD", value.getValue().getValue());

		// MessageCount should be 2
		messageCount = FolderTestUtils.readValue(folder,"Alias1/_MessageCount");
		assertEquals(new UInt32(2), messageCount.getValue().getValue());
		
		// Now set the Handshake again
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(true));
		
		// Folder should try to evaluate another message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();		
		
		// Handshake should be true
		handshake = FolderTestUtils.readValue(folder,"Alias1/_Handshake");
		assertEquals(true, handshake.getValue().getValue());	
		
		// Setting the Handshake again should have no effect
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(true));		
	}
	
	@Test
	public void testRedundancy() throws Exception {
		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
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
		FolderTestUtils.writeValue(folder, "Alias1/_Handshake", new Variant(true));
		
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
	}
	
	@Test
	public void testFullStateTransfer() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
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
	}
	
	@Test
	public void testBrowseTree() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		messageConfig.setQueueMode(QueueMode.HANDSHAKE);
		new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
	
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
	}
	
	@Test
	public void testInvalidMessage() throws Exception {
		// Here we simply test that no Exception is thrown
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		IndexMessageFolder folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65}, null); // Message is 1 byte too short

		// The folder should have added a schedule to evaluate the message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
	}	
	
	private NodeId buildNodeId(String address) {
		return new NodeId(String.format("[%s]%s", DEVICE_NAME, address), 1);
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
