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
package com.chitek.ignition.drivers.generictcp.meta.config.ui;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.validation.AbstractFormValidator;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.ValidationError;
import org.apache.wicket.validation.validator.RangeValidator;

import com.chitek.ignition.drivers.generictcp.meta.config.HeaderConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.HeaderTagConfig;
import com.chitek.ignition.drivers.generictcp.types.HeaderDataType;
import com.chitek.ignition.drivers.generictcp.util.Util;
import com.chitek.wicket.FeedbackTextField;
import com.chitek.wicket.listeditor.EditorListItem;
import com.chitek.wicket.listeditor.EditorSubmitLink;
import com.chitek.wicket.listeditor.ListEditor;
import com.chitek.wicket.listeditor.UniqueListItemValidator;
import com.inductiveautomation.ignition.gateway.web.models.LenientResourceModel;

@SuppressWarnings("serial")
public class HeaderConfigUI extends AbstractConfigUI<HeaderConfig> {

	private final static String titleKey = "GenericTcpDriver.headerTab";
	
	// Components that need to be referenced after construction
	private Form<?> editForm;
	private ListEditor<HeaderTagConfig> editor;
	private CheckBox useHeaderCheckbox;
	private CheckBox useHandshakeCheckBox;
	private TextField<String> handshakeMsgTextField;

	private final ResourceModel labelValue = new LenientResourceModel("valuelabel");
	private final ResourceModel labelSize = new LenientResourceModel("sizelabel");

	public HeaderConfigUI(String panelId, HeaderConfig config) {
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

		useHeaderCheckbox = new CheckBox("useHeader");
		tableContainer.add(useHeaderCheckbox);
		tableContainer.add(new CheckBox("sizeIncludesHeader"));

		useHandshakeCheckBox = getUseHandshakeCheckBox();
		tableContainer.add(useHandshakeCheckBox);

		handshakeMsgTextField = getHandshakeMsgTextField();
		tableContainer.add(handshakeMsgTextField);

		// Create the list editor
		recalcTagOffsets(getConfig().getTags());
		editor = new ListEditor<HeaderTagConfig>("tags", new PropertyModel<List<HeaderTagConfig>>(
			getDefaultModel(), "tags")) {
			@Override
			protected void onPopulateItem(EditorListItem<HeaderTagConfig> item) {

				item.setModel(new CompoundPropertyModel<HeaderTagConfig>(item.getModelObject()));

				HeaderDataType dataType = item.getModelObject().getDataType();

				// Offset is displayed only for information
				item.add(new Label("offset").setOutputMarkupId(true));
				item.add(getDataTypeDropdown());
				item.add(getValueTextField().setEnabled(dataType.isHasValue()).setVisible(dataType.isHasValue()));
				item.add(getSizeTextField().setEnabled(dataType.isArrayAllowed()));

				// Create the edit links to be used in the list editor
				item.add(getInsertLink());
				item.add(getDeleteLink());
				item.add(getMoveUpLink().setVisible(item.getIndex() > 0));
				item.add(getMoveDownLink().setVisible(item.getIndex() < getList().size() - 1));
			}
		};

		Label noItemsLabel = new Label("no-items-label", new StringResourceModel("noheaderitems", this, null)) {
			@Override
			public boolean isVisible() {
				return editor.getList().size() == 0;
			}
		};

		tableContainer.add(editor);

		tableContainer.add(noItemsLabel);

		tableContainer.add(new SubmitLink("add-row-link") {
			@Override
			public void onSubmit() {
				editor.addItem(new HeaderTagConfig(HeaderDataType.Dummy));
				// Recalculate the offsets
				recalcOffsets();
				// Adjust the visibility of the edit links
				rebuildLinks(editor);
			}
		}.setDefaultFormProcessing(false));

		editForm.add(tableContainer);

		editForm.add(new HeaderFormValidator());
	}

	private CheckBox getUseHandshakeCheckBox() {
		CheckBox checkbox = new CheckBox("useHandshake");

		checkbox.add(new OnChangeAjaxBehavior() {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				boolean value = ((CheckBox) getComponent()).getConvertedInput();				
				target.add(getComponent().getParent().get("handshakeMsg").setEnabled(value));
			}
		});

		return checkbox;
	}

	private TextField<String> getHandshakeMsgTextField() {
		TextField<String> textField = new FeedbackTextField<String>("handshakeMsg");

		textField.setRequired(true);
		textField.setEnabled(getConfig().isUseHandshake());

		textField.setLabel(new StringResourceModel("handshakeMsg.DisplayName", this, null));
		textField.setOutputMarkupId(true);
		textField.setOutputMarkupPlaceholderTag(true);

		return textField;
	}

	/**
	 * Create the DataType drop down. When the data type is changed, the offsets will be recalculated and updated.
	 */
	private DropDownChoice<HeaderDataType> getDataTypeDropdown() {
		DropDownChoice<HeaderDataType> dropDown = new DropDownChoice<HeaderDataType>("dataType",
			HeaderDataType.getOptions(), new EnumChoiceRenderer<HeaderDataType>(this)) {
			@Override
			protected void onComponentTag(ComponentTag tag) {
				super.onComponentTag(tag);
				if (hasErrorMessage()) {
					String style = "background-color:#ffff00;";
					String oldStyle = tag.getAttribute("style");
					tag.put("style", style + oldStyle);
				}
			}
		};

		dropDown.setRequired(true);

		// When the data type is changed, the offsets are recalculated and displayed
		dropDown.add(new OnChangeAjaxBehavior() {
			@SuppressWarnings("unchecked")
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				// Recalculate and display the offsets
				updateOffsets(target);

				// Disable value field when DataType does not use a value.
				DropDownChoice<HeaderDataType> choice = (DropDownChoice<HeaderDataType>) getComponent();
				MarkupContainer parent = choice.getParent();
				HeaderDataType dataType = choice.getConvertedInput();
				target.add(parent.get("rawValue").setEnabled(dataType.isHasValue()).setVisible(dataType.isHasValue()));
				target.add(parent.get("size").setEnabled(dataType.isArrayAllowed()));
			}
		});

		dropDown.add(new UniqueListItemValidator<HeaderDataType>(dropDown) {
			@Override
			public String getValue(IValidatable<HeaderDataType> validatable) {
				return String.valueOf(validatable.getValue().name());
			}
		}
		.setMessageKey("dataType.SpecialTypesValidator")
		.setFilterList(HeaderDataType.getSpecialItemNames()));

		return dropDown;
	}

	private TextField<String> getValueTextField() {
		TextField<String> textField = new FeedbackTextField<String>("rawValue");

		textField.setRequired(true);

		textField.setLabel(labelValue);
		textField.setOutputMarkupId(true);
		textField.setOutputMarkupPlaceholderTag(true);

		return textField;
	}

	private TextField<Integer> getSizeTextField() {
		TextField<Integer> textField = new FeedbackTextField<Integer>("size");

		textField.setRequired(true);
		textField.add(new RangeValidator<Integer>(1, 255));
		textField.setLabel(labelSize);
		textField.setOutputMarkupId(true);
		textField.setOutputMarkupPlaceholderTag(true);

		return textField;
	}

	private EditorSubmitLink getInsertLink() {
		EditorSubmitLink insertLink = new EditorSubmitLink("insert-link") {

			@Override
			public void onSubmit() {
				ListEditor<HeaderTagConfig> editor = getEditor();
				// add a new item at the end of the list
				addItem(new HeaderTagConfig());

				// shift the index property of all items after current one
				int idx = getItem().getIndex();

				// Move the new item up to the required position
				for (int i = editor.size() - 1; i > idx; i--) {
					editor.swapItems(i, i - 1);
				}

				// Recalculate the offsets
				recalcOffsets();
				// Adjust the visibility of the edit links
				rebuildLinks(editor);
			}
		};

		return insertLink;
	}

	private EditorSubmitLink getDeleteLink() {
		EditorSubmitLink deleteLink = new EditorSubmitLink("delete-link") {
			@Override
			public void onSubmit() {
				ListEditor<HeaderTagConfig> editor = getEditor();
				int idx = getItem().getIndex();
				for (int i = idx + 1; i < getItem().getParent().size(); i++) {
					EditorListItem<?> item = (EditorListItem<?>) getItem().getParent().get(i);
					item.setIndex(item.getIndex() - 1);
				}
				getList().remove(idx);
				editor.remove(getItem());

				// Recalculate the offsets
				recalcOffsets();
				// Adjust the visibility of the edit links
				rebuildLinks(editor);
			}
		};

		return deleteLink;
	}

	private EditorSubmitLink getMoveUpLink() {
		EditorSubmitLink moveUpLink = new EditorSubmitLink("move-up-link") {
			@Override
			public void onSubmit() {
				ListEditor<HeaderTagConfig> editor = getEditor();
				int idx = getItem().getIndex();
				getEditor().swapItems(idx - 1, idx);

				// Recalculate the offsets
				recalcOffsets();
				// Adjust the visibility of the edit links
				rebuildLinks(editor);
			}
		};

		return moveUpLink;
	}

	private EditorSubmitLink getMoveDownLink() {
		EditorSubmitLink moveUpLink = new EditorSubmitLink("move-down-link") {
			@Override
			public void onSubmit() {
				ListEditor<HeaderTagConfig> editor = getEditor();
				int idx = getItem().getIndex();
				getEditor().swapItems(idx, idx + 1);

				// Recalculate the offsets
				recalcOffsets();
				// Adjust the visibility of the edit links
				rebuildLinks(editor);
			}
		};

		return moveUpLink;
	}

	/**
	 * Recalculate the offsets in the given tag list
	 */
	private void recalcTagOffsets(List<HeaderTagConfig> tags) {
		int offset = 0;
		for (HeaderTagConfig tc : tags) {
			tc.setOffset(offset);
			offset += tc.getDataType().getByteCount() * tc.getSize();
		}		
	}
	
	/**
	 * Recalculate the offsets in the editor's internal list
	 */
	private void recalcOffsets() {
		recalcTagOffsets(editor.getList());
	}

	/**
	 * Update the offsets displayed in the list editor
	 * @param target
	 * 		The RequestTarget used for updating.
	 */
	private void updateOffsets(AjaxRequestTarget target) {
		recalcOffsets();

		// Update the offsets
		List<Label> labels = editor.getComponentsById("offset", Label.class);
		for (Label lab : labels) {
			target.add(lab);
		}
	}

	/**
	 * Rebuild the edit links inside the list editor.
	 * 
	 * @param editor
	 *            The ListEditor instance
	 */
	private void rebuildLinks(ListEditor<?> editor) {
		List<EditorSubmitLink> moveDownLinks = editor.getComponentsById("move-down-link", EditorSubmitLink.class);
		List<EditorSubmitLink> moveUpLinks = editor.getComponentsById("move-up-link", EditorSubmitLink.class);
		for (int i = 0; i < editor.size(); i++) {
			moveUpLinks.get(i).setVisible(i > 0);
			moveDownLinks.get(i).setVisible(i < editor.size() - 1);
		}
	}

	private class HeaderFormValidator extends AbstractFormValidator {

		@Override
		public FormComponent<?>[] getDependentFormComponents() {
			return null;
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public void validate(Form<?> paramForm) {
			List<DropDownChoice> types = editor.getComponentsById("dataType", DropDownChoice.class);
			List<TextField> values = editor.getComponentsById("rawValue", TextField.class);
			boolean sizeTagDefined = false;

			if (!useHeaderCheckbox.getConvertedInput()) {
				return;
			}

			for (int i=0; i < types.size(); i++) {
				HeaderDataType type = (HeaderDataType) types.get(i).getConvertedInput();
				String inputVal=(String) values.get(i).getConvertedInput();
				int minimum = 0;
				int maximum = 65535;
				switch (type) {
				case PacketSize:
					sizeTagDefined = true;
					break;
				case Byte:
					maximum = 255;
				case Word:
					try {
						int parsedVal = Integer.decode(inputVal);
						if (parsedVal < minimum || parsedVal > maximum) {
							ValidationError error = new ValidationError();
							error.addKey("RangeValidator");
							error.setVariable("minimum", minimum);
							error.setVariable("maximum", maximum);
							values.get(i).error(error);
						} else {
							values.get(i).setConvertedInput(inputVal.trim().toLowerCase());
						}
					} catch (Exception e) {
						ValidationError error = new ValidationError();
						error.addKey("ParseableValidator");
						values.get(i).error(error);
					}
					break;
				default:break;
				}
			}

			// Check if a size tag is defined
			if (!sizeTagDefined) {
				ValidationError error = new ValidationError();
				error.addKey("NoSizeTag");
				useHeaderCheckbox.error(error);
			}

			// Check the handshake message
			if (useHandshakeCheckBox.getConvertedInput()) {
				// Initial value selected - Check data type
				String value = handshakeMsgTextField.getInput();

				try {
					Map<String, Number> map = new HashMap<String, Number>();
					map.put("timestamp", (Integer.valueOf(0)));
					map.put("sequence", (Short.valueOf((short)0)));
					map.put("lenb", (Byte.valueOf((byte) (10 & 0xff))));
					map.put("lenw", (Short.valueOf((short) (10 & 0xffff))));
					Util.hexString2ByteArray(value.trim(), map, ByteOrder.BIG_ENDIAN);
					handshakeMsgTextField.setConvertedInput(value.trim());
				} catch (Exception e) {
					ValidationError error = new ValidationError();
					error.addKey("ByteStringValidator");
					error.setVariable("error",e.getMessage());
					handshakeMsgTextField.error(error);
				}
			}

		}
	}

}
