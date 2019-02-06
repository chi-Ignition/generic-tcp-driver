/*******************************************************************************
 * Copyright 2012-2019 C. Hiesserich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.chitek.ignition.drivers.generictcp.folder;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.chitek.ignition.drivers.generictcp.tags.ReadableArrayTag;
import com.chitek.ignition.drivers.generictcp.tags.ReadableTcpDriverTag;
import com.chitek.ignition.drivers.generictcp.tags.WritableTag;
import com.google.common.collect.ImmutableSet;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;
import com.inductiveautomation.xopc.driver.api.items.WriteItem;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;

/**
 * Base class for handling of binary messages.
 * 
 * @author chi
 * 
 */
public abstract class MessageFolder implements ISubscriptionChangeListener{

	protected static final DataValue EMPTY_STRING_VALUE = new DataValue(new Variant(""));
	public static final String UPDATER_COMMAND_NAME = "subscriptions";
	public static final String TIMESTAMP_TAG_NAME = "/_Timestamp";
	public static final String MESSAGE_COUNT_TAG_NAME = "/_MessageCount";
	public static final String HANDSHAKE_TAG_NAME = "/_Handshake";
	public static final String QUEUE_SIZE_TAG_NAME = "/_QueueSize";

	/**
	 * Mapping of address strings to driver tags
	 */
	protected Map<String, DynamicDriverTag> addressTagMap;

	/**
	 * List of all Nodes that were added to the NodeManager
	 */
	private final List<UaNode> uaNodes;

	private final IGenericTcpDriverContext driverContext;
	private final int folderId;
	private final String folderAddress;
	protected final Logger log;

	/**
	 * This Lock synchronizes all access to tag values. This makes sure, that a incoming message will always be
	 * consistent.
	 */
	protected Lock tagLock;

	protected SubscriptionUpdater subscriptionUpdater;

	/**
	 * 
	 * @param driverContext
	 * 		The driver context
	 * @param folderId
	 * 		The unique folder id
	 * @param folderAddress
	 * 		The base address of this folder
	 */
	public MessageFolder(IGenericTcpDriverContext driverContext, int folderId, String folderAddress) {
		this.driverContext = driverContext;
		this.folderId = folderId;
		this.folderAddress = folderAddress;
		this.log = Logger.getLogger(String.format("%s.Folder[%s]", driverContext.getLoggerName(), folderAddress));
		tagLock = new ReentrantLock();
		addressTagMap = new HashMap<String, DynamicDriverTag>();
		uaNodes = new ArrayList<UaNode>();
	}

	/**
	 * @return
	 * 	The unique id of this folder
	 */
	public int getFolderId() {
		return folderId;
	}

	/**
	 * @return
	 * 	The folders base address
	 */
	public String getFolderAddress() {
		return folderAddress;
	}

	/**
	 * @return
	 * 	The device name of this driver
	 */
	protected String getDeviceName() {
		return driverContext.getDeviceName();
	}

	/**
	 * @return
	 * 	The driver context
	 */
	public IGenericTcpDriverContext getDriverContext() {
		return driverContext;
	}

	public void writeItems(List<WriteItem> items) {
		for (WriteItem item : items) {
			DynamicDriverTag tag = addressTagMap.get(item.getAddress());
			if (tag != null && tag instanceof WritableTag) {
				item.setWriteStatus(
					((WritableTag) tag).setValue(new DataValue(item.getWriteValue()))
					);
			} else {
				item.setWriteStatus(StatusCode.BAD);
			}
		}
	}

	/**
	 * Updates the value for the given List of ReadItems
	 * 
	 * @param list
	 */
	public void readItems(List<? extends ReadItem> list) {
		tagLock.lock();
		try {
			for (ReadItem item : list) {
				DynamicDriverTag tag = addressTagMap.get(item.getAddress());
				if (tag != null) {
					item.setValue(tag.getValue());
					if (log.isTraceEnabled())
						log.trace(String.format("ReadItem %s - Value: %s",
							item.getAddress(), tag.getValue().getValue() != null? tag.getValue().getValue().getValue():"null"));
				}
			}
		} catch (Exception ex) {
			log.error("Exception in readItems Message " + folderAddress, ex);

		} finally {
			tagLock.unlock();
		}
	}

	/**
	 * Cancels the current subscription and starts updating the given items in a new schedule.
	 * 
	 * @param items
	 * @param executor
	 */
	public void changeSubscription(List<SubscriptionItem> toAdd, List<SubscriptionItem> toRemove) {

		// Set the address object in the given subscription item
		List<SubscriptionItem> toAddValid = null;
		if (toAdd != null) {
			toAddValid = new ArrayList<SubscriptionItem>(toAdd.size());
			for (SubscriptionItem item : toAdd) {
				DynamicDriverTag tag = addressTagMap.get(item.getAddress());
				if (tag != null) {
					item.setAddressObject(tag);
					toAddValid.add(item);
				} else {
					log.error("Unknown subscription item:" + item.getAddress());
				}
			}
		}
		
		if (subscriptionUpdater == null) {
			subscriptionUpdater = new SubscriptionUpdater(tagLock, log, this);
			driverContext.registerSelfSchedulingRunnable(getFolderAddress(), UPDATER_COMMAND_NAME, subscriptionUpdater);
		}
		
		subscriptionUpdater.changeSubscription(toAddValid, toRemove);
	}

	@Override
	public void subscriptionChanged(long rate, Set<String> addresses) {
		// Default implementation does nothing
	}
	
	@Override
	public void beforeSubscriptionUpdate() {
		// Default implementation does nothing
	}
	
	/**
	 * Called when the device is connected or disconnected
	 *
	 * @param isConnected
	 */
	public abstract void connectionStateChanged(boolean isConnected);
	
	/**
	 * Called when the activity level in a redundant system changes
	 *
	 * @param isActive
	 */
	public abstract void activityLevelChanged(boolean isActive);
	
	/**
	 * Removes all registered nodes form the NodeManager and stops the subscription updater
	 */
	public void shutdown() {

		// Stop subscriptions before removing the Nodes
		tagLock.lock();
		try {
			if (subscriptionUpdater != null) {
				driverContext.unregisterScheduledRunnable(getFolderAddress(), UPDATER_COMMAND_NAME);
				subscriptionUpdater = null;
			}
		} finally {
			tagLock.unlock();
		}

		removeNodes();
	}

	/**
	 * Removes all registered nodes form the NodeManager
	 */
	protected void removeNodes() {

		for (UaNode uaNode : uaNodes) {
			getDriverContext().removeNode(uaNode);
		}

		uaNodes.clear();
	}

	/**
	 * Creates a Node for the given tag and adds it to the NodeManager. On driver shutdown, the nodes have to
	 * be removed by calling {@link MessageFolder#removeNodes}.<br />
	 * 
	 * @param driver
	 * @param tag
	 * @return The created Node
	 */
	protected UaVariableNode buildAndAddNode(DynamicDriverTag tag) {

		// NodeId in the Ignition default format '[DEVICENAME]ADDRESS'. Namespace is always 1
		NodeId nodeId = new NodeId(1, String.format("[%s]%s", getDeviceName(), tag.getAddress()));
		String displayName = tag instanceof ReadableTcpDriverTag
			? ((ReadableTcpDriverTag) tag).getDisplayName()
				: tag.getAddress().substring(tag.getAddress().lastIndexOf("/") + 1);
			String browseName = tag instanceof ReadableTcpDriverTag
				? ((ReadableTcpDriverTag) tag).getBrowseName()
					: displayName;

				ImmutableSet<AccessLevel> accessLevel;
				if (tag instanceof WritableTag)
					accessLevel = ((WritableTag) tag).getAccessLevel();
				else
					accessLevel = AccessLevel.READ_ONLY;

				UInteger[] arrayDimensions;
				ValueRank valueRank;
				if (tag instanceof ReadableArrayTag) {
					arrayDimensions = new UInteger[1];
					arrayDimensions[0] = uint(((ReadableArrayTag) tag).getValueArrayLength());
					valueRank = ValueRank.OneDimension;
				} else {
					arrayDimensions = new UInteger[0];
					valueRank = ValueRank.Scalar;
				}
				
				
				UaVariableNodeBuilder nodeBuilder = getDriverContext().getVariableNodeBuilder();
				nodeBuilder
				.setNodeId(nodeId)
				.setBrowseName(new QualifiedName(0, browseName))
				.setDisplayName(new LocalizedText(displayName))
				.setDescription(new LocalizedText(displayName))
				.setDataType(tag.getDataType().getNodeId())
				.setArrayDimensions(arrayDimensions)
				.setValueRank(valueRank.getValue())
				.setTypeDefinition(Identifiers.BaseDataVariableType)
				.setAccessLevel(ubyte(AccessLevel.getMask(accessLevel)))
				.setUserAccessLevel(ubyte(AccessLevel.getMask(accessLevel)));
				UaVariableNode uaNode = nodeBuilder.build();
				getDriverContext().addNode(uaNode, tag.getAddress());

				addressTagMap.put(tag.getAddress(), tag);
				uaNodes.add(uaNode);
				return uaNode;
	}

	/**
	 * Creates a FolderNode for the given address and adds it to the NodeManager. On driver shutdown, the nodes have to be removed
	 * by calling {@link MessageFolder#removeNodes}.
	 * 
	 * @param driver
	 * @param tag
	 * @return The created Node
	 */
	protected UaNode buildAndAddFolderNode(String address, String browseName) {

		// NodeId in the Ignition default format '[DEVICENAME]ADDRESS'. Namespace is always 1
		NodeId nodeId = new NodeId(1, String.format("[%s]%s", getDeviceName(), address));

		UaFolderNode folderNode = new UaFolderNode(
				getDriverContext().getNodeContext(), nodeId, new QualifiedName(1, browseName), new LocalizedText(browseName));
		
		getDriverContext().addNode(folderNode, address);

		uaNodes.add(folderNode);
		return folderNode;
	}

}
