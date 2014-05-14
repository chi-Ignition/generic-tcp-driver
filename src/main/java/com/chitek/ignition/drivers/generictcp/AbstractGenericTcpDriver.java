package com.chitek.ignition.drivers.generictcp;

import java.io.File;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.chitek.ignition.drivers.generictcp.folder.BrowseTree;
import com.chitek.ignition.drivers.generictcp.folder.FolderManager;
import com.chitek.ignition.drivers.generictcp.folder.MessageFolder;
import com.chitek.ignition.drivers.generictcp.redundancy.FullUpdateMessage;
import com.chitek.ignition.drivers.generictcp.redundancy.StateUpdate;
import com.chitek.ignition.drivers.generictcp.redundancy.StatusUpdateMessage;
import com.chitek.ignition.drivers.generictcp.types.DriverState;
import com.inductiveautomation.ignition.common.execution.ExecutionManager;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;
import com.inductiveautomation.ignition.gateway.redundancy.RedundancyManager;
import com.inductiveautomation.ignition.gateway.redundancy.types.ActivityLevel;
import com.inductiveautomation.ignition.gateway.redundancy.types.RedundancyState;
import com.inductiveautomation.ignition.gateway.redundancy.types.RedundancyStateAdapter;
import com.inductiveautomation.ignition.gateway.redundancy.types.RuntimeStateProvider;
import com.inductiveautomation.opcua.nodes.Node;
import com.inductiveautomation.opcua.nodes.builders.NodeBuilder;
import com.inductiveautomation.opcua.nodes.builders.ObjectNodeBuilder;
import com.inductiveautomation.opcua.nodes.builders.VariableNodeBuilder;
import com.inductiveautomation.opcua.types.Guid;
import com.inductiveautomation.opcua.types.NodeId;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.xopc.driver.api.BrowseOperation;
import com.inductiveautomation.xopc.driver.api.Driver;
import com.inductiveautomation.xopc.driver.api.DriverContext;
import com.inductiveautomation.xopc.driver.api.DriverSubscriptionModel.ModelChangeListener;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.api.items.SubscriptionItem;
import com.inductiveautomation.xopc.driver.api.items.WriteItem;
import com.inductiveautomation.xopc.driver.util.AddressNotFoundException;
import com.inductiveautomation.xopc.driver.util.TagTree.TagTreeNode;

public abstract class AbstractGenericTcpDriver
	implements Driver, ModelChangeListener, IGenericTcpDriverContext, RuntimeStateProvider {

	private static final String RUNTIME_STATE_ID = "TcpBinMsgQueue%s";
	
	private final String deviceName;
	private final DriverContext driverContext;

	protected volatile Logger log;

	private ExecutionManager executionManager;

	private volatile boolean shutdown = false;
	private DriverState state;
	private final Object stateLock = new Object();

	private final Object ioSessionLock = new Object();

	// Redundancy
	private RedundancyListener redundancyListener;
	private boolean redundancyEnabled=false;
	private final String runtimeStateId;

	// TagTree is used to simplify browsing. No tag reference needed, so the address is as key and value tag
	private final BrowseTree browseTree = new BrowseTree();

	private final FolderManager folderManager;

	protected AbstractGenericTcpDriver(DriverContext driverContext) {
		this.deviceName = driverContext.getDeviceName();
		this.driverContext = driverContext;
		runtimeStateId = String.format(RUNTIME_STATE_ID, deviceName);

		this.log = Logger.getLogger(getLoggerName());

		// TODO remove in final version
		log.debug("Initalize");

		folderManager = new FolderManager(log);
		state = DriverState.Disconnected;
		
		// Create a private executionManager with 2 Threads
		executionManager = driverContext.getGatewayContext().createExecutionManager(getDeviceName(), 2);
	}

	/**
	 * Initialize the redundancy system and subscription model.<br />
	 * This method should be called by derived classes after the configuration has been verified.
	 */
	protected void initialize() {
		// Initialize subscriptions
		getDriverContext().getSubscriptionModel().addModelChangeListener(this);
		List<SubscriptionItem> items = getDriverContext().getSubscriptionModel().getSubscriptionItems();
		itemsAdded(items);
		
		// Initialize redundancy
		RedundancyManager rm = getDriverContext().getGatewayContext().getRedundancyManager();
		
		rm.getRuntimeStateManager().registerRuntimeProvider(this);

		redundancyListener = new RedundancyListener();
		rm.addRedundancyStateListener(redundancyListener);

		redundancyEnabled = rm.isRedundancyEnabled();		
	}
	
	@Override
	public void shutdown() {
		// Unregister listeners
		getDriverContext().getSubscriptionModel().removeModelChangeListener(this);
		getRedundancyManager().getRuntimeStateManager().unregisterRuntimeProvider(this);
		if (redundancyListener != null) {
			getRedundancyManager().removeRedundancyStateListener(redundancyListener);
		}
		
		// Remove items from NodeManager
		folderManager.shutdown();

		executionManager.shutdown();

		shutdown = true;
	}

	@Override
	public String getDriverStatus() {
		return getDriverStateInternal().toLocalString();
	}

	/**
	 * The internal DriverState.
	 * 
	 * @return
	 * 		The internalDriverState
	 */
	protected DriverState getDriverStateInternal() {
		synchronized (this.stateLock) {
			return this.state;
		}
	}

	protected void setDriverState(DriverState driverState) {
		synchronized (this.stateLock) {
			DriverState oldState = this.state;
			this.state = driverState;
			// log uses State#name() instead of toString. I don't want localized strings in the log
			this.log.debug(String.format("Driver state changed %s -> %s", oldState.name(), driverState.name()));
		}
	}

	@Override
	public void browse(BrowseOperation browseOperation) {
		List<String> browseNodes = new ArrayList<String>();

		String startingAddress = browseOperation.getStartingAddress();

		TagTreeNode<String> startNode;
		if (startingAddress == null || startingAddress.isEmpty())
			startNode = this.browseTree.getRoot();
		else {
			startNode = browseTree.findTag(browseOperation.getStartingAddress());
		}

		if (startNode != null) {
			for (TagTreeNode<String> childNode : startNode.getChildren()) {
				// TagTreeNode.Address contains a modified address to use the TagTree with Arrays
				// See 'addTagToBrowseTree'
				// TagTreeNode.Tag contains the real address of the tag
				browseNodes.add(childNode.getTag());
				System.out.println("BrowseNode: " + childNode.getAddress() + " Tag:" + childNode.getTag());
			}
		}

		browseOperation.browseDone(StatusCode.GOOD, browseNodes, new Guid());
	}

	/**
	 * All nodes are built and added to the NodeManager on initialization of the message folder.
	 * This function is only called for tags that are not already present in the NodeManager, so in
	 * this implementation it should never be called with a valid address.
	 */
	@Override
	public void buildNode(String address, NodeId nodeId) throws AddressNotFoundException {
		if (log.isTraceEnabled())
			log.trace(String.format("buildNode called for address: %s", address));
		if (browseTree.findTag(address) == null) {
			if (log.isDebugEnabled())
				log.debug(String.format("buildNode called for unknown address: %s", address));
			throw new AddressNotFoundException(String.format("No DriverTag for address \"%s\" found.", address));
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readItems(List<? extends ReadItem> items) {
		getFolderManager().readItems((List<ReadItem>) items);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void writeItems(List<? extends WriteItem> items) {
		getFolderManager().writeItems((List<WriteItem>) items);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void itemsAdded(List<? extends SubscriptionItem> added)
	{
		getFolderManager().alterSubscriptions((List<SubscriptionItem>) added, null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void itemsRemoved(List<? extends SubscriptionItem> removed)
	{
		getFolderManager().alterSubscriptions(null, (List<SubscriptionItem>) removed);
	}

	/**
	 * Add a MessageFolder to the internal map.
	 * 
	 * @param folder
	 * 	The MessageFolder to add to the map.
	 */
	protected void addFolder(MessageFolder folder) {
		synchronized (folderManager) {
			folderManager.addFolder(folder);
		}
	}

	protected MessageFolder getMessageFolder(int deviceId, int messageId) {
		return folderManager.getById(deviceId, messageId);
	}
	
	protected DriverContext getDriverContext() {
		return driverContext;
	}

	public ExecutionManager getExecutionManager() {
		return executionManager;
	}

	protected FolderManager getFolderManager() {
		return folderManager;
	}
	
	public RedundancyManager getRedundancyManager() {
		return driverContext.getGatewayContext().getRedundancyManager();
	}
	
	//*****************************************************************************************
	// IGenericTcpDriverContext
	//

	@Override
	public String getDeviceName() {
		return deviceName;
	}

	@Override
	public abstract String getLoggerName();

	@Override
	public String getDiskPath() {
		return String.format("%s%sTcpBin_%s%s", getDriverContext().getHomeFolder(), File.separator, getDeviceName(), File.separator);
	}
	
	@Override
	public VariableNodeBuilder getVariableNodeBuilder() {
		return driverContext.getNodeBuilderFactory().newVariableNodeBuilder();
	}

	@Override
	public ObjectNodeBuilder getObjectNodeBuilder() {
		return driverContext.getNodeBuilderFactory().newObjectNodeBuilder();
	}

	@Override
	public <E extends Node> E buildAndAddNode(NodeBuilder<E> nodeBuilder, String address) {
		browseTree.addTag(address);
		return nodeBuilder.buildAndAdd(driverContext.getNodeManager());
	}
	
	@Override
	public boolean isActiveNode() {
		return !redundancyEnabled || getActivityLevel()==ActivityLevel.Active;
	}

	@Override
	public void removeNode(Node node) {
		driverContext.getNodeManager().removeNode(node);
	}

	@Override
	public void executeOnce(Runnable command) {
		executionManager.executeOnce(command);
	}

	@Override
	public ScheduledFuture<?> executeOnce(Runnable command, long delay, TimeUnit unit) {
		return executionManager.executeOnce(command, delay, unit);
	}
	
	@Override
	public void registerSelfSchedulingRunnable(String owner, String name, SelfSchedulingRunnable command) {
		executionManager.register(owner, name, command);
	}

	@Override
	public void unregisterScheduledRunnable(String owner, String name) {
		executionManager.unRegister(owner, name);
	}
	
	@Override
	public void postRuntimeStateUpdate(StateUpdate stateUpdate) {
		getRedundancyManager().getRuntimeStateManager().postRuntimeUpdate(getId(), stateUpdate);		
	}
	
	@Override
	public abstract void writeToRemoteDevice(ByteBuffer message, int deviceId);

	//*****************************************************************************************

	protected Object getIoSessionLock() {
		return ioSessionLock;
	}

	/**
	 * @return
	 * 	<code>true</code> when this instance has been shut down
	 */
	protected boolean isShutdown() {
		return shutdown;
	}

	////////////////////////////////////////////////////////////////////////////////
	// RuntimeStateProvider
	////////////////////////////////////////////////////////////////////////////////
	@Override
	public String getId() {
		return runtimeStateId;
	}
	
	/**
	 * Send the complete message queue to the other node.
	 * All queues, including empty ones, will be sent.
	 */
	@Override
	public Serializable getFullState() {
		log.info("Sending full runtime state update.");

		StatusUpdateMessage status = getStatusUpdate();
		
		List<StateUpdate>folderStates = getFolderManager().getFullRuntimeState();
		
		FullUpdateMessage stateObj = new FullUpdateMessage(status, folderStates);
		return stateObj;
	}
	
	@Override
	public void setFullState(Serializable stateObj) {
		log.info("Received full runtime state update.");
		try {
			if (stateObj instanceof FullUpdateMessage) {

				// Update connection status
				updateState(((FullUpdateMessage) stateObj).getStatus());

				// Update folder states
				getFolderManager().setFullRuntimeState(((FullUpdateMessage) stateObj).getFolderStates());
			}
		} catch (Exception e) {
			// An exception will stop the whole RuntimeState system, so it has to be caught here.
			if (log.isDebugEnabled()) {
				log.debug("Exception evaluating RuntimeState update:", e);
			} else {
				log.error("Exception evaluating RuntimeState update: " + e.toString());
			}
		}
	}
	
	@Override
	public void updateState(Serializable stateObj) {
		
		if (stateObj instanceof StateUpdate) {
			getFolderManager().updateRuntimeState((StateUpdate)stateObj);
		}
		
		if (stateObj instanceof StatusUpdateMessage) {
			setStatusUpdate((StatusUpdateMessage) stateObj);
		}
	}
	
	/**
	 * @return
	 * 	A StatusUpdateMessage to be used in a full update for a redundant configuration.
	 */
	protected abstract StatusUpdateMessage getStatusUpdate();
	
	/**
	 * Update the driver status in a redundant configuration. 
	 *
	 * @param statusUpdate
	 * 	The driver status as received from the redundant peer.
	 */
	protected abstract void setStatusUpdate(StatusUpdateMessage statusUpdate);
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private class RedundancyListener extends RedundancyStateAdapter {

		private ActivityLevel currentLevel;

		public RedundancyListener() {
			currentLevel = getRedundancyManager().getCurrentState().getActivityLevel();
		}
		
		public ActivityLevel getActivityLevel() {
			return currentLevel;
		}

		@Override
		public void redundancyStateChanged(RedundancyState newState) {

			if (newState.getActivityLevel() != currentLevel) {

				ActivityLevel newLevel = newState.getActivityLevel();
				log.info(String.format("Redundancy state changed from %s to %s", currentLevel, newLevel));

				activityLevelChanged(currentLevel, newLevel);
				
				currentLevel = newLevel;
			}
		}
	}
	
	public ActivityLevel getActivityLevel() {
		return redundancyListener.getActivityLevel();
	}
	
	/**
	 * Called when the redundancy activity level changes.
	 *
	 * @param currentLevel
	 * @param newLevel
	 */
	protected abstract void activityLevelChanged(ActivityLevel currentLevel, ActivityLevel newLevel);
	
}
