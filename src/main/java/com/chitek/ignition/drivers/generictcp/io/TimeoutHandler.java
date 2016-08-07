/*******************************************************************************
 * Copyright 2016 C. Hiesserich
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

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A helper class for handling timeouts with an NIO server socket.
 *
 */
public class TimeoutHandler {
	/** The timeout in milliseconds **/
	private long timeout=0;

	private long oldestTime=0;
	private SocketAddress oldestAddress = null;
	private Map<SocketAddress, Long> timeoutMap = new HashMap<SocketAddress, Long>();
	
	/**
	 * @param timeout
	 * 	The timeout in milliseconds. A value of 0 disables the timeout.
	 */
	public TimeoutHandler(long timeout) {
		this.timeout=timeout;
	}
	
	/**
	 * Reset the timeout for the given address (when data is received from that address).
	 * 
	 * @param address
	 * 	The InetAddress for which to reset the timeout
	 */
	public void dataReceived(SocketAddress address) {
		
		timeoutMap.put(address, System.nanoTime()/1000000);
				
		if (address.equals(oldestAddress) || oldestAddress==null) {
			// We need to update the oldest client
			updateTimeout();
		}
	}
	
	/**
	 * Remove the given address from the internal map (when the connection to that address is closed).
	 * 
	 * @param address
	 * 	The InetAddress to remove
	 */
	public void removeAddress(SocketAddress address) {
		timeoutMap.remove(address);
		if (timeoutMap.isEmpty()) {
			oldestAddress = null;
			return;
		}
		
		if (address.equals(oldestAddress)) {
			// We need to update the oldest client
			updateTimeout();
		}
	}
	
	/**
	 * Returns the time to the next timeout. If the time is already expired, this method returns 1 instead of 0, because
	 * 0 would disable the timeout when calling blocking socket methods.
	 * 
	 * @return
	 * 	Milliseconds until the next timeout, or 0 if the timeout is disabled.
	 */
	public long getTimeToTimeout() {
		if (oldestAddress==null || timeout==0) {
			return timeout;
		}
		
		long diff = System.nanoTime()/1000000-oldestTime;
		return Math.max(1, timeout-diff);
	}
	
	/**
	 * Test if the timeout is expired
	 * 
	 * @return
	 * 	<code>true</code> if the timeout is expired
	 */
	public boolean isTimeoutExpired() {
		if (oldestAddress==null || timeout==0) {
			return false;
		}
		
		long diff = System.nanoTime()/1000000-oldestTime;
		return (timeout-diff)<=0;
	}
	
	/**
	 * Return the InetAddress whose timeout is about to or has expired.
	 * 
	 * @return
	 * 	The InetAddress that caused a timeout.
	 */
	public SocketAddress getTimeoutAddress() {
		return oldestAddress;
	}
	
	private void updateTimeout() {
		long earliestTime=Long.MAX_VALUE;
		SocketAddress address=null;
		Iterator<Entry<SocketAddress, Long>> it = timeoutMap.entrySet().iterator();
		while (it.hasNext()) {
			Entry<SocketAddress, Long>entry = it.next();
			if (entry.getValue() < earliestTime) {
				earliestTime=entry.getValue();
				address=entry.getKey();
			}
		}
		
		oldestTime=earliestTime;
		oldestAddress=address;
	}
}
