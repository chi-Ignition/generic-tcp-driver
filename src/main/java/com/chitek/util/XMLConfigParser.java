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
package com.chitek.util;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class XMLConfigParser {

	private List<String> warnings;
	
	private Logger log = Logger.getLogger("XMLConfigParser");

	/**
	 * Parses the given XML and uses reflection to apply the configuration.<br />
	 * 'setting' uses the set... methods in the configuration for all found settings.
	 *  Supported types are boolean, int and String.<br />
	 * 'config' creates a new instance of the given type and uses the add... method
	 * in the configuration class to add new elements. The type of the new class is
	 * determined by the parameter type of the add... method. 
	 * 
	 * @param clazz
	 *            The Class to return
	 * @param typeName
	 *            The name of the XML-Element with the configuration
	 * @param configXML
	 *            The XML configuration to parse
	 * @return A new instance of clazz
	 * @throws Exception
	 */
	public Object parseXML(Class<?> clazz, String typeName, String configXML) throws Exception {
		Object config = clazz.newInstance();

		try {
			XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
			reader.setContentHandler(new ContentHandler(config, typeName));
			reader.parse(new InputSource(new StringReader(configXML)));
		} catch (Exception e) {
			throw e;
		}
		return config;
	}

	public String getWarnings() {
		if (warnings != null)
			return StringUtils.join(warnings.toArray(), System.getProperty("line.separator"));

		return null;
	}

	private void warn(String message) {
		if (warnings == null)
			warnings = new ArrayList<String>();
		warnings.add(message);
		log.trace(String.format("Warning: %s", message));
	}

	private class ContentHandler extends DefaultHandler {
		private StackElement parentElement;
		private StackElement currentElement;
		private Stack<StackElement> elementStack;
		private final String configTypeName;

		private StringBuffer chars;
		private String setting;
		private String unknown;
		private boolean inConfigSection;

		ContentHandler(Object config, String typeName) {
			this.configTypeName = typeName;
			this.chars = new StringBuffer();
			setting = null;
			unknown = null;
			inConfigSection = false;

			parentElement = null;
			currentElement = new StackElement(config, typeName);
			elementStack = new Stack<StackElement>();
		}

		public void characters(char[] ch, int start, int length) throws SAXException {
			chars.append(ch, start, length);
		}

		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

			if (unknown != null)
				return;

			if (inConfigSection && qName.equalsIgnoreCase("setting")) {
				setting = attributes.getValue("name");
				unknown = null;
				log.trace(String.format("Start Setting: %s", setting));
			} else if (qName.equalsIgnoreCase("config")) {
				String typeAttr = attributes.getValue("type").toLowerCase();

				setting = null;
				unknown = null;

				if (typeAttr.equalsIgnoreCase(configTypeName)) {
					if (inConfigSection)
						throw new SAXException("Duplicate 'config' element in XML.");

					inConfigSection = true;
				} else if (inConfigSection) {
					log.trace(String.format("Start Config: %s", typeAttr));
					// Check if the type is supported by the current class
					Method method = findMethod(currentElement.getObject(), "add" + typeAttr, Object.class);
					if (method != null) {
						// Save the current element
						parentElement = elementStack.push(currentElement);
						// Create a new instance of the parameter type
						// All subsequent settings will be applied to the new element
						Class<?> param = method.getParameterTypes()[0];
						try {
							currentElement = new StackElement(param.newInstance(), typeAttr);
						} catch (Exception e) {
							throw new SAXException("Instance of Class %s could not be created", e);
						}
					} else {
						// No 'addXXX' method found
						unknown = typeAttr;
						warn(String.format("Unknown config type '%s' in XML.", typeAttr));
					}
				} 
			} else {
				// Unknown type
				setting = null;
				unknown = qName.toLowerCase();
				warn(String.format("Unknown XML Element '%s'", qName));
			}
			chars.setLength(0); // Clear the char buffer

		}

		public void endElement(String uri, String localName, String qName) throws SAXException {

			if (unknown != null && qName.equalsIgnoreCase(unknown)) {
				// End of unknown Element found
				unknown = null;
				return;
			}

			if (qName.equalsIgnoreCase("config")) {

				if (parentElement == null) {
					// End of config section
					inConfigSection = false;
					return;
				}

				// End of sub-item
				log.trace(String.format("End Config: %s - Current class: %s - Parent: %s",
					currentElement.getTypeName(),
					currentElement.getObject().getClass().getSimpleName(),
					parentElement.getObject().getClass().getSimpleName()));
				Method method = findMethod(parentElement.getObject(),
					"add" + currentElement.getTypeName(),
					currentElement.getObject().getClass());
				if (method != null) {
					try {
						method.invoke(parentElement.getObject(), currentElement.getObject());
					} catch (Exception ex) {
						throw new SAXException(String.format("Error adding item of type '%s' to class '%s'",
							currentElement.getTypeName(),
							parentElement.getClass().getSimpleName(), ex));
					}
				} else {
					warn(String.format("Unknown type %s#%s",
						parentElement.getClass().getSimpleName(),
						currentElement.getTypeName()));
				}

				// Restore the parent element
				currentElement = elementStack.pop();
				parentElement = elementStack.isEmpty() ? null : elementStack.peek();

			} else if (qName.equalsIgnoreCase("setting") && setting != null) {
				String value = chars.toString().trim();
				log.trace(String.format("End Setting: %s Value: %s", setting, value));
				Method method = findMethod(currentElement.getObject(), "set" + setting, null);
				if (method != null) {

					Class<?> paramClass = method.getParameterTypes()[0];
					
					try {
						if (paramClass.isAssignableFrom(boolean.class)) {
							method.invoke(currentElement.getObject(), Boolean.parseBoolean(value));
						} else if (paramClass.isAssignableFrom(int.class)) {
							method.invoke(currentElement.getObject(), Integer.parseInt(value));
						} else if (paramClass.isAssignableFrom(String.class)) {
							method.invoke(currentElement.getObject(), value);
						} else {
							warn(String.format("No setter method for setting '%s' in class %s", setting, currentElement
								.getObject().getClass().getSimpleName()));
						}
					} catch (InvocationTargetException ex) { 
						throw new SAXException(String.format(
							"Error parsing configitem with name '%s' and value '%s'. Exception: %s", setting, value,
							ex.getCause()!=null ? ex.getCause().toString() : ex.toString()));
					} catch (Exception ex) {
						throw new SAXException(String.format(
							"Error parsing configitem with name '%s' and value '%s'. Exception: %s", setting, value,
							ex.toString()));
					}
				} else {
					warn(String.format("Unknown setting %s#%s ",
						currentElement.getObject().getClass().getSimpleName(),
						setting));
				}

				setting = null;
			}

		}

		public void fatalError(SAXParseException paramSAXParseException) throws SAXException {
			throw paramSAXParseException;
		}

		/**
		 * Searches the Object o for the Method with the given name. Only public methods with 1 parameter are returned.
		 * 
		 * @param o
		 * @param name
		 * @param paramClazz
		 * 		Class of the methods parameter. If null, only a method with a single boolean, int or String
		 * 		parameter will be returned.
		 * @return The Method, or null if there's no such method.
		 */
		private Method findMethod(Object o, String name, Class<?> paramClass) {
			Method[] methods = o.getClass().getDeclaredMethods();
			for (Method method : methods) {
				if (name.toLowerCase().equals(method.getName().toLowerCase())) {
					// Check if method is public
					if (Modifier.isPublic(method.getModifiers())) {
						// Make sure there is only 1 parameter
						Class<?>[] params = method.getParameterTypes();
						if (params.length == 1) {
							if (paramClass != null && paramClass.isAssignableFrom(params[0]))
								return method;
							else if(params[0].isAssignableFrom(boolean.class)
								|| params[0].isAssignableFrom(int.class)
								|| params[0].isAssignableFrom(String.class))
								return method;
						}				
					}
				}
			}
			return null;
		}
		
		private class StackElement {
			private Object object;
			private String typeName;
			
			public StackElement(Object object, String typeName) {
				this.object = object;
				this.typeName = typeName;
			}

			public Object getObject() {
				return object;
			}

			public String getTypeName() {
				return typeName;
			}
		}
	}

}
