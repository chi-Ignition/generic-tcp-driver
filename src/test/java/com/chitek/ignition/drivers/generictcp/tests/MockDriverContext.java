package com.chitek.ignition.drivers.generictcp.tests;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.UaNodeManager;
import org.eclipse.milo.opcua.sdk.server.api.NodeManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode.UaObjectNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import com.chitek.TestUtils.MockExecutor;
import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.chitek.ignition.drivers.generictcp.folder.BrowseTree;
import com.chitek.ignition.drivers.generictcp.redundancy.StateUpdate;
import com.inductiveautomation.ignition.common.execution.SchedulingController;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;

public class MockDriverContext implements IGenericTcpDriverContext {

	private final String deviceName;
	private final BrowseTree browseTree = new BrowseTree();
	private final Map<NodeId, UaNode> nodeMap = new HashMap<NodeId, UaNode>();
	private final Map<String, SelfSchedulingRunnable> selfSchedulingRunnables = new HashMap<String, SelfSchedulingRunnable>();
	private final MockExecutor executor = new MockExecutor();
	private byte[] lastWrittenMessage;
	private String diskPath;
	public boolean rescheduleRequested;
	private StateUpdate lastStateUpdate;
	private UaNodeContext nodeContext = new MockNodeContext();
	
	// Test methods ///////////////////////////////////////////////////
	public void setDiskPath(String path) {
		diskPath = path;
	}
	
	public BrowseTree getBrowseTree() {
		return browseTree;
	}

	public UaNode getNode(NodeId nodeId) {
		return nodeMap.get(nodeId);
	}

	public MockExecutor getExecutor() {
		return executor;
	}
	
	public SelfSchedulingRunnable getSelfSchedulingRunnable(String owner, String name) {
		return selfSchedulingRunnables.get(owner + name);
	}

	/**
	 * @return
	 * 	Returns the message that has been written using writeToRemoteDevice
	 */
	public byte[] getLastWrittenMessage() {
		return lastWrittenMessage;
	}
	
	/**
	 * @return
	 * 	The last runtime state update set by postRuntimeStateUpdate
	 */
	public StateUpdate getLastStateUpdate() {
		return lastStateUpdate;
	}
	
	///////////////////////////////////////////////////////////////////

	public MockDriverContext(String deviceName) {
		this.deviceName = deviceName;
	}

	@Override
	public String getDeviceName() {
		return deviceName;
	}
	
	@Override
	public String getDiskPath() {
		return diskPath;
	}

	@Override
	public String getLoggerName() {
		return DriverTestSuite.getLogger().getName();
	}

	@Override
	public UaNodeContext getNodeContext() {
		return nodeContext;
	}
	
	@Override
	public UaVariableNodeBuilder getVariableNodeBuilder() {
		return UaVariableNode.builder(nodeContext);
	}

	@Override
	public UaObjectNodeBuilder getObjectNodeBuilder() {
		return UaObjectNode.builder(nodeContext);
	}

	@Override
	public UaNode addNode(UaNode node, String address) {
		System.out.println(String.format("addNode: %s", address));
		browseTree.addTag(address);
		nodeMap.put(node.getNodeId(), node);
		return node;
	}

	@Override
	public void removeNode(UaNode node) {

	}

	@Override
	public void executeOnce(Runnable command) {
		executor.executeOnce(command);
	}

	@Override
	public ScheduledFuture<?> executeOnce(Runnable command, long delay, TimeUnit unit) {
		return executor.executeOnce(command, delay, unit);
	}
	
	@Override
	public void registerSelfSchedulingRunnable(String owner, String name, SelfSchedulingRunnable command) {
		selfSchedulingRunnables.put(owner + name, command);
		command.setController(new Controller());
	}
	
	@Override
	public void unregisterScheduledRunnable(String owner, String name) {
		SelfSchedulingRunnable command = selfSchedulingRunnables.remove(owner + name);
		if (command == null) {
			throw new IllegalArgumentException(String.format("The is no registered runnable for %s.%s", owner, name));
		}
	}

	@Override
	public void writeToRemoteDevice(ByteBuffer message, int deviceId) {
		lastWrittenMessage = new byte[message.remaining()];
		message.get(lastWrittenMessage);
	}

	@Override
	public boolean isActiveNode() {
		return true;
	}
	
	@Override
	public void postRuntimeStateUpdate(StateUpdate stateUpdate) {
		lastStateUpdate = stateUpdate;
	}
	
	private class Controller implements SchedulingController {
		@Override
		public void requestReschedule(SelfSchedulingRunnable source) {
			rescheduleRequested = true;
		}
	}
	
	private class MockNodeContext implements UaNodeContext {

		private UaNodeManager nodeManager = new UaNodeManager();
		private NamespaceTable namespaceTable = new NamespaceTable();
		
		@Override
		public OpcUaServer getServer() {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public NodeManager<UaNode> getNodeManager() {
			return nodeManager;
		}
		
		@Override
		public NamespaceTable getNamespaceTable() {
			return namespaceTable;
		}
		
	}

}
