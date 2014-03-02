/*******************************************************************************
 * Copyright 2012-2013 C. Hiesserich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 ******************************************************************************/
package com.chitek.ignition.drivers.generictcp.meta.config.ui;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.CompoundPropertyModel;

import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;

/**
 * Base class for configuration UIs.
 * 
 * @author chi
 * 
 */
@SuppressWarnings("serial")
public abstract class AbstractConfigUI<T> extends ConfigPanel {

	private final T config;
	
	public AbstractConfigUI(String panelId, String titleKey, T config) {
		super(panelId, titleKey);
		
		this.config = config;
		setDefaultModel(new CompoundPropertyModel<T>(config));
	}

	/**
	 * @return
	 * 	The form that should be submitted when switching tabs
	 */
	public abstract Form<?> getForm();
	
	/**
	 * @return
	 * 	The configuration
	 */
	public T getConfig() {
		return config;
	}
	
	@Override
	protected boolean isTitleVisible() {
		return false;
	}

	@Override
	public String[] getMenuPath() {
		return new String[0];
	}
}
