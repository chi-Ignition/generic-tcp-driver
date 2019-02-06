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
package com.chitek.ignition.drivers.generictcp.types;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.eclipse.milo.opcua.stack.core.BuiltinDataType;

public enum BinaryDataType implements Serializable {
	
	Dummy("Dummy",1,1,BuiltinDataType.Byte, true, -1, true, true),
	UByte("Unsigned Byte",1,1, BuiltinDataType.Byte),
	Byte("Signed Byte",1,1, BuiltinDataType.SByte),
	Bool8("Boolean 8Bit", 1, 8, BuiltinDataType.Boolean),
	Bool16("Boolean 16Bit", 2, 16, BuiltinDataType.Boolean),
	UInt16("Unsigned Int 16Bit", 2, 1, BuiltinDataType.UInt16),
	Int16("Integer 16Bit", 2, 1, BuiltinDataType.Int16),
	UInt32("Unsigned Int 32Bit", 4, 1, BuiltinDataType.UInt32),
	Int32("Integer 32Bit", 4, 1, BuiltinDataType.Int32),
	Float("Float", 4, 1,BuiltinDataType.Float),
	String("String", 1, 1, BuiltinDataType.String),
	RawString("RawString", 1, 1, BuiltinDataType.String),
	MessageAge("Message Age", 4, 1, BuiltinDataType.UInt32, true, 0, false, false);
	
	private String displayString;
	private int byteCount;
	private int arrayLength;
	private BuiltinDataType dataType;
	private boolean special;
	private int specialId;		// Id's <=0 are reserved for special tags
	private boolean hidden;		// Hidden items will not be added as OPC-Nodes
	private boolean arrayAllowed;
	
	private BinaryDataType(String displayString, int byteSize, int arraySize
		, BuiltinDataType dataType) {
		
		this(displayString, byteSize, arraySize, dataType, false, 0, false, true);
	}

	private BinaryDataType(String displayString, int byteSize, int arraySize
		, BuiltinDataType dataType, boolean special, int specialId, boolean hidden, boolean arrayAllowed) {
		
		this.displayString = displayString;
		this.byteCount = byteSize;
		this.arrayLength = arraySize;
		this.dataType = dataType;
		this.special = special;
		this.specialId = specialId;
		this.hidden = hidden;
		this.arrayAllowed = arrayAllowed;
	}
	
	public String getDisplayString() {
		return displayString;
	}

	public int getByteCount() {
		return byteCount;
	}
	
	public int getArrayLength() {
		return arrayLength;
	}

	/**
	 * Special Items do not allow configuration
	 * @return
	 */
	public boolean isSpecial() {
		return special;
	}
	
	/**
	 * Hidden items will not be added as OPC-Nodes and are not accessible by clients.
	 * @return
	 * 		True, if the itme is hidden
	 */
	public boolean isHidden() {
		return hidden;
	}
	
	/**
	 * @return
	 * 		True if this DataType can be configured as an array (size > 1)
	 */
	public boolean isArrayAllowed() {
		return arrayAllowed;
	}
	
	public boolean isString() {
		return this==BinaryDataType.String || this==BinaryDataType.RawString;
	}
	
	public int getSpecialId() {
		return specialId;
	}
	
	public BuiltinDataType getUADataType() {
		return dataType;
	}
	
	/**
	 * Returns <code>true</code> if the given BinaryDataType supports variable length.
	 */
	public boolean supportsVariableLength() {
		return this == BinaryDataType.Dummy || this == BinaryDataType.String || this == BinaryDataType.RawString;
	}
	
	public static List<BinaryDataType> getOptions() {
		return Arrays.asList(BinaryDataType.values());
	}
	
}
