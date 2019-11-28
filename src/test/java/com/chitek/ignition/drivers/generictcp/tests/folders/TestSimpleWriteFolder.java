package com.chitek.ignition.drivers.generictcp.tests.folders;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.Before;
import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.folder.SimpleWriteFolder;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.tests.MockDriverContext;
import com.chitek.ignition.drivers.generictcp.tests.TestUtils;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.WritebackDataType;
import com.inductiveautomation.xopc.driver.util.TagTree.TagTreeNode;

public class TestSimpleWriteFolder {

	private static final String DEVICE_NAME = "DeviceName";
	private MockDriverContext driverContext;
	
	@Before
	public void setup() throws Exception {
		driverContext = new MockDriverContext(DEVICE_NAME);
	}
	
	@Test
	public void testBrowseTree() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
	
		TagTreeNode<String> rootNode = driverContext.getBrowseTree().findTag("device1/[Writeback]");
		assertNotNull(rootNode);
		assertNotNull(rootNode.getTag());
		
		String[] expectedNodes = new String[]{"device1/[Writeback]/Write", "device1/[Writeback]/ID", "device1/[Writeback]/Value"};
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
	public void testRead() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		SimpleWriteFolder folder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		
		// Not connected - read should response with the initial value but state 'not connected'
		DataValue value = FolderTestUtils.readValue(folder, "device1/[Writeback]/Value");
		assertEquals(new Variant("0x38,0x39"), value.getValue());
		assertEquals(StatusCodes.Bad_NotConnected, value.getStatusCode().getValue());
				
		// Connect
		folder.connectionStateChanged(true);
		
		// Connected - read should response with the initial value and state 'good'
		value = FolderTestUtils.readValue(folder, "device1/[Writeback]/Value");
		assertEquals(new Variant("0x38,0x39"), value.getValue());
		assertEquals(StatusCode.GOOD, value.getStatusCode());		
	}
	
	@Test
	public void testTriggeredWrite() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		SimpleWriteFolder folder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		
		// Write an id
		FolderTestUtils.writeValue(folder, "device1/[Writeback]/ID", new Variant((byte)77));
		FolderTestUtils.writeValue(folder, "device1/[Writeback]/Value", new Variant("0x1,0x2"));
		
		
		// Now send a message by setting the Write tag
		StatusCode statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Write", new Variant(true));
		// That should fail because we are not connected
		assertEquals(StatusCodes.Bad_NotConnected, statusCode.getValue());
		
		// Connect
		folder.activityLevelChanged(true);
		folder.connectionStateChanged(true);
		
		// Folder should send initial message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 7,(byte) 0xff,(byte) 0xff,99,0x38,0x39}, driverContext.getLastWrittenMessage());		
		
		// 
		statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Write", new Variant(true));
		assertEquals(StatusCode.GOOD, statusCode);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 7,(byte) 0xff,(byte) 0xff,77,1,2}, driverContext.getLastWrittenMessage());
	}
	
	@Test
	public void testWriteOnChange() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		writebackConfig.setSendOnValueChange(true);
		SimpleWriteFolder folder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		
		// Connect
		folder.activityLevelChanged(true);
		folder.connectionStateChanged(true);
		
		// Folder should send initial message
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 7,(byte) 0xff,(byte) 0xff,99,0x38,0x39}, driverContext.getLastWrittenMessage());	
		
		StatusCode statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Value", new Variant("0x65,0x66"));
		assertEquals(StatusCode.GOOD, statusCode);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 7,(byte) 0xff,(byte) 0xff,99,0x65,0x66}, driverContext.getLastWrittenMessage());
		
		// Changing the id should not trigger sending
		statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/ID", new Variant(45));
		assertEquals(StatusCode.GOOD, statusCode);
		assertEquals(0, driverContext.getExecutor().getScheduledCount());
		
		// Manual write should still be possible
		statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Write", new Variant(true));
		assertEquals(StatusCode.GOOD, statusCode);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 7,(byte) 0xff,(byte) 0xff,45,0x65,0x66}, driverContext.getLastWrittenMessage());		
	}
	
	@Test
	public void testNumericIntValue() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		writebackConfig.setDataType(WritebackDataType.Int32);
		writebackConfig.setSendOnValueChange(true);
		writebackConfig.setSendInitialValue(false);
		SimpleWriteFolder folder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		
		// Connect
		folder.connectionStateChanged(true);
		
		StatusCode statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Value", new Variant(199999));
		assertEquals(StatusCode.GOOD, statusCode);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 9,(byte) 0xff,(byte) 0xff,99,0x00,0x03,0x0d,0x3f}, driverContext.getLastWrittenMessage());
		
		// Test  a negative value
		statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Value", new Variant(-199999));
		assertEquals(StatusCode.GOOD, statusCode);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 9,(byte) 0xff,(byte) 0xff,99,(byte) 0xff,(byte) 0xfc,(byte) 0xf2,(byte) 0xc1}, driverContext.getLastWrittenMessage());
	}
	
	@Test
	public void testNumericUIntValue() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		writebackConfig.setDataType(WritebackDataType.UInt32);
		writebackConfig.setSendOnValueChange(true);
		writebackConfig.setSendInitialValue(false);
		SimpleWriteFolder folder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		
		// Connect
		folder.connectionStateChanged(true);
		
		StatusCode statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Value", new Variant(4294967295l));
		assertEquals(StatusCode.GOOD, statusCode);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 9,(byte) 0xff,(byte) 0xff,99,(byte) 0xff,(byte) 0xff,(byte) 0xff,(byte) 0xff}, driverContext.getLastWrittenMessage());
	}
	
	@Test
	public void testReverseByteOrder() throws Exception {
		// DriverSettings with ReverseByteOrder = true
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, true, 1, (2^32)-1, OptionalDataType.None);
		
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		writebackConfig.setDataType(WritebackDataType.Int32);
		writebackConfig.setSendOnValueChange(true);
		writebackConfig.setSendInitialValue(false);
		SimpleWriteFolder folder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		
		// Connect
		folder.connectionStateChanged(true);
		
		StatusCode statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Value", new Variant(199999));
		assertEquals(StatusCode.GOOD, statusCode);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{9, 0,(byte) 0xff,(byte) 0xff,99,0x3f,0x0d,0x03,0x00}, driverContext.getLastWrittenMessage());
	}

	@Test
	public void testActivityLevel() throws Exception {
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		
		SimpleWriteFolder folder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		
		// Connect
		folder.connectionStateChanged(true);
		
		// Folder should NOT send initial message because the folder is not active
		assertEquals(0, driverContext.getExecutor().getScheduledCount());
		
		// Now make the folder active. The initial message should be send now.
		folder.activityLevelChanged(true);
		
		assertEquals(1, driverContext.getExecutor().getScheduledCount());
		driverContext.getExecutor().runCommand();
		assertArrayEquals(new byte[]{0, 7,(byte) 0xff,(byte) 0xff,99,0x38,0x39}, driverContext.getLastWrittenMessage());
	
		
		// Not active again
		folder.activityLevelChanged(false);
		
		// Writes should not be executed
		StatusCode statusCode = FolderTestUtils.writeValue(folder, "device1/[Writeback]/Write", new Variant(true));
		assertEquals(StatusCodes.Bad_NotConnected, statusCode.getValue());
		assertEquals(0, driverContext.getExecutor().getScheduledCount());
	}
}
