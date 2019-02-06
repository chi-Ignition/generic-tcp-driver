/*******************************************************************************
 * Copyright 2012-2019 C. Hiesserich
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

import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;

/**
 * This tag folder contains the status tags for a passive device
 *
 */
public class DeviceStatusFolder extends MessageFolder {

	public static final String FOLDER_NAME = "[Status]";
	
	private DataValue isConnectedValue;

	private boolean isConnected = false;

	public DeviceStatusFolder(IGenericTcpDriverContext driverContext, int deviceId, String deviceAlias) {
		super(driverContext, FolderManager.getFolderId(deviceId, FolderManager.DEVICE_STATUS_ID), deviceAlias);

		isConnectedValue = new DataValue(new Variant(false));

		addSpecialTags(deviceAlias);
	}

	@Override
	public void connectionStateChanged(boolean isConnected) {
		if (this.isConnected != isConnected) {
			isConnectedValue = new DataValue(new Variant(isConnected));
			this.isConnected = isConnected;
		}
	}
	
	@Override
	public void activityLevelChanged(boolean isActive) {
		// This folder ignores the activity level	
	}

	/**
	 * Adds the special tags in this message to the NodeManager and the Drivers browseTree.
	 * 
	 * @param folderName
	 * 	The name of the tag folder
	 */
	private void addSpecialTags(String deviceAlias) {

		// Create the device root folder
		buildAndAddFolderNode(deviceAlias, deviceAlias);
		
		// Create the folder node
		String folderName = String.format("%s/%s", deviceAlias, FOLDER_NAME);
		buildAndAddFolderNode(folderName, FOLDER_NAME);

		// Special tags
		DynamicDriverTag driverTag = new DynamicDriverTag(folderName + "/Is Connected", BuiltinDataType.Boolean) {
			@Override
			public DataValue getValue() {
				return isConnectedValue;
			}
		};
		buildAndAddNode(driverTag).setValue(new DataValue(new Variant(false)));
	}

}
