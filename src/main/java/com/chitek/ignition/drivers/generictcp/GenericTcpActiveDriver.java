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
package com.chitek.ignition.drivers.generictcp;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

import com.chitek.ignition.drivers.generictcp.configuration.settings.GenericTcpClientDriverSettings;
import com.chitek.ignition.drivers.generictcp.folder.IndexMessageFolder;
import com.chitek.ignition.drivers.generictcp.folder.MessageHeader;
import com.chitek.ignition.drivers.generictcp.folder.SimpleWriteFolder;
import com.chitek.ignition.drivers.generictcp.folder.StatusFolder;
import com.chitek.ignition.drivers.generictcp.io.ClientEventHandler;
import com.chitek.ignition.drivers.generictcp.io.IMessageHandler;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.redundancy.StatusUpdateMessage;
import com.chitek.ignition.drivers.generictcp.types.DriverState;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.QueueMode;
import com.inductiveautomation.ignition.gateway.redundancy.types.ActivityLevel;
import com.inductiveautomation.ignition.gateway.util.GatewayUtils;
import com.inductiveautomation.iosession.socket.AsyncSocketIOSession;
import com.inductiveautomation.xopc.driver.api.DriverContext;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

public class GenericTcpActiveDriver extends AbstractGenericTcpDriver implements IMessageHandler {

	public static final String LOGGER_NAME = "TcpClientDriver";

	private StatusFolder statusFolder;
	private SimpleWriteFolder simpleWriteFolder;
	private MessageHeader messageHeader;

	private final Object connectingSocketLock = new Object();
	private volatile Socket connectingSocket;
	private AsyncSocketIOSession ioSession;
	private final Semaphore reconnectSemaphore;
	private volatile String connectedHost = "";

	private boolean connectionEnabled = true;
	private static final long reconnectInterval = 5000L;

	// Settings
	private DriverConfig messageConfig;
	private DriverSettings driverSettings;
	private WritebackConfig writebackConfig;
	private HeaderConfig headerConfig;

	public GenericTcpActiveDriver(DriverContext driverContext, GenericTcpClientDriverSettings deviceSettings) {
		super(driverContext);

		connectingSocket = null;
		reconnectSemaphore = new Semaphore(1, true);

		initSettings(deviceSettings);
		initialize(deviceSettings);
	}

	private void initialize(GenericTcpClientDriverSettings deviceSettings) {
		
		// Create the disk folder for message queues
		File folder = new File(getDiskPath());
		folder.mkdir();		// is mkdir atomic? may fail if multiple devices start up at the same time

		// There is no configuration, if the user never clicked 'save' in the config page
		if (messageConfig == null || messageConfig.messages.size() == 0) {
			// driverConfig = new DriverConfig();
			setDriverState(DriverState.ConfigError);
			log.error("Driver could not be initialized - There is no message configuration.");
			return;
		}

		// Check message id
		if (driverSettings.getMessageIdType()==OptionalDataType.None && messageConfig.messages.get(0)==null) {
			setDriverState(DriverState.ConfigError);
			log.error("Driver could not be initialized - No message with ID0 configured.");
			return;
		}

		final List<Integer> idWithHandshake;
		idWithHandshake = initializeMessageFolders();

		// Delete unused queue files
		cleanupQueues(idWithHandshake);

		// Add the header
		if (headerConfig != null  && headerConfig.isUseHeader()) {
			messageHeader = new MessageHeader(headerConfig, driverSettings.getByteOrder(), log);
			log.debug(String.format("Driver is configured to use a message header. Expected header length: %d bytes", messageHeader.getHeaderLength()));
		}

		super.initialize();
		
		// Update state for all message folders
		getFolderManager().updateActivityLevel(isActiveNode());
		getFolderManager().updateConnectionState(0, false);
		
		if (!isActiveNode()) {
			log.info("Node Redundancy State is not active. Starting with connection disabled.");
		} else if (!connectionEnabled) {
			log.info("Connect on Startup is disabled. Starting offline.");
		} else {
			scheduleConnect(false);
		}
	}

	private List<Integer> initializeMessageFolders() {

		// Add the status folder
		statusFolder = new StatusFolder(this);
		addFolder(statusFolder);

		// Add the simple write folder
		// There is no configuration, if the user never clicked 'save' in the writeback config
		if (writebackConfig != null && writebackConfig.isEnabled()) {
			simpleWriteFolder = new SimpleWriteFolder(this, driverSettings, 0, null, writebackConfig);
			addFolder(simpleWriteFolder);
		}

		// Keep track of folders with handshake to cleanup unused queues
		final List<Integer>idWithHandshake = new ArrayList<Integer>(messageConfig.messages.size());

		// Add all known message tags to the node map
		List<String> alias = new ArrayList<String>(messageConfig.messages.size());
		for (Map.Entry<Integer, MessageConfig> configEntry : messageConfig.messages.entrySet()) {
			MessageConfig message = configEntry.getValue();
			if (message.tags.size() == 0) {
				log.warn(String.format("No tags configured in message ID%s.", message.getMessageId()));
			} else if (getMessageFolder(0, message.getMessageId()) != null) {
				log.warn(String.format("Configuration error. Duplicate message ID%s.", message.getMessageId()));
			} else if (alias.contains(message.getMessageAlias())) {
				log.warn(String.format("Configuration error. Duplicate message alias '%s'.",
					message.getMessageAlias()));
			} else if (messageConfig.getMessageIdType() == OptionalDataType.None && message.getMessageId() > 0) {
				log.warn(String.format("MessageIDType is 'None'. Message ID %d is ignored.",
					message.getMessageId()));
			} else if (messageConfig.getMessageIdType() == OptionalDataType.UByte	&& message.getMessageId() > 255)  {
				log.warn(String.format("MessageIDType is 'Byte'. Message ID %d is ignored.", message.getMessageId()));
			} else {
				IndexMessageFolder messageFolder = 
						new IndexMessageFolder(message,
								driverSettings,
								0,
								message.getMessageAlias(),
								this) ;
				addFolder(messageFolder);
				alias.add(message.getMessageAlias());
				if (message.getQueueMode() != QueueMode.NONE) {
					idWithHandshake.add(message.messageId);
				}
			}
		}

		return idWithHandshake;
	}

	@Override
	public void shutdown() {
		log.debug("Shutdown start");

		// Disconnect from device
		disconnect();

		super.shutdown();

		setDriverState(DriverState.Terminated);
		log.debug("Shutdown finished");
	}

	private void scheduleConnect(boolean immediate) {
		if (connectionEnabled
			&& getRedundancyManager().isActive()
			&& getDriverStateInternal() != DriverState.Terminated) {

			log.debug("New connection schedule started.");
			getExecutionManager().executeOnce(new Runnable() {
				@Override
				public void run() {
					connect();
				}
			}, immediate ? 20 : reconnectInterval);
		} else {
			log.debug(String.format("ScheduleConnect was called but connection is disabled. connectionEnabled:%s-Redundancy.isActive:%s",
				connectionEnabled, getRedundancyManager().isActive()));
		}
	}

	public void notifyConnectDone(boolean success) {
		if (isShutdown()) {
			return;
		}

		if (success) {
			setDriverState(DriverState.Connected);
			getFolderManager().updateConnectionState(0, true);
		} else {
			setDriverState(DriverState.Disconnected);
			scheduleConnect(false);
		}
	}

	public void connect() {
		// There might be another scheduled connection attempt if client triggers a reconnect.
		if (getDriverStateInternal() != DriverState.Disconnected) {
			log.debug("Connection schedule not executed. DriverState not Disconnected.");
			return;
		}

		setDriverState(DriverState.Connecting);
		log.debug(String.format("Opening TCP connection to %s at port %d ...",
			driverSettings.getHostname(), driverSettings.getPort()));

		try {
			Socket socket = new Socket();
			synchronized (connectingSocketLock) {
				connectingSocket = socket;
			}
			socket.connect(new InetSocketAddress(InetAddress.getByName(driverSettings.getHostname()), driverSettings.getPort()));
			synchronized (connectingSocketLock) {
				connectingSocket = null;
			}
			socket.setSoTimeout(driverSettings.getTimeout());

			synchronized (getIoSessionLock()) {
				// Make sure that the connection has not been disabled
				if (getDriverStateInternal() == DriverState.Connecting) {
					ioSession = new AsyncSocketIOSession(socket, new ThreadFactory()     {
						@Override
						public Thread newThread(Runnable r) {
							return new Thread(r, String.format("AsyncSocketIOSession[%s]", getDeviceName()));
						}
					});
					ioSession.setEventHandler(new ClientEventHandler(log, messageConfig, driverSettings, messageHeader, this));
					ioSession.start();
					String ip = socket.getInetAddress().getHostAddress();
					String hostname = socket.getInetAddress().getHostName();
					if (ip.equalsIgnoreCase(hostname))
						connectedHost = ip;
					else
						connectedHost = String.format("%s (%s)", ip, hostname);
					notifyConnectDone(true);
				}
			}
		} catch (Exception ex) {
			log.error("Connect Error: " + ex.getMessage());

			if (ioSession != null) {
				ioSession.stop();
				ioSession = null;
			}
			notifyConnectDone(false);
		}
		log.debug("Connect finished");
	}

	protected void reconnect() {
		if (getDriverStateInternal() != DriverState.Connected) {
			return;
		}

		log.debug("Reconnecting...");

		boolean acquired = reconnectSemaphore.tryAcquire();

		if (acquired)
			try {
				disconnect();
				connect();
			} finally {
				reconnectSemaphore.release();
			}
	}

	public void disconnect() {

		if (getDriverStateInternal() == DriverState.Disconnected) {
			log.debug("disconnect() called, but DriverState is already disconnected.");
			return;
		}

		synchronized (getIoSessionLock()) {
			setDriverState(DriverState.Disconnecting);

			if (ioSession != null)
				ioSession.stop();
			ioSession = null;
		}

		// Socket.connect is run in a seperate thread (started by scheduleConnect)
		// By closing the socket here, connect is interrupted (will throw 'Socket operation on nonsocket: connect')
		// Without this, a device may connect right at the the driver is shutdown, the connect would then success
		// and a new IOSession would be created.
		synchronized (connectingSocketLock) {
			if (connectingSocket != null) {
				log.debug("Closing connecting Socket");
				try {
					connectingSocket.close();
				} catch (IOException e) {
				}
			}
		}

		getFolderManager().updateConnectionState(0, false);
		setDriverState(DriverState.Disconnected);
		
		log.debug("Disconnect done");
	}

	@Override
	public void writeToRemoteDevice(ByteBuffer message, int deviceId) {
		// Synchronize, to prevent a disconnect while writing
		synchronized (getIoSessionLock()) {
			if (getDriverStateInternal() == DriverState.Connected) {
				if (log.isTraceEnabled()) {
					log.trace("Sending message to device " + ByteUtilities.toString(message));
				}
				ioSession.write(message);
			}
		}
	}

	/**
	 * Called by the StatusFolder to update the driver status. Unchanged parameters may be null.
	 * 
	 * @param connectionEnabled
	 * @param hostname
	 * @param port
	 */
	public void updateStatus(Boolean enableConnection, String newHostname, Integer newPort) {
		if (newHostname != null)
			driverSettings.setHostname(newHostname);
		if (newPort != null)
			driverSettings.setPort(newPort);
		if (enableConnection != null) {
			if (!connectionEnabled && enableConnection) {
				connectionEnabled = true;
				if ( getRedundancyManager().isActive() )
					scheduleConnect(true);
			}
			if (this.connectionEnabled && !enableConnection) {
				connectionEnabled = false;
				disconnect();
			}
		}

		StatusUpdateMessage stateObj = getStatusUpdate();
		getRedundancyManager().getRuntimeStateManager().postRuntimeUpdate(getId(), stateObj);
	}

	private void initSettings(GenericTcpClientDriverSettings settings) {

		try {
			messageConfig = settings.getParsedMessageConfig();
		} catch (Exception e) {
			messageConfig = null;
			log.error(e.getMessage());
		}

		try {
			writebackConfig = settings.getParsedWritebackConfig();
		} catch (Exception e) {
			writebackConfig = null;
			log.error(e.getMessage());
		}

		try {
			headerConfig = settings.getParsedHeaderConfig();
		} catch (Exception e) {
			headerConfig = null;
			log.error(e.getMessage());
		}

		driverSettings = settings.getDriverSettings();
	}

	public String getHostname() {
		return driverSettings.getHostname();
	}

	public int getPort() {
		return driverSettings.getPort();
	}

	public boolean isConnectionEnabled() {
		return connectionEnabled;
	}

	public ByteOrder getDriverByteOrder() {
		return driverSettings.getByteOrder();
	}

	public int getTimestampFactor() {
		return driverSettings.getTimestampFactor();
	}

	public OptionalDataType getMessageIdType() {
		return driverSettings.getMessageIdType();
	}

	public String getConnectedHost() {
		if (getDriverStateInternal() == DriverState.Connected)
			return connectedHost;
		else
			return "";
	}

	@Override
	public String getLoggerName() {
		return String.format("%s[%s]", LOGGER_NAME, getDeviceName() );
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// IMessageHandler
	
	@Override
	public void messageReceived(InetSocketAddress remoteSocket, int messageId, byte[] messageData, byte[] handshakeMessage) {
		IndexMessageFolder messageFolder = (IndexMessageFolder) getMessageFolder(0, messageId);
		if (messageFolder != null) {
			messageFolder.messageArrived(messageData, handshakeMessage);
		} else {
			log.error(String.format("MessageHandler received unknown message ID %d.", messageId));
		}
	}
	
	@Override
	public boolean clientConnected(InetSocketAddress remoteSocket) {
		// This method is used only for server sockets. Simply do nothing here
		return false;
	}
	
	@Override
	public void clientDisconnected(InetSocketAddress remoteSocket) {
		log.error(String.format("Connection to %s at port %d lost.", driverSettings.getHostname(), driverSettings.getPort()));
		setDriverState(DriverState.Disconnected);
		getFolderManager().updateConnectionState(0, false);
		scheduleConnect(false);
	}	
	
	@Override
	public void clientReadTimeout(InetSocketAddress remoteSocket) {
		getExecutionManager().executeOnce(new Runnable() {
			@Override
			public void run() {
				log.warn("Reconnecting due to socket read timeout.");
				reconnect();
			}
		});
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Delete ununsed queue filed from the driver directory.
	 * 
	 * @param idList
	 * 	List of valid message id's whose queue files should not be deleted
	 */
	private void cleanupQueues(final List<Integer> idList) {
		GatewayUtils.clearDirectory(new File(getDiskPath()), new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				log.debug(String.format("file: %s", pathname.getName()));
				if(pathname.getName().startsWith(IndexMessageFolder.QUEUE_FILE_PREFIX)) {
					int id = Integer.parseInt(pathname.getName().substring(
						IndexMessageFolder.QUEUE_FILE_PREFIX.length(),
						pathname.getName().lastIndexOf('.')));
					if(!idList.contains(id)) {
						log.debug(String.format("Deleting unused queue file '%s'", pathname.toString()));
						return true;
					}
				}
				return false;
			}
		});
	}

	@Override
	protected void activityLevelChanged(ActivityLevel currentLevel, ActivityLevel newLevel) {
		if (newLevel != ActivityLevel.Active) {
			// We are now the backup node. Disconnect, so the new master can connect
			disconnect();
		} else {
			scheduleConnect(true);
		}
		
		getFolderManager().updateActivityLevel(newLevel == ActivityLevel.Active);
	}
	
	@Override
	protected StatusUpdateMessage getStatusUpdate() {
		return new StatusUpdateMessage(connectionEnabled, driverSettings.getHostname(), driverSettings.getPort());
	}
	
	@Override
	protected void setStatusUpdate(StatusUpdateMessage statusUpdate) {
		boolean setConnectionEnabled = statusUpdate.isConnectionEnabled();
		String hostname = statusUpdate.getHostname();
		int port = statusUpdate.getPort();
		
		if (log.isDebugEnabled()) {
			log.debug(String.format("Received runtime state update. connectionEnabled: %s, hostname: %s, port: %d",
					setConnectionEnabled, hostname, port));
		}
		
		driverSettings.setHostname(hostname);
		driverSettings.setPort(port);
		connectionEnabled=setConnectionEnabled;
		
		statusFolder.updateStatus(setConnectionEnabled, hostname, port);
	}
}
