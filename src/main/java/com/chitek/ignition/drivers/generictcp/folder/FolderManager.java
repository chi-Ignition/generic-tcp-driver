package com.chitek.ignition.drivers.generictcp.folder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.inductiveautomation.xopc.driver.api.items.DriverItem;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;
import com.inductiveautomation.xopc.driver.api.items.WriteItem;

public class FolderManager {

	public static final int STATUS_ID = -2;
	public static final int WRITEBACK_ID = -3;
	public static final int DEVICE_STATUS_ID = -4;

	/**
	 * @param deviceId
	 * @param folderId
	 * @return
	 * 	The unique folder id for the given device and folder id.
	 */
	public static int getFolderId(int deviceId, int folderId) {
		int id =  (deviceId << 24) | (folderId & 0xffffff);
		return id;
	}

	public static String folderIdAsString(int folderId) {
		int deviceId = folderId >> 24;
		int messageId = folderId & 0xffffff;
		return String.format("device %d-msg %d", deviceId, messageId);
	}
	
	/**
	 * 
	 * @param deviceId
	 * @param folderId
	 * @return
	 * 	<code>true</code> if the given folder id matches the device id
	 */
	public static boolean deviceIdEquals(int deviceId, int folderId) {
		boolean result = (folderId >> 24) == deviceId;
		return result;
	}
	
	private final Logger log;
	
	/**
	 * Map of message folders<br>
	 * Key is (device id << 24) | (message id & 0xffffff) (device id is the index in the device list)<br>
	 * device id is 0 in active mode
	 */
	private final Map<Integer, MessageFolder> folderMap = new HashMap<Integer, MessageFolder>();

	/**
	 * Maps folder address to folders
	 */
	private final Map<String, MessageFolder> addressMap = new HashMap<String, MessageFolder>();

	public FolderManager(Logger log) {
		this.log = log;
	}
	
	/**
	 * Add a MessageFolder to the internal map.
	 * 
	 * @param folder
	 * 	The MessageFolder to add to the map.
	 */
	public void addFolder(MessageFolder folder) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Adding folder %s with ID %s", folder.getClass().getSimpleName(), folder.getFolderId()));
		}
		folderMap.put(folder.getFolderId(), folder);
		addressMap.put(folder.getFolderAddress(), folder);
	}

	/**
	 * Returns the folder for the given message id and the given device.
	 *
	 * @param deviceId
	 * @param messageId
	 * @return
	 * 	The message folder or null, if no folder is found.
	 */
	public MessageFolder getById(int deviceId, int messageId) {
		return folderMap.get(getFolderId(deviceId, messageId));
	}

	/**
	 * Returns the folder for the given id.
	 *
	 * @param folderId
	 * @return
	 * 	The message folder or null, if no folder is found.
	 */
	public MessageFolder getById(int folderId) {
		return folderMap.get(folderId);
	}	
	
	/**
	 * Returns the folder that contains the given tag address. This method only evaluates the tag path,
	 * there is no check that the tag really exists in the returned folder.
	 * 
	 * @param tagAddress
	 * @return
	 * 	The folder that matches the given tag address.
	 */
	public MessageFolder getByTagAddress(String tagAddress) {
		String folderAddress = null;
		int slash = tagAddress.indexOf("/");
		int secondSlash = tagAddress.indexOf("/", slash + 1);
		if (secondSlash > -1) {
			// First check if the address points to a sub-folder
			folderAddress = tagAddress.substring(0, secondSlash);
			MessageFolder folder = addressMap.get(folderAddress);
			if (folder != null) {
				return folder;
			}
		}
		if (slash > -1) {
			// No sub-folder
			folderAddress = tagAddress.substring(0, slash);
			MessageFolder folder = addressMap.get(folderAddress);
			return folder;	// Maybe null, id the address is not found
		}

		return null;
	}

	/**
	 * Update all message folders with changed subscriptions
	 * 
	 * @param folderItemMap
	 */
	public void alterSubscriptions(List<SubscriptionItem> subscribe, List<SubscriptionItem> unsubscribe) {
		
		if (subscribe != null) {
			Map<MessageFolder, List<SubscriptionItem>> toSubscribe = sortDriverItems(subscribe);
			for (Entry<MessageFolder, List<SubscriptionItem>> entry : toSubscribe.entrySet()) {
				MessageFolder folder = entry.getKey();
				folder.changeSubscription(entry.getValue(), null);
			}
		}
		
		if (unsubscribe != null) {
			Map<MessageFolder, List<SubscriptionItem>> toUnsubscribe = sortDriverItems(unsubscribe);
			for (Entry<MessageFolder, List<SubscriptionItem>> entry : toUnsubscribe.entrySet()) {
				MessageFolder folder = entry.getKey();
				folder.changeSubscription(null, entry.getValue());
			}
		}
	}
	
	public void readItems(List<ReadItem> items) {

		// Sort items by folder
		Map<MessageFolder, List<ReadItem>> messageItemMap = sortDriverItems(items);

		// Let the folders red the items
		for (Map.Entry<MessageFolder, List<ReadItem>> entry : messageItemMap.entrySet() ) {
			MessageFolder messageFolder = entry.getKey();
			messageFolder.readItems(entry.getValue());
		}
	}

	public void writeItems(List<WriteItem> items) {
		// Sort items by folder
		Map<MessageFolder, List<WriteItem>> messageItemMap = sortDriverItems(items);

		// Let the folders write the items
		for (Map.Entry<MessageFolder, List<WriteItem>> entry : messageItemMap.entrySet() ) {
			MessageFolder messageFolder =entry.getKey();
			if (messageFolder != null) {
				messageFolder.writeItems(entry.getValue());
			}
		}
	}
	
	/**
	 * Sorts the given list of items by the containing message folder address
	 * 
	 * @param items
	 * @return
	 * 	A map with the MessageFolder as key and a List of DriverItem as value
	 */
	@SuppressWarnings("unchecked")
	private <T extends DriverItem> Map<MessageFolder, List<T>> sortDriverItems(List<T> items) {
		Map<MessageFolder, List<T>> sorted = new HashMap<MessageFolder, List<T>>();
		for (DriverItem item : items) {
			String address = item.getAddress();
			MessageFolder folder = getByTagAddress(address);

			if (folder != null) {
				List<DriverItem> list = (List<DriverItem>) sorted.get(folder);
				if (list == null) {
					sorted.put(folder, (List<T>) (list = new ArrayList<DriverItem>()));
				}
				list.add(item);
			} else {
				// No folder found with the given address
				log.warn(String.format("Client tried to access unknown item '%s'", address));
			}
		}

		return sorted;
	}

	/**
	 * Update redundancy activity level in all folders.
	 * 
	 * @param isActive
	 * 		true, if this is the active node in a redundant configuration
	 */
	public void updateActivityLevel(boolean isActive) {
		for (MessageFolder messageFolder : folderMap.values()) {
			messageFolder.activityLevelChanged(isActive);
		}
	}
	
	/**
	 * Update connection state in all folders.
	 * 
	 * @param deviceId
	 * 		The device id for the connected device
	 * @param isConnected
	 * 		true, if the driver is connected to the remote device
	 */
	public void updateConnectionState(int deviceId, boolean isConnected) {
		for (MessageFolder messageFolder : folderMap.values()) {
			if (deviceIdEquals(deviceId, messageFolder.getFolderId())) {
				messageFolder.connectionStateChanged(isConnected);
			}
		}
	}
	
	/**
	 * Calls {@link MessageFolder#shutdown()} for all folders and removes them from the map
	 */
	public void shutdown() {
		Iterator<Map.Entry<Integer, MessageFolder>> entries = folderMap.entrySet().iterator();
		while (entries.hasNext()) {
			Entry<Integer, MessageFolder> entry = entries.next();
			entry.getValue().shutdown();
			entries.remove();
		}

		// Clear the address map
		addressMap.clear();
	}

}
