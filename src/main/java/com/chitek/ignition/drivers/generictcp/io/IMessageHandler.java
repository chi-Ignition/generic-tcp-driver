package com.chitek.ignition.drivers.generictcp.io;

import java.net.InetSocketAddress;

public interface IMessageHandler {

	/**
	 * @param remoteSocket
	 * @param messageId
	 * @param messageData
	 * 	The received message<br>
	 *  0..7  - Packet receive timestamp<br>
	 *  8..15 - Header Timestamp <br>
	 *  16...   - The message data
	 * @param handshakeMessage
	 */
	public void messageReceived(InetSocketAddress remoteSocket, int messageId, byte[] messageData, byte[] handshakeMessage);

	public boolean clientConnected(InetSocketAddress remoteSocket);

	public void clientDisconnected(InetSocketAddress remoteSocket);
	
	/**
	 * A read timeout occurred. This method is only used for client sockets.
	 *
	 * @param remoteSocket
	 */
	public void clientReadTimeout(InetSocketAddress remoteSocket);
}
