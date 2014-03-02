/*******************************************************************************
 * Copyright 2012-2013 C. Hiesserich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 ******************************************************************************/
package com.chitek.ignition.drivers.generictcp.folder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.chitek.ignition.drivers.generictcp.meta.config.IDriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.redundancy.FolderStateProvider;
import com.chitek.ignition.drivers.generictcp.redundancy.StateUpdate;
import com.chitek.ignition.drivers.generictcp.tags.WritableTag;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.WritebackDataType;
import com.chitek.ignition.drivers.generictcp.util.Util;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.opcua.types.DataType;
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.opcua.types.UInt16;
import com.inductiveautomation.opcua.types.Variant;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

public class SimpleWriteFolder extends MessageFolder implements FolderStateProvider {

	public static final String FOLDER_NAME = "[Writeback]";

	private volatile boolean isActiveNode;

	private boolean isConnected = false;

	private volatile long numValue = 0; // The numeric value to write.
	private volatile byte[] byteValue; // The ByteString value
	private volatile int messageId = 0; // The message ID to write

	private long initialNumValue;
	private byte[] initialByteValue;
	private int initialMessageId;
	
	private final int deviceId;
	private final WritebackConfig config;
	private final ByteOrder byteOrder;
	private int fixedLength = 0; // The fixed message length
	private final int maxLength;

	private DataValue idValue;
	private DataValue valueValue;
	private DataValue writeValue;

	public SimpleWriteFolder(IGenericTcpDriverContext driverContext, IDriverSettings driverSettings, int deviceId, String deviceAlias, WritebackConfig config) {

		super(driverContext, FolderManager.getFolderId(deviceId, FolderManager.WRITEBACK_ID), deviceAlias != null ? (deviceAlias + "/" + FOLDER_NAME) : FOLDER_NAME);

		this.deviceId = deviceId;
		byteOrder = driverSettings.getByteOrder();
		this.config = config;

		// Initialize DataValues
		setInitialValues();

		addSpecialTags();
		addTagsFromConfig(config);

		// Parse the prefix if it is used
		int prefixLen = 0;
		if (config.isUsePrefix()) {
			try {
				byte[] prefix = parsePrefix(0, 0);
				prefixLen = prefix.length;
			} catch (Exception e) {
				log.error("Prefix can no be parsed. Prefix disabled. Error:" + e.getLocalizedMessage());
				config.setUsePrefix(false);
			}
		}

		// Calculate the fixed message length
		fixedLength = prefixLen;

		// HexString size is dynamic
		if (config.getDataType() != WritebackDataType.ByteString)
			fixedLength += config.getDataType().getUADataType().getNumBytes();

		// The maximum length depends on use of the message length field
		maxLength = config.getPrefix().contains("lenb") ? 255 : 65535;
	}


	public void connectionStateChanged(boolean isConnected) {
		if (isConnected && !this.isConnected) {
			writeValue = new DataValue(writeValue.getValue(), StatusCode.GOOD);
			idValue = new DataValue(idValue.getValue(), StatusCode.GOOD);
			valueValue = new DataValue(valueValue.getValue(), StatusCode.GOOD);
			// Send initial values
			if (config.getSendInitialValue() && isActiveNode) {
				getDriverContext().executeOnce(new Runnable() {
					@Override
					public void run() {
						writeMessage(true);
					}
				});
			}
		} else {
			writeValue = new DataValue(writeValue.getValue(), StatusCode.BAD_NOT_CONNECTED);
			idValue = new DataValue(idValue.getValue(), StatusCode.BAD_NOT_CONNECTED);
			valueValue = new DataValue(valueValue.getValue(), StatusCode.BAD_NOT_CONNECTED);
		}

		this.isConnected = isConnected;
	}

	@Override
	public void activityLevelChanged(boolean isActive) {
		
		// Send the initial values if the device is already connected
		if (config.getSendInitialValue() && isConnected && isActive &&!isActiveNode) {
			getDriverContext().executeOnce(new Runnable() {
				@Override
				public void run() {
					writeMessage(true);
				}
			});			
		}
			
		isActiveNode = isActive;
	}

	private void setInitialValues() {
		if (config.getSendInitialValue()) {
			switch (config.getDataType()) {
			case ByteString:
				try {
					byteValue = Util.hexString2ByteArray(config.getInitialValue());
				} catch (ParseException e) {
					log.error("Initial value can no be parsed. Error:" + e.getLocalizedMessage());
					byteValue = new byte[] { 0 };
				}
				numValue = 0;
				break;
			default:
				numValue = Long.parseLong(config.getInitialValue());
				byteValue = new byte[]{};
			}
			valueValue = new DataValue(Util.makeVariant(config.getInitialValue(), config.getDataType().getUADataType()));
		} else {
			byteValue = null;
			numValue = 0;
			switch (config.getDataType()) {
			case ByteString:
				valueValue = new DataValue(Util.makeVariant("", config.getDataType().getUADataType()));
				break;
			default:
				valueValue = new DataValue(Util.makeVariant(0, config.getDataType().getUADataType()));
			}
		}
		
		initialByteValue = byteValue;
		initialNumValue = numValue;
		messageId = initialMessageId = config.getInitialId();
		
		valueValue = new DataValue(valueValue.getValue(), StatusCode.BAD_NOT_CONNECTED);
		idValue = new DataValue(Util.makeVariant(messageId, config.getMessageIdType().getUADataType()), StatusCode.BAD_NOT_CONNECTED);
		writeValue = new DataValue(new Variant(false), StatusCode.BAD_NOT_CONNECTED);
	}

	private ByteBuffer buildMessage(boolean initialValues) {
		ByteBuffer b = null;

		tagLock.lock();
		try {
			int sendId;
			long sendNumValue;
			byte[] sendByteValue;
			if (initialValues) {
				sendId = initialMessageId;
				sendNumValue = initialNumValue;
				sendByteValue = initialByteValue;
			} else {
				sendId = messageId;
				sendNumValue = numValue;
				sendByteValue = byteValue;
			}
			
			// Calculate the message length
			int messageLength = fixedLength;
			if (config.getDataType() == WritebackDataType.ByteString)
				messageLength += sendByteValue.length;

			b = ByteBuffer.allocate(messageLength);
			b.order(byteOrder);

			byte[] prefix = parsePrefix(messageLength, sendId);
			b.put(prefix);

			switch (config.getDataType()) {
			case Int16:
				b.putShort((short) sendNumValue);
				break;
			case UInt16:
				b.putShort((short) (sendNumValue & 0xffffL));
				break;
			case Int32:
				b.putInt((int) sendNumValue);
				break;
			case UInt32:
				b.putInt((int) (sendNumValue & 0xffffffffL));
				break;
			case ByteString:
				b.put(sendByteValue);
			}
		} catch (ParseException e1) {
			log.error("Error parsing prefix: " + e1.getLocalizedMessage());
		} catch (Exception e) {
			log.error("Exception in writeMessage " + e.toString());
		} finally {
			tagLock.unlock();
		}

		b.flip();
		return b;
	}

	/**
	 * 
	 * @param initialValues
	 * 	<code>true</code> to send the initial values.
	 */
	private void writeMessage(boolean initialValues) {

		if (config.getDataType() == WritebackDataType.ByteString && byteValue == null) {
			// No valid value to send
			log.error("Tried to send but there is no valid value");
			return;
		}

		ByteBuffer b = buildMessage(initialValues);

		if (log.isTraceEnabled()) {
			log.trace("Sending writeback message to device " + ByteUtilities.toString(b));
		}
		getDriverContext().writeToRemoteDevice(b, deviceId);
	}

	private byte[] parsePrefix(int messageLength, int id) throws ParseException {
		Number idValue;
		if (config.getMessageIdType() == OptionalDataType.UByte)
			idValue = Byte.valueOf((byte) ((id > -1 ? id : this.messageId) & 0xff));
		else
			idValue = Short.valueOf((short) ((id > -1 ? id : this.messageId) & 0xffff));
		if (config.isUsePrefix()) {
			Map<String, Number> map = new HashMap<String, Number>();
			map.put("id", idValue);
			map.put("lenb", (Byte.valueOf((byte) (messageLength & 0xff))));
			map.put("lenw", (Short.valueOf((short) (messageLength & 0xffff))));
			return Util.hexString2ByteArray(config.getPrefix(), map, byteOrder);
		}

		return new byte[0];
	}

	private void addSpecialTags() {
		// Create the folder node
		buildAndAddFolderNode(getFolderAddress(), FOLDER_NAME);

		// Write - writing 'true' will send the message. The value to read will always be false.
		WritableTag writeTag = new WritableTag(getFolderAddress() + "/Write", DataType.Boolean) {
			@Override
			public StatusCode setValue(DataValue paramDataValue) {

				if (!isConnected || !isActiveNode)
					return StatusCode.BAD_NOT_CONNECTED;

				boolean newValue;
				try {
					newValue = TypeUtilities.toBool(paramDataValue.getValue().getValue());
				} catch (ClassCastException e) {
					return StatusCode.BAD_VALUE;
				}
				
				if (newValue) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Client set %s/Write to true. Sending message.", getFolderAddress()));
					}

					getDriverContext().executeOnce(new Runnable() {
						@Override
						public void run() {
							writeMessage(false);
						}
					});
				}

				return StatusCode.GOOD;
			}

			@Override
			public DataValue getValue() {
				return writeValue;
			}
		};
		buildAndAddNode(writeTag).setValue(writeTag.getValue());
	}

	private void addTagsFromConfig(final WritebackConfig config) {
		// ID
		if (config.getMessageIdType() != OptionalDataType.None) {
			WritableTag idTag = new WritableTag(getFolderAddress() + "/ID", config.getMessageIdType().getUADataType()) {

				@Override
				public StatusCode setValue(DataValue paramDataValue) {

					if (log.isTraceEnabled()) {
						log.trace(String.format("Client set %s/ID to %s.", getFolderAddress(), paramDataValue.getValue().getValue()));
					}

					int newValue;
					try {
						newValue = (Integer) TypeUtilities.coerce(paramDataValue.getValue().getValue(), Integer.class);
						// Make sure the id is in the correct range
						switch (config.getMessageIdType()) {
						case UByte:
							if (newValue < 0 || newValue > 255)
								return StatusCode.BAD_OUT_OF_RANGE;
							break;
						case UInt16:
							if (newValue < UInt16.MIN_VALUE || newValue > UInt16.MAX_VALUE)
								return StatusCode.BAD_OUT_OF_RANGE;
							break;
						case None:
							break;
						}
					} catch (ClassCastException e) {
						return StatusCode.BAD_OUT_OF_RANGE;
					}

					tagLock.lock();
					messageId = newValue;
					idValue = paramDataValue;
					tagLock.unlock();

					// Post the updated value to the redundant backup
					postRedundancyUpdate();

					return StatusCode.GOOD;
				}

				@Override
				public DataValue getValue() {
					return idValue;
				}
			};
			buildAndAddNode(idTag).setValue(idTag.getValue());
		}

		WritableTag valueTag = new WritableTag(getFolderAddress() + "/Value", config.getDataType().getUADataType()) {

			@Override
			public StatusCode setValue(DataValue paramDataValue) {

				if (log.isTraceEnabled()) {
					log.trace(String.format("Client set %s/Value to %s.", getFolderAddress(), paramDataValue.getValue().getValue()));
				}

				if (!isConnected && config.getSendOnValueChange())
					return StatusCode.BAD_NOT_CONNECTED;

				tagLock.lock();
				try {
					if (config.getDataType() == WritebackDataType.ByteString) {
						byte[] val;
						try {
							val = Util.hexString2ByteArray((String) paramDataValue.getValue().getValue());
						} catch (ParseException e) {
							log.error("Client input can not be parsed as HexString. " + e.getMessage());
							return StatusCode.BAD_OUT_OF_RANGE;
						}
						// Check the message length
						if (fixedLength + val.length > maxLength) {
							log.warn("HexString exceeds maximum message length.");
							return StatusCode.BAD_OUT_OF_RANGE;
						}
						byteValue = val;
					} else {
						numValue = TypeUtilities.toLong(paramDataValue.getValue().getValue());
					}

					valueValue = paramDataValue;
				} finally {
					tagLock.unlock();
				}

				// Post the updated value to the redundant backup
				postRedundancyUpdate();

				if (config.getSendOnValueChange()) {
					getDriverContext().executeOnce(new Runnable() {
						@Override
						public void run() {
							writeMessage(false);
						}
					});
				}

				return StatusCode.GOOD;
			}

			@Override
			public DataValue getValue() {
				return valueValue;
			}
		};
		buildAndAddNode(valueTag).setValue(valueTag.getValue());
	}

	/**
	 * Post updated MessageId and Value to the redundant backup, if this gateway is the master.
	 */
	private void postRedundancyUpdate() {
		if (isActiveNode) {
			log.debug("Posting redundancy status update.");
			getDriverContext().postRuntimeStateUpdate(new FolderStateUpdate(getFolderId(), messageId, valueValue.getValue().getValue().toString()));
		}
	}

	/**
	 * Receive status updates from the active gateway if redundancy is enabled. Only the backup driver in a redundant
	 * configuration will receive this message to update messageId and Value.
	 */
	@Override
	public void updateRuntimeState(StateUpdate stateUpdate) {
		if (stateUpdate instanceof FolderStateUpdate) {
			FolderStateUpdate folderStateUpdate = (FolderStateUpdate) stateUpdate;
			try {
				tagLock.lock();
				if (config.getDataType() == WritebackDataType.ByteString) {
					byte[] val;
					try {
						val = Util.hexString2ByteArray(folderStateUpdate.getValue());
					} catch (ParseException e) {
						log.error("Value in writeback redundancy update can not be parsed as ByteString.");
						return;
					}
					byteValue = val;
					valueValue = new DataValue(Util.makeVariant(folderStateUpdate.getValue(), config.getDataType().getUADataType()));
				} else {
					numValue = TypeUtilities.toLong(folderStateUpdate.getValue());
					valueValue = new DataValue(Util.makeVariant(numValue, config.getDataType().getUADataType()));
				}

				messageId = folderStateUpdate.getMessageId();
				idValue = new DataValue(Util.makeVariant(messageId, config.getMessageIdType().getUADataType()), idValue.getStatusCode());
			} finally {
				tagLock.unlock();
			}

			if (log.isDebugEnabled()) {
				log.debug(String.format("'%s' received writeback update from redundant Master. message id: %s, value: %s",
						FolderManager.folderIdAsString(getFolderId()), messageId, folderStateUpdate.getValue()));
			}
		}
	}
	
	@Override
	public StateUpdate getFullState() {
		return new FolderStateUpdate(getFolderId(), messageId, valueValue.getValue().getValue().toString());
	}

	@Override
	public void setFullState(StateUpdate stateUpdate) {
		if (stateUpdate instanceof FolderStateUpdate) {
			updateRuntimeState(stateUpdate);
		}
	}
	
	/**
	 * Message for the redundancy system.
	 * 
	 */
	private static class FolderStateUpdate extends StateUpdate {
		private static final long serialVersionUID = 1L;
		private final int messageId;
		private final String value;

		public FolderStateUpdate(int folderId, int messageId, String value) {
			super(folderId);
			this.messageId = messageId;
			this.value = value;
		}

		public int getMessageId() {
			return messageId;
		}

		public String getValue() {
			return value;
		}
	}

}
