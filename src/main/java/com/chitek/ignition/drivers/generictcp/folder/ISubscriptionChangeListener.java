package com.chitek.ignition.drivers.generictcp.folder;

import java.util.Set;

public interface ISubscriptionChangeListener {
	
	/**
	 * Called by the SubscriptionUpdater after items have been added or removed.
	 *
	 * @param rate
	 * 	The subscription rate in milliseconds
	 * @param itemAdresses
	 * 	The addresses of all subscribed items
	 */
	public void subscriptionChanged(long rate, Set<String> itemAdresses);
	
	/**
	 * Called by the subscription updater before subscribed items are updated
	 */
	public void beforeSubscriptionUpdate();
}
