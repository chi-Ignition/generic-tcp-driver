package com.chitek.ignition.drivers.generictcp.tests.folders;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import com.inductiveautomation.xopc.driver.api.items.WriteItem;

public class MockWriteItem implements WriteItem {

	private String address;
	private Object addressObject;
	private Variant value;
	private StatusCode writeStatus;
	
	public MockWriteItem(String address, Variant value) {
		this.address = address;
		this.value = value;
	}
	
	@Override
	public String getAddress() {
		return address;
	}

	@Override
	public Object getAddressObject() {
		return addressObject;
	}

	@Override
	public NodeId getNodeId() {
		return null;
	}

	@Override
	public void setAddressObject(Object obj) {
		addressObject = obj;
	}

	@Override
	public Variant getWriteValue() {
		return value;
	}

	@Override
	public void setWriteStatus(StatusCode status) {
		writeStatus = status;
	}

	public StatusCode getWriteStatus() {
		return writeStatus;
	}
}
