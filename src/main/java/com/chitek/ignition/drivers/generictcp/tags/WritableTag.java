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

import java.util.EnumSet;

import com.inductiveautomation.opcua.types.AccessLevel;
import com.inductiveautomation.opcua.types.DataType;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;
import com.inductiveautomation.xopc.driver.api.tags.WritableDriverTag;

public abstract class WritableTag extends DynamicDriverTag
		implements WritableDriverTag {

	public WritableTag(String address, DataType dataType)
	{
		super(address, dataType);
	}

	public EnumSet<AccessLevel> getAccessLevel() {
		return EnumSet.of(AccessLevel.CurrentRead, AccessLevel.CurrentWrite);
	}
	
}
