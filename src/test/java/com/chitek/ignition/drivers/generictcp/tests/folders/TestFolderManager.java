package com.chitek.ignition.drivers.generictcp.tests.folders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.chitek.ignition.drivers.generictcp.folder.DeviceStatusFolder;
import com.chitek.ignition.drivers.generictcp.folder.FolderManager;
import com.chitek.ignition.drivers.generictcp.folder.IndexMessageFolder;
import com.chitek.ignition.drivers.generictcp.folder.SimpleWriteFolder;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.tests.DriverTestSuite;
import com.chitek.ignition.drivers.generictcp.tests.MockDriverContext;
import com.chitek.ignition.drivers.generictcp.tests.TestUtils;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;

public class TestFolderManager {

	private MockDriverContext driverContext;
	private static final String DEVICE_NAME = "DeviceName";
	
    @ClassRule
    public static TemporaryFolder testFolder = new TemporaryFolder();
	
	@Before
	public void setup() throws Exception {
		driverContext = new MockDriverContext(DEVICE_NAME);
		// Set temporary path for message queues
		driverContext.setDiskPath(testFolder.newFolder().getAbsolutePath());
	}
	
	@Test
	public void testTagAddress() throws Exception {
		FolderManager fm = new FolderManager(DriverTestSuite.getLogger());
		MockMessageFolder folder1 = new MockMessageFolder(driverContext, 1 ,"folder1");
		fm.addFolder(folder1);
		
		MockMessageFolder folder2 = new MockMessageFolder(driverContext, 1 ,"folder2");
		fm.addFolder(folder2);
		
		assertEquals(folder1, fm.getByTagAddress("folder1/item1"));
		assertEquals(folder2, fm.getByTagAddress("folder2/anItem"));
		
		// Test nested addresses
		assertEquals(folder1, fm.getByTagAddress("folder1/sub/item1"));
	}
	
	@Test
	public void testSubscription() throws Exception {

		FolderManager fm = new FolderManager(DriverTestSuite.getLogger());
		MockMessageFolder folder1 = new MockMessageFolder(driverContext, 1 ,"folder1");
		fm.addFolder(folder1);
		
		MockMessageFolder folder2 = new MockMessageFolder(driverContext, 1 ,"folder2");
		fm.addFolder(folder2);
		
		// Add subscriptions to both folders
		List<SubscriptionItem> items = new ArrayList<SubscriptionItem>();
		MockSubscriptionItem item1 = new MockSubscriptionItem("folder1/item1", 1000);
		items.add(item1);
		MockSubscriptionItem item2 = new MockSubscriptionItem("folder2/item1", 1000);
		items.add(item2);
		
		fm.alterSubscriptions(items, null);	
		assertEquals(item1, folder1.getSubscriptionItem("folder1/item1"));
		assertEquals(item2, folder2.getSubscriptionItem("folder2/item1"));
		
		// Remove 1 subscription
		items = new ArrayList<SubscriptionItem>();
		items.add(item1);
		fm.alterSubscriptions(null, items);
		assertNull(folder1.getSubscriptionItem("folder1/item1"));
		assertEquals(item2, folder2.getSubscriptionItem("folder2/item1"));
	}
	
	@Test
	public void testUpdateConnectionState() throws Exception {
		FolderManager fm = new FolderManager(DriverTestSuite.getLogger());
		
		// Setup a typical passive mode configuration
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		WritebackConfig writebackConfig = TestUtils.readWritebackConfig("/testWritebackConfig.xml");
		SimpleWriteFolder writeFolder = new SimpleWriteFolder(driverContext, driverSettings, 1, "device1", writebackConfig);
		fm.addFolder(writeFolder);
		
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		IndexMessageFolder msgFolder = new IndexMessageFolder(messageConfig, driverSettings, 1, "device1/" + messageConfig.getMessageAlias(), driverContext);
		fm.addFolder(msgFolder);
		
		DeviceStatusFolder statusFolder = new DeviceStatusFolder(driverContext, 1, "device1");
		fm.addFolder(statusFolder);
		
		fm.updateConnectionState(1, false);
		
		DataValue value = FolderTestUtils.readValue(msgFolder,"device1/Alias1/Data1");
		assertEquals(StatusCodes.Bad_NotConnected, value.getStatusCode().getValue());
		value = FolderTestUtils.readValue(writeFolder, "device1/[Writeback]/Value");
		assertEquals(StatusCodes.Bad_NotConnected, value.getStatusCode().getValue());
		value = FolderTestUtils.readValue(statusFolder, "device1/[Status]/Is Connected");
		assertEquals(StatusCode.GOOD, value.getStatusCode());
		assertEquals(false, value.getValue().getValue());
		
		// Now update the connection state
		fm.updateConnectionState(1, true);
		
		value = FolderTestUtils.readValue(msgFolder,"device1/Alias1/Data1");
		assertEquals(StatusCodes.Bad_WaitingForInitialData, value.getStatusCode().getValue());
		value = FolderTestUtils.readValue(writeFolder, "device1/[Writeback]/Value");
		assertEquals(StatusCode.GOOD, value.getStatusCode());	
		value = FolderTestUtils.readValue(statusFolder, "device1/[Status]/Is Connected");
		assertEquals(StatusCode.GOOD, value.getStatusCode());
		assertEquals(true, value.getValue().getValue());
	}
	
}
