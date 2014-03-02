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

import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.WritebackDataType;
import com.chitek.util.XMLConfigParser;

@SuppressWarnings("serial")
public class WritebackConfig implements Serializable {

	private static final String XML_CONFIG_NAME = "WritebackConfig";

	private boolean enableWriteback=false;
	/** The byte string that should be send as a prefix */
	private String prefix = "0xff,0xff";
	/** TRUE if a message prefix should be send back to the device */
	private boolean usePrefix=false;
	private OptionalDataType messageIdType = OptionalDataType.None;
	private WritebackDataType dataType = WritebackDataType.Int32;
	private boolean sendInitialValue = false;
	private boolean sendOnValueChange = false;
	private String initialValue = "";
	private int initialId = 0;

	public boolean isEnabled() {
		return enableWriteback;
	}

	public void setEnabled(boolean isEnabled) {
		this.enableWriteback = isEnabled;
	}

	public OptionalDataType getMessageIdType() {
		return messageIdType;
	}

	public void setMessageIdType(OptionalDataType messageIdType) {
		this.messageIdType = messageIdType;
	}

	/**
	 * Method used by XML-Parser
	 * @param messageLengthName
	 */
	public void setMessageIdType(String messageIdTypeName) {
		this.messageIdType = OptionalDataType.valueOf(messageIdTypeName);
	}

	public WritebackDataType getDataType() {
		return dataType;
	}

	public void setDataType(WritebackDataType dataType) {
		this.dataType = dataType;
	}

	/**
	 * Method used by XML-Parser
	 * @param dataTypeName
	 */
	public void setDataType(String dataTypeName) {
		this.dataType = WritebackDataType.valueOf(dataTypeName);
	}

	public boolean getSendOnValueChange() {
		return sendOnValueChange;
	}

	public void setSendOnValueChange(boolean sendOnValueChange) {
		this.sendOnValueChange = sendOnValueChange;
	}

	public boolean getSendInitialValue() {
		return sendInitialValue;
	}

	public void setSendInitialValue(boolean sendInitialValue) {
		this.sendInitialValue = sendInitialValue;
	}

	public String getInitialValue() {
		return initialValue;
	}

	public void setInitialValue(String initialValue) {
		this.initialValue = initialValue;
	}

	public int getInitialId() {
		return initialId;
	}

	public void setInitialId(int initialId) {
		this.initialId = initialId;
	}

	public boolean isUsePrefix() {
		return usePrefix;
	}

	public void setUsePrefix(boolean usePrefix) {
		this.usePrefix = usePrefix;
	}

	public String getPrefix() {
		return prefix.trim();
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
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
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "Enabled", enableWriteback ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "MessageIdType", messageIdType.name() ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "SendOnValueChange", sendOnValueChange ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "SendInitialValue", sendInitialValue ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "DataType", dataType.name() ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "InitialValue", initialValue ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "InitialId", initialId ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "UsePrefix", usePrefix ));
		sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "Prefix", prefix ));
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
	public static WritebackConfig fromXMLString(String xml) throws Exception {

		if (xml == null || xml.equals(""))
			return null;

		XMLConfigParser parser = new XMLConfigParser();
		WritebackConfig parsed;
		try {
			parsed = (WritebackConfig) parser.parseXML(WritebackConfig.class, XML_CONFIG_NAME, xml);
		} catch (Exception e) {
			throw e;
		}
		return parsed;
	}
}
