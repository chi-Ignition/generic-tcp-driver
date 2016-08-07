package com.chitek.ignition.drivers.generictcp.tests.io;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.io.TimeoutHandler;

public class TestTimeoutHandler {
	
	@Test
	public void testTimeout() throws Exception {
		TimeoutHandler handler = new TimeoutHandler(10);
		
		assertEquals(10, handler.getTimeToTimeout());
		assertEquals(null, handler.getTimeoutAddress());
	
		InetSocketAddress addr1 = new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 192,(byte) 168,0,1}),9999);
		InetSocketAddress addr2 = new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 192,(byte) 168,0,2}),9998);
		
		handler.dataReceived(addr1);
		assertThat(handler.getTimeToTimeout(), is(greaterThanOrEqualTo(9L)));
		assertEquals(addr1, handler.getTimeoutAddress());
		assertFalse(handler.isTimeoutExpired());
		
		handler.dataReceived(addr2);
		assertEquals(addr1, handler.getTimeoutAddress());
		
		handler.removeAddress(addr1);
		assertEquals(addr2, handler.getTimeoutAddress());
		
		long timeout=handler.getTimeToTimeout();
		assertThat(timeout, is(lessThanOrEqualTo(10L)));
		
		Thread.sleep(5);
		assertThat(handler.getTimeToTimeout(), is(lessThanOrEqualTo(timeout-4L)));
		
		Thread.sleep(5);
		assertTrue(handler.isTimeoutExpired());
	}
	
}
