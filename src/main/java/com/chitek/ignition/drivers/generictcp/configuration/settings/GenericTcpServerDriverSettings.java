package com.chitek.ignition.drivers.generictcp.configuration.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.wicket.validation.validator.RangeValidator;

import simpleorm.dataset.SFieldFlags;

import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettingsPassive;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.RemoteDevice;
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
import com.inductiveautomation.opcua.types.UInt32;
import com.inductiveautomation.opcua.util.Base64;
import com.inductiveautomation.xopc.driver.api.configuration.DeviceSettingsRecord;

@SuppressWarnings("serial")
public class GenericTcpServerDriverSettings extends PersistentRecord implements IGenericTcpDriverSettings{

	public static final RecordMeta<GenericTcpServerDriverSettings> META = new RecordMeta<GenericTcpServerDriverSettings>(
		GenericTcpServerDriverSettings.class, "GenTcpServerDriverSettings");

	public static final LongField DeviceSettingsId = new LongField(META, "DeviceSettingsId", SFieldFlags.SPRIMARY_KEY);
	public static final ReferenceField<DeviceSettingsRecord> DeviceSettings = new ReferenceField<DeviceSettingsRecord>(
		META, DeviceSettingsRecord.META, "DeviceSettings", DeviceSettingsId);

	/* Connectivity */
	public static StringField ServerHostname = new StringField(META, "ServerHostname");
	public static IntField ServerPort = new IntField(META, "ServerPort", SFieldFlags.SMANDATORY);
	public static StringField Devices = new StringField(META, "Devices");

	/* Message Handling */
	public static BooleanField ReverseByteOrder = new BooleanField(META, "ReverseByteOrder");
	public static IntField TimestampFactor = new IntField(META, "TimestampFactor");
	public static LongField MaxTimestamp = new LongField(META, "MaxTimestamp");

	/* Config */
	public static BlobField MessageConfig = new BlobField(META, "MessageConfig");
	public static BlobField HeaderConfig = new BlobField(META, "HeaderConfig");
	public static BlobField WritebackConfig = new BlobField(META, "WritebackConfig");

	/* Categories */
	public static final Category Connectivity = new Category("GenericTcpServerDriverSettings.Category.Connectivity", 1001)
	.include(ServerHostname, ServerPort, Devices);
	public static Category MessageHandling = new Category("GenericTcpServerDriverSettings.Category.MessageHandling", 1002)
	.include(ReverseByteOrder, TimestampFactor);

	static {
		DeviceSettings.getFormMeta().setVisible(false);
		MessageConfig.getFormMeta().setVisible(false);
		HeaderConfig.getFormMeta().setVisible(false);
		WritebackConfig.getFormMeta().setVisible(false);

		ServerPort.addValidator(new RangeValidator<Integer>(1, 65535));
		ReverseByteOrder.setDefault(false);
		TimestampFactor.setDefault(1);
		TimestampFactor.addValidator(new RangeValidator<Integer>(1, 1000));
		MaxTimestamp.setDefault(UInt32.MAX_VALUE);
		MaxTimestamp.getFormMeta().addValidator(new RangeValidator<Long>((long)128, UInt32.MAX_VALUE));

		Devices.setMultiLine();

		MessageConfig.setDefault(new byte[0]);
		HeaderConfig.setDefault(new byte[0]);
		WritebackConfig.setDefault(new byte[0]);
	}

	@Override
	public RecordMeta<?> getMeta() {
		return META;
	}

	public DriverSettingsPassive getDriverSettings() {

		OptionalDataType messageIdType;
		try {
			messageIdType = getParsedMessageConfig().getMessageIdType();
		} catch (Exception e) {
			messageIdType = OptionalDataType.None;
		}

		return new DriverSettingsPassive(
			getServerHostname(),
			getServerPort(),
			getDevices(),
			getReverseByteOrder(),
			getTimestampFactor(),
			getMaxTimestamp(),
			messageIdType);
	}

	public String getServerHostname() {
		return getString(ServerHostname);
	}

	public int getServerPort() {
		return getInt(ServerPort);
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
	
	public List<RemoteDevice> getDevices() {
		String rawValue = getString(Devices);
		if (rawValue == null) {
			return new ArrayList<RemoteDevice>(0);
		}
		StringTokenizer st = new StringTokenizer(rawValue,"\n\r");
		List<RemoteDevice>deviceList = new ArrayList<RemoteDevice>(st.countTokens());
		while (st.hasMoreTokens()) {
			String entry = st.nextToken();
			int comma = entry.indexOf(",");
			String hostname = entry.substring(0, comma).toLowerCase().trim();
			String alias = entry.substring(comma+1, entry.length()).trim();
			alias.replaceAll("\\s+",""); // Remove whitespace from alias
			deviceList.add(new RemoteDevice(hostname, alias));
		}
		return deviceList;
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
					throw new Exception(BundleUtil._("GenericTcpDriver.error.xmlParseError", e.getMessage()));
				}
			} else {
				throw new Exception(BundleUtil._("GenericTcpDriver.error.notBase64"));
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
					throw new Exception(BundleUtil._("GenericTcpDriver.error.xmlParseError", e.getMessage()));
				}
			} else {
				throw new Exception(BundleUtil._("GenericTcpDriver.error.notBase64"));
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
				String configString = new String(decode);
				WritebackConfig config;
				try {
					config = com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig.fromXMLString(configString);
					return config;
				} catch (Exception e) {
					throw new Exception(BundleUtil._("GenericTcpDriver.error.xmlParseError", e.getMessage()));
				}
			} else {
				throw new Exception(BundleUtil._("GenericTcpDriver.error.notBase64"));
			}
		}

		return null;
	}

	public void setServerPort(int port) {
		setInt(ServerPort, port);
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

}
