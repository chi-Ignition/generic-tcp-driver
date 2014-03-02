package com.chitek.ignition.drivers.generictcp.meta.config;

import java.nio.ByteOrder;

import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;

public interface IDriverSettings {
	public ByteOrder getByteOrder();

	public int getTimestampFactor();

	/**
	 * The maximum time between two parts of a data package. If a package is not completed in
	 * the time given here, incoming data will be discarded.
	 *
	 * @return
	 */
	public int getMessageTimeout();

	public OptionalDataType getMessageIdType();
}
