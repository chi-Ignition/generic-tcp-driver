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
package com.chitek.ignition.drivers.generictcp.meta.config;

import java.io.Serializable;

import com.chitek.ignition.drivers.generictcp.types.HeaderDataType;

@SuppressWarnings("serial")
public class HeaderTagConfig implements Serializable {
	
	private static final String XML_CONFIG_NAME = "HeaderTagConfig";
	
	private  HeaderDataType dataType;
	
	/** The input is stored as a raw String to support hex input */
	private String rawValue;		
	
	/** The value for fixed value tags */
	private transient int value;
	
	/** The size if this tag is an array */
	private int size;
	
	/** Byte offset in message - has to be set by the using class*/
	private transient int offset; 
	
	public HeaderTagConfig() {
		this(HeaderDataType.Dummy, 0, 1);
	}
	
	public HeaderTagConfig(HeaderDataType dataType) {
		this(dataType, 0, 1);
	}
	
	public HeaderTagConfig(HeaderDataType dataType, int value, int size) {
		this.dataType = dataType;
		this.value = value;
		this.rawValue = String.valueOf(value);
		this.size = size;
	}
	
	public HeaderDataType getDataType() {
		return dataType;
	}
	
	/**
	 * Method used by XML-Parser
	 * *
	 * @param dataTypeName
	 */
	public void setDataType(String dataTypeName) {
		this.dataType = HeaderDataType.valueOf(dataTypeName);
	}
	
	/**
	 * 
	 * @return
	 * 	The raw input string
	 */
	public String getRawValue() {
		return rawValue != null ? rawValue : String.valueOf(value);
	}
	
	public void setRawValue(String rawValue) {
		this.rawValue = rawValue.trim().equalsIgnoreCase("null") ? "0" : rawValue.trim();
		try {
			this.value = Integer.decode(rawValue);
		} catch (NumberFormatException e) {
			this.value = 0;
		}
	}
	
	public int getValue() {
		return value;
	}
	
	/**
	 * 
	 * @return
	 * 		The array size or 1 for scalar tags.
	 */
	public int getSize() {
		if (dataType.isArrayAllowed() )
			return size;
		else
			return 1;
	}
	
	public void setSize(int size) {
		this.size = size;
	}
	
	/**
	 * The length of the data type in bytes.
	 * 
	 * @return
	 * 	The length in bytes
	 */
	public int getByteCount() {
		return dataType.getByteCount();
	}
	
	/**
	 * 
	 * @return The byte offset of this tag. Has to be set using {@link #setOffset}
	 */
	public int getOffset() {
		return offset;
	}
	
	/**
	 * Set the byte offest of this tag. Only used for display in the list editor
	 * 
	 * @param offset
	 */
	public void setOffset(int offset) {
		this.offset = offset;
	}
	
	/**
	 * Returns this configuration as XML.
	 * 
	 * @return
	 * 		The configuration in XML Format.
	 */
	public String toXMLString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append(String.format("<config type=\"%s\">%n", XML_CONFIG_NAME));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "DataType", dataType.name() ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "RawValue", rawValue ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "Size", size ));
		sb.append("</config>");
		
		return sb.toString();
	}
}
