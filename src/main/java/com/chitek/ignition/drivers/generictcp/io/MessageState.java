package com.chitek.ignition.drivers.generictcp.io;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.chitek.ignition.drivers.generictcp.folder.MessageDataWrapper;
import com.chitek.ignition.drivers.generictcp.folder.MessageHeader;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.IDriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.types.MessageType;
import com.inductiveautomation.ignition.common.execution.ExecutionManager;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

public class MessageState {

	private final Logger log;

	// Configuration
	private final DriverConfig driverConfig;
	private final MessageHeader messageHeader;
	private final int headerLength;
	private final IDriverSettings settings;

	private IMessageHandler messageHandler = null;
	private final InetSocketAddress remoteSocket;

	// State
	public long headerTimestamp;
	private int pendingBytes;
	private short messageIdBytesRec = 0;
	private final byte[] messageIdBytes;
	private boolean messagePending = false;
	private boolean headerReceived = false;
	private boolean headerValid = false;

	/** Time when the start of the current message was received */
	long packetStartDate = 0;
	/** Sequence number of the message for the current timestamp */
	short msgNumber = 0;
	/** Buffer for header data */
	ByteBuffer headerData;

	private final ExecutionManager executionManager;
	private ScheduledFuture<?> timeoutSchedule;
	private final TimeoutHandler timeoutHandler;
	private Object messageLock = new Object();
	
	private int currentMessageId = 0;
	private MessageConfig currentMsgConfig;
	private int currentMsgPos = 0;
	/** Buffer for current message **/
	private byte[] currentMsgData;

	public MessageState(InetSocketAddress remoteSocket, ExecutionManager executionManager, MessageHeader messageHeader, DriverConfig driverConfig, IDriverSettings settings) {
		this(remoteSocket, executionManager, messageHeader, driverConfig, settings, Logger.getLogger(MessageState.class.getSimpleName()));
	}

	/**
	 * 
	 * @param remoteSocket
	 * 	The remote socket that sends the data to this message state
	 * @param executionManager
	 *  The ExecutionManager to use for scheduled commands
	 * @param messageHeader
	 * 	The configured header. May be null, if no header is used
	 * @param driverConfig
	 *  The driver configuration
	 * @param settings
	 *  The driver settings
	 * @param log
	 */
	public MessageState(InetSocketAddress remoteSocket, ExecutionManager executionManager, MessageHeader messageHeader, DriverConfig driverConfig, IDriverSettings settings, Logger log) {

		this.log = log;

		this.remoteSocket = remoteSocket;
		this.messageHeader = messageHeader;
		this.driverConfig = driverConfig;

		this.executionManager = executionManager;
		if (executionManager != null) {
			timeoutHandler = new TimeoutHandler();
		} else {
			timeoutHandler = null;
		}
		
		// Message lengths are stored in a map, to get fast access when evaluating incoming data
		int maxLength = 0;
		for (Map.Entry<Integer, MessageConfig> configEntry : driverConfig.messages.entrySet()) {
			MessageConfig messageConfig = configEntry.getValue();
			maxLength = Math.max(maxLength, messageConfig.getMessageLength());
		}
		this.headerLength = messageHeader != null ? messageHeader.getHeaderLength() : 0;
		this.settings = settings;
		messageIdBytes = new byte[settings.getMessageIdType().getByteSize()];

		currentMsgData = new byte[maxLength];
		headerData = ByteBuffer.allocate(headerLength);
	}

	public void setMessageHandler(IMessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public boolean isMessagePending() {
		return messagePending;
	}

	public boolean isHeaderReceived() {
		return headerReceived;
	}

	public boolean isHeaderValid() {
		return headerValid;
	}

	public int getPendingBytes() {
		return pendingBytes;
	}

	/**
	 * @return ID of the pending message
	 */
	public int getCurrentMessageId() {
		return currentMessageId;
	}

	/**
	 * Add received data to this message
	 * 
	 * @param Data
	 *            The received data
	 */
	public void addData(ByteBuffer data) {

		if (log.isTraceEnabled()) {
			log.trace(String.format("Received packet with %d bytes of data", data.remaining()));
		}
	
		synchronized (messageLock) {
			if (timeoutSchedule != null) {
				timeoutSchedule.cancel(false);
				timeoutSchedule = null;
			}
		}

		// Timeout schedule is only used for packet based messages
		if (currentMsgConfig == null || currentMsgConfig.getMessageType()==MessageType.FIXED_LENGTH) {
			checkMessageTimeout();
		}

		while (data.hasRemaining()) {

			if (!headerValid) {
				if (headerLength > 0) {
					// A header is configured
					if (!headerReceived) {
						// Evaluate header if it is not already received
						readHeader(data);
						if (!headerReceived) {
							return; // Header is not complete - Wait for more data
						}
					}

					// At this point, the header is received
					if (headerReceived && !headerValid) {
						// Header is invalid - discard the whole packet
						// The packet size has to be correct, even when the header is invalid
						discardPacket(data);
					}
				} else {
					// No header used
					headerValid = true;
				}
			} else if (headerValid && !messagePending) {
				// Header is complete (or not used) - Read the message
				if (messageIdBytesRec < settings.getMessageIdType().getByteSize()) {
					// Add a byte to the message Id
					messageIdBytes[messageIdBytesRec++] = data.get();
					pendingBytes -= 1;
				} else {
					// Message Id is received
					currentMessageId = getMessageId(messageIdBytes);
					currentMsgConfig = driverConfig.getMessageConfig(currentMessageId);
					
					if (currentMsgConfig != null) {
						Integer msgLength = currentMsgConfig.getMessageLength();
						if (headerReceived && msgLength > pendingBytes) {
							// Packet is to short to contain the message
							log.warn(String.format(
									"Received packet is too short for message ID: %d. Remaining bytes in packet: %d, expected length of message: %d",
									currentMessageId, pendingBytes, msgLength));
							// Remove rest of the packet from the buffer
							discardPacket(data);
						} else {
							// Start evaluating the message
							messagePending = true;
							currentMsgPos = 0;
							// Use message length for pending bytes when no header is used
							if (!headerReceived) {
								pendingBytes = msgLength;
							}
						}
					} else {
						// Invalid message Id
						log.error(String.format("Received undefined message ID: %s", currentMessageId));
						if (headerReceived)
							discardPacket(data);
						else {
							// No header- so we can only discard all the received data
							if (log.isDebugEnabled()) {
								log.debug(String.format("Discarded current package with %d bytes", data.remaining()));
							}
							data.position(data.limit());
							reset();
						}
					}
				}
			} else if (messagePending) {
				// valid message ID has been received
				boolean fixedLength = currentMsgConfig.getMessageType()==MessageType.FIXED_LENGTH;
				int bytesToRead;
				if (fixedLength) {
					bytesToRead = Math.min(data.remaining(), currentMsgConfig.getMessageLength()-currentMsgPos);
				} else {
					// Variable length message - read all data that is received
					bytesToRead = data.remaining();
				}

				// Resize the current message buffer if necessary			
				if (currentMsgData.length < currentMsgPos + bytesToRead) {
					currentMsgData = Arrays.copyOf(currentMsgData, currentMsgPos + bytesToRead);
				}
								
				data.get(currentMsgData, currentMsgPos, bytesToRead);
				currentMsgPos += bytesToRead;
				pendingBytes -= bytesToRead;
				if (fixedLength && currentMsgPos == currentMsgConfig.getMessageLength()) {
					// The message is complete
					messagePending = false;
					deliverMessage();
				}

				// The packet has been completely received - prepare for new packet
				if (currentMsgConfig.getMessageType()==MessageType.FIXED_LENGTH && headerReceived && pendingBytes == 0) {
					reset();
				}
			}
		}

		if (messagePending && currentMsgConfig.getMessageType() == MessageType.PACKET_BASED && executionManager != null) {
			timeoutSchedule = executionManager.executeOnce(timeoutHandler, 2000, TimeUnit.MILLISECONDS);
		}
	}

	private void checkMessageTimeout() {
		// Check timeout
		if (messagePending || headerReceived) {
			long age = System.currentTimeMillis() - packetStartDate;
			log.trace(String.format("Packet age: %d ms", age));
			if (age > settings.getMessageTimeout()) {
				// Message is not complete and timed out
				log.warn(String.format("Packet timeout expired, discarding buffer. Message age: %d ms", age));
				reset();
				packetStartDate = System.currentTimeMillis();
			}
		} else {
			packetStartDate = System.currentTimeMillis();
			msgNumber = 0;
		}
	}

	private void readHeader(ByteBuffer data) {
		// add received data to header
		while (headerData.position() < headerLength && data.hasRemaining()) {
			headerData.put(data.get());
		}

		if (headerData.position() == headerLength) {
			headerReceived = true;
			// Evaluate the received header
			headerValid = messageHeader.evaluateHeader(headerData.array());
			pendingBytes = messageHeader.getPacketSize();
			headerTimestamp = messageHeader.getHeaderTimestamp() * settings.getTimestampFactor();

			if (log.isDebugEnabled()) {
				if (!headerValid)
					log.debug(String.format("Received invalid message Header. Ignoring packet with %d bytes.", pendingBytes));
				else
					log.debug(String.format("Received message header. Packet size %d bytes.", pendingBytes));
			}
			if (pendingBytes <= messageIdBytes.length) {
				log.warn("Received a message header without data.");
				headerValid = false;
			}
		} else {
			return;
		}
	}

	private int getMessageId(byte[] data) {

		// Read the message id
		switch (settings.getMessageIdType()) {
		case None:
			return 0;
		case UByte:
			// The absolute get() method does not increase the buffers position
			return data[0];
		default:
			// The absolute get() method does not increase the buffers position
			// default: UInt16
			return (ByteUtilities.get(settings.getByteOrder()).getShort(data, 0)) & 0xffff;
		}
	}

	/**
	 * Discards the rest of the received data packet. The buffer position is set to the end of the packet and the
	 * headerReceived flag is reset.
	 * 
	 */
	private void discardPacket(ByteBuffer data) {
		int bytesToDiscard = Math.min(pendingBytes, data.remaining());
		
		if (log.isDebugEnabled()) {
			log.debug(String.format("Discarded current message with %d bytes", bytesToDiscard));
		}
		
		if (log.isTraceEnabled()) {
			byte[] msg = new byte[bytesToDiscard];
			data.mark();
			data.get(msg);
			log.trace(String.format("Discarded %d bytes: %s", bytesToDiscard, ByteUtilities.toString(msg)));
		} else {
			data.position(data.position() + bytesToDiscard);
		}
		pendingBytes -= bytesToDiscard;

		if (pendingBytes == 0) {
			reset();
		}
	}

	public void reset() {
		messagePending = false;
		headerReceived = false;
		headerValid = false;
		pendingBytes = 0;
		messageIdBytesRec = 0;
		headerData.clear();
		msgNumber = 0;
	}

	private void deliverMessage() {

		if (messageHandler == null) {
			log.error("deliverMessage failed. No message handler set.");
		} else {

			// The byte array is stored by the receiver, so we have to create a new one every time
			byte[] messageData = Arrays.copyOfRange(currentMsgData, 0, currentMsgPos);
			if (log.isDebugEnabled()) {
				log.debug(String.format("Delivering message ID %d with %d bytes of payload data.", currentMessageId, currentMsgPos));
			}
			
			// Wrap the message with timestamps
			byte[] wrappedMessage = MessageDataWrapper.wrapMessage(packetStartDate, headerTimestamp, msgNumber, messageData, settings.getByteOrder());

			if (headerReceived && pendingBytes == 0)
				// Last message in packet - send handshake to device
				messageHandler.messageReceived(remoteSocket, currentMessageId, wrappedMessage, messageHeader.getHandshakeMsg());
			else
				messageHandler.messageReceived(remoteSocket, currentMessageId, wrappedMessage, null);
		}

		messagePending = false;
		messageIdBytesRec = 0;
		msgNumber ++;
	}
	
	private class TimeoutHandler implements Runnable {

		@Override
		public void run() {
			synchronized (messageLock) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Timeout for message ID %d expired", currentMessageId));
				}
				
				try {
					if (currentMsgConfig != null && currentMsgConfig.getMessageType() == MessageType.PACKET_BASED && currentMsgPos >= currentMsgConfig.getMessageLength()) {
						if (log.isDebugEnabled()) {
							log.debug(String.format("Delivering packet based message ID %d", currentMessageId));
						}
						deliverMessage();
					} else {
						reset();
					}
				} finally {
					timeoutSchedule = null;
				}
			}
		}
		
	}
}
