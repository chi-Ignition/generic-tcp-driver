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
package com.chitek.wicket.listeditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;


@SuppressWarnings("serial")
public class UniqueListItemValidator<T> implements IValidator<T> {
	
	private FormComponent<T> parent;
	private String messageKey;
	private List<String> filter;
	private boolean caseSensitive;
	
	/**
	 * 
	 * @param parent
	 * 		The component this evaluator belongs to
	 */
	public UniqueListItemValidator(FormComponent<T> parent) {
		this(parent, false);
	}
	
	/**
	 * 
	 * @param parent
	 * 		The component this evaluator belongs to
	 * @param caseSensitive
	 * 		True to compare String values case sensitive. Default is false.
	 */
	public UniqueListItemValidator(FormComponent<T> parent, boolean caseSensitive) {
		this.parent = parent;
		this.caseSensitive = caseSensitive;
		messageKey="UniqueListItemValidator";
	}
	
	/**
	 * Set the message 
	 * 
	 * @param messageKey
	 * @return this for chaining
	 */
	public UniqueListItemValidator<T> setMessageKey(String messageKey) {
		this.messageKey=messageKey;
		return this;
	}

	/**
	 * When a filter is set, only values matching one of the filter strings are evaluated.
	 * 
	 * @param
	 * 		Array of Strings to be used as validation filter
	 * @return this for chaining
	 */
	public UniqueListItemValidator<T> setFilterList(String[] filter) {
		
		if (!caseSensitive) {
			this.filter = new ArrayList<String>();
			for (int i=0; i<filter.length; i++) {	
				this.filter.add(filter[i].toLowerCase());
				this.filter.add(filter[i].toUpperCase());
			}	
		} else
			this.filter=Arrays.asList(filter);
		
		return this;
	}
	
	/**
	 * Validator for items in {@link ListEditor}. Validates the input to be unique among all
	 * entrys in the list. 
	 */
	@SuppressWarnings("rawtypes") @Override
	public void validate(IValidatable<T> validatable) {
	
		ListEditor<?> editor = parent.findParent(ListEditor.class);
		String id = parent.getId();

		int count = 0;
		List<FormComponent> fields = editor.getComponentsById(id, FormComponent.class);

		String parentValue = getValue(validatable);
		
		if (filter != null && caseSensitive && !filter.contains(parentValue))
			return;
		if (filter != null && !caseSensitive && !filter.contains(parentValue.toLowerCase()))
			return;
		if (filter != null && !caseSensitive && !filter.contains(parentValue.toUpperCase()))
			return;
		
		for (FormComponent field : fields) {
			String value = field.getInput();

			if (value != null && (caseSensitive ? value.equals(parentValue) : value.equalsIgnoreCase(parentValue)) ) {
				count++;
			}
		}
			
		if (count > 1) {
			ValidationError error = new ValidationError();
			error.addKey(messageKey);
			validatable.error(error);
		}
	}
	
	/**
	 * Can be overridden to return the String representation of the components value.
	 * 
	 * The values of the list items are determined using {@link FormComponent#getInput()}, which returns
	 * the raw input, while the default implementation of getValue returns the ConvertedInput. 
	 * 
	 * @param validatable
	 * @return
	 */
	public String getValue(IValidatable<T> validatable) {
		return validatable.getValue().toString();
	}
}
