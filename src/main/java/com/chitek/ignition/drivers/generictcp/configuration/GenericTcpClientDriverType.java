package com.chitek.ignition.drivers.generictcp.configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.chitek.ignition.drivers.generictcp.GenericTcpClientDriver;
import com.chitek.ignition.drivers.generictcp.configuration.settings.GenericTcpClientDriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.ui.ConfigUiTabs;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.ReferenceField;
import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import com.inductiveautomation.xopc.driver.api.Driver;
import com.inductiveautomation.xopc.driver.api.DriverContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import com.inductiveautomation.xopc.driver.api.configuration.DriverType;
import com.inductiveautomation.xopc.driver.api.configuration.links.ConfigurationUILink;
import com.inductiveautomation.xopc.driver.api.configuration.links.LinkEntry;

@SuppressWarnings("serial")
public class GenericTcpClientDriverType extends DriverType {

	public static final String TYPE_ID = "GenericTcpClient";

	public GenericTcpClientDriverType() {
		super(TYPE_ID, "GenericTcpDriver.GenericTcpClientDriverType.Name", "GenericTcpDriver.GenericTcpClientDriverType.Description");
	}

	@Override
	public Driver createDriver(DriverContext driverContext, DeviceSettingsRecord deviceSettings) {
		GenericTcpClientDriverSettings settings = findProfileSettingsRecord(driverContext.getGatewayContext(), deviceSettings);

		return new GenericTcpClientDriver(driverContext, settings);
	}

	@Override
	public RecordMeta<? extends PersistentRecord> getSettingsRecordType() {
		return GenericTcpClientDriverSettings.META;
	}

	@Override
	public ReferenceField<?> getSettingsRecordForeignKey() {
		return GenericTcpClientDriverSettings.DeviceSettings;
	}

	@Override
	public List<LinkEntry> getLinks() {
		LinkEntry[] list = {new ConfigLink()};
		return Arrays.asList(list);
	}

	/*
	 * Link to PacketConfigUI
	 */
	private static class ConfigLink implements ConfigurationUILink {

		@Override
		public String getLinkText(Locale locale) {
			return BundleUtil.get().getStringLenient(locale, "GenericTcpDriver.configLink");
		}

		@Override
		public ConfigPanel getConfigurationUI(IConfigPage configPage, ConfigPanel returnPanel, PersistentRecord record,	Callback callback) {
			return new ConfigUiTabs(configPage, returnPanel, record, callback);
		}

	}

}
