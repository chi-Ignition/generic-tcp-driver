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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.IAjaxIndicatorAware;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.extensions.ajax.markup.html.AjaxIndicatorAppender;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.form.validation.AbstractFormValidator;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.model.util.ListModel;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.apache.wicket.util.time.Duration;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.util.visit.IVisitor;
import org.apache.wicket.validation.CompoundValidator;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;
import org.apache.wicket.validation.validator.PatternValidator;
import org.apache.wicket.validation.validator.RangeValidator;
import org.apache.wicket.validation.validator.StringValidator;

import com.chitek.ignition.drivers.generictcp.meta.config.DriverConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.MessageConfig;
import com.chitek.ignition.drivers.generictcp.meta.config.TagConfig;
import com.chitek.ignition.drivers.generictcp.types.BinaryDataType;
import com.chitek.ignition.drivers.generictcp.types.MessageType;
import com.chitek.ignition.drivers.generictcp.types.OptionalDataType;
import com.chitek.ignition.drivers.generictcp.types.QueueMode;
import com.chitek.ignition.drivers.generictcp.types.TagLengthType;
import com.chitek.wicket.FeedbackTextField;
import com.chitek.wicket.NonMatchStringValidator;
import com.chitek.wicket.listeditor.EditorListItem;
import com.chitek.wicket.listeditor.EditorSubmitLink;
import com.chitek.wicket.listeditor.ListEditor;
import com.chitek.wicket.listeditor.UniqueListItemValidator;
import com.inductiveautomation.ignition.gateway.web.models.LenientResourceModel;

@SuppressWarnings("serial")
public class MessageConfigUI extends AbstractConfigUI<DriverConfig> implements IAjaxIndicatorAware {

	private final static String titleKey = "GenericTcpDriver.messageTab";

	/** Id of the message that is selected in the drop down */
	private int currentMessageId;

	/** The message config that is currently displayed in the editor */
	private MessageConfig currentMessage;
	private IModel<MessageConfig> currentMessageModel;

	// Components that need to be referenced after construction
	Form<?> editForm;
	private DropDownChoice<OptionalDataType> messageIdTypeDropDown;
	private DropDownChoice<Integer> currentMessageIdDropdown;
	private DropDownChoice<MessageType> messageTypeDropdown;
	private TextField<Integer> messageIdTextField;
	private TextField<String> messageAliasTextField;
	private ListEditor<TagConfig> editor;

	private final ResourceModel labelAlias = new LenientResourceModel("aliaslabel");
	private final ResourceModel labelId = new LenientResourceModel("idlabel");
	private final ResourceModel labelSize = new LenientResourceModel("sizelabel");

	// Special tag names that are not allowed as alias
	private static final String[] specialAlias = { "_Handshake", "_MessageCount", "_QueueSize", "_Timestamp", "_Message Age" };

	public MessageConfigUI(String panelId, DriverConfig config) {
		super(panelId, titleKey, config);

		addComponents();
	}

	@Override
	public String getAjaxIndicatorMarkupId() {
		return "ajax-indicator";
	}

	public Form<?> getForm() {
		return editForm;
	}
	
	private void addComponents() {

		// Select the first message for initial display
		if (getConfig().messages.isEmpty()) {
			// No messages configured. Create a new message with id=1.
			getConfig().addMessageConfig(new MessageConfig(1));
		}

		currentMessage = getConfig().getMessageList().get(0);
		currentMessage.calcOffsets(getConfig().getMessageIdType().getByteSize());
		currentMessageId = currentMessage.getMessageId();

		// *******************************************************************************************
		// *** Form for XML import
		final FileUploadField uploadField = new FileUploadField("upload-field", new ListModel<FileUpload>());

		Form<?> uploadForm = new Form<Object>("upload-form") {
			@Override
			protected void onSubmit() {
				try {
					FileUpload upload = uploadField.getFileUpload();
					if (upload != null) {
						handleOnUpload(upload.getInputStream());
					} else {
						warn(new StringResourceModel("warn.noFileToUpload", this, null).getString());
					}
				} catch (Exception e) {
					this.error(new StringResourceModel("import.error", this, null).getString() + " Exception: " + e.toString());
				}
			}
		};
		uploadForm.add(uploadField);

		SubmitLink importLink = new SubmitLink("import-link");
		uploadForm.add(importLink);

		add(uploadForm);

		// *******************************************************************************************
		// *** The message configuration
		currentMessageModel = new PropertyModel<MessageConfig>(this, "currentMessage");
		editForm = new Form<MessageConfig>("edit-form", new CompoundPropertyModel<MessageConfig>(currentMessageModel)) {
			@Override
			protected void onError() {
				// Validation error - reset the message dropdown to the original value
				// Clear input causes the component to reload the model
				currentMessageIdDropdown.clearInput();
				super.onError();
			}
		};

		editForm.add(new MessageFormValidator());
		
		WebMarkupContainer tableContainer = new WebMarkupContainer("table-container");

		messageIdTypeDropDown = getMessageIdTypeDropdown();
		tableContainer.add(messageIdTypeDropDown);

		currentMessageIdDropdown = getCurrentMessageIdDropdown();

		tableContainer.add(currentMessageIdDropdown);
		Button buttonNew = new Button("new");
		buttonNew.add(new AjaxFormSubmitBehavior("onclick") {
			@Override
			protected void onSubmit(AjaxRequestTarget target) {
				handleNew(target);
			}

			@Override
			protected void onError(AjaxRequestTarget target) {
				handleError(target);
			}
		});
		tableContainer.add(buttonNew);

		Button buttonCopy = new Button("copy");
		buttonCopy.add(new AjaxFormSubmitBehavior("onclick") {
			@Override
			protected void onSubmit(AjaxRequestTarget target) {
				handleCopy(target);
			}

			@Override
			protected void onError(AjaxRequestTarget target) {
				handleError(target);
			}
		});
		tableContainer.add(buttonCopy);

		Button deleteButton = new Button("delete");
		deleteButton.add(new AjaxEventBehavior("onclick") {
			@Override
			protected void onEvent(AjaxRequestTarget target) {
				handleDelete(target);
			}
		});
		tableContainer.add(deleteButton);
		
		messageTypeDropdown = getMessageTypeDropdown(); 
		tableContainer.add(messageTypeDropdown);

		tableContainer.add(getQueueModeDropdown());

		tableContainer.add(new CheckBox("usePersistance").setOutputMarkupId(true));

		WebMarkupContainer listEditorContainer = new WebMarkupContainer("list-editor");
		
		messageIdTextField = getMessageIdTextField();
		listEditorContainer.add(messageIdTextField);

		messageAliasTextField = getMessageAliasTextField();
		listEditorContainer.add(messageAliasTextField);

		// Create the list editor
		editor = new ListEditor<TagConfig>("tags", new PropertyModel<List<TagConfig>>(currentMessageModel, "tags")) {
			@Override
			protected void onPopulateItem(EditorListItem<TagConfig> item) {

				item.setModel(new CompoundPropertyModel<TagConfig>(item.getModelObject()));

				BinaryDataType dataType = item.getModelObject().getDataType();
				boolean enable = dataType.isSpecial() ? false : true;

				// Offset is displayed only for information
				item.add(new Label("offset").setOutputMarkupId(true));

				if (enable) {
					item.add(getIdTextField());
				} else {
					// The static TextField has no validation. Validation would fail for special tags.
					item.add(getSpecialIdTextField().setEnabled(false));
				}

				item.add(getAliasTextField().setVisible(enable));

				item.add(getSizeTextField().setEnabled(dataType.isArrayAllowed()));
				
				item.add(getTagLengthTypeDropDown().setEnabled(dataType.supportsVariableLength()));

				item.add(getDataTypeDropdown());

				// Create the edit links to be used in the list editor
				item.add(getInsertLink());
				item.add(getDeleteLink());
				item.add(getMoveUpLink().setVisible(item.getIndex() > 0));
				item.add(getMoveDownLink().setVisible(item.getIndex() < getList().size() - 1));
			}
		};
		listEditorContainer.add(editor);

		Label noItemsLabel = new Label("no-items-label", new StringResourceModel("noitems", this, null)) {
			@Override
			public boolean isVisible() {
				return editor.getList().size() == 0;
			}
		};

		listEditorContainer.add(noItemsLabel);

		listEditorContainer.add(new EditorSubmitLink("add-row-link") {
			@Override
			public void onSubmit() {
				editor.addItem(new TagConfig());

				// Adjust the visibility of the edit links
				updateListEditor(editor);
			}
		});

		listEditorContainer.setOutputMarkupId(true);
		
		tableContainer.add(listEditorContainer);
		editForm.add(tableContainer);

		// XML export
		SubmitLink exportLink = new SubmitLink("export-link", editForm) {
			@Override
			public void onSubmit() {
				ResourceStreamRequestHandler handler = new ResourceStreamRequestHandler(getResourceStream(), getFileName());
				handler.setContentDisposition(ContentDisposition.ATTACHMENT);
				handler.setCacheDuration(Duration.NONE);
				getRequestCycle().scheduleRequestHandlerAfterCurrent(handler);
			}

			private String getFileName() {
				return String.format("MsgConfig_%s.xml", currentMessageModel.getObject().getMessageId());
			}

			private IResourceStream getResourceStream() {
				String config = currentMessageModel.getObject().toXMLString();
				return new StringResourceStream(config, "text/xml");
			}
		};
		editForm.add(exportLink);

		add(editForm);
	}

	private DropDownChoice<Integer> getCurrentMessageIdDropdown() {

		IChoiceRenderer<Integer> messageConfigRender = new IChoiceRenderer<Integer>() {
			@Override
			public Object getDisplayValue(Integer object) {
				return String.format("%d - %s", object, getConfig().getMessageConfig(object).getMessageAlias());
			}

			@Override
			public String getIdValue(Integer object, int index) {
				return object.toString();
			}
		};

		IModel<List<Integer>> messageListModel = new LoadableDetachableModel<List<Integer>>() {
			@Override
			protected List<Integer> load() {
				List<MessageConfig> messages = getConfig().getMessageList();
				List<Integer> list = new ArrayList<Integer>(messages.size());
				for (MessageConfig messageConfig : messages) {
					list.add(messageConfig.getMessageId());
				}
				return list;
			}
		};

		DropDownChoice<Integer> dropDown = new DropDownChoice<Integer>("currentMessage", new PropertyModel<Integer>(this, "currentMessageId"), messageListModel, messageConfigRender);

		dropDown.add(new AjaxFormSubmitBehavior("onchange") {

			@Override
			protected void onSubmit(final AjaxRequestTarget target) {

				// Reset feedback messages
				target.addChildren(getPage(), FeedbackPanel.class);

				// Change the current message
				currentMessage = getConfig().getMessageConfig(currentMessageId);
				currentMessage.calcOffsets(getConfig().getMessageIdType().getByteSize());

				// Refresh the form
				updateForm(target);
			}

			@Override
			protected void onError(final AjaxRequestTarget target) {
				// Add the drop down to revert the changed selection
				target.add(getComponent());

				handleError(target);
			};
		});

		dropDown.add(new AjaxIndicatorAppender());
		dropDown.setOutputMarkupId(true);

		return dropDown;
	}

	private DropDownChoice<QueueMode> getQueueModeDropdown() {
		DropDownChoice<QueueMode> dropDown = new DropDownChoice<QueueMode>("queueMode", QueueMode.getOptions(), new EnumChoiceRenderer<QueueMode>(this));
		dropDown.setOutputMarkupId(true);
		return dropDown;
	}
	
	private DropDownChoice<MessageType> getMessageTypeDropdown() {
		DropDownChoice<MessageType> dropDown = new DropDownChoice<MessageType>("messageType", MessageType.getOptions(), new EnumChoiceRenderer<MessageType>(this));
		dropDown.setOutputMarkupId(true);
		
		// When the MessageType is changed, the tag length type dropdowns are updated
		dropDown.add(new AjaxFormComponentUpdatingBehavior("onchange") {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				updateTagLength(target);

				// Reset feedback messages
				target.addChildren(getPage(), FeedbackPanel.class);
			}
		});
		
		return dropDown;
	}

	private DropDownChoice<OptionalDataType> getMessageIdTypeDropdown() {
		DropDownChoice<OptionalDataType> dropDown = new DropDownChoice<OptionalDataType>("messageIdType", new PropertyModel<OptionalDataType>(getDefaultModel(), "messageIdType"),
				OptionalDataType.getOptions(), new EnumChoiceRenderer<OptionalDataType>(this));

		// When the MessageId Type is changed, the offsets are recalculated and displayed
		dropDown.add(new AjaxFormComponentUpdatingBehavior("onchange") {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				updateOffsets(target);

				// Reset feedback messages
				target.addChildren(getPage(), FeedbackPanel.class);
			}
		});

		return dropDown;
	}

	private TextField<Integer> getMessageIdTextField() {

		TextField<Integer> textField = new FeedbackTextField<Integer>("messageId");
		textField.setRequired(true);

		CompoundValidator<Integer> validator = new CompoundValidator<Integer>();
		validator.add(new RangeValidator<Integer>(0, 65535));
		validator.add(new UniqueMessageIdValidator());
		textField.add(validator);
		textField.setOutputMarkupId(true);

		textField.add(new AjaxFormComponentUpdatingBehavior("onchange") {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				// The id is the key for the message map, so we have to replace it here
				getConfig().messages.remove(currentMessageId);
				getConfig().addMessageConfig(currentMessage);
				currentMessageId = currentMessage.getMessageId();
				target.add(currentMessageIdDropdown);

				// Clear feedback messages
				target.addChildren(getPage(), FeedbackPanel.class);
				target.add(getComponent());
			}

			@Override
			protected void onError(AjaxRequestTarget target, RuntimeException e) {
				target.addChildren(getPage(), FeedbackPanel.class);
				target.add(getComponent());
			}
		});

		return textField;
	}

	private TextField<String> getMessageAliasTextField() {

		TextField<String> textField = new FeedbackTextField<String>("messageAlias");
		textField.setRequired(true);

		CompoundValidator<String> validator = new CompoundValidator<String>();

		validator.add(new PatternValidator("[A-Za-z0-9_]+"));
		validator.add(StringValidator.lengthBetween(1, 32));
		validator.add(new UniqueMessageAliasValidator());
		textField.add(validator);

		textField.setLabel(labelAlias); // Use the same label as the items
		textField.setOutputMarkupId(true);

		textField.add(new AjaxFormComponentUpdatingBehavior("onchange") {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(currentMessageIdDropdown);
				target.addChildren(getPage(), FeedbackPanel.class);
				target.add(getComponent());
			}

			@Override
			protected void onError(AjaxRequestTarget target, RuntimeException e) {
				target.addChildren(getPage(), FeedbackPanel.class);
				target.add(getComponent());
			}
		});

		return textField;
	}

	private TextField<Integer> getIdTextField() {

		TextField<Integer> textField = new FeedbackTextField<Integer>("id");
		textField.setRequired(true);

		CompoundValidator<Integer> validator = new CompoundValidator<Integer>();
		validator.add(new UniqueListItemValidator<Integer>(textField).setMessageKey("id.UniqueValueValidator"));
		validator.add(new RangeValidator<Integer>(1, 254));
		textField.add(validator);

		textField.setLabel(labelId);
		textField.setOutputMarkupId(true);

		return textField;
	}

	/**
	 * The ID field for special DataTypes. Range will not be evaluated.
	 * 
	 * @return
	 */
	private TextField<Integer> getSpecialIdTextField() {

		TextField<Integer> textField = new TextField<Integer>("id");
		textField.setRequired(true);

		textField.add(new UniqueListItemValidator<Integer>(textField).setMessageKey("id.UniqueValueValidator"));

		textField.setLabel(labelId);
		textField.setOutputMarkupId(true);

		return textField;
	}

	private TextField<Integer> getSizeTextField() {

		TextField<Integer> textField = new FeedbackTextField<Integer>("size");

		textField.setRequired(true);
		textField.add(new RangeValidator<Integer>(1, 1000));

		textField.setLabel(labelSize);

		textField.setOutputMarkupId(true);

		// When the size is changed, the offsets are recalculated and displayed
		textField.add(new OnChangeAjaxBehavior() {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				updateOffsets(target);
			}
		});

		return textField;
	}

	private DropDownChoice<TagLengthType> getTagLengthTypeDropDown() {
		DropDownChoice<TagLengthType> dropDown = new DropDownChoice<TagLengthType>("tagLengthType", TagLengthType.getOptions(), new EnumChoiceRenderer<TagLengthType>(this)) {
			@Override
			public boolean isVisible() {
				return currentMessage.isVariableLength();
			}
		};
		
		dropDown.add(new AjaxFormComponentUpdatingBehavior("onchange") {
	
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(feedback);
			}
			
			@Override
			protected void onError(AjaxRequestTarget target, RuntimeException e) {
				target.add(feedback);
			}
		});
		
		dropDown.setOutputMarkupId(true);
		dropDown.setOutputMarkupPlaceholderTag(true);
		
		dropDown.add(new UniqueListItemValidator<TagLengthType>(dropDown) {
			@Override
			public String getValue(IValidatable<TagLengthType> validatable) {
				return String.valueOf(validatable.getValue().name());
			}
		}
		.setMessageKey("tagLengthType.OnlyOnePackedBasedValidator")
		.setFilterList(new String[] { TagLengthType.PACKET_BASED.name() }));
		
		return dropDown;		
	}
	
	private TextField<String> getAliasTextField() {
		TextField<String> textField = new FeedbackTextField<String>("alias");

		textField.setRequired(true);

		CompoundValidator<String> validator = new CompoundValidator<String>();
		validator.add(new PatternValidator("[A-Za-z0-9_]+"));

		validator.add(StringValidator.lengthBetween(1, 32));

		validator.add(new UniqueListItemValidator<String>(textField).setMessageKey("alias.UniqueValueValidator"));

		validator.add(new NonMatchStringValidator(specialAlias));

		textField.add(validator);
		textField.setLabel(labelAlias);
		textField.setOutputMarkupId(true);
		textField.setOutputMarkupPlaceholderTag(true);

		return textField;
	}

	/**
	 * Create the DataType dropdown. When the datatype is changed, the offsets will be recalculated and updated.
	 */
	private DropDownChoice<BinaryDataType> getDataTypeDropdown() {
		DropDownChoice<BinaryDataType> dropDown = new DropDownChoice<BinaryDataType>("dataType", BinaryDataType.getOptions(), new EnumChoiceRenderer<BinaryDataType>(this)) {
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

		// When the datatype is changed, the offsets are recalculated and displayed
		dropDown.add(new OnChangeAjaxBehavior() {
			@SuppressWarnings("unchecked")
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				// Recalculate and display the offsets
				updateOffsets(target);

				// Disable input fields when DataType is a special type.
				// Current state is determined by the enabled state of TextField("id")
				DropDownChoice<BinaryDataType> choice = (DropDownChoice<BinaryDataType>) getComponent();
				MarkupContainer parent = choice.getParent();
				Component idTextField = parent.get("id");
				BinaryDataType dataType = choice.getConvertedInput();
				if (dataType.isSpecial() && idTextField.isEnabled()) {
					// DataType changed to special type
					// Store current values
					((TextField<Integer>) idTextField).getRawInput();
					idTextField.replaceWith(getSpecialIdTextField().setEnabled(false));
					target.add(parent.get("id"));
					target.add(parent.get("alias").setVisible(false));
				} else if (!idTextField.isEnabled()) {
					idTextField.replaceWith(getIdTextField());
					target.add(parent.get("id").setEnabled(true));
					target.add(parent.get("alias").setVisible(true));
				}
				target.add(parent.get("size").setEnabled(dataType.isArrayAllowed()));
				target.add(parent.get("tagLengthType").setEnabled(dataType.supportsVariableLength()));
				if (!dataType.supportsVariableLength()) {
					EditorListItem<TagConfig> listItem = getComponent().findParent(EditorListItem.class);
					if (listItem != null) {
						listItem.getModelObject().setTagLengthType(TagLengthType.FIXED_LENGTH);
					}
				}
			}
		});

		dropDown.add(new UniqueListItemValidator<BinaryDataType>(dropDown) {
			@Override
			public String getValue(IValidatable<BinaryDataType> validatable) {
				return String.valueOf(validatable.getValue().name());
			}
		}.setMessageKey("dataType.SpecialTypesValidator").setFilterList(new String[] { BinaryDataType.MessageAge.name() }));

		return dropDown;
	}

	private EditorSubmitLink getInsertLink() {
		EditorSubmitLink insertLink = new EditorSubmitLink("insert-link") {

			@Override
			public void onSubmit() {
				ListEditor<TagConfig> editor = getEditor();
				// add a new item at the end of the list
				addItem(new TagConfig());

				// shift the index property of all items after current one
				int idx = getItem().getIndex();

				// Move the new item up to the required position
				for (int i = editor.size() - 1; i > idx; i--) {
					editor.swapItems(i, i - 1);
				}

				// Refresh the editor
				updateListEditor(editor);
			}
		};

		return insertLink;
	}

	private EditorSubmitLink getDeleteLink() {
		EditorSubmitLink deleteLink = new EditorSubmitLink("delete-link") {
			@Override
			public void onSubmit() {
				ListEditor<TagConfig> editor = getEditor();
				int idx = getItem().getIndex();
				for (int i = idx + 1; i < getItem().getParent().size(); i++) {
					EditorListItem<?> item = (EditorListItem<?>) getItem().getParent().get(i);
					item.setIndex(item.getIndex() - 1);
				}
				getList().remove(idx);
				editor.remove(getItem());

				updateListEditor(editor);
			}
		};

		return deleteLink;
	}

	private EditorSubmitLink getMoveUpLink() {
		EditorSubmitLink moveUpLink = new EditorSubmitLink("move-up-link") {
			@Override
			public void onSubmit() {
				ListEditor<TagConfig> editor = getEditor();
				int idx = getItem().getIndex();
				editor.swapItems(idx - 1, idx);

				updateListEditor(editor);
			}
		};

		return moveUpLink;
	}

	private EditorSubmitLink getMoveDownLink() {
		EditorSubmitLink moveUpLink = new EditorSubmitLink("move-down-link") {
			@Override
			public void onSubmit() {
				ListEditor<TagConfig> editor = getEditor();
				int idx = getItem().getIndex();
				editor.swapItems(idx, idx + 1);

				updateListEditor(editor);
			}
		};

		return moveUpLink;
	}

	public MessageConfig getCurrentMessage() {
		return currentMessage;
	}

	private void handleOnUpload(InputStream inputStream) {

		try {
			StringBuilder sb = new StringBuilder();

			BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			while ((line = br.readLine()) != null)
				sb.append(line);
			br.close();

			MessageConfig importConfig = MessageConfig.fromXMLString(sb.toString());

			importConfig.setMessageId(currentMessageId);
			getConfig().addMessageConfig(importConfig);
			currentMessage = importConfig;
			currentMessageId = importConfig.messageId;

			// Refresh the model data in the list editor
			currentMessageIdDropdown.clearInput();
			messageIdTextField.clearInput();
			messageAliasTextField.clearInput();
			editor.reloadModel();

			// Make sure the new alias is unique (can't be done in the validator)
			for (MessageConfig message : getConfig().getMessageList()) {
				if (importConfig.getMessageAlias().equalsIgnoreCase(message.getMessageAlias())) {
					ValidationError error = new ValidationError();
					error.addKey(UniqueMessageAliasValidator.messageKey);
					messageAliasTextField.error(error);
				}
			}

			info(new StringResourceModel("info.import", this, null, currentMessageId).getString());

		} catch (Exception e) {
			error(getString("import.error") + " Exception: " + e.toString());
		}

	}

	/**
	 * Common handler for errors during an Ajax submit
	 */
	private void handleError(final AjaxRequestTarget target) {
		// Update feedback panel and components with errors
		target.addChildren(getPage(), FeedbackPanel.class);
		target.getPage().visitChildren(FormComponent.class, new IVisitor<Component, Void>() {
			@Override
			public void component(Component component, IVisit<Void> arg1) {
				if (component.hasErrorMessage()) {
					target.add(component);
				}
			}
		});
	}

	/**
	 * Button 'New' - Create a new message
	 */
	private void handleNew(AjaxRequestTarget target) {

		// Increment message ID
		int newMessageId = getNextMessageId(currentMessageId);

		// Add the new message
		currentMessage = new MessageConfig(newMessageId);
		getConfig().addMessageConfig(currentMessage);
		currentMessageId = newMessageId;

		// Show feedback messages
		info(new StringResourceModel("info.new", this, null, newMessageId).getString());
		target.addChildren(getPage(), FeedbackPanel.class);

		doValidation();

		currentMessageIdDropdown.detachModels(); // This is necessary to refresh the drop down
		updateForm(target);
	}

	/**
	 * Button 'Copy' - Copy the current message
	 */
	private void handleCopy(AjaxRequestTarget target) {

		// Increment message ID
		int newMessageId = getNextMessageId(currentMessageId);

		// Copy and add the new message
		MessageConfig current = currentMessage;
		currentMessage = current.copy();
		currentMessage.setMessageId(newMessageId);
		currentMessage.setMessageAlias("");
		getConfig().addMessageConfig(currentMessage);
		currentMessageId = newMessageId;

		// Show feedback messages
		info(new StringResourceModel("info.copy", this, null, newMessageId).getString());
		target.addChildren(getPage(), FeedbackPanel.class);

		doValidation();

		currentMessageIdDropdown.detachModels(); // This is necessary to refresh the drop down
		updateForm(target);
	}

	/**
	 * Button 'Delete' (Ajax). Delete the current message.
	 */
	private void handleDelete(AjaxRequestTarget target) {

		getConfig().messages.remove(currentMessageId);

		// Show feedback messages
		info(new StringResourceModel("info.deleted", this, null, currentMessageId).getString());
		target.addChildren(getPage(), FeedbackPanel.class);

		if (getConfig().messages.isEmpty()) {
			// No message left. Create a new message with id=1.
			getConfig().addMessageConfig(new MessageConfig(1));
		}
		// Select the first configured message for initial display
		currentMessage = getConfig().getMessageList().get(0);
		currentMessageId = currentMessage.messageId;

		// Refresh the drop down choice
		target.add(currentMessageIdDropdown);

		doValidation();

		updateForm(target);
	}

	/**
	 * Some common checks that can not be done in validators
	 */
	private void doValidation() {

		if (getConfig().getMessageIdType() == OptionalDataType.None) {
			if (getConfig().getMessageConfig(0) == null)
				warn(new StringResourceModel("warn.noMessageId0", this, null).getString());
		} else if (getConfig().getMessageIdType() == OptionalDataType.UByte) {
			boolean ok = true;
			for (MessageConfig message : getConfig().getMessageList()) {
				if (message.getMessageId() > 255) {
					ok = false;
					break;
				}
			}
			if (!ok)
				warn(new StringResourceModel("warn.MessageIdGT255", this, null).getString());
		}
	}

	/**
	 * Get the next available message id.
	 * 
	 * @param id
	 * @return The next available message id greater than the given id.
	 */
	private int getNextMessageId(int id) {

		int newId = id + 1;
		for (MessageConfig message : getConfig().getMessageList()) {
			if (message.getMessageId() > id) {
				if (message.getMessageId() == newId)
					newId++;
				else
					break;
			}
		}
		return newId;
	}

	/**
	 * Recalculate the offsets in the editor's internal list
	 */
	private void recalcOffsets() {
		int offset = getConfig().getMessageIdType().getByteSize();
		for (TagConfig tc : editor.getList()) {
			tc.setOffset(offset);
			offset += tc.getDataType().getByteCount() * tc.getSize();
		}
	}

	/**
	 * Update the edit form
	 */
	private void updateForm(AjaxRequestTarget target) {
		// Refresh the drop down choice
		target.add(currentMessageIdDropdown);
		target.add(target.getPage().get("config-contents:tabs:panel:edit-form:table-container:usePersistance"));
		target.add(target.getPage().get("config-contents:tabs:panel:edit-form:table-container:queueMode"));
		target.add(target.getPage().get("config-contents:tabs:panel:edit-form:table-container:messageType"));

		// Refresh the form
		editor.reloadModel();
		MarkupContainer listEditorContainer = (MarkupContainer) target.getPage().get("config-contents:tabs:panel:edit-form:table-container:list-editor");
		target.add(listEditorContainer);
	}

	/**
	 * Update the tag length input in the list editor
	 * @param target
	 */
	private void updateTagLength(AjaxRequestTarget target) {
				
		@SuppressWarnings("rawtypes")
		List<DropDownChoice> choices = editor.getComponentsById("tagLengthType", DropDownChoice.class);
		for (DropDownChoice<?> choice : choices) {
			target.add(choice);
		}
	}
	
	/**
	 * Update the offsets displayed in the list editor
	 */
	private void updateOffsets(AjaxRequestTarget target) {
		recalcOffsets();

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
	private void updateListEditor(ListEditor<?> editor) {
		// Update links
		List<EditorSubmitLink> moveDownLinks = editor.getComponentsById("move-down-link", EditorSubmitLink.class);
		List<EditorSubmitLink> moveUpLinks = editor.getComponentsById("move-up-link", EditorSubmitLink.class);
		for (int i = 0; i < editor.size(); i++) {
			moveUpLinks.get(i).setVisible(i > 0);
			moveDownLinks.get(i).setVisible(i < editor.size() - 1);
		}

		// Recalculate the offsets
		recalcOffsets();
	}

	private class UniqueMessageIdValidator implements IValidator<Integer> {
		private static final String messageKey = "UniqueMessageIdValidator";

		@Override
		public void validate(IValidatable<Integer> validatable) {
			int value = validatable.getValue();

			// Id changed - make sure it is not used
			for (MessageConfig message : getConfig().getMessageList()) {
				if (message.getMessageId() == value && message != currentMessage) {
					ValidationError error = new ValidationError();
					error.addKey(messageKey);
					validatable.error(error);
					return;
				}
			}
		}
	}

	/**
	 * Checks is the message alias is unique amongst all defined messages
	 * 
	 */
	private class UniqueMessageAliasValidator implements IValidator<String> {
		static final String messageKey = "UniqueMessageAliasValidator";

		@Override
		public void validate(IValidatable<String> validatable) {
			String value = validatable.getValue();
			// The model has not yet been updated, so we can check if the alias has been changed
			if (value.equalsIgnoreCase(currentMessageModel.getObject().getMessageAlias()))
				return;
			// Alias changed - make sure it is not used
			for (MessageConfig message : getConfig().getMessageList()) {
				if (value.equalsIgnoreCase(message.getMessageAlias())) {
					ValidationError error = new ValidationError();
					error.addKey(messageKey);
					validatable.error(error);
					return;
				}
			}
		}

	}

	private class MessageFormValidator extends AbstractFormValidator {

		@Override
		public FormComponent<?>[] getDependentFormComponents() {
			return null;
		}

		@Override
		public void validate(Form<?> paramForm) {
			if (getConfig().getMessageIdType() == OptionalDataType.None) {
				if (getConfig().getMessageConfig(0) == null) {
					error(messageIdTextField, "warn.noMessageId0");
					// error(new StringResourceModel("warn.noMessageId0", this, null).getString());
				}
			}
			
			editor.updateModel();
			
			if (currentMessage.getMessageType() == MessageType.PACKET_BASED) {
				// Make sure the is an variable length tag
				boolean ok = false;
				for (TagConfig tagConfig : currentMessage.getTags()) {
					if (tagConfig.getTagLengthType() == TagLengthType.PACKET_BASED) {
						ok = true;
						break;
					}
				}
				if (!ok) {
					error(messageTypeDropdown, "error.noPacketBasedLengthTag");
				}
			}
		}
		
	}
	
}
