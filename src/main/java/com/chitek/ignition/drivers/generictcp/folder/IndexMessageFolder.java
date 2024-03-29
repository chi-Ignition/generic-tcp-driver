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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import com.chitek.ignition.drivers.generictcp.IGenericTcpDriverContext;
import com.chitek.ignition.drivers.generictcp.meta.config.IDriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.TagConfig;

import com.chitek.ignition.drivers.generictcp.tags.ReadableArrayTag;
import com.chitek.ignition.drivers.generictcp.tags.ReadableBoolArrayTag;
import com.chitek.ignition.drivers.generictcp.tags.ReadableStringTag;
import com.chitek.ignition.drivers.generictcp.tags.ReadableTcpDriverTag;
import com.chitek.ignition.drivers.generictcp.tags.WritableTag;
import com.chitek.ignition.drivers.generictcp.types.BinaryDataType;
import com.chitek.ignition.drivers.generictcp.types.QueueMode;
import com.chitek.ignition.drivers.generictcp.types.TagLengthType;
import com.chitek.ignition.drivers.generictcp.util.VariantByteBuffer;
import com.chitek.util.PersistentQueue;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

/**
 * The message folder is the implementation of a configured message. It handles incoming messages and subscriptions.
 * 
 * @author chi
 * 
 */
public class IndexMessageFolder extends MessageFolder {
	public final static String QUEUE_FILE_PREFIX = "tcpBinMsgQueue";
	public final static String QUEUE_FILE_EXTENSION = ".que";
	private final static int MAX_PENDING_MESSAGES = 15;
	private static final int MAX_QUEUE_SIZE = 500;

	protected final List<ReadableTcpDriverTag> varTags; // List of all configured tags
	protected ReadableTcpDriverTag messageAgeTag = null;
	protected ReadableTcpDriverTag varLengthTag = null;

	private final int deviceId; // The device id is used for passive mode
	protected final IDriverSettings driverSettings;
	private final int configHash; // HashCode of the message configuration

	private int messageLength; // Length of this message
	private int messageBytesAfterVarTag; // The message length after the variable length tag
	private int messageAgeOffset = -1; // Byte offset of the messageAge (if configured)
	private final MessageDataWrapper dataWrapper = new MessageDataWrapper();

	protected volatile long messageCount;
	private final AtomicInteger pendingEvaluations;

	private final QueueMode queueMode;
	private boolean queueActive;
	private volatile boolean handshakeBit;
	/** Wait for UPC-UA client to reset handshake in Handshake Mode */
	private volatile boolean waitHandshake;
	private volatile PersistentQueue<byte[]> queue;
	private final Object queueLock = new Object();

	/** First timestamp published to client after this node became active */
	private long firstPublishedTimestamp;

	private volatile boolean delayActive;
	/** timer for actions synchronized with the subscription */
	private volatile int delayTimer;

	/** true, if there are items subscribed **/
	private volatile boolean subscriptionPresent;

	protected DataValue messageCountValue;
	protected DataValue timestampValue;
	private volatile DataValue handshakeValue;
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
		this.messageLength = 0;
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
	 *            The name of the folder in the browse tree that contains this message
	 */
	private void init(MessageConfig messageConfig, String folderAddress) {
		createFolder(messageConfig);

		timestampValue = new DataValue(StatusCodes.Bad_WaitingForInitialData);
		messageCountValue = new DataValue(new Variant(uint(0)));
		handshakeValue = new DataValue(new Variant(uint(0)));

		this.handshakeBit = true;
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
				queueSizeValue = new DataValue(new Variant(ushort(queue.size())));
				queueActive = false;
			} catch (IOException e) {
				log.error(String.format("Error enabling handshake mode for Message ID%d. Can not create persitent queue in path %s:%s", getFolderId(), path, e.toString()));
				queue = null;
			}
		}

		addDefaultTags(folderAddress);

		log.debug(String.format("Message initialized with %d tags. Queue size: %s", addressTagMap.size(), queue != null ? queue.size() : "Not used"));
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
		if (queueMode != QueueMode.NONE) {
			return;
		}

		// Update tag qualities
		StatusCode statusCode = new StatusCode(isConnected ? StatusCodes.Bad_WaitingForInitialData : StatusCodes.Bad_NotConnected);
		setTagQuality(statusCode);
	}

	@Override
	public void activityLevelChanged(boolean isActive) {
		// Activity Level is ignored if queue mode is not used
		// In this case, we only react on connection state changes
		if (queueMode == QueueMode.NONE) {
			return;
		}

		StatusCode statusCode = new StatusCode((isActive) ? StatusCodes.Bad_WaitingForInitialData : StatusCodes.Bad_NotConnected);
		setTagQuality(statusCode);

		// Active -> Not active
		if (queueMode != QueueMode.NONE && !isActive && queueActive) {
			queueActive = false;
			firstPublishedTimestamp = 0;

			cancelSchedule();
		}

		// Not active -> Active
		if (queueMode != QueueMode.NONE && isActive && !queueActive) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("'%s' update driver state -> Active with Queue.", FolderManager.folderIdAsString(getFolderId())));
			}

			queueSizeValue = new DataValue(new Variant(ushort(queue.size())));
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

			timestampValue = new DataValue(statusCode);
		} finally {
			tagLock.unlock();
		}
	}

	/**
	 * In Handshake Mode, the incoming message is added to the handshake queue, without handshake it is immediately
	 * published to clients.<br />
	 * The first 8 bytes of the buffer have to contain the absolute time when the message was received by the driver and the
	 * sequence number if multiple messages have been received with the same timestamp.<br />
	 * The second 8 byte may contain a relative time offset received with a packet header.
	 * 
	 * @param message
	 *            The incoming message.<br/>
	 *            Bytes 0-7 in the array are combination of receive timestamp and the index of messages received with the
	 *            same timestamp.<br />
	 *            timestamp << 16 + number<br />
	 *            Bytes 8-15 contain the header timestamp or 0 if no header is used
	 * @param handshakeMsg
	 *            if this param is not null, the value is sent back to the device after the message has been added to the
	 *            queue
	 */
	public void messageArrived(final byte[] message, final byte[] handshakeMsg) {

		if (log.isTraceEnabled()) {
			log.trace(String.format("Message with id %d received: %s", ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0),
					ByteUtilities.toString(Arrays.copyOfRange(message, 16, message.length))));
		}

		// Make sure that messages don't arrive to fast
		int pending = pendingEvaluations.incrementAndGet();
		if (pending > MAX_PENDING_MESSAGES) {
			// Messages arriving to fast - discard message
			pendingEvaluations.decrementAndGet();
			log.error("Messages arriving to fast. Discarded latest message.");
			return;
		}

		if (queueMode == QueueMode.NONE)
			// No Handshake - Evaluate message
			getDriverContext().executeOnce(new Runnable() {
				@Override
				public void run() {
					evaluateMessage(message);
					// If this message is the last one in a package with header, send the confirmation to the device
					if (handshakeMsg != null) {
						if (log.isDebugEnabled()) {
							log.debug(String.format("Sending handshake message to device:%s", ByteUtilities.toString(handshakeMsg)));
						}
						writeHandshake(handshakeMsg);
					}
					pendingEvaluations.decrementAndGet();
				}
			});
		else {
			// Add message to queue
			synchronized (queueLock) {
				if (queue.size() > MAX_QUEUE_SIZE) {
					log.error("Maximum queue size exceeded, discarding oldest message.");
					pollMessageFromQueue(false);
				}
				addMessageToQueue(message);

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
	 * Start evaluation of the message queue. The oldest message is passed to the client, but not removed from the queue
	 * until a client sets the handshake. This way, the current message will be saved on shutdown. If the handshake is set
	 * at the moment a shutdown happens, the message may be evaluated twice after the restart.
	 */
	private void evaluateQueuedMessage() {
		byte[] message;
		synchronized (queueLock) {

			// Peek oldest message for evaluation
			message = (queue.peek());

			if (message != null) {
				if (log.isDebugEnabled())
					log.debug(String.format("Evaluating queued message with id %d.", ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0)));
				handshakeBit = false;

				// In delayed mode, evaluate next message after 2 subscription cycles
				if (queueMode == QueueMode.DELAYED && delayActive) {
					delayTimer = 2;
				}

				// In Handshake mode, set timeout
				if (queueMode == QueueMode.HANDSHAKE) {
					delayTimer = 5;
					waitHandshake = true;
				}
			} else {
				// No message queued
				if (log.isDebugEnabled())
					log.debug("Message queue empty. Set handshake true");
				handshakeBit = true;
			}
		}

		if (message != null) {
			evaluateMessage(message);
		}
	}

	@Override
	public void subscriptionChanged(final long rate, final Set<String> itemAddresses) {

		// This method executes in the run() method of our SubscriptionUpdater

		if (log.isDebugEnabled())
			log.debug(String.format("Subscription changed. New rate:%dms, items:%d", rate, itemAddresses.size()));

		subscriptionPresent = false;
		if (itemAddresses.size() > 0) {
			for (String itemAddress : itemAddresses) {
				if (itemAddress.endsWith(MESSAGE_COUNT_TAG_NAME) || itemAddress.endsWith(HANDSHAKE_TAG_NAME)) {
					subscriptionPresent = true;
					break;
				}
			}
		}

		if (queueMode == QueueMode.DELAYED) {
			// Cancel the delayed task if there is no subscription any more
			if (!subscriptionPresent && delayActive) {
				delayActive = false;
				cancelSchedule();
				if (log.isDebugEnabled())
					log.debug("Message has no subscriptions to _MessageCount or _Handshake. Delayed queue mode cancelled.");
			}
			if (subscriptionPresent && !delayActive) {
				if (log.isDebugEnabled())
					log.debug("_MessageCount or _Handshake subscribed. Delayed queue mode activated.");
				delayActive = true;
				evaluateQueuedMessage();
			}
		}
	}
	
	@Override
	public void beforeSubscriptionUpdate() {
		if (delayTimer>0) {
			delayTimer -= 1;
			if (delayTimer == 0) {
				// Timer expired - run action
				if (queueMode == QueueMode.DELAYED && delayActive) {
					// poll next message from queue
					pollMessageFromQueue(true);
				}
				if (queueMode == QueueMode.HANDSHAKE && !handshakeBit && waitHandshake) {
					// Set handshake to 0
					if (log.isDebugEnabled()) {
						log.debug("Handshake timeout expired. Setting _HandshakeTag to 0");
					}
					try {
						tagLock.lock();
						handshakeValue = new DataValue(new Variant(uint(0)));
					} finally {
						tagLock.unlock();
					}
					delayTimer = 2;	// Reset handshake after 2 subscription cycles
					waitHandshake = false;
				} else if (!waitHandshake) {
					try {
						tagLock.lock();
						if (log.isDebugEnabled()) {
							log.debug(String.format("Setting _Handshake back to %d after handshake timeout", messageCount));
						}
						handshakeValue = new DataValue(new Variant(messageCount));
					} finally {
						tagLock.unlock();
					}
					delayTimer = 5;	// Reset handshake after 5 subscription cycles
					waitHandshake = true;
				}
			}
		}
		
	}

	@Override
	public void readItems(List<? extends ReadItem> list) {
		
		if (queueMode == QueueMode.HANDSHAKE && delayTimer>0) {
			for (ReadItem item : list) {
				if (item.getAddress().endsWith(HANDSHAKE_TAG_NAME)) {
					// Handshake tag has been read. Check timer to toggle handshake
					beforeSubscriptionUpdate();
				}
			}
		}
		
		super.readItems(list);
	}
	
	/**
	 * Cancel the scheduled actions
	 */
	private void cancelSchedule() {
		delayTimer = 0;
		waitHandshake = false;
	}

	/**
	 * Evaluate the incoming message and update the tag values. Access to tag values is locked during the update. This makes
	 * sure that subscription updates will not mix data from two messages.
	 * 
	 * @param message
	 *            The incoming message.<br/>
	 *            Bytes 0-7 in the array are combination of receive timestamp and the index of messages received with the
	 *            same timestamp.<br />
	 *            timestamp << 16 + number<br />
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
			DateTime timestampUtc = new DateTime(timestamp);

			long headerTimestamp = dataWrapper.getHeaderTimestamp();

			tagLock.lock();
			try {
				if (messageAgeOffset > -1) {
					// MessageAge is configured - read it first
					int pos = buffer.position();
					buffer.position(pos + messageAgeOffset);
					long messageAge = (buffer.getInt() & 0xffffffff) * (long) driverSettings.getTimestampFactor();
					long calculatedAge = headerTimestamp - messageAge;
					if (calculatedAge < 0) {
						calculatedAge += driverSettings.getMaxTimestamp() + 1;
					}
					if (calculatedAge >= 0) {
						timestamp -= calculatedAge;
						timestampUtc = new DateTime(timestamp);
						messageAgeTag.setValue(new Variant(uint(calculatedAge)), timestampUtc);
						if (log.isTraceEnabled()) {
							log.trace(String.format(
									"Evaluate message. Received: %s (%d) - Header timestamp: %d - Message Age: %d - Calculated: Message age: %dms - Timestamp: %s -  Timestamp factor: %d",
									DateFormat.getDateTimeInstance().format(new Date(dataWrapper.getTimeReceived())), dataWrapper.getSequenceId(), headerTimestamp, messageAge, calculatedAge,
									timestamp, driverSettings.getTimestampFactor()));
						}
					} else {
						calculatedAge = 0;
						timestampUtc = new DateTime(timestamp);
						messageAgeTag.setValue(new Variant(uint(calculatedAge)), StatusCodes.Bad_OutOfRange, timestampUtc);
						log.error(String.format("Evaluated Message has an invalid age. Header timestamp: %d - messageAge: %d", headerTimestamp, messageAge));
					}

					// Restore buffer start position
					buffer.position(pos);
				}

				for (ReadableTcpDriverTag driverTag : varTags) {
					switch (driverTag.getDriverDataType()) {
					case Dummy: // Dummy: Ignore value
						buffer.position(buffer.position() + getTagReadSize(buffer.remaining(), driverTag));
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
						driverTag.setValue(buffer.readString(getTagReadSize(buffer.remaining(), driverTag)), timestampUtc);
						break;
					case RawString:
						driverTag.setValue(buffer.readByteString(getTagReadSize(buffer.remaining(), driverTag)), timestampUtc);
						break;
					case MessageAge:
						buffer.getInt(); // Has already been read - just skip
						break;
					}
				}

				timestampValue = new DataValue(new Variant(timestamp));

				if (messageCount < UInteger.MAX_VALUE)
					messageCount++;
				else
					messageCount = 0;
				messageCountValue = new DataValue(new Variant(uint(messageCount)));
				handshakeValue = new DataValue(new Variant(uint(messageCount)));

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
				if (messageCount < UInteger.MAX_VALUE)
					messageCount++;
				else
					messageCount = 0;
				messageCountValue = new DataValue(new Variant(uint(messageCount)));
				handshakeValue = new DataValue(new Variant(uint(messageCount)));
			} finally {
				tagLock.unlock();
			}
		}
	}

	/**
	 * @param remainingBytes
	 *            Remaining bytes message buffer
	 * @param tag
	 *            The tag for which to get the read length.
	 * @return The tag size in bytes
	 */
	private int getTagReadSize(int remainingBytes, ReadableTcpDriverTag tag) {
		if (tag == varLengthTag) {
			return remainingBytes - messageBytesAfterVarTag;
		} else {
			return tag.getReadSize();
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
	 * Creates the DriverTag and the UANode for the given configuration. The UANode is added to the NodeManager and to the
	 * drivers browseTree. For tags within an array, this method is called recursively with arrayLength = -1, to create the
	 * child tags.
	 * 
	 * @param address
	 * @param id
	 * @param alias
	 * @param index
	 *            Tag index for arrays, used in browseName and displayName. -1 if tag has no index.
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
				UaVariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}
		} else if (arrayLength > 1) {
			// Size > 1 - create an array
			// This method is called recursively to create the tags in the array
			if (dataType.getUADataType() == BuiltinDataType.Boolean)
				tag = new ReadableBoolArrayTag(address, id, alias, dataType, arrayLength);
			else
				tag = new ReadableArrayTag(address, id, alias, index, dataType, arrayLength);

			for (int i = 0; i < arrayLength; i++) {
				String childAddress = String.format("%s[%d]", address, i);
				ReadableTcpDriverTag childTag = createTag(childAddress, id, alias, i, dataType, -1);
				((ReadableArrayTag) tag).addChild(childTag);
			}

			// Add the new tag as an UANode after all childs have been added
			if (!tag.getDriverDataType().isHidden()) {
				UaVariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}

		} else if (dataType.getArrayLength() > 1) {
			// No array, but the DataType needs an array
			tag = new ReadableArrayTag(address, id, alias, index, dataType, dataType.getArrayLength());

			for (int i = 0; i < dataType.getArrayLength(); i++) {
				String childAddress = String.format("%s[%d]", address, i);
				ReadableTcpDriverTag childTag = new ReadableTcpDriverTag(childAddress, id, alias, i, dataType);
				UaVariableNode childNode = buildAndAddNode(childTag);
				childTag.setUaNode(childNode);
				((ReadableArrayTag) tag).addChild(childTag);
			}

			if (dataType.getUADataType() == BuiltinDataType.Boolean) {
				String childAddress = String.format("%s[raw]", address);
				ReadableTcpDriverTag childTag = new ReadableTcpDriverTag(childAddress, id, alias + "_raw", -1, BinaryDataType.UInt16);
				UaVariableNode childNode = buildAndAddNode(childTag);
				childTag.setUaNode(childNode);
				((ReadableArrayTag) tag).addChildRaw(childTag);
			}

			// Add the new tag as an UANode after all childs have been added
			if (!tag.getDriverDataType().isHidden()) {
				UaVariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}

		} else {
			// No array - create a simple tag
			tag = new ReadableTcpDriverTag(address, id, alias, index, dataType);
			if (!tag.getDriverDataType().isHidden()) {
				UaVariableNode uaNode = buildAndAddNode(tag);
				tag.setUaNode(uaNode);
			}
		}

		return tag;
	}

	/**
	 * Creates the configured tags for this message and adds them to the NodeManager and the drivers browseTree.<br />
	 * After calling this method, messageLength contains the correct length of this message in byte.
	 * 
	 * @param messageConfig
	 * @param folderName
	 */
	private void addTagsFromConfig(MessageConfig messageConfig, String folderName) {

		short bytesAfterVarTag = 0;
		for (TagConfig config : messageConfig.tags) {
			ReadableTcpDriverTag tag = createTag(folderName, config);
			varTags.add(tag);
			if (config.getDataType() == BinaryDataType.MessageAge) {
				messageAgeOffset = messageLength;
				messageAgeTag = tag;
			}
			if (varLengthTag != null) {
				bytesAfterVarTag += config.getSize() * config.getDataType().getByteCount();
			}
			if (config.getTagLengthType() != TagLengthType.FIXED_LENGTH) {
				if (varLengthTag == null) {
					if (config.getDataType().supportsVariableLength()) {
						varLengthTag = tag;
					} else {
						log.warn(String.format("The tag '%s' is configured with variable length but data type %s does not support variable length. Tag uses fixed length instead.", config.getAlias(),
								config.getDataType()));
					}
				} else {
					log.warn(String.format("More than one tag is configured with variable length. Tag '%s' uses fixed length instead.", config.getAlias()));
				}
			}
			messageLength += config.getSize() * config.getDataType().getByteCount();
		}

		messageBytesAfterVarTag = bytesAfterVarTag;
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
		DynamicDriverTag driverTag = new DynamicDriverTag(folderName + TIMESTAMP_TAG_NAME, BuiltinDataType.DateTime) {
			@Override
			public DataValue getValue() {
				return timestampValue;
			}
		};
		buildAndAddNode(driverTag).setValue(driverTag.getValue());

		// MessageCount
		driverTag = new DynamicDriverTag(folderName + MESSAGE_COUNT_TAG_NAME, BuiltinDataType.UInt32) {
			@Override
			public DataValue getValue() {
				return messageCountValue;
			}
		};
		buildAndAddNode(driverTag).setValue(driverTag.getValue());

		// Writable handshake tag
		if (queueMode == QueueMode.HANDSHAKE) {
			WritableTag handshakeTag = new WritableTag(folderName + HANDSHAKE_TAG_NAME, BuiltinDataType.Int64) {
				@Override
				public StatusCode setValue(DataValue paramDataValue) {
					Long newValue;
					try {
						newValue = TypeUtilities.toLong(paramDataValue.getValue().getValue());
					} catch (ClassCastException e) {
						return new StatusCode(StatusCodes.Bad_InvalidArgument);
					}

					if (!getDriverContext().isActiveNode() && newValue != handshakeValue.getValue().getValue()) {
						// Handshake is not accepted when this is not the active node
						log.warn("Client tried to set Handshake on non active cluster node");
						return new StatusCode(StatusCodes.Bad_NotWritable);
					}

					handshakeValue = new DataValue(new Variant(uint(newValue)));

					if (log.isDebugEnabled())
						log.debug(String.format("Handshake for message id %d set by client. Value: %d - MassageCount: %d - Handshake State: %s", getFolderId(), newValue, messageCount, handshakeBit));

					if (newValue == 0 && handshakeBit==false) {
						// Remove the acknowledged message from queue and evaluate the next one
						handshakeBit = true;
						cancelSchedule();
						pollMessageFromQueue(true);
					}

					return StatusCode.GOOD;
				}

				@Override
				public DataValue getValue() {
					return handshakeValue;
				}

			};
			buildAndAddNode(handshakeTag).setValue(handshakeTag.getValue());
		}

		if (queueMode != QueueMode.NONE) {
			// QueueSize
			driverTag = new DynamicDriverTag(folderName + QUEUE_SIZE_TAG_NAME, BuiltinDataType.UInt32) {
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

	/**
	 * 
	 * @param evaluateNext
	 *            true - evaluate next message in queue
	 */
	private void pollMessageFromQueue(boolean evaluate) {
		// Remove message from queue
		synchronized (queueLock) {
			byte[] removed = queue.peek();
			if (removed != null) {
				removeMessageFromQueue(removed);
				// Store the first polled timestamp
				if (firstPublishedTimestamp == 0)
					firstPublishedTimestamp = ByteUtilities.get(driverSettings.getByteOrder()).getLong(removed, 0);
			} else {
				log.error("Message queue inconsistent. Tried to remove message from empty queue");
			}
		}

		// Start asynchronous evaluation of new message
		if (evaluate) {
			getDriverContext().executeOnce(new Runnable() {
				@Override
				public void run() {
					evaluateQueuedMessage();
				}
			});
		}
	}

	/**
	 * This method is called when this node receives a message from the device or when the redundant peer posts a queue
	 * update.
	 * 
	 * @param message
	 */
	public void addMessageToQueue(byte[] message) {
		synchronized (queueLock) {
			queue.add(message);

			queueSizeValue = new DataValue(new Variant(ushort(queue.size())));
			if (log.isDebugEnabled())
				log.debug(String.format("Message with id %d and %d bytes length added to queue. New queue size: %d", ByteUtilities.get(driverSettings.getByteOrder()).getLong(message, 0),
						message.length, queue.size()));

			if (handshakeBit && queueActive) {
				// Evaluate message immediately if handshake is already set
				if (log.isDebugEnabled()) {
					log.debug("Handshake is true. Evaluate queued message without delay");
				}
				handshakeBit = false;
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
					queueSizeValue = new DataValue(new Variant(ushort(queue.size())));

					if (log.isDebugEnabled())
						log.debug(String.format("Message with id %d polled from queue. New queue size: %d", timestampToRemove, queue.size()));
				} else {
					log.warn(String.format("Message queue inconsistent. Id %d should be removed, but id of queue head was %d", timestampToRemove, timestampQueue));
					int discarded = 0;
					while (queue.size() > 0 && timestampQueue <= timestampToRemove) {
						// Entry at queue head is older then entry that should be removed - try to catch up
						queue.poll();
						discarded++;
						if (queue.size() > 0)
							timestampQueue = ByteUtilities.get(driverSettings.getByteOrder()).getLong(queue.peek(), 0);
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
	 * Returns the first timestamp that has been published to clients by this message folder.
	 * 
	 * @return The first published timestamp
	 */
	public long getFirstPublishedTimestamp() {
		return firstPublishedTimestamp;
	}

	/**
	 * This method should only be used by unit tests. Do not call!
	 * 
	 * @return
	 */
	public long getMessageCount() {
		return messageCount;
	}
}
