package com.chitek.ignition.drivers.generictcp.tests.folders;

import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.NodeId;
import com.inductiveautomation.xopc.driver.api.items.ReadItem;

public class MockReadItem implements ReadItem {

	private Object addressObject;
	private DataValue value;
	private final String address;

	public MockReadItem(String address) {
		this.address = address;
	}

	@Override
	public NodeId getNodeId() {
		throw new RuntimeException("getNodeId not implemented");
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
	public void setAddressObject(Object paramObject) {
		this.addressObject = paramObject;
	}

	@Override
	public void setValue(DataValue paramDataValue) {
		this.value = paramDataValue;
	}

	public DataValue getValue() {
		return value;
	}

}
