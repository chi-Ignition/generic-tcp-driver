/*******************************************************************************
 * Copyright 2012-2019 C. Hiesserich
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
package com.chitek.ignition.drivers.generictcp.meta.config.ui;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.validation.AbstractFormValidator;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.util.string.Strings;
import org.apache.wicket.validation.ValidationError;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

import com.chitek.ignition.drivers.generictcp.meta.config.WritebackConfig;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.WritebackDataType;
import com.chitek.ignition.drivers.generictcp.util.Util;
import com.chitek.wicket.FeedbackTextField;

@SuppressWarnings("serial")
public class WritebackConfigUI extends AbstractConfigUI<WritebackConfig> {

	private final static String titleKey = "GenericTcpDriver.writebackTab";

	// Components for evaluation
	private Form<?> editForm;
	private WebMarkupContainer settingsTable;
	private CheckBox enabledCheckBox;
	private CheckBox sendInitialValueCheckBox;
	private DropDownChoice<WritebackDataType> dataTypeDropDown;
	private DropDownChoice<OptionalDataType> messageIdTypeDropDown;
	private TextField<Integer> initialIdTextField;
	private TextField<String> initialValueTextField;
	private CheckBox usePrefixCheckBox;
	private TextField<String> prefixTextField;

	public WritebackConfigUI(String panelId, WritebackConfig config) {
		super(panelId, titleKey, config);

		addComponents();
	}

	public Form<?> getForm() {
		return editForm;
	}
	
	private void addComponents() {
		
		editForm = new Form<Object>("edit-form");
		add(editForm);

		WebMarkupContainer tableContainer = new WebMarkupContainer("table-container", getDefaultModel());
		tableContainer.setOutputMarkupId(true);

		enabledCheckBox = getEnabledCheckBox();
		tableContainer.add(enabledCheckBox);

		settingsTable = new WebMarkupContainer("settings-table");
		settingsTable.setOutputMarkupId(true);
		settingsTable.setEnabled(getConfig().isEnabled());
		tableContainer.add(settingsTable);

		messageIdTypeDropDown = getMessageIdTypeDropdown();
		settingsTable.add(messageIdTypeDropDown);

		dataTypeDropDown = new DropDownChoice<WritebackDataType>("dataType", WritebackDataType.getOptions(), new EnumChoiceRenderer<WritebackDataType>(this));
		settingsTable.add(dataTypeDropDown);

		usePrefixCheckBox = getUsePrefixCheckBox();
		settingsTable.add(usePrefixCheckBox);

		prefixTextField = getPrefixTextField();
		settingsTable.add(prefixTextField);

		settingsTable.add(new CheckBox("sendOnValueChange"));

		sendInitialValueCheckBox = getSendInitialValueCheckBox();
		settingsTable.add(sendInitialValueCheckBox);

		initialValueTextField = getInitialValueTextField();
		settingsTable.add(initialValueTextField);

		initialIdTextField = getInitialIdTextField();
		settingsTable.add(initialIdTextField);

		editForm.add(tableContainer);

		editForm.add(new WritebackFormValidator());
	}

	private DropDownChoice<OptionalDataType> getMessageIdTypeDropdown() {
		DropDownChoice<OptionalDataType> dropDown = 
				new DropDownChoice<OptionalDataType>("messageIdType", OptionalDataType.getOptions(), new EnumChoiceRenderer<OptionalDataType>(this));
	
		dropDown.add(new AjaxFormComponentUpdatingBehavior("onchange") {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(getComponent().getParent().get("initialId").setEnabled(getConfig().getMessageIdType() != OptionalDataType.None));
			}
		});
		
		return dropDown;
	}
	
	private CheckBox getEnabledCheckBox() {
		CheckBox checkbox = new CheckBox("enabled");

		checkbox.add(new OnChangeAjaxBehavior() {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				boolean value = ((CheckBox) getComponent()).getConvertedInput();

				if (value) {
					settingsTable.setEnabled(true);
				} else {
					settingsTable.setEnabled(false);
				}
				target.add(settingsTable);
			}
		});

		return checkbox;
	}

	private CheckBox getUsePrefixCheckBox() {
		CheckBox checkbox = new CheckBox("usePrefix");

		checkbox.add(new OnChangeAjaxBehavior() {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				boolean value = ((CheckBox) getComponent()).getConvertedInput();

				Component prefixTextField = getComponent().getParent().get("prefix");
				if (value && !prefixTextField.isEnabled()) {
					prefixTextField.setEnabled(true);
					target.add(prefixTextField);
				} else if (!value) {
					prefixTextField.setEnabled(false);
					target.add(prefixTextField);
				}
			}
		});

		return checkbox;
	}

	private TextField<String> getPrefixTextField() {
		TextField<String> textField = new FeedbackTextField<String>("prefix");

		textField.setRequired(true);
		textField.setEnabled(getConfig().isUsePrefix());

		textField.setLabel(new StringResourceModel("prefix.DisplayName", this, null));
		textField.setOutputMarkupId(true);
		textField.setOutputMarkupPlaceholderTag(true);

		return textField;
	}

	private CheckBox getSendInitialValueCheckBox() {
		CheckBox checkbox = new CheckBox("sendInitialValue");

		// When the datatype is changed, the offsets are recalculated and
		// displayed
		checkbox.add(new OnChangeAjaxBehavior() {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				boolean value = ((CheckBox) getComponent()).getConvertedInput();

				Component initialValueTextField = getComponent().getParent().get("initialValue");
				Component initialIdTextField = getComponent().getParent().get("initialId");

				if (value && !initialValueTextField.isEnabled()) {
					initialValueTextField.setEnabled(true);
					initialIdTextField.setEnabled(true);
				} else {
					initialValueTextField.setEnabled(false);
					initialIdTextField.setEnabled(false);
				}
				target.add(initialValueTextField);
				target.add(initialIdTextField);
			}
		});

		return checkbox;
	}

	private TextField<String> getInitialValueTextField() {
		TextField<String> textField = new FeedbackTextField<String>("initialValue");

		textField.setRequired(true);
		textField.setEnabled(getConfig().getSendInitialValue());

		textField.setLabel(new StringResourceModel("initialValue.DisplayName", this, null));
		textField.setOutputMarkupId(true);
		textField.setOutputMarkupPlaceholderTag(true);

		return textField;
	}

	private TextField<Integer> getInitialIdTextField() {
		TextField<Integer> textField = new FeedbackTextField<Integer>("initialId");

		textField.setRequired(true);
		textField.setEnabled(getConfig().getSendInitialValue());

		textField.setLabel(new StringResourceModel("initialId.DisplayName", this, null));
		textField.setOutputMarkupId(true);
		textField.setOutputMarkupPlaceholderTag(true);

		return textField;
	}

	private class WritebackFormValidator extends AbstractFormValidator {

		@Override
		public FormComponent<?>[] getDependentFormComponents() {
			return new FormComponent<?>[] {
				sendInitialValueCheckBox, messageIdTypeDropDown, dataTypeDropDown,
				initialIdTextField, initialValueTextField, usePrefixCheckBox, prefixTextField };
		}

		@Override
		public void validate(Form<?> paramForm) {
			log().info("Validating WritebackConfig");
			
			if (!Strings.isTrue(enabledCheckBox.getValue())) {
				// No validation when writeback is not enabled
				return;
			}
			
			if (sendInitialValueCheckBox.getConvertedInput()) {
				// Initial value selected - Check data type
				WritebackDataType dataType = dataTypeDropDown.getConvertedInput();
				String value = initialValueTextField.getInput();

				try {
					if (dataType == WritebackDataType.ByteString) {
						// Initial Value is updated with the parsed String
						try {
							Util.hexString2ByteArray(value.trim());
							initialValueTextField.setConvertedInput(value.trim());
						} catch (Exception e) {
							error(initialValueTextField, "DataTypeValidator");
						}
					} else {
						// no String, type is numeric
						long lValue = Long.parseLong(value);
						boolean rangeError = false;
						switch (dataType) {
						case UInt16:
							rangeError = lValue < UShort.MIN_VALUE || lValue > UShort.MAX_VALUE;
							break;
						case Int16:
							rangeError = lValue < Short.MIN_VALUE || lValue > Short.MAX_VALUE;
							break;
						case UInt32:
							rangeError = lValue < UInteger.MIN_VALUE || lValue > UInteger.MAX_VALUE;
							break;
						case Int32:
							rangeError = lValue < Integer.MIN_VALUE || lValue > Integer.MAX_VALUE;
							break;
						default:
							break;
						}
						if (rangeError)
							error(initialValueTextField, "RangeValidator");
						else
							initialValueTextField.setConvertedInput(Long.toString(lValue));
					}
				} catch (NumberFormatException e) {
					error(initialValueTextField, "DataTypeValidator");
				}

				// Check initial Id
				OptionalDataType idDataType = messageIdTypeDropDown.getConvertedInput();
				if (idDataType != OptionalDataType.None && sendInitialValueCheckBox.getConvertedInput()) {
					int initialId = initialIdTextField.getConvertedInput();
					boolean rangeError = false;
					switch (idDataType) {
					case UByte:
						rangeError = initialId < 0 || initialId > 255;
						break;
					case UInt16:
						rangeError = initialId < UShort.MIN_VALUE || initialId > UShort.MAX_VALUE;
						break;
					default:
						break;
					}
					if (rangeError)
						error(initialIdTextField, "RangeValidator");
				}
			}

			// Check the prefix
			if (usePrefixCheckBox.getConvertedInput()) {
				// Initial value selected - Check data type
				String value = prefixTextField.getInput();

				try {
					Map<String, Number> map = new HashMap<String, Number>();
					map.put("id", (Integer.valueOf(0)));
					map.put("lenb", (Byte.valueOf((byte)0)));
					map.put("lenw", (Short.valueOf((short)0)));
					Util.hexString2ByteArray(value.trim(), map, ByteOrder.BIG_ENDIAN);
					prefixTextField.setConvertedInput(value.trim());
				} catch (Exception e) {
					ValidationError error = new ValidationError();
					error.addKey("ByteStringValidator");
					error.setVariable("error",e.getMessage());
					prefixTextField.error(error);
				}

				// Check if ID is configured
				if (prefixTextField.getInput().contains("id")) {
					if (messageIdTypeDropDown.getConvertedInput() == OptionalDataType.None) {
						ValidationError error = new ValidationError();
						error.addKey("IdNone");
						prefixTextField.error(error);
					}
				}
			}
		}

		@Override
		protected Map<String, Object> variablesMap() {
			Map<String, Object> vars = new HashMap<String, Object>(1);

			vars.put("messageDataType", dataTypeDropDown.getConvertedInput().toString());
			vars.put("idDataType", messageIdTypeDropDown.getConvertedInput().toString());
			return vars;
		}
	}

}
