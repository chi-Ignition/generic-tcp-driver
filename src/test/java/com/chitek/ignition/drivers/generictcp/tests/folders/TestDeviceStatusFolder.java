package com.chitek.ignition.drivers.generictcp.tests.folders;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.folder.DeviceStatusFolder;
import com.chitek.ignition.drivers.generictcp.tests.MockDriverContext;
import com.inductiveautomation.opcua.nodes.Node;
import com.inductiveautomation.opcua.types.NodeClass;
import com.inductiveautomation.opcua.types.NodeId;
import com.inductiveautomation.xopc.driver.util.TagTree.TagTreeNode;

public class TestDeviceStatusFolder {

	private static final String DEVICE_NAME = "DeviceName";
	private MockDriverContext driverContext;

	@Before
	public void setup() throws Exception {
		driverContext = new MockDriverContext(DEVICE_NAME);
	}
	
	@Test
	public void testBrowseTree() throws Exception {
		new DeviceStatusFolder(driverContext, 1, "Device1");
	
		TagTreeNode<String> folderNode = driverContext.getBrowseTree().findTag("Device1");
		assertNotNull(folderNode);
		assertNotNull(folderNode.getTag());		
		
		TagTreeNode<String> rootNode = driverContext.getBrowseTree().findTag("Device1/[Status]");
		assertNotNull(rootNode);
		assertNotNull(rootNode.getTag());
		
		String[] expectedNodes = new String[]{"Device1/[Status]/Is Connected"};
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
	public void testNodeId() throws Exception {
		new DeviceStatusFolder(driverContext, 1, "Device1");
		
		NodeId nodeId = new NodeId(String.format("[%s]%s", DEVICE_NAME, "Device1/[Status]"), 1);
		Node folderNode = driverContext.getNode(nodeId);
		assertEquals(NodeClass.Object, folderNode.getNodeClass());
		assertEquals("[Status]", folderNode.getBrowseName().getName());
		
		nodeId = new NodeId(String.format("[%s]%s", DEVICE_NAME, "Device1/[Status]/Is Connected"), 1);
		Node dataNode = driverContext.getNode(nodeId);
		assertEquals(NodeClass.Variable, dataNode.getNodeClass());
		assertEquals("Is Connected", dataNode.getBrowseName().getName());
	}
	
}
