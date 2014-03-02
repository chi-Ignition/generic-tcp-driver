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

import java.util.Arrays;

import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;

/**
 * This validator accepts all Strings that do not match any of the given Strings.
 * If the input matches (ignoring case) any of the given Strings, then an error message will
 * be generated with the key "StringValidator.noMatch".
 * 
 * @author chi
 *
 */
public class NonMatchStringValidator implements IValidator<String> {
	private static final long serialVersionUID = 1L;
	private String[] matches;
	
	/**
	 * 
	 * @param matches
	 * 	An array of Strings that will cause validation to fail.
	 */
	public NonMatchStringValidator(String[] matches) {
		this.matches = matches;
	}
	
	@Override
	public void validate(IValidatable<String> validatable) {
		String value = validatable.getValue();
		for(String match : Arrays.asList(matches)) {
			if(value.equalsIgnoreCase(match)) {
				ValidationError error = new ValidationError();
				error.addKey("StringValidator.noMatch");
				validatable.error(error);
			}
		}
	}

}
