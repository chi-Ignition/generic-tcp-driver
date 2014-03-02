package com.chitek.ignition.drivers.generictcp.redundancy;

import java.io.Serializable;
import java.util.List;

/**
 * Class for transferring full driver state to redundant gateways.
 */
public class FullUpdateMessage implements Serializable {

	private static final long serialVersionUID = 1L;
	private final List<StateUpdate> folderStates;
	private final StatusUpdateMessage status;

	public FullUpdateMessage(StatusUpdateMessage status, List<StateUpdate> folderStates) {
		this.status = status;
		this.folderStates = folderStates;
	}

	public StatusUpdateMessage getStatus() {
		return status;
	}

	public List<StateUpdate> getFolderStates() {
		return folderStates;
	}
}
