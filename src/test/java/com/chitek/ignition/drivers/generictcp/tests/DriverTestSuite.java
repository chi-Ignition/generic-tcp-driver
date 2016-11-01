package com.chitek.ignition.drivers.generictcp.tests;

import org.apache.log4j.Logger;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.chitek.ignition.drivers.generictcp.tests.config.TestConfigParser;
import com.chitek.ignition.drivers.generictcp.tests.folders.TestDeviceStatusFolder;
import com.chitek.ignition.drivers.generictcp.tests.folders.TestFolderManager;
import com.chitek.ignition.drivers.generictcp.tests.folders.TestMessageFolder;
import com.chitek.ignition.drivers.generictcp.tests.folders.TestSimpleWriteFolder;
import com.chitek.ignition.drivers.generictcp.tests.folders.TestSubscription;
import com.chitek.ignition.drivers.generictcp.tests.io.TestMessageState;
import com.chitek.ignition.drivers.generictcp.tests.io.TestNioEventHandler;

@RunWith(Suite.class)
@Suite.SuiteClasses(
		{ TestConfigParser.class,
			TestMessageState.class,
			TestNioEventHandler.class,
			TestMessageFolder.class,
			TestDeviceStatusFolder.class,
			TestSimpleWriteFolder.class,
			TestSubscription.class,
			TestFolderManager.class})

public class DriverTestSuite {

	private static Logger log;

	static {
		log = Logger.getRootLogger();
	}


	public static Logger getLogger() {
		return log;
	}
}
