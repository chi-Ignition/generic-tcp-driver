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

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.stack.core.BuiltinDataType;

import com.google.common.collect.ImmutableSet;
import com.inductiveautomation.xopc.driver.api.tags.DynamicDriverTag;
import com.inductiveautomation.xopc.driver.api.tags.WritableDriverTag;

public abstract class WritableTag extends DynamicDriverTag
		implements WritableDriverTag {

	public WritableTag(String address, BuiltinDataType dataType)
	{
		super(address, dataType);
	}

	public ImmutableSet<AccessLevel> getAccessLevel() {
		return (AccessLevel.READ_WRITE);
	}
	
}
