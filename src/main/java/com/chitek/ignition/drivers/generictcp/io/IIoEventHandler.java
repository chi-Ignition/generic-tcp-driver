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

public interface IIoEventHandler {

	/**
	 * Called when a remote client connects.
	 * 
	 * @param remoteAddress
	 * @return
	 * 	<code>true</code> to accept the connection.
	 */
	public abstract boolean clientConnected(InetSocketAddress remoteAddress);

	/**
	 * Called when a remote client closes the connection.
	 * 
	 * @param remoteAddress
	 */
	public abstract void connectionLost(InetSocketAddress remoteAddress);

	public abstract void dataArrived(InetSocketAddress remoteAddress, ByteBuffer data, int bytesRead);
}
