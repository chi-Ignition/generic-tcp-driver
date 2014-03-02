package com.chitek.ignition.drivers.generictcp.tests.folders;

import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;

public class MockSubscriptionItem extends MockReadItem implements SubscriptionItem {

	private final int samplingRate;
	
	public MockSubscriptionItem(String address, int samplingRate) {
		super(address);
		this.samplingRate = samplingRate;
	}

	@Override
	public int getSamplingRate() {
		return samplingRate;
	}

}
