/*******************************************************************************
 * Copyright 2012-2013 C. Hiesserich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.chitek.ignition.drivers.generictcp.folder;

import com.chitek.ignition.drivers.generictcp.GenericTcpClientDriver;
import com.chitek.ignition.drivers.generictcp.tags.WritableTag;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.opcua.types.DataType;
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.opcua.types.UInt16;
import com.inductiveautomation.opcua.types.Variant;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;

/**
 * Status folder for the Client mode (active) driver type.
 *
 */
public class StatusFolder extends MessageFolder {

	public static final String FOLDER_NAME = "[Status]";

	private final GenericTcpClientDriver driver;
	private final DataValue deviceNameValue;
	private DataValue connectedHostValue;
	private DataValue hostnameValue;
	private DataValue portValue;
	private DataValue connectValue;
	private DataValue isConnectedValue;

	private boolean isConnected = false;

	public StatusFolder(GenericTcpClientDriver driver) {
		super(driver, FolderManager.getFolderId(0, FolderManager.STATUS_ID), FOLDER_NAME);

		this.driver = driver;
		deviceNameValue = new DataValue(new Variant(getDeviceName()));
		connectedHostValue = new DataValue(new Variant(""));
		hostnameValue = new DataValue(new Variant(driver.getHostname()));
		portValue = new DataValue(new Variant(new UInt16(driver.getPort())));
		connectValue = new DataValue(new Variant(driver.isConnectionEnabled()));
		isConnectedValue = new DataValue(new Variant(false));

		addSpecialTags();
	}

	@Override
	public void connectionStateChanged(boolean isConnected) {
		if (this.isConnected != isConnected) {
			isConnectedValue = new DataValue(new Variant(isConnected));
			connectedHostValue = new DataValue(new Variant(driver.getConnectedHost()));
		}
		this.isConnected = isConnected;
	}

	@Override
	public void activityLevelChanged(boolean isActive) {
		// This folder ignores the activity level
	}
	
	/**
	 * Called in a redundant configuration if the active driver sends a status update.
	 * 
	 * @param connectionEnabled
	 * @param hostname
	 * @param port
	 */
	public void updateStatus(boolean connectionEnabled, String hostname, int port) {
		connectValue = new DataValue(new Variant(connectionEnabled));
		hostnameValue = new DataValue(new Variant(hostname));
		portValue = new DataValue(new Variant(new UInt16(port)));
	}

	/**
	 * Adds the special tags in this message to the NodeManager and the Drivers browseTree.
	 * 
	 */
	private void addSpecialTags() {

		// Create the folder node
		buildAndAddFolderNode( getFolderAddress(), FOLDER_NAME);

		// Special tags
		DynamicDriverTag driverTag = new DynamicDriverTag(getFolderAddress() + "/Is Connected", DataType.Boolean) {
			@Override
			public DataValue getValue() {
				return isConnectedValue;
			}
		};
		buildAndAddNode(driverTag).setValue(new DataValue(new Variant(false)));

		driverTag = new DynamicDriverTag(getFolderAddress() + "/Connected Host", DataType.String) {
			@Override
			public DataValue getValue() {
				return connectedHostValue;
			}
		};
		buildAndAddNode(driverTag).setValue(driverTag.getValue());

		driverTag = new DynamicDriverTag(getFolderAddress() + "/Device Name", DataType.String) {
			@Override
			public DataValue getValue() {
				return deviceNameValue;
			}
		};
		buildAndAddNode(driverTag).setValue(driverTag.getValue());

		WritableTag writableTag = new WritableTag(getFolderAddress() + "/Hostname", DataType.String) {
			@Override
			public StatusCode setValue(DataValue paramDataValue) {
				String newHostname = TypeUtilities.toString(paramDataValue.getValue().getValue());

				log.info(String.format("Client set Hostname to %s", newHostname));
				driver.updateStatus(null, newHostname, null);
				hostnameValue = new DataValue(new Variant(newHostname));

				return StatusCode.GOOD;
			}

			@Override
			public DataValue getValue() {
				return hostnameValue;
			}
		};
		buildAndAddNode(writableTag).setValue(writableTag.getValue());

		writableTag = new WritableTag(getFolderAddress() + "/Port", DataType.UInt16) {
			@Override
			public StatusCode setValue(DataValue paramDataValue) {
				int newPort;
				try {
					newPort = TypeUtilities.toInteger(paramDataValue.getValue().getValue());
				} catch (ClassCastException e) {
					return StatusCode.BAD_VALUE;
				}

				log.info(String.format("Client set Port to %d", newPort));
				driver.updateStatus(null, null, newPort);
				portValue = new DataValue(new Variant(new UInt16(newPort)));

				return StatusCode.GOOD;
			}

			@Override
			public DataValue getValue() {
				return portValue;
			}
		};
		buildAndAddNode(writableTag).setValue(writableTag.getValue());

		WritableTag connectTag = new WritableTag(getFolderAddress() + "/Connect", DataType.Boolean) {
			@Override
			public StatusCode setValue(DataValue paramDataValue) {
				boolean newValue;
				try {
					newValue = TypeUtilities.toBool(paramDataValue.getValue().getValue());
				} catch (ClassCastException e) {
					return StatusCode.BAD_VALUE;
				}

				connectValue = new DataValue(new Variant(newValue));
				log.info(String.format("Client set Connect to %s", newValue));
				driver.updateStatus(newValue, null, null);

				return StatusCode.GOOD;
			}

			@Override
			public DataValue getValue() {
				return connectValue;
			}

		};
		buildAndAddNode(connectTag).setValue(connectTag.getValue());
	}

}
