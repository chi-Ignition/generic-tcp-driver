/*******************************************************************************
 * Copyright 2012-2015 C. Hiesserich
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
package com.chitek.ignition.drivers.generictcp;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.wicket.RuntimeConfigurationType;

import com.chitek.ignition.drivers.generictcp.configuration.GenericTcpServerDriverType;
import com.chitek.ignition.drivers.generictcp.configuration.GenericTcpClientDriverType;
import com.chitek.ignition.drivers.generictcp.configuration.settings.GenericTcpServerDriverSettings;
import com.chitek.ignition.drivers.generictcp.configuration.settings.GenericTcpClientDriverSettings;
import com.google.common.collect.Lists;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.xopc.driver.api.configuration.DriverType;
import com.inductiveautomation.xopc.driver.common.AbstractDriverModuleHook;

public class ModuleHook extends AbstractDriverModuleHook {

	public static final String BUNDLE_PREFIX = "GenericTcpDriver";
	private static final int expectedApiVersion = 4;

	private static final List<DriverType> DRIVER_TYPES = Lists.newArrayList();
	static {
		DRIVER_TYPES.add(new GenericTcpClientDriverType());
		DRIVER_TYPES.add(new GenericTcpServerDriverType());
	}

	@Override
	public void setup(GatewayContext context) {
		// Register class with BundleUtil for localization
		BundleUtil.get().addBundle(BUNDLE_PREFIX, ModuleHook.class, "GenericTcpDriver");
		
		// Disable caching in development mode - Without this, the use of bundle util
		// keeps the jar file opened, even after a driver is reloaded by the dev module.
		// Not a big problem, as the temp jar files won't be deleted until the JVM shuts down,
		// but with this option the files can be deleted manually, if all classes are properly
		// unloaded. This allows a quick test for memory leaks.
		if (context.getWebResourceManager().getWebApplication().getConfigurationType() == RuntimeConfigurationType.DEVELOPMENT) {
			context.getWebResourceManager().getWebApplication().getResourceSettings().getLocalizer().clearCache();
			URLConnection con;
			try {
				con = new URLConnection(new URL("file://null")) {

					@Override
					public void connect() throws IOException {
						// NOOP - This is just a dummy

					}
				};
				// This will affect all URLConnections - not sure about side effects
				con.setDefaultUseCaches(false);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		
		// Clear Wicket's markup cache. Actually this is only necessary when the module is upgraded to a new version, but there's
		// no way to detect this situation.
		if (context.getWebResourceManager().getWebApplication().getMarkupSettings().getMarkupFactory().hasMarkupCache()) {
			// When the gateway service is started, the is no markup cache yet.
			context.getWebResourceManager().getWebApplication().getResourceSettings().getPropertiesFactory().clearCache();
			context.getWebResourceManager().getWebApplication().getMarkupSettings().getMarkupFactory().getMarkupCache().clear();
		}
		
		super.setup(context);
	}

	@Override
	public void shutdown() {
		// Remove localization
		BundleUtil.get().removeBundle(BUNDLE_PREFIX);

		// These Bundles are registered automatically by the API but never removed, so we have to do it here
		BundleUtil.get().removeBundle(GenericTcpServerDriverSettings.class.getSimpleName());
		BundleUtil.get().removeBundle(GenericTcpClientDriverSettings.class.getSimpleName());
		ResourceBundle.clearCache();

		super.shutdown();
	}

	@Override
	protected int getExpectedAPIVersion() {
		return expectedApiVersion;
	}

	@Override
	protected List<DriverType> getDriverTypes() {
		return DRIVER_TYPES;
	}

	public boolean isFreeModule() {
		return true;
	}
}
