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

import com.chitek.ignition.drivers.generictcp.types.BinaryDataType;
import com.chitek.ignition.drivers.generictcp.types.TagLengthType;

@SuppressWarnings("serial")
public class TagConfig implements Serializable {
	
	private static final String XML_CONFIG_NAME = "TagConfig";
	
	private int id;
	private BinaryDataType dataType;
	private String alias;
	private int size; // Array size of this tag
	private TagLengthType lengthType = TagLengthType.FIXED_LENGTH;

	private int offset; // Byte offest in message - just informational in config ui

	// Set a default datatype for new items
	public TagConfig()
	{
		dataType = BinaryDataType.Byte;
		size = 1;
	}

	public int getId() {
		if (dataType.isSpecial())
			return dataType.getSpecialId();
		else
			return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public BinaryDataType getDataType() {
		return dataType;
	}

	public void setDataType(BinaryDataType dataType) {
		this.dataType = dataType;
	}
	
	/**
	 * Method used by XML-Parser
	 * *
	 * @param dataTypeName
	 */
	public void setDataType(String dataTypeName) {
		this.dataType = BinaryDataType.valueOf(dataTypeName);
	}

	public TagLengthType getTagLengthType() {
		return lengthType;
	}
	
	public void setTagLengthType(TagLengthType tagLengthType) {
		this.lengthType = tagLengthType;
	}
	
	/**
	 * Method used by XML-Parser
	 * *
	 * @param dataTypeName
	 */
	public void setTagLengthType(String tagLengthTypeName) {
		this.lengthType = TagLengthType.valueOf(tagLengthTypeName);
	}

	public String getAlias() {
		if (!dataType.isSpecial())
			return alias;
		else
			return "_" + dataType.name();
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	/**
	 * 
	 * @return
	 * 		The array size or 1 for scalar tags. Special tags always have size = 1.
	 */
	public int getSize() {
		if (dataType.isArrayAllowed() )
			return size;
		else
			return 1;
	}

	public void setSize(int size) {
		this.size = size < 2 ? 1 : size;
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
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "Alias", alias ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "Id", id ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "DataType", dataType.name() ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "Size", size ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "TagLengthType", lengthType ));
		sb.append("</config>");
		
		return sb.toString();
	}
	
	@Override
	public String toString() {
		if (dataType.isSpecial()) {
			return String.format("Tag '%s' id=%d", dataType.getDisplayString(), dataType.getSpecialId());
		} else {
			return String.format("Tag '%s' id=%d", alias, id);
		}
	}
}
