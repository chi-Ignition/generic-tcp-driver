package com.chitek.ignition.drivers.generictcp.tests;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.chitek.TestUtils.MockExecutor;
import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.chitek.ignition.drivers.generictcp.folder.BrowseTree;
import com.chitek.ignition.drivers.generictcp.redundancy.StateUpdate;
import com.inductiveautomation.ignition.common.execution.SchedulingController;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;
import com.inductiveautomation.opcua.nodes.Node;
import com.inductiveautomation.opcua.nodes.NodeManager;
import com.inductiveautomation.opcua.nodes.ObjectNode;
import com.inductiveautomation.opcua.nodes.VariableNode;
import com.inductiveautomation.opcua.nodes.builders.NodeBuilder;
import com.inductiveautomation.opcua.nodes.builders.ObjectNodeBuilder;
import com.inductiveautomation.opcua.nodes.builders.VariableNodeBuilder;
import com.inductiveautomation.opcua.types.AccessLevel;
import com.inductiveautomation.opcua.types.AttributeType;
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.LocalizedText;
import com.inductiveautomation.opcua.types.NodeClass;
import com.inductiveautomation.opcua.types.NodeId;
import com.inductiveautomation.opcua.types.QualifiedName;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.opcua.types.UInt32;
import com.inductiveautomation.opcua.types.ValueRank;

public class MockDriverContext implements IGenericTcpDriverContext {

	private final String deviceName;
	private final BrowseTree browseTree = new BrowseTree();
	private final Map<NodeId, Node> nodeMap = new HashMap<NodeId, Node>();
	private final Map<String, SelfSchedulingRunnable> selfSchedulingRunnables = new HashMap<String, SelfSchedulingRunnable>();
	private final MockExecutor executor = new MockExecutor();
	private byte[] lastWrittenMessage;
	private String diskPath;
	public boolean rescheduleRequested;
	private StateUpdate lastStateUpdate;

	// Test methods ///////////////////////////////////////////////////
	public void setDiskPath(String path) {
		diskPath = path;
	}
	
	public BrowseTree getBrowseTree() {
		return browseTree;
	}

	public Node getNode(NodeId nodeId) {
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
	public VariableNodeBuilder getVariableNodeBuilder() {
		return new MockVariableNodeBuilder();
	}

	@Override
	public ObjectNodeBuilder getObjectNodeBuilder() {
		return new MockObjectNodeBuilder();
	}

	@Override
	public <E extends Node> E buildAndAddNode(NodeBuilder<E> nodeBuilder, String address) {
		System.out.println(String.format("buildAndAddNode: %s", address));
		browseTree.addTag(address);
		E node = nodeBuilder.buildAndAdd(null);
		nodeMap.put(node.getNodeId(), node);
		return node;
	}

	@Override
	public void removeNode(Node node) {

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

	private class MockVariableNodeBuilder implements VariableNodeBuilder {

		private final MockVariableNode node = new MockVariableNode();

		@Override
		public VariableNode build() {
			return node;
		}

		@Override
		public VariableNode buildAndAdd(NodeManager nodeManager) {
			return node;
		}

		@Override
		public VariableNodeBuilder setNodeId(NodeId nodeId) {
			node.nodeId = nodeId;
			return this;
		}

		@Override
		public VariableNodeBuilder setBrowseName(QualifiedName browseName) {
			node.browseName = browseName;
			return this;
		}

		@Override
		public VariableNodeBuilder setDisplayName(LocalizedText displayName) {
			node.displayName = displayName;
			return this;
		}

		@Override
		public VariableNodeBuilder setDescription(LocalizedText description) {
			node.description = description;
			return this;
		}

		@Override
		public VariableNodeBuilder setTypeDefinition(NodeId typeDefinition) {
			return this;
		}

		@Override
		public VariableNodeBuilder setDataType(NodeId dataType) {
			node.dataTypeId = dataType;
			return this;
		}

		@Override
		public VariableNodeBuilder setValueRank(ValueRank valueRank) {
			node.valueRank = valueRank;
			return this;
		}

		@Override
		public VariableNodeBuilder setArrayDimensions(UInt32[] arrayDimensions) {
			node.arrayDimensions = arrayDimensions;
			return this;
		}

		@Override
		public VariableNodeBuilder setAccessLevel(EnumSet<AccessLevel> accessLevel) {
			node.accessLevel = accessLevel;
			return this;
		}

		@Override
		public VariableNodeBuilder setUserAccessLevel(EnumSet<AccessLevel> userAccessLevel) {
			node.userAccessLevel = userAccessLevel;
			return this;
		}

		@Override
		public VariableNodeBuilder setMinimumSamplingInterval(double minimumSamplingInterval) {
			node.minimumSamplingInterval = minimumSamplingInterval;
			return this;
		}

		@Override
		public VariableNodeBuilder setHistorizing(boolean historizing) {
			node.historizing = historizing;
			return this;
		}

		@Override
		public VariableNodeBuilder setValue(DataValue dataValue) {
			node.dataValue = dataValue;
			return this;
		}
	}

	private class MockObjectNodeBuilder implements ObjectNodeBuilder {

		private final MockObjectNode node = new MockObjectNode();

		@Override
		public ObjectNode build() {
			return node;
		}

		@Override
		public ObjectNode buildAndAdd(NodeManager nodeManager) {
			return node;
		}

		@Override
		public ObjectNodeBuilder setNodeId(NodeId nodeId) {
			node.nodeId = nodeId;
			return this;
		}

		@Override
		public ObjectNodeBuilder setTypeDefinition(NodeId typeDefinition) {
			return this;
		}

		@Override
		public ObjectNodeBuilder setBrowseName(QualifiedName browseName) {
			node.browseName = browseName;
			return this;
		}

		@Override
		public ObjectNodeBuilder setDisplayName(LocalizedText displayName) {
			node.displayName = displayName;
			return this;
		}

		@Override
		public ObjectNodeBuilder setDescription(LocalizedText description) {
			node.description = description;
			return this;
		}

		@Override
		public ObjectNodeBuilder setEventNotifier(byte eventNotifier) {
			node.eventNotifier = eventNotifier;
			return this;
		}

	}


	private abstract class MockNode implements Node {

		protected QualifiedName browseName;
		protected LocalizedText description;
		protected LocalizedText displayName;
		protected NodeId nodeId;

		@Override
		public QualifiedName getBrowseName() {
			return browseName;
		}

		@Override
		public LocalizedText getDisplayName() {
			return displayName;
		}

		@Override
		public LocalizedText getDescription() {
			return description;
		}

		@Override
		public NodeId getNodeId() {
			return nodeId;
		}

		@Override
		public UInt32 getWriteMask() {
			return null;
		}

		@Override
		public UInt32 getUserWriteMask() {
			return null;
		}

		@Override
		public DataValue readAttribute(AttributeType paramAttributeType) {
			return null;
		}

		@Override
		public StatusCode writeAttribute(AttributeType paramAttributeType, DataValue paramDataValue) {
			return null;
		}

		@Override
		public void setAttributeChangeListener(AttributeChangeListener paramAttributeChangeListener) {
		}

	}

	private class MockVariableNode extends MockNode implements VariableNode {
		protected DataValue dataValue;
		protected boolean historizing;
		protected double minimumSamplingInterval;
		protected EnumSet<AccessLevel> userAccessLevel;
		protected EnumSet<AccessLevel> accessLevel;
		protected UInt32[] arrayDimensions;
		protected ValueRank valueRank;
		protected NodeId dataTypeId;

		@Override
		public EnumSet<AccessLevel> getAccessLevel() {
			return accessLevel;
		}
		@Override
		public UInt32[] getArrayDimensions() {
			return arrayDimensions;
		}
		@Override
		public NodeId getDataTypeId() {
			return dataTypeId;
		}
		@Override
		public double getMinimumSamplingInterval() {
			return minimumSamplingInterval;
		}
		@Override
		public EnumSet<AccessLevel> getUserAccessLevel() {
			return userAccessLevel;
		}
		@Override
		public DataValue getValue() {
			return dataValue;
		}
		@Override
		public ValueRank getValueRank() {
			return valueRank;
		}
		@Override
		public boolean isHistorizing() {
			return historizing;
		}
		@Override
		public void setValue(DataValue value) {
			this.dataValue=value;
		}
		@Override
		public NodeClass getNodeClass() {
			return NodeClass.Variable;
		}
	}

	private class MockObjectNode extends MockNode implements ObjectNode {

		private byte eventNotifier;

		@Override
		public NodeClass getNodeClass() {
			return NodeClass.Object;
		}

		@Override
		public byte getEventNotifier() {
			return eventNotifier;
		}
	}
	
	private class Controller implements SchedulingController {
		@Override
		public void requestReschedule(SelfSchedulingRunnable source) {
			rescheduleRequested = true;
		}
	}

}
