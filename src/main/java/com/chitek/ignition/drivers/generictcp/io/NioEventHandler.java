/*******************************************************************************
 * Copyright 2013 C. Hiesserich
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
package com.chitek.ignition.drivers.generictcp.io;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.chitek.ignition.drivers.generictcp.folder.MessageHeader;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.IDriverSettings;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

public class NioEventHandler implements IIoEventHandler {

	private final Logger log;

	private final DriverConfig driverConfig;
	private final IDriverSettings driverSettings;
	private final MessageHeader messageHeader;
	private final IMessageHandler messageHandler;

	private final Map<InetSocketAddress,MessageState> clientMap=new HashMap<InetSocketAddress,MessageState>();

	public NioEventHandler(Logger log, DriverConfig driverConfig, IDriverSettings driverSettings, MessageHeader messageHeader, IMessageHandler messageHandler) {
		this.log = log;
		this.driverConfig = driverConfig;
		this.driverSettings = driverSettings;
		this.messageHeader = messageHeader;
		this.messageHandler = messageHandler;
	}

	@Override
	public boolean clientConnected(InetSocketAddress remoteSocket) {
		return messageHandler.clientConnected(remoteSocket);
	}

	@Override
	public void connectionLost(InetSocketAddress remoteSocket) {
		clientMap.remove(remoteSocket);
		messageHandler.clientDisconnected(remoteSocket);
	}

	@Override
	public void dataArrived(InetSocketAddress remoteSocket, ByteBuffer data, int bytesRead) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Received %d bytes from %s: %s", data.remaining(), remoteSocket.toString(), ByteUtilities.toString(Arrays.copyOfRange(data.array(), 0, bytesRead))));
		}

		MessageState state = getMessageState(remoteSocket);

		state.addData(data);
	}

	/**
	 * Return the message state for the given remote address.
	 * @param remoteAddress
	 * @return
	 */
	private MessageState getMessageState(InetSocketAddress remoteSocket) {
		MessageState state = clientMap.get(remoteSocket);
		if (state == null) {
			state = new MessageState(remoteSocket, messageHeader, driverConfig, driverSettings, log);
			state.setMessageHandler(messageHandler);
			clientMap.put(remoteSocket, state);
		}

		return state;
	}

}
