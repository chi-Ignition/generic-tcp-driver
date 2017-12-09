package com.chitek.ignition.drivers.generictcp.folder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.inductiveautomation.ignition.common.execution.SchedulingController;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.xopc.driver.api.AggregateSubscriptionItem;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;

/**
 * This class is responsible for updating subscriptions in a message folder.
 */
public class SubscriptionUpdater implements SelfSchedulingRunnable {

	public final static int RESCHEDULE_RATE = 250;
	/** Delay for updating special items subscription **/
	public final static int SUBSCRIPTION_DELAY = 25;
	private final static DataValue DATAVALUE_ERROR = new DataValue(StatusCode.BAD_INTERNAL_ERROR);

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
	/* Subscriptions that are used as a trigger (e.g. _MessageCount). Those items must be updated last after all data items */
	private final List<AggregateSubscriptionItem> specialItems = new ArrayList<AggregateSubscriptionItem>();
	
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

		SubscriptionTransaction transaction = new SubscriptionTransaction(toAdd, toRemove);
		synchronized (transactions) {
			transactions.add(transaction);
		}

		nextExecTime=Math.min(nextExecTime, System.currentTimeMillis()+RESCHEDULE_RATE);
		schedulingController.requestReschedule(this);
	}

	@Override
	public long getNextExecDelayMillis() {

		long time = System.currentTimeMillis();
		long delay = nextExecTime>0?Math.max(5,nextExecTime-time):5;

		if (log.isTraceEnabled()) {
			log.trace(String.format("getNextExecDelay called at %s. Delay: %s", time, delay));
		}
		return delay;
	}

	/**
	 * Called by the message folder in queue mode to synchronize the subscription update
	 */
	public void syncExecution() {
		long time = System.currentTimeMillis();
		if (log.isTraceEnabled()) {
			log.trace(String.format("SyncExcution called at time %s. Old Value %s:", time, nextExecTime));
		}
		sendSpecialItems = false;
		nextExecTime=time+5;
		schedulingController.requestReschedule(this);
	}
	
	private void addSubscriptionItems(List<? extends SubscriptionItem> added) {
		synchronized (this.items) {
			for (SubscriptionItem item : added) {
				String address = item.getAddress();
				AggregateSubscriptionItem aggregate;
				aggregate = items.get(address);
				if (aggregate == null) {
					// Maybe it's a special item
					for (AggregateSubscriptionItem asi:specialItems) {
						if (asi.getAddress().equals(address)) {
							aggregate = asi;
							break;
						}
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
						specialItems.add(aggregate);
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
					for (AggregateSubscriptionItem asi:specialItems) {
						if (asi.getAddress().equals(address)) {
							aggregate = asi;
							break;
						}
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
						if (this.items.remove(address) == null)
							this.specialItems.remove(aggregate);
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
		
		tagLock.lock();

		try {
			if (sendSpecialItems) {
				sendSpecialItems = false;
				for (SubscriptionItem item : specialItems) {
					DynamicDriverTag tag = (DynamicDriverTag) item.getAddressObject();
					DataValue value;
					if (tag != null) {
						value = tag.getValue();
					} else {
						value = DATAVALUE_ERROR;
					}

					if (log.isTraceEnabled()) {
						log.trace(String.format("Subscription updating special item %s Value:%s", item.getAddress(), value.toString()));
					}
					item.setValue(value);
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

				for (SubscriptionItem item : specialItems) {
					// Check sampling rate for special items
					newSubscriptionRate = Math.min(newSubscriptionRate, item.getSamplingRate());
				}

				if (subscriptionChanged) {
					// Copy the addresses before passing to the listener
					Set<String> addresses = ImmutableSet.copyOf(Iterables.concat(items.keySet(), specialItems.stream().map(item -> item.getAddress()).collect(Collectors.toList())));
					subscriptionChangeListener.subscriptionChanged(newSubscriptionRate, addresses);
				}
				
				if (log.isTraceEnabled()) {
					log.trace(String.format("Data item subscriptions updated. SendSpecialItems: %s - Next exec: %s", sendSpecialItems, nextExecTime));
				}
			}
		} catch (Exception ex) {
			log.error("Exception in SubscriptionUpdater", ex);
		} finally {
			tagLock.unlock();
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
