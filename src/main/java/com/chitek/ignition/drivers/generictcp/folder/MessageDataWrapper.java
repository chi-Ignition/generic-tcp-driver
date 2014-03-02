package com.chitek.ignition.drivers.generictcp.folder;

import java.nio.ByteOrder;

import com.chitek.ignition.drivers.generictcp.util.VariantByteBuffer;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

/**
 * A wrapper class for the byte array with message data
 */
public class MessageDataWrapper {

	public static byte[] wrapMessage(long receiveTimestamp, long headerTimestamp, short sequenceId, byte[] payload, ByteOrder byteOrder) {
		byte[] messageData = new byte[16 + payload.length];

		// Copy the timestamp to the first 8 byte of the message
		// The timestamp is shifted left by 2 Bytes, the message number is then added
		byte[] timestamp = ByteUtilities.get(byteOrder).fromLong(receiveTimestamp << 16 + sequenceId);
		System.arraycopy(timestamp, 0, messageData, 0, 8);

		// Copy the header timestamp to the second 8 byte of the message
		timestamp = ByteUtilities.get(byteOrder).fromLong(headerTimestamp);
		System.arraycopy(timestamp, 0, messageData, 8, 8);

		// Copy the payload data
		System.arraycopy(payload, 0, messageData, 16, payload.length);

		return messageData;
	}

	private int sequenceId;
	private long timeReceived;
	private long headerTimestamp;

	/**
	 * Evaluate the message info form the given buffer. After execution, the buffer is positioned at the begin of the
	 * message payload.
	 *
	 * @param messageData
	 * @return
	 * 	The length in bytes of the message payload data.
	 */
	public int evaluateData(VariantByteBuffer messageData) {

		// Read the timestamps from the message
		long id = messageData.getLong();
		sequenceId = (int) (id & (0xffff));
		long timestamp = id / 65536; // Remove the sequence number
		timeReceived = timestamp;

		headerTimestamp = messageData.getLong();

		return messageData.remaining();
	}

	/**
	 * @return
	 * 	The sequence id if multiple messages where received in the same package with the same timestamp.
	 */
	public int getSequenceId() {
		return sequenceId;
	}

	/**
	 * @return
	 * 	Time when this message was received by the driver
	 */
	public long getTimeReceived() {
		return timeReceived;
	}

	/**
	 * @return
	 * 	Timestamp received with the message header
	 */
	public long getHeaderTimestamp() {
		return headerTimestamp;
	}
}
