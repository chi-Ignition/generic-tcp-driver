package com.chitek.ignition.drivers.generictcp.meta.config.ui;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.extensions.markup.html.tabs.TabbedPanel;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.CssResourceReference;

import com.chitek.ignition.drivers.generictcp.ModuleHook;
import com.chitek.ignition.drivers.generictcp.configuration.settings.IGenericTcpDriverSettings;
import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.inductiveautomation.ignition.common.Base64;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.redundancy.types.NodeRole;
import com.inductiveautomation.ignition.gateway.web.components.AbstractNamedTab;
import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;
import com.inductiveautomation.ignition.gateway.web.models.RecordModel;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import com.inductiveautomation.xopc.driver.api.configuration.links.ConfigurationUILink;
import com.inductiveautomation.xopc.driver.api.configuration.links.ConfigurationUILink.Callback;

@SuppressWarnings("serial")
public class ConfigUiTabs extends ConfigPanel {

	// CSS Resource to use in the config page
	public static CssResourceReference CSS_STYLE = new CssResourceReference(HeaderConfigUI.class, "res/configui.css");
	
	private RecordModel<PersistentRecord> settingsRecordModel;
	private final IConfigPage configPage;
	private final ConfigPanel returnPanel;
	private final ConfigurationUILink.Callback callback;
	
	private SessionConfig config;
	
	private List<AbstractTabWithForm> tabs;
	private TabbedPanel<AbstractTabWithForm> tabbedPanel;
	
	public ConfigUiTabs(IConfigPage configPage, ConfigPanel returnPanel, PersistentRecord settingsRecord, Callback callback) {
		super("GenericTcpDriver.configurationtitle");
		
		this.configPage = configPage;
		this.returnPanel = returnPanel;
		this.callback = callback;
		
		// Clear caches in development mode
		if (this.getApplication().getConfigurationType() == RuntimeConfigurationType.DEVELOPMENT) {
			this.getApplication().getResourceSettings().getPropertiesFactory().clearCache();
			this.getApplication().getMarkupSettings().getMarkupFactory().getMarkupCache().clear();
		}
		
		settingsRecordModel = new RecordModel<PersistentRecord>(settingsRecord);
		try {
			DriverConfig driverConfig = ((IGenericTcpDriverSettings)settingsRecord).getParsedMessageConfig();
			if (driverConfig == null) {
				driverConfig = new DriverConfig();
			}
			WritebackConfig writebackConfig = ((IGenericTcpDriverSettings)settingsRecord).getParsedWritebackConfig();
			if (writebackConfig == null) {
				writebackConfig = new WritebackConfig();
			}
			HeaderConfig headerConfig = ((IGenericTcpDriverSettings)settingsRecord).getParsedHeaderConfig();
			if (headerConfig == null) {
				headerConfig = new HeaderConfig();
			}
			config = new SessionConfig(driverConfig, writebackConfig, headerConfig);
			setDefaultModel(new Model<SessionConfig>(config));
		} catch (Exception e) {
			error("Exception while loading configuration: " + e.getLocalizedMessage());
			config = null;
		}
		
		addComponents();
	}
	
	private void addComponents() {
		
		boolean enableSubmit = true;
		if (((GatewayContext) this.getApplication()).getRedundancyManager().getCurrentState().getRole() == NodeRole.Backup) {
			info(BundleUtil.get().getStringLenient(ModuleHook.BUNDLE_PREFIX + "ConfigUI.Error.BackupNode"));
			enableSubmit = false;
		}
		
		Link<?> backLink = configPage.createLink("back", returnPanel);
		add(backLink);
		
		SubmitLink submitLink = new SubmitLink("submit") {
			@Override
			public void onSubmit() {
				saveConfiguration();
			}
			@Override
			public Form<?> getForm() {
				return tabs.get(tabbedPanel.getSelectedTab()).getForm();
			}
		};
		submitLink.setEnabled(enableSubmit);
		add(submitLink);
		
		tabs = new ArrayList<AbstractTabWithForm>();
		tabs.add(new AbstractTabWithForm("MessageConfig", "GenericTcpDriver.messageTab") {
			@Override
			public Panel getPanel(String panelId) {
				if (panel == null) {
					panel = new MessageConfigUI(panelId, config.getDriverConfig());
				}
				return panel;
			}
		});
		tabs.add(new AbstractTabWithForm("HeaderConfig", "GenericTcpDriver.headerTab") {
			@Override
			public Panel getPanel(String panelId) {
				if (panel == null) {
					panel = new HeaderConfigUI(panelId, config.getHeaderConfig());
				}
				return panel;
			}
		});
		tabs.add(new AbstractTabWithForm("WritebackConfig", "GenericTcpDriver.writebackTab") {
			@Override
			public Panel getPanel(String panelId) {
				if (panel == null) {
					panel = new WritebackConfigUI(panelId, config.getWritebackConfig());
				}
				return panel;
			}
		});
		
		tabbedPanel = new TabbedPanel<AbstractTabWithForm>("tabs",tabs) {
			@Override
			protected WebMarkupContainer newLink(final String linkId, final int index) {
				// The current tab should be submitted and validated before switching to another tab
				Form<?> form = ((AbstractTabWithForm) tabs.get(getSelectedTab())).getForm();
				 
				if (form != null) {
					SubmitLink link = new SubmitLink(linkId, form) {
						@Override
						public void onSubmit() {
							setSelectedTab(index);
						}
					};
					return link;
				} else {
					return super.newLink(linkId, index);
				}
			}
		};
		add(tabbedPanel);
		
	}
	
	/**
	 * Save the driver configuration
	 */
	private void saveConfiguration() {
		if (((GatewayContext) this.getApplication()).getRedundancyManager().getCurrentState().getRole() == NodeRole.Backup) {
			error(BundleUtil.get().getStringLenient(ModuleHook.BUNDLE_PREFIX + "ConfigUI.Error.BackupNodeSave"));
			return;
		}
		try {
			IGenericTcpDriverSettings settingsRecord = (IGenericTcpDriverSettings)settingsRecordModel.getObject();
			
			String configXML = ((SessionConfig)getDefaultModelObject()).getDriverConfig().toXMLString();
			if (configXML.length() > 0) {
				settingsRecord.setMessageConfig(Base64.encodeBytes(configXML.getBytes()).getBytes());
			} else {
				settingsRecord.setMessageConfig(Base64.encodeBytes(new byte[] { 0 }).getBytes());
			}

			String headerConfigXML = ((SessionConfig)getDefaultModelObject()).getHeaderConfig().toXMLString();
			if (headerConfigXML.length() > 0) {
				settingsRecord.setHeaderConfig(Base64.encodeBytes(headerConfigXML.getBytes()).getBytes());
			} else {
				settingsRecord.setHeaderConfig(Base64.encodeBytes(new byte[] { 0 }).getBytes());
			}			
			
			String writebackConfigXML = ((SessionConfig)getDefaultModelObject()).getWritebackConfig().toXMLString();
			if (writebackConfigXML.length() > 0) {
				settingsRecord.setWritebackConfig(Base64.encodeBytes(writebackConfigXML.getBytes()).getBytes());
			} else {
				settingsRecord.setWritebackConfig(Base64.encodeBytes(new byte[] { 0 }).getBytes());
			}	
			
			// Save and return
			callback.save(settingsRecord.getPersistentRecord());
			configPage.setConfigPanel(returnPanel);
		} catch (Exception e) {
			error(e);
		}
	}
	
	@Override
	protected boolean isFeedbackEnabled() {
		return false;
	}
	
	@Override
	public void renderHead(IHeaderResponse response) {
		// add CSS to the page header, mainly used to display the editor images
		// by using CSS for the images, there is no need to use wickets, which would blow up
		// the session
		response.render(CssHeaderItem.forReference(CSS_STYLE));
	}
	
	@Override
	public String[] getMenuPath() {
		return new String[0];
	}

	abstract class AbstractTabWithForm extends AbstractNamedTab {
		protected AbstractConfigUI<?> panel;
		public AbstractTabWithForm(String name, String titleKey) {
			super(name, titleKey);
		}
	 
		public Form<?> getForm() {
			return panel.getForm();
		}
	}

}
