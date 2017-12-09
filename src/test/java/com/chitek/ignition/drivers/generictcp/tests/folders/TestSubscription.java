package com.chitek.ignition.drivers.generictcp.tests.folders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.chitek.ignition.drivers.generictcp.folder.IndexMessageFolder;
import com.chitek.ignition.drivers.generictcp.folder.MessageFolder;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.tests.MockDriverContext;
import com.chitek.ignition.drivers.generictcp.tests.TestUtils;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;

public class TestSubscription {

	private static final String DEVICE_NAME = "DeviceName";

	private MockDriverContext driverContext;
	private IndexMessageFolder folder;

    @ClassRule
    public static TemporaryFolder testFolder = new TemporaryFolder();
	
	@Before
	public void setup() throws Exception {
		driverContext = new MockDriverContext(DEVICE_NAME);
		driverContext.setDiskPath(testFolder.newFolder().getAbsolutePath());
		
		// Create settings with message id type = None
		DriverSettings driverSettings = new DriverSettings("noHost", 0 , true, 1000, 1000, false, 1, (2^32)-1, OptionalDataType.None);
		MessageConfig messageConfig = TestUtils.readMessageConfig("/testMessageConfigSimple.xml");
		

		folder = new IndexMessageFolder(messageConfig, driverSettings, 0, messageConfig.getMessageAlias(), driverContext);
		// Send an initial message, so we have a value
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,65,66}, null); // 65,66 == 'AB'
		// We have to run the scheduled command to evaluate the message
		driverContext.getExecutor().runCommand();
	}
	
	@Test
	public void testSubscription() throws Exception {
		
		List<SubscriptionItem> items = new ArrayList<SubscriptionItem>();
		MockSubscriptionItem subscriptionData1 = new MockSubscriptionItem("Alias1/Data1", 1000);
		items.add(subscriptionData1);
		
		folder.changeSubscription(items, null);
		
		SelfSchedulingRunnable subscriptionUpdater = driverContext.getSelfSchedulingRunnable(folder.getFolderAddress(), MessageFolder.UPDATER_COMMAND_NAME);
		assertNotNull(subscriptionUpdater);
	
		// We have to run the subscription updater to update the subscriptions
		subscriptionUpdater.run();

		// The subscription item should have a valid value now
		assertEquals("AB", subscriptionData1.getValue().getValue().getValue());
	}

	@Test
	public void testValueUpdates() throws Exception {
		
		List<SubscriptionItem> items = new ArrayList<SubscriptionItem>();
		MockSubscriptionItem subscriptionData1 = new MockSubscriptionItem("Alias1/Data1", 1000);
		items.add(subscriptionData1);
		
		folder.changeSubscription(items, null);
		runUpdater();
		assertEquals("AB", subscriptionData1.getValue().getValue().getValue());
		
		// Send a new message
		folder.messageArrived(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,67,68}, null); // 675,68 == 'CD'
		// We have to run the scheduled command to evaluate the message
		driverContext.getExecutor().runCommand();
		runUpdater();
		assertEquals("CD", subscriptionData1.getValue().getValue().getValue());
	}
	
	/**
	 * @return
	 * 	The scheduled execution rate
	 */
	private long runUpdater() {
		SelfSchedulingRunnable subscriptionUpdater = driverContext.getSelfSchedulingRunnable(folder.getFolderAddress(), MessageFolder.UPDATER_COMMAND_NAME);
	
		// We have to run the subscription updater to update the subscriptions
		subscriptionUpdater.run();
		// The updater has to run twice, first run updates data items, second run updates special items
		subscriptionUpdater.run();	
		return subscriptionUpdater.getNextExecDelayMillis();
	}
	
}
