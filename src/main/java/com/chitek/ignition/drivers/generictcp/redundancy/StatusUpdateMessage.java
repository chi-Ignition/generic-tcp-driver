package com.chitek.ignition.drivers.generictcp.redundancy;

import java.io.Serializable;

/**
 * Class for transferring the driver state to a redundant peer.
 */
public class StatusUpdateMessage implements Serializable {
	private static final long serialVersionUID = 1L;
	private final boolean connectionEnabled;
	private final String hostname;
	private final int port;

	public StatusUpdateMessage(Boolean connectionEnabled, String hostname,
			Integer port) {
		this.connectionEnabled = connectionEnabled;
		this.hostname = hostname;
		this.port = port;
	}

	public boolean isConnectionEnabled() {
		return connectionEnabled;
	}

	public String getHostname() {
		return hostname;
	}

	public int getPort() {
		return port;
	}
}
