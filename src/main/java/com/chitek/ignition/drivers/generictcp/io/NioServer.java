package com.chitek.ignition.drivers.generictcp.io;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface NioServer {
	
	public void start();
	
	public void stop();
	
	public void setEventHandler(IIoEventHandler eventHandler);
	
	public void setTimeout(long timeout);
	
	public void write(InetSocketAddress remoteSocketAddress, ByteBuffer data);
	
	public int  getConnectedClientCount();
}
