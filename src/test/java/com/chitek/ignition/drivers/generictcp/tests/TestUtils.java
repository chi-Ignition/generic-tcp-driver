package com.chitek.ignition.drivers.generictcp.tests;

import static org.junit.Assert.assertNotNull;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;

public class TestUtils {
	public static MessageConfig readMessageConfig(String configName) throws Exception {
		InputStream in = TestUtils.class.getResourceAsStream(configName);
		assertNotNull(String.format("Test ressource '%s' not accessible. Check test setup.", configName),in);
		String xml = IOUtils.toString(in);
		MessageConfig messageConfig = MessageConfig.fromXMLString(xml);
		return messageConfig;
	}

	public static HeaderConfig readHeaderConfig(String configName) throws Exception {
		InputStream in = TestUtils.class.getResourceAsStream(configName);
		assertNotNull(String.format("Test ressource '%s' not accessible. Check test setup.", configName), in);
		String xml = IOUtils.toString(in);
		HeaderConfig headerConfig = HeaderConfig.fromXMLString(xml);
		return headerConfig;
	}
	
	public static WritebackConfig readWritebackConfig(String configName) throws Exception {
		InputStream in = TestUtils.class.getResourceAsStream(configName);
		assertNotNull(String.format("Test ressource '%s' not accessible. Check test setup.", configName),in);
		String xml = IOUtils.toString(in);
		WritebackConfig writebackConfig = WritebackConfig.fromXMLString(xml);
		return writebackConfig;
	}
}
