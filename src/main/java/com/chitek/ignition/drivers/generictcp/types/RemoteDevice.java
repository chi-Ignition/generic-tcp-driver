package com.chitek.ignition.drivers.generictcp.types;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class RemoteDevice {

	private final String hostname;
	private final String alias;
	private InetAddress remoteAddress;
	private InetSocketAddress remoteSocketAddress;
	private int deviceId;

	public RemoteDevice(String hostname, String alias) {
		this.hostname = hostname;
		this.alias = alias;
	}

	public String getHostname() {
		return hostname;
	}

	public String getAlias() {
		return alias;
	}

	/**
	 * Set the numeric id for this device.
	 *
	 * @param deviceId
	 */
	public void setDeviceId(int deviceId) {
		this.deviceId = deviceId;
	}

	/**
	 * @return
	 * 	The assigned deviceId. The device id has to be assigned using {@link #setDeviceId} before.
	 */
	public int getDeviceId() {
		return deviceId;
	}

	/**
	 * @return
	 * 	<code>true</code> if the hostname has already been resolved to an InetAddress. getInetAddress() will return immediatly
	 * 	in this case.
	 */
	public boolean isAddressResolved() {
		return remoteAddress != null;
	}

	public InetAddress getInetAddress() {
		if (remoteAddress != null) {
			return remoteAddress;
		}

		try {
			InetAddress address = InetAddress.getByName(getHostname());
			remoteAddress = address;
			return remoteAddress;
		} catch (UnknownHostException e) {
			return null;
		}
	}

	/**
	 * Used to store the remote socket after the device has connected to the driver.
	 *
	 * @param remoteSocket
	 * 	The remote socket address.
	 */
	public void setRemoteSocketAddress(InetSocketAddress remoteSocket) {
		this.remoteSocketAddress = remoteSocket;
	}

	public InetSocketAddress getRemoteSocketAddress() {
		return remoteSocketAddress;
	}
}
