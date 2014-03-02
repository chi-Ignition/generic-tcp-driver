package com.chitek.ignition.drivers.generictcp.meta.config.ui;

import java.io.Serializable;

import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;

/**
 * This class is used to store the edited configuration in the session.
 */
public class SessionConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	private DriverConfig driverConfig;
	private WritebackConfig writebackConfig;
	private HeaderConfig headerConfig;

	public SessionConfig(DriverConfig driverConfig, WritebackConfig writebackConfig, HeaderConfig headerConfig) {
		this.driverConfig = driverConfig;
		this.writebackConfig = writebackConfig;
		this.headerConfig = headerConfig;
	}

	public DriverConfig getDriverConfig() {
		return driverConfig;
	}

	public WritebackConfig getWritebackConfig() {
		return writebackConfig;
	}

	public HeaderConfig getHeaderConfig() {
		return headerConfig;
	}
}
