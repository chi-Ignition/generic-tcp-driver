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

import java.util.Arrays;
import java.util.List;

import com.chitek.ignition.drivers.generictcp.ModuleHook;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.opcua.types.DataType;

public enum OptionalDataType {
	None, UByte, UInt16;

	public String toLocalString() {
		return BundleUtil.get().getStringLenient(ModuleHook.BUNDLE_PREFIX + "MessageLength." + this.name());
	}

	/**
	 * List with the options to use in a DropDownChoice
	 * 
	 * @return
	 */
	public static List<OptionalDataType> getOptions() {
		return Arrays.asList(values());
	}

	public int getByteSize() {
		switch (this) {
		case None:
			return 0;
		case UByte:
			return 1;
		default:
			return 2;
		}
	}

	public DataType getUADataType() {
		switch (this) {
		case None:
		case UByte:
			return DataType.UByte;
		case UInt16:
			return DataType.UInt16;
		}
		return null;
	}

}
