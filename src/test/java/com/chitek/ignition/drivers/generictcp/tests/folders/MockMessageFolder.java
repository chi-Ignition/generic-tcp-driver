package com.chitek.ignition.drivers.generictcp.tests.folders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.chitek.ignition.drivers.generictcp.folder.MessageFolder;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;

public class MockMessageFolder extends MessageFolder {

	private Map<String, SubscriptionItem> subscriptions = new HashMap<String, SubscriptionItem>();
	
	public MockMessageFolder(IGenericTcpDriverContext driverContext, int folderId, String folderAddress) {
		super(driverContext, folderId, folderAddress);
	}
	
	@Override
	public void connectionStateChanged(boolean isConnected) {
		// Do nothing

	}

	@Override
	public void activityLevelChanged(boolean isActive) {
		// Do nothing

	}

	@Override
	public void changeSubscription(List<SubscriptionItem> toAdd, List<SubscriptionItem> toRemove) {
		if (toAdd != null) {
			for (SubscriptionItem item : toAdd) {
				subscriptions.put(item.getAddress(), item);
			}
		}
		
		if (toRemove != null) {
			for (SubscriptionItem item : toRemove) {
				subscriptions.remove(item.getAddress());
			}
		}
	}
	
	public SubscriptionItem getSubscriptionItem(String address) {
		return subscriptions.get(address);
	}
	
}
