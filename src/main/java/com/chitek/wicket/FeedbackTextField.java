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
package com.chitek.wicket;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;

/**
 * This class extends TextField with a error feedback indicator. Validation errors are indicated by applying a custom
 * style to the component tag. Default is 'background-color:#ffff00;'
 * 
 * 
 * @author chi
 *
 * @param <T>
 * 		The model object type
 */
public class FeedbackTextField<T> extends TextField<T> {
	private static final long serialVersionUID = 1L;
	private String style = "background-color:#ffff00;";
	
	public FeedbackTextField(String id) {
		super(id);
	}

	public FeedbackTextField(String id, Class<T> type) {
		super(id, type);
	}
	
	public FeedbackTextField(String id, IModel<T> model) {
		super(id, model);
	}
	
	public FeedbackTextField(String id, IModel<T> model, Class<T> type) {
		super(id, model, type);
	}
	
	@Override
	protected void onComponentTag(ComponentTag tag) {
		super.onComponentTag(tag);
		if (hasErrorMessage()) {
			String oldStyle = tag.getAttribute("style");
			tag.put("style", style + oldStyle);
		} 
	}
	
	/**
	 * Sets the style to apply in case of an validation error.
	 * 
	 * @param style
	 * 		A CSS style, must end with ';'
	 * @return
	 * 		This, for chaining
	 */
	public FeedbackTextField<T> setStyle(String style) {
		this.style = style;
		return this;
	}
};
	

