package com.chitek.ignition.drivers.generictcp.redundancy;

public interface FolderStateProvider {
	
	/**
	 * Update of the folder state sent from the active node.
	 * @param stateUpdate
	 */
	public void updateRuntimeState(StateUpdate stateUpdate);
	
	/**
	 * A full update of the folder state
	 * @param stateUpdate
	 */
	public void setFullState(StateUpdate stateUpdate);
	
	/**
	 * @return
	 * 	The current state of the folder.
	 */
	public StateUpdate getFullState();
}
