package com.chitek.ignition.drivers.generictcp.types;

import com.chitek.ignition.drivers.generictcp.ModuleHook;
import com.inductiveautomation.ignition.common.BundleUtil;

public enum DriverState {
	Disconnected, Connecting, Connected, Disconnecting, Terminated, Disabled, ConfigError, Listening;

	public String toLocalString() {
		return BundleUtil.get().getStringLenient(ModuleHook.BUNDLE_PREFIX + ".State." + this.name());
	}
}
