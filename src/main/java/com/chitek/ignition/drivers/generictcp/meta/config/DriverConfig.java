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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.util.XMLConfigParser;

public class DriverConfig implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final String XML_CONFIG_NAME = "DriverConfig";
	
	private OptionalDataType messageIdType = OptionalDataType.UByte;
	private String parserWarnings = null;
	
	public Map<Integer, MessageConfig> messages = new HashMap<Integer, MessageConfig>();
	
	/**
	 * Returns the message with the given ID. The byte offsets are recalculated
	 * in the returned message.
	 * 
	 * @param messageId
	 * @return
	 * 		The message configuration with the given Id, or null if message is not defined
	 */
	public MessageConfig getMessageConfig(int messageId) {
		MessageConfig messageConfig=messages.get(messageId);
		if (messageConfig != null)
			messages.get(messageId).calcOffsets(getMessageIdType().getByteSize());
		return messageConfig;
	}
	
	/**
	 * @return
	 * 	A sorted list (by id) of the configured messages.
	 */
	public List<MessageConfig> getMessageList() {
		List<MessageConfig> list = new ArrayList<MessageConfig>(messages.values());
		Collections.sort(list);
		return list;
	}
	
	public void setMessageIdType(OptionalDataType messageIdByteSize) {
		this.messageIdType = messageIdByteSize;
	}
	
	public OptionalDataType getMessageIdType() {
		return messageIdType;
	}
	
	/**
	 * Method used by XML-Parser
	 * @param messageLengthName
	 */
	public void setMessageIdType(String messageIdTypeName) {
		this.messageIdType = OptionalDataType.valueOf(messageIdTypeName);
	}
	
	public void addMessageConfig(MessageConfig messageConfig) {
		messages.put(messageConfig.getMessageId(), messageConfig);
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
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "MessageIdType", messageIdType ));
		for (MessageConfig message : messages.values()) {
			sb.append(String.format("%s%n", message.toXMLString()));
		}
		sb.append("</config>");
		
		return sb.toString();
	}
	
	public void setParserWarnings(String parserWarnings) {
		if (parserWarnings != null && !parserWarnings.isEmpty())
			this.parserWarnings=parserWarnings;
	}
	
	public String getParserWarnings() {
		return parserWarnings;
	}
	
	/**
	 * Parses an XML configuration.
	 * 
	 * @param xml
	 * 		The XML configuration as created by toXMLString method.
	 * @return
	 * 		The parsed configuration.
	 * @throws Exception
	 */
	public static DriverConfig fromXMLString(String xml) throws Exception {
		
		if (xml == null || xml.equals(""))
			return null;
		
		XMLConfigParser parser = new XMLConfigParser();
		DriverConfig parsed;
		try {
			parsed = (DriverConfig) parser.parseXML(DriverConfig.class, DriverConfig.XML_CONFIG_NAME, xml);
		} catch (Exception e) {
			throw e;
		}
		
		parsed.setParserWarnings(parser.getWarnings());
		
		return parsed;
	}
	
}
