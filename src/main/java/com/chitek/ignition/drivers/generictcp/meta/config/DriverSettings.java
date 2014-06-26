package com.chitek.ignition.drivers.generictcp.meta.config;

import java.nio.ByteOrder;

import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;

/**
 * This class wraps general driver settings. In addition, the messageIdType is also availabe from this class though it is not a
 * setting, but configured with the message.
 *
 */
public class DriverSettings implements IDriverSettings {
	private String hostname;
	private int port;
	private final boolean connectOnStartup;
	private final int timeout;
	private final int messageTimeout;
	private final ByteOrder byteOrder;
	private final int timestampFactor;
	private final long maxTimestamp;
	private final OptionalDataType messageIdType;

	public DriverSettings(
		String hostname,
		int port,
		boolean connectOnStartup,
		int timeout,
		int packetTimeout,
		boolean reverseByteOrder,
		int timestampFactor,
		long maxTimestamp,
		OptionalDataType messageIdType)
	{
		this.hostname = hostname;
		this.port = port;
		this.connectOnStartup = connectOnStartup;
		this.timeout = timeout;
		this.messageTimeout = packetTimeout;
		this.byteOrder = reverseByteOrder ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
		this.timestampFactor = timestampFactor;
		this.maxTimestamp = maxTimestamp;
		this.messageIdType = messageIdType;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isConnectOnStartup() {
		return connectOnStartup;
	}

	/** If no data is received for more than the time given here, the driver will disconnect
	 * an try to reconnect. 0 disables this function.
	 *
	 * @return
	 * 	The timeout in ms.
	 */
	public int getTimeout() {
		return timeout;
	}

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
