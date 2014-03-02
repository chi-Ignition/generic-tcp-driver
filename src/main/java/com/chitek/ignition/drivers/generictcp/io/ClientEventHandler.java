package com.chitek.ignition.drivers.generictcp.io;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import com.chitek.ignition.drivers.generictcp.folder.MessageHeader;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.IDriverSettings;
import com.inductiveautomation.iosession.async.IOEventHandler;
import com.inductiveautomation.iosession.socket.IOTimeoutHandler;

public class ClientEventHandler implements IOEventHandler, IOTimeoutHandler {
	
	private final IMessageHandler messageHandler;
	private final MessageState state;
	
	public ClientEventHandler(Logger log, DriverConfig driverConfig, IDriverSettings driverSettings, MessageHeader messageHeader, IMessageHandler messageHandler) {
			this.messageHandler = messageHandler;
			
			state = new MessageState(null, messageHeader, driverConfig, driverSettings, log);
			state.setMessageHandler(messageHandler);
		}
	
	
	@Override
	public void dataArrived(byte[] data, int bytesRead) {
		state.addData(ByteBuffer.wrap(data, 0, bytesRead));
	}

	@Override
	public void connectionLost(IOException e) {
		messageHandler.clientDisconnected(null);
	}

	@Override
	public void readTimeout(final SocketTimeoutException e) {
		messageHandler.clientReadTimeout(null);
	}
}
