package com.chitek.ignition.drivers.generictcp.configuration.settings;

import org.apache.wicket.validation.validator.RangeValidator;

import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.inductiveautomation.ignition.common.Base64;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.localdb.persistence.BlobField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.BooleanField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.Category;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IntField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.ReferenceField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;

import simpleorm.dataset.SFieldFlags;

@SuppressWarnings("serial")
public class GenericTcpClientDriverSettings extends PersistentRecord implements IGenericTcpDriverSettings {

	static final long MAX_TIMESTAMP = 0xffffffffL;
	
	public static final RecordMeta<GenericTcpClientDriverSettings> META = new RecordMeta<GenericTcpClientDriverSettings>(
		GenericTcpClientDriverSettings.class, "GenTcpClientDriverSettings");

	public static final LongField DeviceSettingsId = new LongField(META, "DeviceSettingsId", SFieldFlags.SPRIMARY_KEY);
	public static final ReferenceField<DeviceSettingsRecord> DeviceSettings = new ReferenceField<DeviceSettingsRecord>(
		META, DeviceSettingsRecord.META, "DeviceSettings", DeviceSettingsId);

	/* Connectivity */
	public static StringField Hostname = new StringField(META, "Hostname", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
	public static IntField Port = new IntField(META, "Port", SFieldFlags.SMANDATORY);
	public static BooleanField ConnectOnStartup = new BooleanField(META, "ConnectOnStartup");
	public static IntField Timeout = new IntField(META, "Timeout");

	/* Message Handling */
	public static IntField PacketTimeout = new IntField(META, "PacketTimeout");
	public static BooleanField ReverseByteOrder = new BooleanField(META, "ReverseByteOrder");
	public static IntField TimestampFactor = new IntField(META, "TimestampFactor");
	public static LongField MaxTimestamp = new LongField(META, "MaxTimestamp");

	/* Config */
	public static BlobField MessageConfig = new BlobField(META, "MessageConfig");
	public static BlobField HeaderConfig = new BlobField(META, "HeaderConfig");
	public static BlobField WritebackConfig = new BlobField(META, "WritebackConfig");

	/* Categories */
	public static final Category Connectivity = new Category("GenericTcpClientDriverSettings.Category.Connectivity", 1001)
	.include(Hostname, Port, ConnectOnStartup, Timeout);
	public static Category MessageHandling = new Category("GenericTcpClientDriverSettings.Category.MessageHandling", 1002)
	.include(PacketTimeout, ReverseByteOrder, TimestampFactor, MaxTimestamp);

	static {
		DeviceSettings.getFormMeta().setVisible(false);
		MessageConfig.getFormMeta().setVisible(false);
		HeaderConfig.getFormMeta().setVisible(false);
		WritebackConfig.getFormMeta().setVisible(false);

		Hostname.setDefault("");
		Port.setDefault(1999);
		Port.addValidator(new RangeValidator<Integer>(1, 65535));
		ConnectOnStartup.setDefault(true);
		Timeout.setDefault(0);
		PacketTimeout.setDefault(1000);
		PacketTimeout.addValidator(new RangeValidator<Integer>(50, 10000));
		ReverseByteOrder.setDefault(false);
		TimestampFactor.setDefault(1);
		TimestampFactor.addValidator(new RangeValidator<Integer>(1, 1000));
		MaxTimestamp.setDefault(MAX_TIMESTAMP);
		MaxTimestamp.getFormMeta().addValidator(new RangeValidator<Long>((long)128, MAX_TIMESTAMP));

		MessageConfig.setDefault(new byte[0]);
		HeaderConfig.setDefault(new byte[0]);
		WritebackConfig.setDefault(new byte[0]);
	}

	@Override
	public RecordMeta<?> getMeta() {
		return META;
	}

	public DriverSettings getDriverSettings() {

		OptionalDataType messageIdType;
		try {
			messageIdType = getParsedMessageConfig().getMessageIdType();
		} catch (Exception e) {
			messageIdType = OptionalDataType.None;
		}

		return new DriverSettings(
			getHostname(),
			getPort(),
			getConnectOnStartup(),
			getTimeout(),
			getPacketTimeout(),
			getReverseByteOrder(),
			getTimestampFactor(),
			getMaxTimestamp(),
			messageIdType);
	}

	public String getHostname() {
		return getString(Hostname);
	}

	public int getPort() {
		return getInt(Port);
	}

	public boolean getConnectOnStartup() {
		return getBoolean(ConnectOnStartup);
	}

	public int getTimeout() {
		return getInt(Timeout);
	}

	public int getPacketTimeout() {
		return getInt(PacketTimeout);
	}
	
	public boolean getReverseByteOrder() {
		return getBoolean(ReverseByteOrder);
	}

	public int getTimestampFactor() {
		return getInt(TimestampFactor);
	}

	public long getMaxTimestamp() {
		return getLong(MaxTimestamp);
	}
	
	@Override
	public byte[] getMessageConfig() {
		return getBytes(MessageConfig);
	}

	@Override
	public DriverConfig getParsedMessageConfig() throws Exception {
		byte[] value = getBytes(MessageConfig);
		if (value != null) {
			byte[] decode = Base64.decode(new String(value));
			if (decode != null) {
				String configString = new String(Base64.decode(new String(value)));
				DriverConfig config;
				try {
					config = DriverConfig.fromXMLString(configString);
					return config;
				} catch (Exception e) {
					throw new Exception(BundleUtil.i18n("GenericTcpDriver.error.xmlParseError", e.getMessage()));
				}
			} else {
				throw new Exception(BundleUtil.i18n("GenericTcpDriver.error.notBase64"));
			}
		}

		return null;
	}

	@Override
	public byte[] getHeaderConfig() {
		return getBytes(HeaderConfig);
	}

	@Override
	public HeaderConfig getParsedHeaderConfig() throws Exception {
		byte[] value = getBytes(HeaderConfig);
		if (value != null) {
			byte[] decode = Base64.decode(new String(value));
			if (decode != null) {
				String configString = new String(Base64.decode(new String(value)));
				HeaderConfig config;
				try {
					config = com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig.fromXMLString(configString);
					return config;
				} catch (Exception e) {
					throw new Exception(BundleUtil.i18n("GenericTcpDriver.error.xmlParseError", e.getMessage()));
				}
			} else {
				throw new Exception(BundleUtil.i18n("GenericTcpDriver.error.notBase64"));
			}
		}

		return null;
	}

	@Override
	public byte[] getWritebackConfig() {
		return getBytes(WritebackConfig);
	}

	@Override
	public WritebackConfig getParsedWritebackConfig() throws Exception {
		byte[] value = getBytes(WritebackConfig);
		if (value != null) {
			byte[] decode = Base64.decode(new String(value));
			if (decode != null) {
				String configString = new String(Base64.decode(new String(value)));
				WritebackConfig config;
				try {
					config = com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig.fromXMLString(configString);
					return config;
				} catch (Exception e) {
					throw new Exception(BundleUtil.i18n("GenericTcpDriver.error.xmlParseError", e.getMessage()));
				}
			} else {
				throw new Exception(BundleUtil.i18n("GenericTcpDriver.error.notBase64"));
			}
		}

		return null;
	}

	public void setHostname(String hostname) {
		setString(Hostname, hostname);
	}

	public void setPort(int port) {
		setInt(Port, port);
	}

	public void setConnectOnStartup(boolean connectOnStartup) {
		setBoolean(ConnectOnStartup, connectOnStartup);
	}

	public void setTimeout(int timeout) {
		setInt(Timeout, timeout);
	}

	public void setPacketTimeout(int timeout) {
		setInt(PacketTimeout, timeout);
	}
	
	public void setReverseByteOrder(boolean reverseByteOrder) {
		setBoolean(ReverseByteOrder, reverseByteOrder);
	}

	public void setTimestampFactor(int timestampFactor) {
		setInt(TimestampFactor, timestampFactor);
	}
	
	public void setMaxTimestamp(long maxTimestamp) {
		setLong(MaxTimestamp, maxTimestamp);
	}

	@Override
	public void setMessageConfig(byte[] messageConfig) {
		setBytes(MessageConfig, messageConfig);
	}

	@Override
	public void setHeaderConfig(byte[] headerConfig) {
		setBytes(HeaderConfig, headerConfig);
	}

	@Override
	public void setWritebackConfig(byte[] writebackConfig) {
		setBytes(WritebackConfig, writebackConfig);
	}

	@Override
	public PersistentRecord getPersistentRecord() {
		return this;
	}

	@Override
	public boolean isWritebackEnabled() {
		return true;
	}
}
