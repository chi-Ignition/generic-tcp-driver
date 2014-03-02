package com.chitek.ignition.drivers.generictcp;

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.chitek.ignition.drivers.generictcp.redundancy.StateUpdate;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;
import com.inductiveautomation.opcua.nodes.Node;
import com.inductiveautomation.opcua.nodes.builders.NodeBuilder;
import com.inductiveautomation.opcua.nodes.builders.ObjectNodeBuilder;
import com.inductiveautomation.opcua.nodes.builders.VariableNodeBuilder;

/**
 * The GenericTcpDriverContext wraps all information that has to be passed
 * from the driver class to message folders.
 *
 */
public interface IGenericTcpDriverContext {

	/**
	 * @return
	 * 	The user-assigned name for this device (driver instance).
	 * @see {@link com.inductiveautomation.xopc.driver.api.DriverContext#getDeviceName DriverContext.getDeviceName}
	 */
	public String getDeviceName();

	/**
	 * @return
	 * 	The base name to be used when creating sub-loggers.
	 */
	public String getLoggerName();
	
	/**
	 * Returns the disk path for this driver instance. The path contains the unique driver id.
	 * 
	 * @return
	 * 	The disk path to store message queues.
	 */
	public String getDiskPath();
	
	/**
	 * @return
	 * 	A new {@link VariableNodeBuilder}
	 */
	public VariableNodeBuilder getVariableNodeBuilder();

	/**
	 * @return
	 * 	A new {@link VariableNodeBuilder}
	 */
	public ObjectNodeBuilder getObjectNodeBuilder();

	/**
	 * Wraps a call to {@link com.inductiveautomation.opcua.nodes.builders.NodeBuilder#buildAndAdd NodeBuilder.buildAndAdd}
	 * and adds the given address to the drivers browse tree.
	 *
	 * @param nodeBuilder
	 * 	A nodeBuilder obtained {@link #getVariableNodeBuilder()} or {@link #getObjectNodeBuilder()}
	 * @param address
	 * 	The tag address to register for browsing.
	 * @return
	 * 	The created Node
	 */
	public <E extends Node> E buildAndAddNode(NodeBuilder<E> nodeBuilder, String address);

	/**
	 * Remove a Node from the drivers NodeManager.
	 *
	 * @param node
	 * 	The node to remove.
	 * @see com.inductiveautomation.opcua.nodes.NodeManager#removeNode NodeManager.removeNode
	 */
	public void removeNode(Node node);

	/**
	 * Exceute the given command in the drivers ExecutionManager.
	 *
	 * @param command
	 * 	The Runnable to execute.
	 * @see com.inductiveautomation.ignition.common.execution.ExecutionManager#executeOnce(Runnable) ExecutionManager.executeOnce(Runnable)
	 */
	public void executeOnce(Runnable command);

	/**
	 * Schedule the given command in the drivers ExecutionManager.
	 *
	 * @param command
	 * 	The Runnable to execute.
	 * 
	 * @see com.inductiveautomation.ignition.common.execution.ExecutionManager#executeOnce(Runnable, long, TimeUnit) ExecutionManager.executeOnce(Runnable, long, TimeUnit)
	 */
	public ScheduledFuture<?> executeOnce(java.lang.Runnable command, long delay, TimeUnit unit);

	/**
	 * Registers a self scheduling command to be executed. Self scheduling commands provide their own execution delay.
	 * 
	 * @param owner
	 * 	Name of the "owner"- just a string qualifier for the command name.
	 * @param name
	 * 	 Identifier used in conjunction with the owner to identify the command.
	 * @param command
	 * 	The Runnable to execute.
	 *
	 * @see com.inductiveautomation.ignition.common.execution.ExecutionManager#register(String, String, SelfSchedulingRunnable) ExecutionManager.register(String, String, SelfSchedulingRunnable)
	 */
	void registerSelfSchedulingRunnable(String owner, String name, SelfSchedulingRunnable command);
	
	/**
	 * Unregisters a given command. If it is in the process of executing, it will be interrupted.
	 * 
	 * @param owner
	 * 	Name of the "owner"- just a string qualifier for the command name.
	 * @param name
	 * 	 Identifier used in conjunction with the owner to identify the command.
	 *
	 * @see com.inductiveautomation.ignition.common.execution.ExecutionManager#unregister(String, String) ExecutionManager.unregister(String, String)
	 */
	void unregisterScheduledRunnable(String owner, String name);
	
	/**
	 * Writes a message to the connected remote device.
	 *
	 * @param message
	 * 	The message to write.
	 * @param deviceId
	 * 	The remote device to write to
	 */
	public void writeToRemoteDevice(ByteBuffer message, int deviceId);

	/**
	 * @return
	 * 	The RedundancyManager
	 */
	//public RedundancyManager getRedundancyManager();

	/**
	 * @return
	 * 	<code>true</code> if this is the active node in a redundant configuration (or if redundancy is disabled).
	 */
	public boolean isActiveNode();

	/**
	 * Post a state update to the non active node in a redundant system.
	 *
	 * @see com.inductiveautomation.ignition.gateway.redundancy.RuntimeStateManager#postRuntimeUpdate(String, java.io.Serializable)
	 *  RuntimeStateManager.postRuntimeUpdate(String, java.io.Serializable)
	 *
	 * @param stateUpdate
	 * 	The state to send.
	 */
	public void postRuntimeStateUpdate(StateUpdate stateUpdate);
}
