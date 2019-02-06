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

import org.eclipse.milo.opcua.stack.core.util.ArrayUtil;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import com.chitek.ignition.drivers.generictcp.types.BinaryDataType;
import com.chitek.ignition.drivers.generictcp.util.Util;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

/**
 * A readable tag with an array as its value. The child tags contain the individual array elements.
 * 
 * @author chi
 *
 */
public class ReadableArrayTag extends ReadableTcpDriverTag {
	
	ReadableTcpDriverTag[] childTags;
	ReadableTcpDriverTag childRaw;
	protected int valueArrayLength = 0;
	protected int childCount = 0;
	
	public ReadableArrayTag(String address, int id, String alias, int index, BinaryDataType dataType, int arrayLength)
	{
		super(address, id, alias, index, dataType);
		this.value = initialValue;
		this.childTags = new ReadableTcpDriverTag[arrayLength];
		this.readSize = arrayLength / dataType.getArrayLength();
		// For 1-dimensional arrays, the values array length is the same as childCount
		this.valueArrayLength = arrayLength;
	}
	
	@Override
	public void setValue(Variant newValue, StatusCode statusCode, DateTime timestamp) {
		
		if (newValue.getValue().getClass().isArray() && ArrayUtil.getDimensions(newValue.getValue())[0] != valueArrayLength) {
			throw new IllegalArgumentException(
				String.format("SetValue in ReadableArray '%s' expects an Variant with array size %d. Argument has array size %d."
					,getAddress(), valueArrayLength, ArrayUtil.getDimensions(newValue.getValue())[0] ));
		}
		this.value = new DataValue(newValue, statusCode, timestamp, timestamp);
		
		Object[] value = (Object[]) newValue.getValue();
		
		// Set values for childs
		// A simple for loop is used here, because it performs better with arrays than a (for x : childTags)
		int rawValue=0;
		for (int i = 0; i < childCount; i++) {
			Variant childValue = Util.makeVariant(value[i], this.getDataType());
			childTags[i].setValue(childValue, statusCode, timestamp);
			if ( childRaw!=null && (Boolean)value[i]) rawValue += 1<<i;
		}
		
		if (childRaw != null)
			childRaw.setValue(new Variant(ushort(rawValue) ), statusCode, timestamp);
	}
	
	@Override
	public void setValue(StatusCode statusCode) {
		this.value = new DataValue(statusCode);
		for (int i = 0; i < childCount; i++) {
			childTags[i].setValue(statusCode);
		}
		if (childRaw != null)
			childRaw.setValue(statusCode);
	}
	
	@Override
	public void setUaNodeValue() {
		if (uaNode != null)
			uaNode.setValue(value);
		
		for (int i = 0; i < childCount; i++) {
			if (childTags[i].uaNode != null)
				childTags[i].setUaNodeValue();
		}
		
		if (childRaw != null && childRaw.uaNode != null)
			childRaw.setUaNodeValue();
	}
	
	public void addChild(ReadableTcpDriverTag childTag) {
		childTags[childCount++] = childTag;
	}
	
	/**
	 * Adds an additional child to contain the raw value for boolean arrays.
	 * 
	 * @param childTag
	 */
	public void addChildRaw(ReadableTcpDriverTag childTag) {
		childRaw = childTag;
	}
	
	@Override
	public int getValueArrayLength() {
		return valueArrayLength;
	}
	
	@Override
	public int getReadSize() {
		return readSize;
	}
}
