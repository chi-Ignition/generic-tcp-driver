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
import com.inductiveautomation.opcua.types.DataValue;
import com.inductiveautomation.opcua.types.StatusCode;
import com.inductiveautomation.opcua.types.UtcTime;
import com.inductiveautomation.opcua.types.Variant;

/**
 * A readable tag for Boolean Arrays. In difference to other ArrayTags, Boolean tags have a
 * second array dimension. The Ignition API does not support 2-dimensional arrays, so here the
 * value of the top-level tag is an big array with all the child values.<br />
 * For example, a tag with DataType Bool8 and size 3 will have the following structure:
 * <pre>
 *ReadableBoolArrayTag - Value {x0.0, x0.1, ..., x3.7}
 *	ReadableArrayTag - Value {x0.0, x0.1, ..., x0.7}
 *		ReadableTag  - Value x0.0
 *		ReadableTag  - Value x0.1
 *		...
 *		ReadableTag  - Value x0.7
 *	ReadableArrayTag - Value {x1.0, x1.1, ..., x1.7}
 *		ReadableTag  - Value x1.0
 *		...
 *	...
 * </pre>
 * 
 * @author chi
 *
 */
public class ReadableBoolArrayTag extends ReadableArrayTag {
	

	public ReadableBoolArrayTag(String address, int id, String alias, BinaryDataType dataType, int arrayLength)
	{
		super(address, id, alias, -1, dataType, arrayLength);
		
		// The 1st level tag of a 2-dimensional array contains all values in one big array
		this.readSize = arrayLength;
		this.valueArrayLength = arrayLength * dataType.getArrayLength();
	}
	
	@Override
	public void setValue(Variant newValue, StatusCode statusCode, UtcTime timestamp) {
		
		if (newValue.getArrayLength() != this.valueArrayLength) {
			throw new IllegalArgumentException(
				String.format("SetValue in ReadableBoolArray '%s' expects an Variant with array size %d. Argument has array size %d."
					,getAddress(), this.valueArrayLength, newValue.getArrayLength() ));
		}
		this.value = new DataValue(newValue, statusCode, timestamp, timestamp);
		
		Object[] value = (Object[]) newValue.getValue();
		
		// Set values for childs
		for (int i = 0; i < childCount; i++) {

			// Set the correct array dimensions for the Variant
			int childArraySize = childTags[i].getValueArrayLength();

			// Special treatment for BOOLEAN
			// Ignition API does not support multi-dimensional Variants, so for Boolean tags
			// with an array length > 1, the top level tag contains all Booleans in one big array
			Boolean[] rawValue = new Boolean[childArraySize];
			System.arraycopy(value, i * childArraySize, rawValue, 0, childArraySize);

			Variant childValue = new Variant(rawValue);
			childTags[i].setValue(childValue, statusCode, timestamp);
		}
	}
	
}
