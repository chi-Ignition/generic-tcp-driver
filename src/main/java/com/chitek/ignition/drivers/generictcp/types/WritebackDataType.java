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


public enum WritebackDataType implements Serializable {
	UInt16(2, BuiltinDataType.UInt16),
	Int16(2, BuiltinDataType.Int16),
	UInt32(4,BuiltinDataType.UInt32),
	Int32(4, BuiltinDataType.Int32),
	ByteString(0, BuiltinDataType.String);
	
	private int byteSize;
	private BuiltinDataType uaDataType;
	
	private WritebackDataType(int byteSize, BuiltinDataType dataType) {
		this.byteSize = byteSize;
		this.uaDataType = dataType;
	}
	
	public int getByteSize() {
		return byteSize;
	}

	public BuiltinDataType getUADataType() {
		return uaDataType;
	}
	
	/**
	 * List with the options to use in a DropDownChoice
	 * 
	 * @return
	 */
	public static List<WritebackDataType> getOptions() {
		return Arrays.asList(values());
	}
}
