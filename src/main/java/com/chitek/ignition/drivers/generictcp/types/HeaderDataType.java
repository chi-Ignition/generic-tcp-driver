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
package com.chitek.ignition.drivers.generictcp.types;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data types to be used in the message header.
 * 
 * 
 * @author chi
 *
 */
public enum HeaderDataType implements Serializable {
	
	Dummy(1, false, false, true),
	Byte(1, false, true, false),
	Word(2, false, true, false),
	PacketSize(2, true, false, false),
	Timestamp(4, true, false, false),
	SequenceId(2, true, false, false);
	
	private int byteCount;
	private boolean special;
	private boolean hasValue;
	private boolean arrayAllowed;
	
	private HeaderDataType(int byteSize, boolean special, boolean hasValue, boolean arrayAllowed) {
		
		this.byteCount = byteSize;
		this.special = special;
		this.hasValue = hasValue;
		this.arrayAllowed = arrayAllowed;
	}
	
	public int getByteCount() {
		return byteCount;
	}
	
	/**
	 * Special Items are alowed only once in a config
	 * @return
	 */
	public boolean isSpecial() {
		return special;
	}
	
	/**
	 * 
	 * @return
	 * 		True if this type uses a value. All tags that use a value are treated as fixed values, that have to be present
	 * 		in the header with the defined value. 
	 */
	public boolean isHasValue() {
		return hasValue;
	}
	
	/**
	 * @return
	 * 		True if this type can be configured as an array (size > 1)
	 */
	public boolean isArrayAllowed() {
		return arrayAllowed;
	}
	
	public static List<HeaderDataType> getOptions() {
		return Arrays.asList(HeaderDataType.values());
	}
	
	public static String[] getSpecialItemNames() {
		List<String> result=new ArrayList<String>();
		for (HeaderDataType value:HeaderDataType.values()) {
			if (value.isSpecial())
				result.add(value.name());
		}
		
		return result.toArray(new String[0]);
	}
	
}
