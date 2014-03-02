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
import java.util.List;

import com.chitek.ignition.drivers.generictcp.types.QueueMode;
import com.chitek.util.XMLConfigParser;

public class MessageConfig implements Comparable<MessageConfig>, Serializable {

	private static final long serialVersionUID = 1L;
	private static final String XML_CONFIG_NAME = "MessageConfig";
	
	public List<TagConfig> tags = new ArrayList<TagConfig>();
	private String messageAlias = "";
	public int messageId;
	private QueueMode queueMode = QueueMode.NONE;
	private boolean usePersistance = false;
	private int messageLength = 0;
	private int configHash = 0;

	/**
	 * Constructor used by the XML Parser
	 */
	public MessageConfig() {
		
	}
	
	public MessageConfig(int id) {
		this.messageId = id;
	}
	
	public String getMessageAlias() {
		return messageAlias;
	}

	public void setMessageAlias(String messageAlias) {
		this.messageAlias = messageAlias;
	}

	public int getMessageId() {
		return messageId;
	}

	public void setMessageId(int messageId) {
		this.messageId = messageId;
	}

	public void setQueueMode(QueueMode queueMode) {
		this.queueMode = queueMode;
	}

	/**
	 * Method used by XML-Parser
	 * @param queueModeName
	 */
	public void setQueueMode(String messageLengthTypeName) {
		this.queueMode = QueueMode.valueOf(messageLengthTypeName.trim().toUpperCase());
	}
	
	public QueueMode getQueueMode() {
		return queueMode;
	}

	public boolean isUsePersistance() {
		return usePersistance;
	}

	public void setUsePersistance(boolean usePersistance) {
		this.usePersistance = usePersistance;
	}

	/**
	 * Used by the XML parser to add a TagConfig.
	 * 
	 * @param tagConfig
	 */
	public void addTagConfig(TagConfig tagConfig) {
		tags.add(tagConfig);
		messageLength += tagConfig.getSize()*tagConfig.getDataType().getByteCount();
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
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "MessageAlias", messageAlias ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "MessageId", messageId ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "UsePersistance", usePersistance ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "QueueMode", queueMode.name() ));
		for (TagConfig tag : tags) {
			sb.append(String.format("%s%n", tag.toXMLString()));
		}
		sb.append("</config>");
		
		return sb.toString();
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
	public static MessageConfig fromXMLString(String xml) throws Exception {
		
		if (xml == null || xml.equals(""))
			return null;
		
		XMLConfigParser parser = new XMLConfigParser();
		MessageConfig parsed;
		try {
			parsed = (MessageConfig) parser.parseXML(MessageConfig.class, MessageConfig.XML_CONFIG_NAME, xml);
		} catch (Exception e) {
			throw e;
		}
		return parsed;
	}
	
	/**
	 * A hash value to check for configuration changes. Simply returns the HashCode of the XML-Configuration.
	 * @return
	 * 		The HashCode of the XML-Configuration String.
	 */
	public int getConfigHash() {
		configHash = toXMLString().hashCode();
		return configHash;
	}

	/**
	 * Recalculate the byte offsets. Only used as information in the list editor
	 * 
	 * @param messageIdByteSize
	 *            Size of the massage header in byte
	 */
	public void calcOffsets(int messageIdByteSize) {
		int offset = messageIdByteSize;
		for (TagConfig tag : tags) {
			tag.setOffset(offset);
			offset += tag.getDataType().getByteCount() * tag.getSize();
		}
	}

	/**
	 * Returns the length of the complete message
	 * 
	 * @return The message length in bytes.
	 */
	public int getMessageLength() {
		return messageLength;
	}
	
	/**
	 * @return
	 * 	A deep copy of this MessageConfig.
	 */
	public MessageConfig copy() {
		String xml = toXMLString();
		try {
			return MessageConfig.fromXMLString(xml);
		} catch (Exception e) {
			// This should not happen
			return new MessageConfig(messageId);
		}
	}
	
	@Override
	public String toString() {
		return String.format("Msg '%s' id=%d", messageAlias, messageId);
	}

	@Override
	public int compareTo(MessageConfig other) {
		if (messageId < other.getMessageId()) return -1;
		if (messageId > other.getMessageId()) return 1;
		return 0;
	}
}
