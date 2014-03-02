package com.chitek.ignition.drivers.generictcp.folder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import org.apache.log4j.Logger;

import com.google.common.collect.ImmutableSet;
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

	public final static int RESCHEDULE_RATE = 100;
	private final static DataValue DATAVALUE_ERROR = new DataValue(StatusCode.BAD_INTERNAL_ERROR);

	private final Logger log;

	/** The lock for accessing driver items **/
	private final Lock tagLock;

	private final ISubscriptionChangeListener subscriptionChangeListener;

	private SchedulingController schedulingController;

	private volatile long subscriptionRate = 0;

	private final List<SubscriptionTransaction> transactions = new LinkedList<SubscriptionTransaction>();
	private final Map<String, AggregateSubscriptionItem> items = new HashMap<String, AggregateSubscriptionItem>();

	public SubscriptionUpdater(Lock tagLock, Logger log, ISubscriptionChangeListener listener) {
		this.log = log;
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

		schedulingController.requestReschedule(this);
	}

	@Override
	public long getNextExecDelayMillis() {
		synchronized (transactions) {
			if (!transactions.isEmpty()) {
				// There are subscription changes to process, do that soon.
				if (subscriptionRate > 0 && subscriptionRate < RESCHEDULE_RATE) {
					return subscriptionRate;
				} else {
					return RESCHEDULE_RATE;
				}
			}
		}
		return subscriptionRate;
	}

	private void addSubscriptionItems(List<? extends SubscriptionItem> added) {
		synchronized (this.items) {
			for (SubscriptionItem item : added) {
				String address = item.getAddress();
				AggregateSubscriptionItem aggregate = items.get(address);
				if (aggregate == null) {
					if (log.isTraceEnabled()) {
						log.trace("Creating AggregateSubscriptionItem for " + address);
					}
					aggregate = new AggregateSubscriptionItem(item);
					aggregate.setAddressObject(item.getAddressObject());
					items.put(address, aggregate);
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
				AggregateSubscriptionItem aggregate = items.get(address);
				if (aggregate == null) {
					log.warn(String.format("Tried to unsubscribe item %s that was not subscribed.", address));
				} else {
					aggregate.removeItem(item);
					if (this.log.isTraceEnabled()) {
						this.log.trace(String.format("Removed item %s from AggregateSubscriptionItem. Count now %d", address, aggregate.getItems().size()));
					}
					if (aggregate.isEmpty())
						this.items.remove(address);
					if (this.log.isTraceEnabled()) {
						this.log.trace(String.format("Removed empty AggregateSubscriptionItem for item %s.", address));
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

		try {
			int samplingRate = Integer.MAX_VALUE;
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
				samplingRate = Math.min(samplingRate, item.getSamplingRate());
			}
			
			if (samplingRate != subscriptionRate) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Subscription rate changed from %d to %d", subscriptionRate, samplingRate));
				}
				subscriptionRate = samplingRate;
			}
			
			if (subscriptionChanged) {
				// Copy the addresses before passing to the listener
				Set<String> addresses = ImmutableSet.copyOf(items.keySet());
				subscriptionChangeListener.subscriptionChanged(subscriptionRate, addresses);
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
