package com.chitek.ignition.drivers.generictcp.meta.config;

import java.nio.ByteOrder;
import java.util.List;

import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.RemoteDevice;

/**
 * This class wraps general driver settings. In addition, the messageIdType is also availabe from this class though it is not a
 * setting, but configured with the message.
 *
 */
public class DriverSettingsPassive implements IDriverSettings {
	private final String serverHostname;
	private final int serverPort;
	private final int timeout;
	private final boolean useUdp;
	private final boolean acceptAll;
	private final List<RemoteDevice> devices;
	private final int messageTimeout;
	private final ByteOrder byteOrder;
	private final int timestampFactor;
	private final long maxTimestamp;
	private final OptionalDataType messageIdType;

	public DriverSettingsPassive(
		String serverHostname,
		int serverPort,
		int timeout,
		boolean useUdp,
		boolean acceptAll,
		List<RemoteDevice> devices,
		int packetTimeout,
		boolean reverseByteOrder,
		int timestampFactor,
		long maxTimestamp,
		OptionalDataType messageIdType)
	{
		this.serverHostname = serverHostname;
		this.serverPort = serverPort;
		this.timeout=timeout;
		this.useUdp = useUdp;
		this.acceptAll = acceptAll;
		this.devices = devices;
		this.messageTimeout = packetTimeout;
		this.byteOrder = reverseByteOrder ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
		this.timestampFactor = timestampFactor;
		this.maxTimestamp = maxTimestamp;
		this.messageIdType = messageIdType;
	}

	public String getServerAddress() {
		return serverHostname;
	}

	public int getServerPort() {
		return serverPort;
	}
	
	/** If no data is received for more than the time given here, the driver will disconnect.
	 * 0 disables this function.
	 *
	 * @return
	 * 	The timeout in ms.
	 */
	public long getTimeout() {
		return timeout*1000;
	}
	
	public boolean getUseUdp() {
		return useUdp;
	}
	
	/**
	 * @return
	 * 	true if this device should ignore the devices list and accept all incoming connections
	 */
	public boolean getAcceptAll() {
		return acceptAll;
	}

	public List<RemoteDevice> getDevices() {
		return devices;
	}

	/**
	 * The maximum time between two parts of a data package. If a package is not completed in
	 * the time given here, incoming data will be discarded.
	 * 
	 * @return
	 */
	@Override
	public int getMessageTimeout() {
		return messageTimeout;
	}

	@Override
	public ByteOrder getByteOrder() {
		return byteOrder;
	}

	@Override
	public int getTimestampFactor() {
		return timestampFactor;
	}

	@Override
	public long getMaxTimestamp() {
		return maxTimestamp;
	}
	
	/**
	 * @return
	 * 		The message id type from the message config. This is no general setting, but
	 * 		added here for convenience.
	 */
	@Override
	public OptionalDataType getMessageIdType() {
		return messageIdType;
	}

}
