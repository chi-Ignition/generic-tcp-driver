package com.chitek.ignition.drivers.generictcp.tests.config;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderTagConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.TagConfig;
import com.chitek.ignition.drivers.generictcp.tests.DriverTestSuite;
import com.chitek.ignition.drivers.generictcp.types.HeaderDataType;

public class TestConfigParser {

	@Before
	public void setup() throws Exception {
		DriverTestSuite.getLogger();
	}

	@Test
	public void testHeaderConfig() throws Exception {
		InputStream in = this.getClass().getResourceAsStream("/testHeaderConfig.xml");
		String xml = IOUtils.toString(in);
		HeaderConfig headerConfig = HeaderConfig.fromXMLString(xml);
		assertEquals("Wrong header size", 4, headerConfig.getHeaderSize());
		assertEquals("Configured header tags",2,headerConfig.getTags().size());
		HeaderTagConfig tag = headerConfig.getFirstFixedTag();
		assertEquals("Type of fixed tag", HeaderDataType.Word, tag.getDataType());
	}

	@Test
	public void testMessageConfig() throws Exception {
		InputStream in = this.getClass().getResourceAsStream("/testMessageConfig.xml");
		String xml = IOUtils.toString(in);
		MessageConfig messageConfig = MessageConfig.fromXMLString(xml);
		assertEquals("Message id", 1, messageConfig.getMessageId());
		assertEquals("Message length", 4, messageConfig.getMessageLength());
		assertEquals("Configured tags", 2 , messageConfig.tags.size());
		TagConfig tag = messageConfig.tags.get(0);
		assertEquals("Tag 1 ID", 1, tag.getId());
	}
}
