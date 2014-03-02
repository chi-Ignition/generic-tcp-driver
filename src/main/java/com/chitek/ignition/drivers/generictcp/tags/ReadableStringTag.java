/*******************************************************************************
 * Copyright 2012-2013 C. Hiesserich
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

import com.chitek.ignition.drivers.generictcp.types.BinaryDataType;

public class ReadableStringTag extends ReadableTcpDriverTag {

	private int stringLength;
	
	public ReadableStringTag(String address, int id, String alias, BinaryDataType dataType, int stringLength) {
		super(address, id, alias, dataType);
		this.stringLength = stringLength;
	}

	/**
	 * Returns the count of data to read from network. 
	 * 
	 * @return
	 */
	public int getReadSize() {
		return stringLength;
	}
	
}
