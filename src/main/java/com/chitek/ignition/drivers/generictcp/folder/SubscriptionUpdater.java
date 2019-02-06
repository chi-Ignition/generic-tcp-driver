package com.chitek.ignition.drivers.generictcp.folder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import org.apache.log4j.Logger;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.inductiveautomation.ignition.common.execution.SchedulingController;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;
import com.inductiveautomation.xopc.driver.api.AggregateSubscriptionItem;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;
import com.inductiveautomation.xopc.driver.api.tags.DriverTag;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;

/**
 * This class is responsible for updating subscriptions in a message folder.
 */
public class SubscriptionUpdater implements SelfSchedulingRunnable {

	public final static int RESCHEDULE_RATE = 250;
	/** Delay for updating special items subscription **/
	public final static int SUBSCRIPTION_DELAY = 25;
	private final static DataValue DATAVALUE_ERROR = new DataValue(StatusCodes.Bad_InternalError);

	private final Logger log;

	/** The lock for accessing driver items **/
	private final Lock tagLock;

	private final ISubscriptionChangeListener subscriptionChangeListener;

	private SchedulingController schedulingController;
	private long nextExecTime = 0;
	private long nextExecTimeData = 0;
	private boolean sendSpecialItems;

	private volatile long newSubscriptionRate = 0;
	private volatile long subscriptionRate = 0;

	private final List<SubscriptionTransaction> transactions = new LinkedList<SubscriptionTransaction>();
	private final Map<String, AggregateSubscriptionItem> items = new HashMap<String, AggregateSubscriptionItem>();
	/* Subscriptions that is used as a trigger (_MessageCount). This items must be updated last after all data items */
	private AggregateSubscriptionItem messageCountItem = null;
	private volatile DataValue messageCountValue = null;
	private AggregateSubscriptionItem handshakeItem = null;

	public SubscriptionUpdater(Lock tagLock, Logger log, ISubscriptionChangeListener listener) {
		this.log = Logger.getLogger(String.format("%s.Subscription", log.getName()));
		this.tagLock = tagLock;
		this.subscriptionChangeListener = listener;
	}

	/**
	 * Changed subscriptions are stored in a transaction list here and processed in the run() method.
	 * 
	 * @param toAdd
	 * @param toRemove
	 */
	public void changeSubscription(List<SubscriptionItem> toAdd, List<SubscriptionItem> toRemove) {

		if (log.isTraceEnabled()) {
			log.trace(String.format("changeSubscription called. toAdd: %s - toRemove: %s", toAdd != null ? toAdd.size() : "null", toRemove != null ? toRemove.size() : "null"));
		}

		SubscriptionTransaction transaction = new SubscriptionTransaction(toAdd, toRemove);
		synchronized (transactions) {
			transactions.add(transaction);
		}

		nextExecTime = Math.min(nextExecTime, System.currentTimeMillis() + RESCHEDULE_RATE);
		schedulingController.requestReschedule(this);
	}

	@Override
	public long getNextExecDelayMillis() {

		long time = System.currentTimeMillis();
		long delay = nextExecTime > 0 ? Math.max(5, nextExecTime - time) : 5;

		if (log.isTraceEnabled()) {
			log.trace(String.format("getNextExecDelay called at %s. Delay: %s", time, delay));
		}
		return delay;
	}

	private void addSubscriptionItems(List<? extends SubscriptionItem> added) {
		synchronized (this.items) {
			for (SubscriptionItem item : added) {
				String address = item.getAddress();
				AggregateSubscriptionItem aggregate;
				aggregate = items.get(address);
				if (aggregate == null) {
					// Maybe it's a special item
					if (messageCountItem != null && messageCountItem.getAddress().equals(address)) {
						aggregate = messageCountItem;
					}
					if (handshakeItem != null && handshakeItem.getAddress().equals(address)) {
						aggregate = handshakeItem;
					}
				}
				if (aggregate == null) {
					if (log.isDebugEnabled()) {
						log.debug("Creating AggregateSubscriptionItem for " + address);
					}
					aggregate = new AggregateSubscriptionItem(item);
					aggregate.setAddressObject(item.getAddressObject());
					if (address.endsWith(MessageFolder.MESSAGE_COUNT_TAG_NAME)) {
						// The message count Trigger item is stored separate, it has to be updated after all other items
						messageCountItem = aggregate;
					} else if (address.endsWith(MessageFolder.HANDSHAKE_TAG_NAME)) {
						// The _Handshake Trigger item is stored separate, it has to be updated after all other items
						handshakeItem = aggregate;
					} else {
						items.put(address, aggregate);
					}
				} else {
					aggregate.addItem(item);
					if (this.log.isTraceEnabled()) {
						this.log.trace("Added " + address + " to AggregateSubscriptionItem. Count now " + aggregate.getItems().size());
					}
				}
			}
		}
	}

	private void removeSubscriptionItems(List<? extends SubscriptionItem> removed) {
		synchronized (this.items) {
			for (SubscriptionItem item : removed) {
				String address = item.getAddress();
				AggregateSubscriptionItem aggregate;
				aggregate = items.get(address);
				if (aggregate == null) {
					// Maybe it's a special item
					if (messageCountItem != null && messageCountItem.getAddress().equals(address)) {
						aggregate = messageCountItem;
					}
					if (handshakeItem != null && handshakeItem.getAddress().equals(address)) {
						aggregate = handshakeItem;
					}
				}
				if (aggregate == null) {
					log.warn(String.format("Tried to unsubscribe item %s that was not subscribed.", address));
				} else {
					aggregate.removeItem(item);
					if (this.log.isTraceEnabled()) {
						this.log.trace(String.format("Removed item %s from AggregateSubscriptionItem. Count now %d", address, aggregate.getItems().size()));
					}
					if (aggregate.isEmpty()) {
						if (this.items.remove(address) == null) {
							// Aggregate is not in the items list - Must have been the _MessageCount
							if (aggregate == messageCountItem) 
								messageCountItem = null;
							if (aggregate == handshakeItem) 
								handshakeItem = null;
						}
						if (this.log.isDebugEnabled()) {
							this.log.debug(String.format("Removed empty AggregateSubscriptionItem for item %s.", address));
						}
					}
				}
			}
		}
	}

	@Override
	public void setController(SchedulingController controller) {
		this.schedulingController = controller;
	}

	@Override
	public void run() {

		try {
			if (sendSpecialItems) {
				sendSpecialItems = false;
				if (messageCountItem != null) {
					DataValue value = messageCountValue;
					if (log.isTraceEnabled()) {
						log.trace(String.format("Subscription updating special item %s Value:%s", messageCountItem.getAddress(), value.toString()));
					}
					messageCountItem.setValue(value);
				}
				
				if (handshakeItem != null) {
					DataValue value = ((DriverTag) handshakeItem.getAddressObject()).getValue();
					if (log.isTraceEnabled()) {
						log.trace(String.format("Subscription updating special item %s Value:%s", handshakeItem.getAddress(), value.toString()));
					}
					handshakeItem.setValue(value);
				}

				if (newSubscriptionRate != subscriptionRate) {
					if (log.isDebugEnabled()) {
						log.debug(String.format("Subscription rate changed from %d to %d", subscriptionRate, newSubscriptionRate));
					}
					subscriptionRate = newSubscriptionRate;
					sendSpecialItems = false;
					nextExecTimeData = System.currentTimeMillis() + subscriptionRate - SUBSCRIPTION_DELAY;
				}

				nextExecTime = nextExecTimeData;

				if (log.isTraceEnabled()) {
					log.trace(String.format("Special item subscriptions updated. Next exec: %s", nextExecTime));
				}
			} else {
				nextExecTimeData = System.currentTimeMillis() + Math.max(subscriptionRate, 50);
				// Update the special items in the next run
				nextExecTime = System.currentTimeMillis() + SUBSCRIPTION_DELAY;
				sendSpecialItems = true;

				subscriptionChangeListener.beforeSubscriptionUpdate();
				
				try {
					tagLock.lock();
					boolean subscriptionChanged = false;
					synchronized (transactions) {
						if (!transactions.isEmpty()) {
							Iterator<SubscriptionTransaction> it = transactions.iterator();
							while (it.hasNext()) {
								SubscriptionTransaction transaction = it.next();
								it.remove();
								if (transaction.toAdd != null && !transaction.toAdd.isEmpty()) {
									addSubscriptionItems(transaction.toAdd);
								}
								if (transaction.toRemove != null && !transaction.toRemove.isEmpty()) {
									removeSubscriptionItems(transaction.toRemove);
								}
							}
							subscriptionChanged = true;
						}
					}

					newSubscriptionRate = Integer.MAX_VALUE;
					for (SubscriptionItem item : items.values()) {
						DynamicDriverTag tag = (DynamicDriverTag) item.getAddressObject();
						DataValue value;
						if (tag != null) {
							value = tag.getValue();
						} else {
							value = DATAVALUE_ERROR;
						}

						if (log.isTraceEnabled()) {
							log.trace(String.format("Subscription updating tag %s Value:%s", item.getAddress(), value.toString()));
						}
						item.setValue(value);
						// The sampling rate is checked here as we have to iterate anyway.
						newSubscriptionRate = Math.min(newSubscriptionRate, item.getSamplingRate());
					}

					// Store value of _MessageCount tag
					if (messageCountItem != null) {
						messageCountValue = ((DriverTag) messageCountItem.getAddressObject()).getValue();
						// Check sampling rate for special items
						newSubscriptionRate = Math.min(newSubscriptionRate, messageCountItem.getSamplingRate());
					}

					// Check sampling rate for handshake
					if (handshakeItem != null) {					
						newSubscriptionRate = Math.min(newSubscriptionRate, handshakeItem.getSamplingRate());
					}
					
					if (subscriptionChanged) {
						// Copy the addresses before passing to the listener
						Builder<String> b = ImmutableSet.builder();
						b.addAll(items.keySet());

						if (messageCountItem != null)
							b.add(messageCountItem.getAddress());
						if (handshakeItem != null)
							b.add(handshakeItem.getAddress());

						subscriptionChangeListener.subscriptionChanged(newSubscriptionRate, b.build());
					}
				} finally {
					tagLock.unlock();
				}

				if (log.isTraceEnabled()) {
					log.trace(String.format("Data item subscriptions updated. SendSpecialItems: %s - Next exec: %s", sendSpecialItems, nextExecTime));
				}
			}
		} catch (Exception ex) {
			log.error("Exception in SubscriptionUpdater", ex);
		}
	}

	/**
	 * A single transaction with items to add and remove.
	 */
	private static class SubscriptionTransaction {
		final List<? extends SubscriptionItem> toAdd;
		final List<? extends SubscriptionItem> toRemove;

		/**
		 * @param toAdd
		 *            Items to add to the subscription
		 * @param toRemove
		 *            Items to remove from the subscription
		 */
		public SubscriptionTransaction(final List<SubscriptionItem> toAdd, final List<SubscriptionItem> toRemove) {
			this.toAdd = toAdd;
			this.toRemove = toRemove;
		}
	}

}
