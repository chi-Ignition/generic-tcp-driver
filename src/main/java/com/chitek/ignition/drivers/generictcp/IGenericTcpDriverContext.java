package com.chitek.ignition.drivers.generictcp;

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode.UaObjectNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;

import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;

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
	public UaVariableNodeBuilder getVariableNodeBuilder();

	/**
	 * @return
	 * 	A new {@link VariableNodeBuilder}
	 */
	public UaObjectNodeBuilder getObjectNodeBuilder();

	/**
	 * @return
	 * 	The {@link UaNodeContext}
	 */
	public UaNodeContext getNodeContext();
	
	/**
	 * Adds the node to the NodeManager by calling {@link #UaNodeManager.addNode(node)}
	 * and adds the given address to the drivers browse tree.
	 *
	 * @param node
	 * 	A UaNode obtained from {@link #getVariableNodeBuilder()} or {@link #getObjectNodeBuilder()}
	 * @param address
	 * 	The tag address to register for browsing.
	 * @return
	 * 	The added node
	 */
	public UaNode addNode(UaNode node, String address);

	/**
	 * Remove a Node from the drivers NodeManager.
	 *
	 * @param node
	 * 	The node to remove.
	 * @see org.eclipse.milo.opcua.sdk.server.UaNodeManager#removeNode(UaNode)
	 */
	public void removeNode(UaNode node);

	/**
	 * Execute the given command in the drivers ExecutionManager.
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

}
