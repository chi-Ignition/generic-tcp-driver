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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.chitek.ignition.drivers.generictcp.meta.config.IDriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.TagConfig;
import com.chitek.ignition.drivers.generictcp.redundancy.FolderStateProvider;
import com.chitek.ignition.drivers.generictcp.redundancy.StateUpdate;
import com.chitek.ignition.drivers.generictcp.tags.ReadableArrayTag;
import com.chitek.ignition.drivers.generictcp.tags.ReadableBoolArrayTag;
import com.chitek.ignition.drivers.generictcp.tags.ReadableStringTag;
import com.chitek.ignition.drivers.generictcp.tags.ReadableTcpDriverTag;
import com.chitek.ignition.drivers.generictcp.tags.WritableTag;
import com.chitek.ignition.drivers.generictcp.types.BinaryDataType;
import com.chitek.ignition.drivers.generictcp.types.QueueMode;
import com.chitek.ignition.drivers.generictcp.util.VariantByteBuffer;
import com.chitek.util.PersistentQueue;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.opcua.nodes.VariableNode;
import com.inductiveautomation.opcua.types.DataType;
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.opcua.types.UInt16;
import com.inductiveautomation.opcua.types.UInt32;
import com.inductiveautomation.opcua.types.UtcTime;
import com.inductiveautomation.opcua.types.Variant;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

/**
 * The message folder is the implementation of a configured message. It handles incoming messages and subscriptions.
 * 
 * @author chi
 * 
 */
public class IndexMessageFolder extends MessageFolder implements FolderStateProvider {
	public final static String QUEUE_FILE_PREFIX="tcpBinMsgQueue";
	public final static String QUEUE_FILE_EXTENSION=".que";
	private final static int MAX_PENDING_MESSAGES = 15;
	private static final int MAX_QUEUE_SIZE = 500;

	protected final List<ReadableTcpDriverTag> varTags; // List of all configured tags
	protected ReadableTcpDriverTag messageAgeTag = null;

	private final int deviceId;	// The device id is used for passive mode
	protected final IDriverSettings driverSettings;
	private final int configHash;	// HashCode of the message configuration

	private int messageLength;			// Length of this message
	private int messageAgeOffset=-1;	// Byte offset of the messageAge (if configured)
	private final MessageDataWrapper dataWrapper = new MessageDataWrapper();

	protected volatile long messageCount;
	private final AtomicInteger pendingEvaluations;

	private final QueueMode queueMode;
	private boolean queueActive;
	private volatile boolean handshakeValue;
	private volatile PersistentQueue<byte[]> queue;
	private final Object queueLock = new Object();

	/** First timestamp published to client after this node became active */
	private long firstPublishedTimestamp;

	/** timestamp at queue tail when last full update was posted to redundant master */
	private long lastPostedTimestamp;

	private volatile boolean delayActive;
	private volatile ScheduledFuture<?> delaySchedule;

	/** true, if there are items subscribed **/
	private volatile boolean subscriptionPresent;
	private volatile long subscriptionRate;

	protected DataValue messageCountValue;
	protected DataValue timestampValue;
	private DataValue queueSizeValue;

	/**
	 * Create a new folder with all tags defined in the given message config.
	 * 
	 */
	public IndexMessageFolder(MessageConfig messageConfig, IDriverSettings driverSettings, int deviceId, String folderAddress, IGenericTcpDriverContext driverContext) {
		super(driverContext, FolderManager.getFolderId(deviceId, messageConfig.getMessageId()), folderAddress);

		varTags = new ArrayList<ReadableTcpDriverTag>();

		this.driverSettings = driverSettings;
		this.deviceId = deviceId;
		this.messageLength = driverSettings.getMessageIdType().getByteSize();
		this.messageCount = 0;

		this.pendingEvaluations = new AtomicInteger();

		this.queueMode = messageConfig.getQueueMode();
		configHash = messageConfig.getConfigHash();

		init(messageConfig, folderAddress);
	}

	/**
	 * Initialize the configured tags
	 *
	 * @param messageConfig
	 * @param folderAddress
	 * 	The name of the folder in the browse tree that contains this message
	 */
	private void init(MessageConfig messageConfig, String folderAddress) {
		createFolder(messageConfig);

		timestampValue = new DataValue(StatusCode.BAD_WAITING_FOR_INITIAL_DATA);
		messageCountValue = new DataValue(new Variant(new UInt32(0)));

		this.handshakeValue = true;
		this.subscriptionPresent = false;
		this.delayActive = false;

		addTagsFromConfig(messageConfig, folderAddress);

		// In Handshake or Delayed mode, load queued message from disk
		this.firstPublishedTimestamp = 0;
		if (queueMode != QueueMode.NONE) {
			String path = getDriverContext().getDiskPath() + String.format("%s%d%s", QUEUE_FILE_PREFIX, getFolderId(), QUEUE_FILE_EXTENSION);
			try {
				// The persistent queue loads content from disk on initialization
				queue = new PersistentQueue<byte[]>(path, configHash, messageConfig.isUsePersistance(), log);
				queueSizeValue = new DataValue(new Variant(new UInt16(queue.size())));
				queueActive = false;
			} catch (IOException e) {
				log.error(String.format("Error enabling handshake mode for Message ID%d. Can not create persitent queue in path %s:%s", getFolderId(), path, e.toString()));
				queue = null;
			}
		}

		addDefaultTags(folderAddress);

		log.info(String.format("Message initialized with %d tags. Queue size: %s", addressTagMap.size(), queue!=null?queue.size():"Not used"));
	}

	@Override
	public void shutdown() {
		if (queue != null)
			queue.close();
		super.shutdown();
	}

	@Override
	public void connectionStateChanged(boolean isConnected) {
		// Connection state is ignored if queue is used
		if (queueMode!=QueueMode.NONE) {
			return;
		}
		
		// Update tag qualities
		StatusCode statusCode = isConnected	? StatusCode.BAD_WAITING_FOR_INITIAL_DATA : StatusCode.BAD_NOT_CONNECTED;
		setTagQuality(statusCode);
	}
	
	@Override
	public void activityLevelChanged(boolean isActive) {
		// Activity Level is ignored if queue mode is not used
		// In this case, we only react on connection state changes
		if (queueMode==QueueMode.NONE) {
			return;
		}
		
		StatusCode statusCode = (isActive) ? StatusCode.BAD_WAITING_FOR_INITIAL_DATA : StatusCode.BAD_NOT_CONNECTED;
		setTagQuality(statusCode);
		
		// Active -> Not active
		if (queueMode!=QueueMode.NONE && !isActive && queueActive) {
			queueActive = false;
			firstPublishedTimestamp = 0;

			// Post remaining queue entry to new master
			// There is a small time span after the full update message and before the driver becomes inactive.
			// There may be messages added to the queue that have not been posted in the full update.
			if (lastPostedTimestamp != 0) {
				synchronized (queueLock) {
					int index = queue.size();
					while (index > 0) {
						byte[] message = queue.get(index-1);
						long tailTimestamp = ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0);
						if (tailTimestamp > lastPostedTimestamp) {
							FolderStateUpdate stateUpdate = new FolderStateUpdate(getFolderId(), message, false);
							getDriverContext().postRuntimeStateUpdate(stateUpdate);
							if (log.isDebugEnabled()) {
								log.debug(String.format("'%s' posted message id %d to redundant master that arrived after full update."
										, FolderManager.folderIdAsString(getFolderId()), tailTimestamp));
							}
							index -= 1;
						} else
							break;
					}
				}
			}
		}
		
		// Not active -> Active
		if (queueMode!=QueueMode.NONE && isActive && !queueActive) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("'%s' update driver state -> Active with Handshake.", FolderManager.folderIdAsString(getFolderId())));
			}
			
			queueSizeValue = new DataValue(new Variant(new UInt16(queue.size())));
			queueActive = true;
			// Start asynchronous evaluation of queued message
			getDriverContext().executeOnce(new Runnable() {
				@Override
				public void run() {
					evaluateQueuedMessage();
				}
			});
		}
	}
	
	/**
	 * Update the status code for all tags (except %Handshake and %MessageCount which are always GOOD)  
	 * 
	 * @param statusCode
	 */
	private void setTagQuality(StatusCode statusCode) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("'%s' update driver state. Setting quality to %s", FolderManager.folderIdAsString(getFolderId()), statusCode.toString()));
		}
		
		tagLock.lock();
		try {
			for (ReadableTcpDriverTag tag : varTags) {
				tag.setValue(statusCode);
				tag.setUaNodeValue();
			}

			timestampValue=new DataValue(statusCode);
		} finally {
			tagLock.unlock();
		}	
	}

	/**
	 * In Handshake Mode, the incoming message is added to the handshake queue, without handshake
	 * it is immediately published to clients.<br />
	 * The first 8 bytes of the buffer have to contain the absolute time when the message was received by the driver
	 * and the sequence number if multiple messages have been received with the same timestamp.<br />
	 * The second 8 byte may contain a relative time offset received with a packet header.
	 * 
	 * @param message
	 *            The incoming message.<br/>
	 *            Bytes 0-7 in the array are combination of receive timestamp and the index of messages received with the same timestamp.<br />
	 *             timestamp << 16 + number<br />
	 *            Bytes 8-15 contain the header timestamp or 0 if no header is used
	 * @param handshakeMsg
	 * 			if this param is not null, the value is sent back to the device after the message has been added to the queue
	 */
	public void messageArrived(final byte[] message, final byte[] handshakeMsg)  {

		if (log.isTraceEnabled()) {
			log.trace(String.format(
				"Message with id %d received: %s",
				ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0), ByteUtilities.toString(Arrays.copyOfRange(message, 16, message.length))));
		}

		// Make sure that messages don't arrive to fast
		int pending = pendingEvaluations.incrementAndGet();
		if (pending > MAX_PENDING_MESSAGES) {
			// Messages arriving to fast - discard message
			pendingEvaluations.decrementAndGet();
			log.error("Messages arriving to fast. Discarded latest message.");
			return;
		}

		if (queueMode==QueueMode.NONE)
			// No Handshake - Evaluate message
			getDriverContext().executeOnce(new Runnable() {
				@Override
				public void run() {
					evaluateMessage(message);
					pendingEvaluations.decrementAndGet();
				}
			});
		else {
			// Add message to queue
			synchronized (queueLock) {
				if (queue.size()>MAX_QUEUE_SIZE) {
					pollMessageFromQueue();
					log.error("Maximum queue size exceeded, discarding oldest message.");
				}
				addMessageToQueue(message);

				// Post message to backup gateway
				FolderStateUpdate stateUpdate = new FolderStateUpdate(getFolderId(), message, false);
				getDriverContext().postRuntimeStateUpdate(stateUpdate);
				lastPostedTimestamp = ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0);

				// If this message is the last one in a package with header, send the confirmation to the device
				if (handshakeMsg != null) {
					if (log.isDebugEnabled()) {
						log.debug(String.format("Sending handshake message to device:%s", ByteUtilities.toString(handshakeMsg)));
					}
					writeHandshake(handshakeMsg);
				}
				pendingEvaluations.decrementAndGet();
			}
		}
	}

	private void writeHandshake(byte[] message) {
		getDriverContext().writeToRemoteDevice(ByteBuffer.wrap(message), deviceId);
	}

	/**
	 * Start evaluation of the message queue. The oldest message is passed to the client, but not removed
	 * from the queue until a client sets the handshake. This way, the current message will be saved on
	 * shutdown. If the handshake is set at the moment a shutdown happens, the message may be evaluated twice
	 * after the restart.
	 */
	private void evaluateQueuedMessage() {
		byte[] message;
		synchronized (queueLock) {

			// Peek oldest message for evaluation
			message = (queue.peek());

			if (message != null) {
				if (log.isDebugEnabled())
					log.debug(String.format("Evaluating queued message with id %d.", ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0)));
				handshakeValue = false;

				// In delayed mode, evaluate next message after the given time
				if (queueMode == QueueMode.DELAYED && delayActive) {
					delaySchedule=getDriverContext().executeOnce(new queueDelay(), subscriptionRate*3, TimeUnit.MILLISECONDS);
				}
			} else {
				// No message queued
				handshakeValue = true;
			}
		}

		if (message != null)
			evaluateMessage(message);
	}

	@Override
	public void subscriptionChanged(final long rate, final Set<String> itemAddresses) {

		// This method executes in the run() method of our SubscriptionUpdater
		
		subscriptionPresent = false;
		if (itemAddresses.size() > 0) {
			for (String itemAddress : itemAddresses) {
				if (itemAddress.endsWith("_Timestamp") || itemAddress.endsWith("_MessageCount")) {
					subscriptionPresent = true;
					break;
				}
			}
		}

		subscriptionRate = rate;
		if (queueMode == QueueMode.DELAYED) {
			// Cancel the delayed task if there is no subscription any more
			if (!subscriptionPresent && delayActive) {
				delayActive = false;
				if (delaySchedule != null && !delaySchedule.isDone()) {
					delaySchedule.cancel(false);
					delaySchedule = null;
				}
				if (log.isDebugEnabled())
					log.debug("Message has no subscriptions to _Timestamp or _MessageCount. Delayed queue mode cancelled.");
			}
			if (subscriptionPresent && !delayActive) {
				if (log.isDebugEnabled())
					log.debug("Message _Timestamp or _MessageCount subscribed. Delayed queue mode activated.");
				delayActive = true;
				evaluateQueuedMessage();
			}
		}
	}

	private class queueDelay implements Runnable {
		@Override
		public void run() {
			// Remove the acknowledged message from queue and evaluate the next one
			if (delayActive)
				pollMessageFromQueue();
		}
	}

	/**
	 * Evaluate the incoming message and update the tag values. Access to tag values is locked during the update. This
	 * makes sure that subscription updates will not mix data from two messages.
	 * 
	 * @param message
	 *            The incoming message.<br/>
	 *            Bytes 0-7 in the array are combination of receive timestamp and the index of messages received with the same timestamp.<br />
	 *             timestamp << 16 + number<br />
	 *            Bytes 8-15 contain the header timestamp or 0 if no header is used
	 */
	protected void evaluateMessage(byte[] message) {

		VariantByteBuffer buffer = new VariantByteBuffer(message);

		// Set byte order. If reverseByteOrder is configured, we use LITTLE_ENDIAN
		buffer.order(driverSettings.getByteOrder());

		// Read the timestamps from the message
		try {
			// Evaluate the message info data (timestamps...)
			dataWrapper.evaluateData(buffer);

			long timestamp = dataWrapper.getTimeReceived();
			UtcTime timestampUtc = UtcTime.fromJavaTime(timestamp);

			long headerTimestamp = dataWrapper.getHeaderTimestamp();

			tagLock.lock();
			try {
				if (messageAgeOffset > -1) {
					// MessageAge is configured - read it first
					int pos = buffer.position();
					buffer.position(pos + messageAgeOffset);
					long messageAge = (buffer.getInt() & 0xffffffff) * (long)driverSettings.getTimestampFactor();
					long calculatedAge = headerTimestamp - messageAge;
					if (calculatedAge >= 0) {
						timestamp -= calculatedAge;
						timestampUtc = UtcTime.fromJavaTime(timestamp);
						messageAgeTag.setValue(new Variant(new UInt32(calculatedAge)), timestampUtc);
						if (log.isTraceEnabled()) {
							log.trace(String.format(
								"Evaluate message. Received: %s (%d) - Header timestamp: %d - Message Age: %d - Calculated: Message age: %dms - Timestamp: %s -  Timestamp factor: %d",
								DateFormat.getDateTimeInstance().format(new Date(dataWrapper.getTimeReceived())), dataWrapper.getSequenceId(),
								headerTimestamp, messageAge, calculatedAge, timestamp, driverSettings.getTimestampFactor()));
						}
					} else {
						calculatedAge = 0;
						timestampUtc = UtcTime.fromJavaTime(timestamp);
						messageAgeTag.setValue(new Variant(new UInt32(calculatedAge)), StatusCode.BAD_OUT_OF_RANGE,
							timestampUtc);
						log.error(String.format(
							"Evaluated Message has an invalid age. Header timestamp: %d - messageAge: %d",
							headerTimestamp, messageAge));
					}

					// Restore buffer start position
					buffer.position(pos);
				}

				// Buffer contains the message id - just skip
				buffer.position(buffer.position() + driverSettings.getMessageIdType().getByteSize());

				for (ReadableTcpDriverTag driverTag : varTags) {
					switch (driverTag.getDriverDataType()) {
					case Dummy: // Dummy: Ignore value
						buffer.position(buffer.position() + driverTag.getReadSize());
						break;
					case Bool8:
						driverTag.setValue(buffer.readBool8(driverTag.getReadSize()), timestampUtc);
						break;
					case Bool16:
						driverTag.setValue(buffer.readBool16(driverTag.getReadSize()), timestampUtc);
						break;
					case Byte:
						driverTag.setValue(buffer.readByte(driverTag.getReadSize()), timestampUtc);
						break;
					case UByte:
						driverTag.setValue(buffer.readUByte(driverTag.getReadSize()), timestampUtc);
						break;
					case UInt16:
						driverTag.setValue(buffer.readUInt16(driverTag.getReadSize()), timestampUtc);
						break;
					case Int16:
						driverTag.setValue(buffer.readInt16(driverTag.getReadSize()), timestampUtc);
						break;
					case UInt32:
						driverTag.setValue(buffer.readUInt32(driverTag.getReadSize()), timestampUtc);
						break;
					case Int32:
						driverTag.setValue(buffer.readInt32(driverTag.getReadSize()), timestampUtc);
						break;
					case Float:
						driverTag.setValue(buffer.readFloat(driverTag.getReadSize()), timestampUtc);
						break;
					case String:
						driverTag.setValue(buffer.readString(driverTag.getReadSize()), timestampUtc);
						break;
					case MessageAge:
						buffer.getInt(); // Has already been read - just skip
						break;
					}
				}

				timestampValue = new DataValue(new Variant(timestamp));

				if (messageCount < UInt32.MAX_VALUE)
					messageCount++;
				else
					messageCount = 0;
				messageCountValue = new DataValue(new Variant(new UInt32(messageCount)));

			} catch (BufferUnderflowException ex) {
				log.error(String.format("BufferUnderflowException while evaluating message with %d bytes of payload data.", message.length - 16));
			} catch (Exception ex) {
				log.error("Exception while evaluating message", ex);
			} finally {
				tagLock.unlock();
			}
		} catch (Exception ex) {
			log.error(String.format("Exception while evaluating message timestamps. MessageBufferSize: %d", message.length));
			if (log.isDebugEnabled())
				log.debug("Stacktrace:", ex);

			// Increase the message count even if something went wrong
			tagLock.lock();
			try {
				if (messageCount < UInt32.MAX_VALUE)
					messageCount++;
				else
					messageCount = 0;
				messageCountValue = new DataValue(new Variant(new UInt32(messageCount)));
			} finally {
				tagLock.unlock();
			}
		}
	}

	/**
	 * Returns the message length in bytes. Valid only after all var nodes have been added.
	 * 
	 * @return
	 */
	public int getMessageLength() {
		return messageLength;
	}

	private ReadableTcpDriverTag createTag(String folderName, TagConfig config) {
		String address = folderName + "/" + config.getAlias();
		BinaryDataType dataType = config.getDataType();
		int arrayLength = config.getSize();

		ReadableTcpDriverTag newTag = createTag(address, config.getId(), config.getAlias(), -1, dataType, arrayLength);
		return newTag;
	}

	/**
	 * Creates the DriverTag and the UANode for the given configuration. The UANode is added to the
	 * NodeManager and to the drivers browseTree.
	 * For tags within an array, this method is called recursively with arrayLength = -1, to
	 * create the child tags.
	 * 
	 * @param address
	 * @param id
	 * @param alias
	 * @param index
	 * 	Tag index for arrays, used in browseName and displayName. -1 if tag has no index.
	 * @param dataType
	 * @param arraySize
	 * @return
	 */
	private ReadableTcpDriverTag createTag(String address, int id, String alias, int index, BinaryDataType dataType, int arrayLength) {

		ReadableTcpDriverTag tag;

		if (dataType.isString()) {
			// Strings can not be arrays, array length is used as string length here
			tag = new ReadableStringTag(address, id, alias, dataType, arrayLength);
			if (!tag.getDriverDataType().isHidden()) {
				VariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}
		} else if (arrayLength > 1) {
			// Size > 1 - create an array
			// This method is called recursively to create the tags in the array
			if (dataType.getUADataType() == DataType.Boolean)
				tag = new ReadableBoolArrayTag(address, id, alias, dataType, arrayLength);
			else
				tag = new ReadableArrayTag(address, id, alias, index, dataType, arrayLength);

			for (int i=0; i<arrayLength; i++) {
				String childAddress = String.format("%s[%d]", address, i);
				ReadableTcpDriverTag childTag = createTag(childAddress, id, alias, i, dataType, -1);
				((ReadableArrayTag)tag).addChild(childTag);
			}

			// Add the new tag as an UANode after all childs have been added
			if (!tag.getDriverDataType().isHidden()) {
				VariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}

		} else if (dataType.getArrayLength() > 1) {
			// No array, but the DataType needs an array
			tag = new ReadableArrayTag(address, id, alias, index, dataType, dataType.getArrayLength());

			for (int i=0; i<dataType.getArrayLength(); i++) {
				String childAddress = String.format("%s[%d]", address, i);
				ReadableTcpDriverTag childTag = new ReadableTcpDriverTag(childAddress, id, alias, i, dataType);
				VariableNode childNode = buildAndAddNode(childTag);
				childTag.setUaNode(childNode);
				((ReadableArrayTag)tag).addChild(childTag);
			}

			if (dataType.getUADataType() == DataType.Boolean) {
				String childAddress = String.format("%s[raw]", address);
				ReadableTcpDriverTag childTag = new ReadableTcpDriverTag(childAddress, id, alias+"_raw", -1, BinaryDataType.UInt16);
				VariableNode childNode = buildAndAddNode(childTag);
				childTag.setUaNode(childNode);
				((ReadableArrayTag)tag).addChildRaw(childTag);
			}

			// Add the new tag as an UANode after all childs have been added
			if (!tag.getDriverDataType().isHidden()) {
				VariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}

		} else {
			// No array - create a simple tag
			tag = new ReadableTcpDriverTag(address, id, alias, index, dataType);
			if (!tag.getDriverDataType().isHidden()) {
				VariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}
		}

		return tag;
	}

	/**
	 * Creates the configured tags for this message and adds them to the NodeManager and
	 * the drivers browseTree.<br />
	 * After calling this method, messageLength contains the correct length of this message in byte.
	 * 
	 * @param messageConfig
	 * @param folderName
	 */
	private void addTagsFromConfig(MessageConfig messageConfig, String folderName) {

		for (TagConfig config : messageConfig.tags) {
			ReadableTcpDriverTag tag = createTag(folderName, config);
			varTags.add(tag);
			if (config.getDataType()==BinaryDataType.MessageAge) {
				messageAgeOffset = messageLength;
				messageAgeTag = tag;
			}
			messageLength += config.getSize() * config.getDataType().getByteCount();

		}
	}

	/**
	 * Create the message folders.
	 * 
	 * @param messageConfig
	 */
	private void createFolder(final MessageConfig messageConfig) {
		String folderBrowseName = String.format("%d - %s", messageConfig.getMessageId(), messageConfig.getMessageAlias());

		// Add the message folder
		buildAndAddFolderNode(getFolderAddress(), folderBrowseName);
	}

	/**
	 * Adds the special tags in this message to the NodeManager and the Drivers browseTree.
	 * 
	 * @param folderName
	 */
	protected void addDefaultTags(String folderName) {

		// Special tags
		DynamicDriverTag driverTag = new DynamicDriverTag(folderName + "/_Timestamp", DataType.DateTime) {
			@Override
			public DataValue getValue() {
				return timestampValue;
			}
		};
		buildAndAddNode(driverTag).setValue(driverTag.getValue());

		// MessageCount
		driverTag = new DynamicDriverTag(folderName + "/_MessageCount", DataType.UInt32) {
			@Override
			public DataValue getValue() {
				return messageCountValue;
			}
		};
		buildAndAddNode(driverTag).setValue(driverTag.getValue());

		// Writable handshake tag
		if (queueMode==QueueMode.HANDSHAKE) {
			WritableTag handshakeTag = new WritableTag(folderName + "/_Handshake", DataType.Boolean) {
				@Override
				public StatusCode setValue(DataValue paramDataValue) {
					boolean newValue;
					try {
						newValue = TypeUtilities.toBool(paramDataValue.getValue().getValue());
					} catch (ClassCastException e) {
						return StatusCode.BAD_VALUE;
					}

					if (!newValue) {
						// Setting to false is not allowed - return an error
						return StatusCode.BAD_OUT_OF_RANGE;
					}

					if (handshakeValue) {
						// Handshake is already set - do nothing
						return StatusCode.GOOD;
					} 
					
					if(!getDriverContext().isActiveNode()) {
						// Handshake is not accepted when this is not the active node
						log.warn("Client tried to set Handshake on non active cluster node");
						return StatusCode.BAD_NOT_WRITABLE;
					}

					if (log.isDebugEnabled())
						log.debug(String.format("Handshake for message id %d set by client", getFolderId()));

					// Remove the acknowledged message from queue and evaluate the next one
					pollMessageFromQueue();

					return StatusCode.GOOD;
				}

				@Override
				public DataValue getValue() {
					return new DataValue(new Variant(handshakeValue));
				}

			};
			buildAndAddNode(handshakeTag).setValue(handshakeTag.getValue());
		}

		if (queueMode != QueueMode.NONE) {
			// QueueSize
			driverTag = new DynamicDriverTag(folderName + "/_QueueSize", DataType.UInt32) {
				@Override
				public DataValue getValue() {
					return queueSizeValue;
				}
			};
			buildAndAddNode(driverTag).setValue(driverTag.getValue());
		}
	}

	public int getConfigHash() {
		return configHash;
	}

	private void pollMessageFromQueue() {
		// Remove message from queue
		synchronized (queueLock) {
			byte[] removed =queue.peek();
			if (removed != null) {
				removeMessageFromQueue(removed);
				// Update queue on non-active node
				FolderStateUpdate stateUpdate = new FolderStateUpdate(getFolderId(), removed, true);
				getDriverContext().postRuntimeStateUpdate(stateUpdate);
				// Store the first polled timestamp
				if (firstPublishedTimestamp == 0)
					firstPublishedTimestamp=ByteUtilities.get(driverSettings.getByteOrder()).getLong(removed, 0);
			} else {
				log.error("Message queue inconsistent. Tried to remove message from empty queue");
			}
		}

		// Start asynchronous evaluation of new message
		getDriverContext().executeOnce(new Runnable() {
			@Override
			public void run() {
				evaluateQueuedMessage();
			}
		});
	}

	/**
	 * This method is called when this node receives a message from the device or when the redundant
	 * peer posts a queue update.
	 * 
	 * @param message
	 */
	public void addMessageToQueue(byte[] message) {
		synchronized (queueLock) {
			queue.add(message);

			queueSizeValue = new DataValue(new Variant(new UInt16(queue.size())));
			if (log.isDebugEnabled())
				log.debug(String.format("Message with id %d and %d bytes length added to queue. New queue size: %d",
					ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0), message.length, queue.size()));

			if (handshakeValue && queueActive) {
				// Evaluate message immediately if handshake is already set
				handshakeValue = false;
				// Start asynchronous evaluation of queued message
				getDriverContext().executeOnce(new Runnable() {
					@Override
					public void run() {
						evaluateQueuedMessage();
						pendingEvaluations.decrementAndGet();
					}
				});
			}
		}
	}

	public void removeMessageFromQueue(byte[] message) {

		synchronized (queueLock) {
			byte[] removed = queue.peek();

			if (removed != null) {

				long timestampQueue = ByteUtilities.get(driverSettings.getByteOrder()).getLong(removed, 0);
				long timestampToRemove = ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0);

				if (timestampQueue == timestampToRemove) {
					queue.poll();
					queueSizeValue = new DataValue(new Variant(new UInt16(queue.size())));

					if (log.isDebugEnabled())
						log.debug(String.format("Message with id %d polled from queue. New queue size: %d",
							timestampToRemove, queue.size()));
				} else {
					log.warn(String.format(
						"Message queue inconsistent. Id %d should be removed, but id of queue head was %d",
						timestampToRemove, timestampQueue));
					int discarded = 0;
					while (queue.size()>0 && timestampQueue<=timestampToRemove) {
						// Entry at queue head is older then entry that should be removed - try to catch up
						queue.poll();
						discarded ++;
						if (queue.size() > 0) timestampQueue = ByteUtilities.get(driverSettings.getByteOrder()).getLong(queue.peek(), 0);
					}
					if (discarded > 0)
						log.warn(String.format("Removed %d old entrys from message queue. New queue size is %d.", discarded, queue.size()));
				}
			} else {
				log.error("Message queue inconsistent. Tried to remove message from empty queue");
			}
		}
	}

	/**
	 * This method returns the current message queue by COPYING its content to a byte array.
	 * This is a deep copy, there is no reference to the original ArrayDeque.
	 * 
	 * @return
	 * 		A copy of the current message queue or an empty array if the queue is empty
	 */
	private byte[][] getQueueAsArray() {

		if (queue == null) {
			return new byte[][]{};
		}
		
		byte[][] queueArray;
		synchronized (queueLock) {
			queueArray = queue.toArray(new byte[0][0]);
			log.debug(String.format("Queue with size %d copied for redundant peer.", queue.size()));

			if (queue.size() > 0)
				lastPostedTimestamp = ByteUtilities.get(driverSettings.getByteOrder()).getLong(queue.peekLast(), 0);
			else
				lastPostedTimestamp = System.currentTimeMillis() * 65536;
		}
		return queueArray;
	}

	/**
	 * Returns the first timestamp that has been published to clients by this message folder.
	 * 
	 * @return
	 * 	The first published timestamp
	 */
	public long getFirstPublishedTimestamp() {
		return firstPublishedTimestamp;
	}

	/**
	 * Replace the message queue
	 * 
	 * @param messageConfigHash
	 * @param firstPublishedTimestamp
	 * @param queue
	 */
	private void setMessageQueue(int messageConfigHash, long firstPublishedTimestamp, byte[][] newQueue) {

		if (this.queue==null)
			return;

		// Check if the configuration has been changed
		if (messageConfigHash != configHash) {
			log.warn(String.format("Configuration has been changed. Dropping %d queued messages.", newQueue.length));
			synchronized (queueLock) {
				this.queue.clear();
			}
			return;
		}

		ByteUtilities bu = ByteUtilities.get(driverSettings.getByteOrder());
		long queueTailTimestamp = 0;
		if (this.queue.size()>0) {
			queueTailTimestamp = bu.getLong(this.queue.peekLast(), 0);
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format(
			"Received full queue update. First timestamp published by peer:%d, timestamp at current queue tail: %d. New queue size: %d.",
			firstPublishedTimestamp, queueTailTimestamp, newQueue.length));
		}

		synchronized (queueLock) {
			byte[][] oldQueue=queue.toArray(new byte[0][0]);
			this.queue.clear();
			int saved=0;
			for (byte[]msg : oldQueue) {
				// Restore all messages from the current queue thet has not been published by the peer
				long id = bu.getLong(msg, 0);
				if (id<firstPublishedTimestamp) {
					queue.add(msg);
					saved ++;
				}
			}
			if (log.isDebugEnabled()) {
				log.debug(String.format("Restored %d messages from old queue that have not been published by peer", saved));
			}
			this.queue.addAll(Arrays.asList(newQueue));
		}
	}

	@Override
	public StateUpdate getFullState() {
		FolderFullState fullState = new FolderFullState(getFolderId(), getConfigHash(), getFirstPublishedTimestamp(), getQueueAsArray());
		return fullState;
	}
	
	@Override
	public void setFullState(StateUpdate stateUpdate) {
		if (stateUpdate instanceof FolderFullState) {
			FolderFullState fullState = (FolderFullState) stateUpdate;
			setMessageQueue(fullState.getConfigHash(), fullState.getFirstPublishedTimestamp(), fullState.getQueue());
		}
	}
	
	@Override
	public void updateRuntimeState(StateUpdate stateUpdate) {
		if (stateUpdate instanceof FolderStateUpdate) {
			FolderStateUpdate folderStateUpdate = (FolderStateUpdate) stateUpdate;
			if (folderStateUpdate.isRemoved()) {
				// Remove from queue
				if (!queueActive) {
					// Only the inactive node accepts remove messages. This might result in messages published
					// twice, but this is better than loosing messages.
					removeMessageFromQueue(folderStateUpdate.getMessage());
				}
			} else {
				addMessageToQueue(folderStateUpdate.getMessage());
			}
			
			if (log.isTraceEnabled()) {
				log.trace(String.format("'%s' received runtime state update.", FolderManager.folderIdAsString(getFolderId())));
			}
		}
	}
	
	/**
	 * This method should only be used by unit tests. Do not call!
	 * @return
	 */
	public long getMessageCount() {
		return messageCount;
	}

	/**
	 * Message for the redundancy system.
	 * 
	 */
	private static class FolderStateUpdate extends StateUpdate {
		private static final long serialVersionUID = 1L;
		private final byte[] message;

		public FolderStateUpdate(int folderId, byte[] message, boolean remove) {
			super(folderId);
			// Create a deep copy to transfer the message
			if (remove) {
				// Massage removed from queue - send only the timestamp
				this.message = Arrays.copyOf(message, 8);
			} else {
				this.message = Arrays.copyOf(message, message.length);
			}
		}

		public byte[] getMessage() {
			return message;
		}
		
		/**
		 * @return
		 * 	<code>true</code> if this message should be removed from the queue.
		 */
		public boolean isRemoved() {
			return message.length == 8;
		}
	}
	
	private static class FolderFullState extends StateUpdate {
		private static final long serialVersionUID = 1L;
		private final int configHash;
		private final long firstPublishedTimestamp;
		private final byte[][] queue;
		
		public FolderFullState(int folderId, int configHash, long firstPublishedTimestamp, byte[][] queue) {
			super(folderId);
			this.configHash = configHash;
			this.firstPublishedTimestamp = firstPublishedTimestamp;
			this.queue = queue;
		}
		
		public byte[][] getQueue() {
			return queue;
		}
		
		/**
		 * @return
		 * 	Timestamp of the first message that has been published by this folder
		 */
		public long getFirstPublishedTimestamp() {
			return firstPublishedTimestamp;
		}
		
		/**
		 * @return
		 * 	The hash value of the message configuration. Used to detect configuration changes.
		 */
		public int getConfigHash() {
			return configHash;
		}
	}
	
}
