package com.chitek.ignition.drivers.generictcp.configuration.settings;

import java.io.Serializable;

import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;

public interface IGenericTcpDriverSettings extends Serializable {

	/**
	 * @return
	 * 	This class, cast to a PersistentRecord.
	 */
	public PersistentRecord getPersistentRecord();

	public byte[] getMessageConfig();

	public DriverConfig getParsedMessageConfig() throws Exception;

	public byte[] getHeaderConfig();

	public HeaderConfig getParsedHeaderConfig() throws Exception;

	public byte[] getWritebackConfig();

	public WritebackConfig getParsedWritebackConfig() throws Exception;

	public void setMessageConfig(byte[] messageConfig);

	public void setHeaderConfig(byte[] headerConfig);

	public void setWritebackConfig(byte[] writebackConfig);
	
	/**
	 * @return
	 * 	true if writeback config page should be shown
	 */
	public boolean isWritebackEnabled();

}
