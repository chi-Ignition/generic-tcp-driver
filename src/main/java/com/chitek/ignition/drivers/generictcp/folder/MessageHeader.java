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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderTagConfig;
import com.chitek.ignition.drivers.generictcp.util.Util;

public class MessageHeader {

	public static final String LOGGER_NAME = "GenericTcpDriver.MessageHeader";

	protected final Logger log;

	private final ByteOrder byteOrder;
	private final HeaderConfig config;
	private final HeaderTagConfig firstFixedTag;
	private final HeaderTagConfig packetSizeTag;

	/**
	 * Message length of the last received header
	 */
	private int packetSize = 0;

	/**
	 * Timestamp of the last received header
	 */
	private long headerTimestamp = 0;

	/** Sequence Id of the last received header */
	private long sequenceId = 0;

	/**
	 * True if the last received header matches the configured header
	 */
	private boolean headerValid = false;

	private ByteBuffer handshakeBuffer;
	private byte[] handshakeMsg = null;
	private int handshakeMsgLength = 0;

	public MessageHeader(HeaderConfig config, ByteOrder byteOrder) {
		this(config, byteOrder, Logger.getLogger(LOGGER_NAME));
	}

	/**
	 * 
	 * @param config
	 *            The HeaderConfig
	 * @param byteOrder
	 *            ByteOrder to use while parsing incoming data
	 * @param logger
	 *            The logger to use
	 */
	public MessageHeader(HeaderConfig config, ByteOrder byteOrder, Logger logger) {

		this.log = logger;
		this.byteOrder = byteOrder;

		this.config = config;
		this.firstFixedTag = config.getFirstFixedTag();
		this.packetSizeTag = config.getPacketSizeTag();

		// Check the handshake message
		if (config.isUseHandshake()) {
			Map<String, Number> values = new HashMap<String, Number>();
			values.put("timestamp", (Integer.valueOf((int) (headerTimestamp & 0xffffffff))));
			values.put("sequence", (Short.valueOf((short) (sequenceId & 0xffff))));
			values.put("lenb", (Byte.valueOf((byte) (10 & 0xff))));
			values.put("lenw", (Short.valueOf((short) (10 & 0xffff))));
			String msg = config.getHandshakeMsg();
			try {
				byte[] parsed = Util.hexString2ByteArray(msg, values, byteOrder);
				handshakeMsgLength = parsed.length;
				this.handshakeBuffer = ByteBuffer.allocate(handshakeMsgLength);
				this.handshakeBuffer.order(byteOrder);
				this.handshakeMsg = new byte[handshakeMsgLength];
			} catch (ParseException e) {
				log.error("Initial value can no be parsed. Handshake disabled. Error:" + e.getLocalizedMessage());
				config.setUseHandshake(false);
			}
		}

	}

	public int getHeaderLength() {
		return config.getHeaderSize();
	}

	/**
	 * Evaluate a message header. For a header to be valid, all fixed values have to match the configured values.<br />
	 * The buffer length is not checked by this method, so it has to match the configured length.
	 * 
	 * @param message
	 *            The message header to evaluate.
	 * @return True if the header is valid
	 */
	public boolean evaluateHeader(final byte[] message) {

		if (log.isTraceEnabled()) {
			log.trace(String.format("Evaluating header: %s", Util.byteArray2HexString(message)));
		}
		
		// Read the packetSize first
		packetSize = byteOrder.equals(ByteOrder.LITTLE_ENDIAN) 
				? (int) (((message[packetSizeTag.getOffset() + 1] & 0xff) << 8) | (message[packetSizeTag.getOffset() & 0xff]))
				: (int) (((message[packetSizeTag.getOffset()] & 0xff) << 8) | (message[packetSizeTag.getOffset() + 1] & 0xff));

		// Check if packet size is valid
		if (config.isSizeIncludesHeader() && packetSize < config.getHeaderSize()) {
			headerValid = false;
			if (log.isDebugEnabled()) {
				log.debug(String.format("Header invalid. Received packet size %d is shorter than header length.", packetSize));
			}
			// We return the header length here, as this is the amount of bytes to discard
			packetSize = message.length;
			return false;		
		}
		
		// Do a quick check of the first fixed tag
		// This way we can return fast if the message is obviously invalid
		if (firstFixedTag != null) {
			int value;
			switch (firstFixedTag.getDataType()) {
			case Byte:
				value = message[firstFixedTag.getOffset()] & 0xff;
				break;
			case Word:
				value = byteOrder.equals(ByteOrder.LITTLE_ENDIAN) 
					? (int) (((message[firstFixedTag.getOffset() + 1] & 0xff) << 8) | (message[firstFixedTag.getOffset()] & 0xff))
					: (int) (((message[firstFixedTag.getOffset()] & 0xff) << 8) | (message[firstFixedTag.getOffset() + 1] & 0xff));
				break;
			default:
				value = 0;
			}

			if (value != firstFixedTag.getValue()) {
				headerValid = false;
				if (log.isDebugEnabled())
					log.debug(String.format("Header invalid. Expected fixed value: %s at offset %d, found value %d", firstFixedTag.getValue(), firstFixedTag.getOffset(), value));
				return false;
			}
		}

		ByteBuffer buffer = ByteBuffer.wrap(message);

		// Set byte order. If reverseByteOrder is configured, we use LITTLE_ENDIAN
		buffer.order(byteOrder);

		try {
			headerTimestamp = 0;
			headerValid = true;
			int value = 0;

			for (HeaderTagConfig tag : config.getTags()) {
				switch (tag.getDataType()) {
				case Dummy:
					// Dummy - Ignore
					buffer.position(buffer.position() + tag.getSize());
					break;
				case PacketSize:
					// Has already been reed. Ignore here
					buffer.position(buffer.position() + tag.getByteCount());
					break;
				case Timestamp:
					headerTimestamp = buffer.getInt() & 0xffffffff;
					break;
				case SequenceId:
					sequenceId = buffer.getShort() & 0xffff;
					break;
				case Byte:
					value = buffer.get() & 0xff;
					if (tag.getValue() != value) {
						headerValid = false;
						if (log.isDebugEnabled()) {
							log.debug(String.format("Header invalid. Expected fixed value: %s at offset %d, found value %d", tag.getValue(), buffer.position() - 1, value));
						}
					}
					break;
				case Word:
					value = buffer.getShort() & 0xffff;
					if (tag.getValue() != value) {
						headerValid = false;
						if (log.isDebugEnabled()) {
							log.debug(String.format("Header invalid. Expected fixed value: %s at offset %d, found value %d", tag.getValue(), buffer.position() - 1, value));
						}
					}
					break;
				default:
					break;
				}
				// No need to check further if the header is invalid
				if (!headerValid)
					break;
			}

			if (config.isUseHandshake()) {
				// Build the handshake message
				Map<String, Number> values = new HashMap<String, Number>();
				values.put("timestamp", (Integer.valueOf((int) (headerTimestamp & 0xffffffff))));
				values.put("sequence", (Short.valueOf((short) (sequenceId & 0xffff))));
				values.put("lenb", (Byte.valueOf((byte) (handshakeMsgLength & 0xff))));
				values.put("lenw", (Short.valueOf((short) (handshakeMsgLength & 0xffff))));
				String msg = config.getHandshakeMsg();
				handshakeMsg = Util.hexString2ByteArray(msg, values, byteOrder);
			}

		} catch (Exception ex) {
			log.error("Exception while evaluating header message");
			if (log.isDebugEnabled())
				log.debug("Stacktrace:", ex);
		}

		if (log.isDebugEnabled())
			log.debug(String.format("Message header evaluated. Packet length: %d - Timestamp: %d - Header Valid: %b", packetSize, headerTimestamp, headerValid));

		return headerValid;
	}

	/**
	 * 
	 * @return The timestamp in the last evaluated header.
	 */
	public long getHeaderTimestamp() {
		return headerTimestamp;
	}

	/**
	 * 
	 * @return The packet length from the last evaluated header, without the size of the header.
	 */
	public int getPacketSize() {
		return config.isSizeIncludesHeader() ? packetSize - config.getHeaderSize() : packetSize;
	}

	/**
	 * 
	 * @return True, if the last received header matched all configured fixed values.
	 */
	public boolean isHeaderValid() {
		return headerValid;
	}

	public byte[] getHandshakeMsg() {
		return handshakeMsg;
	}

}
