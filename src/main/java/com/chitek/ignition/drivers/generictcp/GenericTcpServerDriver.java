package com.chitek.ignition.drivers.generictcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.chitek.ignition.drivers.generictcp.configuration.settings.GenericTcpServerDriverSettings;
import com.chitek.ignition.drivers.generictcp.folder.DeviceStatusFolder;
import com.chitek.ignition.drivers.generictcp.folder.FolderManager;
import com.chitek.ignition.drivers.generictcp.folder.IndexMessageFolder;
import com.chitek.ignition.drivers.generictcp.folder.MessageHeader;
import com.chitek.ignition.drivers.generictcp.folder.SimpleWriteFolder;
import com.chitek.ignition.drivers.generictcp.io.IMessageHandler;
import com.chitek.ignition.drivers.generictcp.io.NioEventHandler;
import com.chitek.ignition.drivers.generictcp.io.NioServer;
import com.chitek.ignition.drivers.generictcp.io.NioTcpServer;
import com.chitek.ignition.drivers.generictcp.io.NioUdpServer;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverSettingsPassive;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.redundancy.StatusUpdateMessage;
import com.chitek.ignition.drivers.generictcp.types.DriverState;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.RemoteDevice;
import com.chitek.ignition.drivers.generictcp.types.QueueMode;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.redundancy.types.ActivityLevel;
import com.inductiveautomation.xopc.driver.api.DriverContext;
import com.inductiveautomation.xopc.driver.util.ByteUtilities;

public class GenericTcpServerDriver extends AbstractGenericTcpDriver
implements IMessageHandler {

	public static final String LOGGER_NAME = "TcpServerDriver";

	// Settings
	private DriverConfig messageConfig;
	private DriverSettingsPassive driverSettings;
	private WritebackConfig writebackConfig;
	private HeaderConfig headerConfig;

	private MessageHeader messageHeader;

	private NioServer nioServer;

	private final Map<InetAddress, Integer>deviceAddressIdMap = new HashMap<InetAddress, Integer>();
	private final Map<Integer, RemoteDevice>deviceMap = new HashMap<Integer, RemoteDevice>();

	public GenericTcpServerDriver(DriverContext driverContext, GenericTcpServerDriverSettings deviceSettings) {
		super(driverContext);

		initSettings(deviceSettings);
		initialize(deviceSettings);
	}

	private void initSettings(GenericTcpServerDriverSettings settings) {

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

	private void initialize(GenericTcpServerDriverSettings deviceSettings) {

		// There is no configuration, if the user never clicked 'save' in the config page
		if (messageConfig == null || messageConfig.messages.size() == 0) {
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

		// Check remote devices
		List<RemoteDevice> remoteDevices = driverSettings.getDevices();
		if (remoteDevices.isEmpty()) {
			setDriverState(DriverState.ConfigError);
			log.error("Driver could not be initialized - No remote devices configured in driver settings.");
			return;
		}

		initializeMessageFolders(driverSettings.getDevices());

		super.initialize();
		
		// Initialize Server Socket
		if (isActiveNode() || getActivityLevel().isWarm()) {
			connect();
		} else {
			log.info("Node Redundancy State is neither active nor warm. Starting with server disabled.");
		}
	}

	private List<Integer> initializeMessageFolders(List<RemoteDevice> devices) {
		// Keep track of folders with handshake to cleanup unused queues
		final List<Integer>idWithHandshake = new ArrayList<Integer>(messageConfig.messages.size());

		int deviceId = 0;
		for (RemoteDevice device : devices) {
			log.debug(String.format("Adding folders for device %s - %s", device.getHostname(), device.getAlias()));

			// Assign an id to the device
			device.setDeviceId(deviceId);
			deviceMap.put(deviceId, device);

			// Create the device folder
			DeviceStatusFolder statusFolder = new DeviceStatusFolder(this, deviceId, device.getAlias());
			addFolder(statusFolder);

			// Add all known message tags to the node map
			List<String> alias = new ArrayList<String>(messageConfig.messages.size());
			for (Map.Entry<Integer, MessageConfig> configEntry : messageConfig.messages.entrySet()) {
				MessageConfig message = configEntry.getValue();
				if (message.tags.size() == 0) {
					log.warn(String.format("No tags configured in message ID%s.", message.getMessageId()));
				} else if (getMessageFolder(deviceId, message.getMessageId()) != null) {
					log.warn(String.format("Configuration error. Duplicate message ID%s.", message.getMessageId()));
				} else if (alias.contains(message.getMessageAlias())) {
					log.warn(String.format("Configuration error. Duplicate message alias '%s'.",
						message.getMessageAlias()));
				} else if (messageConfig.getMessageIdType() == OptionalDataType.None && message.getMessageId() > 0) {
					log.warn(String.format("MessageIDType is 'None'. Message ID %d is ignored.",
						message.getMessageId()));
				} else if (messageConfig.getMessageIdType() == OptionalDataType.UByte
					&& message.getMessageId() > 255)  {
					log.warn(String.format("MessageIDType is 'Byte'. Message ID %d is ignored.",
						message.getMessageId()));
				} else {
					IndexMessageFolder messageFolder = 
							new IndexMessageFolder(
									message,
									driverSettings,
									FolderManager.getFolderId(device.getDeviceId(), message.getMessageId()),
									String.format("%s/%s", device.getAlias(), message.getMessageAlias()),
									this);
					addFolder(messageFolder);
					alias.add(message.getMessageAlias());
					if (message.getQueueMode() != QueueMode.NONE) {
						idWithHandshake.add(FolderManager.getFolderId(device.getDeviceId(), message.getMessageId()));
					}
				}
			}

			// Add the simple write folder
			// There is no configuration, if the user never clicked 'save' in the writeback config
			if (writebackConfig != null && writebackConfig.isEnabled()) {
				SimpleWriteFolder simpleWriteFolder = new SimpleWriteFolder(this, driverSettings, deviceId, device.getAlias(), writebackConfig);
				addFolder(simpleWriteFolder);
			}
			
			getFolderManager().updateConnectionState(deviceId, false);
			
			deviceId++;
		}

		// Add the header
		if (headerConfig != null  && headerConfig.isUseHeader()) {
			messageHeader = new MessageHeader(headerConfig, driverSettings.getByteOrder(), log);
			log.debug(String.format("Driver is configured to use a message header. Expected header length: %d bytes", messageHeader.getHeaderLength()));
		}

		// Update state for all message folders
		getFolderManager().updateActivityLevel(isActiveNode());

		return idWithHandshake;
	}

	@Override
	public void shutdown() {
		log.debug("Shutdown start");

		disconnect();

		super.shutdown();

		setDriverState(DriverState.Terminated);
		log.debug("Shutdown finished");
	}

	private void scheduleConnect() {
		if (getActivityLevel().isWarm() && getDriverStateInternal() != DriverState.Terminated) {

			log.debug("New connection schedule started.");
			getExecutionManager().executeOnce(new Runnable() {
				@Override
				public void run() {
					connect();
				}
			}, 20);
		} 
	}
	
	private void connect() {
		
		if (getDriverStateInternal() == DriverState.Connecting || nioServer != null) {
			log.debug("connect() called, but server is already started.");
			return;
		}
		
		setDriverState(DriverState.Connecting);
		log.debug(String.format("Creating server socket"));

		try {
			synchronized (getIoSessionLock()) {
				InetSocketAddress isa;
				if (driverSettings.getServerAddress() == null || driverSettings.getServerAddress().isEmpty())
					isa = new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), driverSettings.getServerPort());
				else
					isa = new InetSocketAddress(driverSettings.getServerAddress(), driverSettings.getServerPort());
				
				if (driverSettings.getUseUdp()) {
					nioServer = new NioUdpServer(isa, Logger.getLogger(log.getName() + "." + "NioServer"));
				} else {
					nioServer = new NioTcpServer(isa, Logger.getLogger(log.getName() + "." + "NioServer"));
				}

				nioServer.setEventHandler(new NioEventHandler(log, getExecutionManager(), messageConfig, driverSettings, messageHeader, this));
				nioServer.start();
				notifyConnectDone(true);
			}
		} catch (IOException ex) {
			log.error("Error creating ServerSocket: " + ex.getMessage());
			notifyConnectDone(false);
		}
	}
	
	private void disconnect() {
		// Disconnect from device
		if (nioServer!=null)
			nioServer.stop();
		
		setDriverState(DriverState.Disconnected);
		nioServer = null;
	}

	public void notifyConnectDone(boolean success) {
		if (isShutdown()) {
			return;
		}

		if (success) {
			setDriverState(DriverState.Listening);
		} else {
			setDriverState(DriverState.Disconnected);
		}
	}

	@Override
	public void writeToRemoteDevice(ByteBuffer message, int deviceId) {
		// Synchronize, to prevent a disconnect while writing
		synchronized (getIoSessionLock()) {
			if (getDriverStateInternal() == DriverState.Listening) {
				RemoteDevice remoteDevice = deviceMap.get(deviceId);
				if (remoteDevice == null) {
					log.error(String.format("writeMessage called with unknown deviceId %d", deviceId));
					return;
				}
				if (remoteDevice.getRemoteSocketAddress() == null) {
					log.error(String.format("writeMessage called for not connected remote device %s(%s)",
						remoteDevice.getAlias(), remoteDevice.getHostname()));
					return;
				}
				if (log.isTraceEnabled()) {
					log.trace(String.format("Sending message to device %s():%s",
						remoteDevice.getAlias(), remoteDevice.getInetAddress(), ByteUtilities.toString(message)));
				}
				nioServer.write(remoteDevice.getRemoteSocketAddress(), message);
			}
		}
	}

	@Override
	public String getDriverStatus() {
		if (getDriverStateInternal() == DriverState.Listening) {
			int connectedClientCount = nioServer.getConnectedClientCount();
			if (connectedClientCount == 1)
				return BundleUtil.get().getStringLenient(ModuleHook.BUNDLE_PREFIX + ".State.passiveConnectedSingular");
			else if (connectedClientCount > 1)
				return BundleUtil.get().getStringLenient(ModuleHook.BUNDLE_PREFIX + ".State.passiveConnectedPlural", connectedClientCount);

		}
		return getDriverStateInternal().toLocalString();
	}

	//***********************************************************************************************************************
	// IMessageHandler

	@Override
	public void messageReceived(InetSocketAddress socket, int messageId, byte[] messageData, byte[] handshakeMessage) {

		Integer deviceId = deviceAddressIdMap.get(socket.getAddress());
		if (deviceId == null) {
			log.error(String.format("MessageHandler received message from unknown device %s.", socket.toString()));
			return;
		}
	
		if (log.isDebugEnabled()) {
			log.debug(String.format("MessageHandler received message id %d from device %d with %d bytes of data.", messageId, deviceId, messageData.length));
		}
		
		if (!isActiveNode()) {
			// Ignore message if this node is not active
			if (log.isDebugEnabled()) {
				log.debug(String.format("Received message ID %d ignored. This node is not active..", messageId));
			}
			return;
		}
		
		IndexMessageFolder messageFolder = (IndexMessageFolder) getMessageFolder(deviceId, messageId);
		if (messageFolder != null) {
			messageFolder.messageArrived(messageData, handshakeMessage);
		} else {
			log.error(String.format("MessageHandler received unknown message ID %d.", messageId));
		}
	}

	@Override
	public boolean clientConnected(InetSocketAddress remoteSocket) {

		if (log.isDebugEnabled()) {
			log.debug(String.format("Remote device %s(%s) connected.", remoteSocket.getHostName(), remoteSocket.getAddress().getHostAddress()));
		}

		// Try to find the connecting device in our configuration
		for (RemoteDevice device : driverSettings.getDevices()) {
			if (device.getHostname().equals(remoteSocket.getAddress().getHostAddress())
				|| device.getHostname().equalsIgnoreCase(remoteSocket.getHostName())) {

				processClientConnected(device, remoteSocket);
				return true;
			}
		}

		// If we arrive here, the connecting device was not found in the configuration
		// Returning false will close the connection
		log.warn(String.format("Remote device %s(%s) tried to connect but is not listed in the driver settings.", remoteSocket.getHostName(), remoteSocket.getAddress().getHostAddress()));
		return false;
	}

	@Override
	public void clientDisconnected(InetSocketAddress remoteSocket) {

		if (log.isDebugEnabled()) {
			log.debug(String.format("Remote device %s(%s) disconnected.", remoteSocket.getHostName(), remoteSocket.getAddress().getHostAddress()));
		}

		// Remove disconnected device from map
		Integer deviceId = deviceAddressIdMap.remove(remoteSocket.getAddress());
		if (deviceId == null) {
			log.error(String.format("MessageHandler called for disconnect from unknown device %s.", remoteSocket.toString()));
			return;
		}

		getFolderManager().updateConnectionState(deviceId, false);
	}
	
	@Override
	public void clientReadTimeout(InetSocketAddress remoteSocket) {
		// Only used for client sockets, so simply ignored here
	}

	private void processClientConnected(final RemoteDevice remoteDevice, final InetSocketAddress remoteSocket) {
		remoteDevice.setRemoteSocketAddress(remoteSocket);
		deviceAddressIdMap.put(remoteSocket.getAddress(), remoteDevice.getDeviceId());
		getFolderManager().updateConnectionState(remoteDevice.getDeviceId(), true);
	}

	//***********************************************************************************************************************

	@Override
	public String getLoggerName() {
		return String.format("%s[%s]", LOGGER_NAME, getDeviceName() );
	}
	
	@Override
	protected void activityLevelChanged(ActivityLevel currentLevel,	ActivityLevel newLevel) {
		if (newLevel.isWarm()) {
			log.info("ActivityLevel changed. Starting server.");
			scheduleConnect();
		}
		
		getFolderManager().updateActivityLevel(newLevel == ActivityLevel.Active);
	}
	
	@Override
	protected StatusUpdateMessage getStatusUpdate() {
		// The passive driver has no status
		return new StatusUpdateMessage(false, null, 0);
	}
	
	@Override
	protected void setStatusUpdate(StatusUpdateMessage statusUpdate) {
		// The passive driver has no status, so we can ignore the update
	}
}
