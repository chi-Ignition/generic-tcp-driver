/*******************************************************************************
 * Copyright 2012-2019 C. Hiesserich
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
package com.chitek.ignition.drivers.generictcp.tags;

import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import com.chitek.ignition.drivers.generictcp.types.BinaryDataType;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;

/**
 * A simple tag that is only readable.
 * 
 * @author chi
 *
 */
public class ReadableTcpDriverTag extends DynamicDriverTag {

	protected BinaryDataType driverDataType;
	protected DataValue value;
	private final String alias;
	private final int id;
	private final int index;
	protected UaVariableNode uaNode;
	protected int readSize;
	
	protected static final DataValue initialValue = new DataValue(StatusCode.BAD);

	public ReadableTcpDriverTag(String address, int id, String alias, BinaryDataType dataType)
	{
		super(address, dataType.getUADataType());
		this.id = id;
		this.alias = alias;
		this.index = -1;
		this.driverDataType = dataType;
		this.value = initialValue;
	}
	
	/**
	 * Constructor for indexed tags. The index is added to browseName and displayName
	 * 
	 * @param address
	 * @param id
	 * @param alias
	 * @param index
	 * @param dataType
	 */
	public ReadableTcpDriverTag(String address, int id, String alias, int index, BinaryDataType dataType)
	{
		super(address, dataType.getUADataType());
		this.id = id;
		this.alias = alias;
		this.index = index;
		this.driverDataType = dataType;
		this.value = initialValue;
	}	

	public DataValue getValue() {
		return value;
	}

	public void setValue(StatusCode statusCode) {
		this.value = new DataValue(statusCode);
	}
	
	public void setValue(long statusCode) {
		this.value = new DataValue(statusCode);
	}
	
	public void setValue(Variant newValue, DateTime timestamp) {
		setValue(newValue, StatusCode.GOOD, timestamp);
	}

	public void setValue(Variant newValue, long statusCode, DateTime timestamp) {
		this.value = new DataValue(newValue, new StatusCode(statusCode), timestamp,	this.value.getServerTime());
	}
	
	public void setValue(Variant newValue, StatusCode statusCode, DateTime timestamp) {
		this.value = new DataValue(newValue, statusCode, timestamp,	this.value.getServerTime());
	}

	/**
	 * Sets the value of the corresponding uaNode
	 */
	public void setUaNodeValue() {
		if (uaNode != null)
			uaNode.setValue(value);
	}

	public void setUaNode(UaVariableNode node) {
		this.uaNode = node;
	}

	public UaVariableNode getUaNode() {
		return uaNode;
	}

	public BinaryDataType getDriverDataType() {
		return driverDataType;
	}

	public String getDisplayName() {
		if (index > -1)
			return String.format("%s_%02d", alias, index);
		else
			return alias;
	}
	
	public String getBrowseName() {
		if (index > -1)
			return String.format("%s[%02d]", alias, index);
		else
			return alias;		
	}
	
	public int getId() {
		return id;
	}
	
	/**
	 * Returns the length of the array length of this tags OPC Value.
	 * 
	 * @return
	 * 		This values array length, or -1 if this tags value is scalar.<br />
	 * 		
	 */
	public int getValueArrayLength() {
		return -1;
	}
	
	/**
	 * Returns the count of data to read from network. 
	 * 
	 * @return
	 */
	public int getReadSize() {
		return 1;
	}

}
