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

import com.chitek.ignition.drivers.generictcp.types.HeaderDataType;
import com.chitek.util.XMLConfigParser;

/**
 * A message header configuration.<br />
 * The default constructor returns a empty config with useHeader set for <code>false</code>.
 * 
 * 
 * @author chi
 *
 */
@SuppressWarnings("serial")
public class HeaderConfig implements Serializable {

	private static final String XML_CONFIG_NAME = "HeaderConfig";
	
	private List<HeaderTagConfig> tags=new ArrayList<HeaderTagConfig>();
	/** TRUE if the header should be used */
	private boolean useHeader=false;
	/** TRUE is the packet size received with the header includes the length of the header itself */
	private boolean sizeIncludesHeader=true;
	/** TRUE if a handshake message should be send back to the device */
	private boolean useHandshake=false;
	/** The byte string that should be send as a handshake */
	private String handshakeMsg = "0xfe,0xfe,timestamp";
	private transient int headerSize=0;
	private transient HeaderTagConfig firstFixedTag=null;
	private transient HeaderTagConfig packetSizeTag=null;
	
	/**
	 * Used by the XML parser to add a new HeaderTagConfig.
	 * Tags are evaluated in the order they are added.
	 * 
	 * @param tagConfig The tag to add
	 */
	public void addHeaderTagConfig(HeaderTagConfig tagConfig) {
		tags.add(tagConfig);
		if (tagConfig.getDataType().isHasValue() && firstFixedTag==null)
			firstFixedTag = tagConfig;
		if (tagConfig.getDataType().equals(HeaderDataType.PacketSize)) {
			packetSizeTag = tagConfig;
		}
		tagConfig.setOffset(headerSize);
		headerSize += tagConfig.getByteCount() * (tagConfig.getDataType().isArrayAllowed() ? tagConfig.getSize() : 1);
	}
	
	public List<HeaderTagConfig> getTags() {
		return tags;
	}
	
	/**
	 * @return
	 * 	TRUE if the header should be used
	 */
	public boolean isUseHeader() {
		return useHeader;
	}
	
	public void setUseHeader(boolean useHeader) {
		this.useHeader = useHeader;
	}
	
	/**
	 * 
	 * @return
	 * 	TRUE if the packet size received with the header includes the size of the header itself.
	 */
	public boolean isSizeIncludesHeader() {
		return sizeIncludesHeader;
	}
	
	public void setSizeIncludesHeader(boolean sizeIncludesHeader) {
		this.sizeIncludesHeader = sizeIncludesHeader;
	}
	
	public boolean isUseHandshake() {
		return useHandshake;
	}
	
	public void setUseHandshake(boolean useHandshake) {
		this.useHandshake = useHandshake;
	}
	
	public String getHandshakeMsg() {
		return handshakeMsg;
	}
	
	public void setHandshakeMsg(String handshakeMsg) {
		this.handshakeMsg = handshakeMsg;
	}
	
	/**
	 * Returns the size of the complete header message in bytes.
	 * 
	 * @return
	 * 	the header length in bytes.
	 */
	public int getHeaderSize() {
		return headerSize;
	}

	/**
	 * The packet size tag.
	 * 
	 * @return
	 * 	The packet size tag or null if there is no such tag.
	 */
	public HeaderTagConfig getPacketSizeTag() {
		return packetSizeTag;
	}
	
	/**
	 * The first tag that contains a fixed value.
	 * 
	 * @return
	 * 	The first tag with a fixed value or null if there is no fixed tag.
	 */
	public HeaderTagConfig getFirstFixedTag() {
		return firstFixedTag;
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
		for (HeaderTagConfig tag : tags) {
			sb.append(String.format("%s%n", tag.toXMLString()));
			sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "UseHeader", useHeader ));
			sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "SizeIncludesHeader", sizeIncludesHeader ));
			sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "UseHandshake", useHandshake ));
			sb.append(String.format("\t<setting name=\"%s\">%s</setting>%n", "HandshakeMsg", handshakeMsg ));
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
	public static HeaderConfig fromXMLString(String xml) throws Exception {
		
		if (xml == null || xml.equals(""))
			return null;
		
		XMLConfigParser parser = new XMLConfigParser();
		HeaderConfig parsed;
		try {
			parsed = (HeaderConfig) parser.parseXML(HeaderConfig.class, XML_CONFIG_NAME, xml);
		} catch (Exception e) {
			throw e;
		}
		return parsed;
	}
}
