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
import java.util.Collections;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.form.IFormModelUpdateListener;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.util.visit.IVisitor;


@SuppressWarnings("serial")
public abstract class ListEditor<T> extends RepeatingView implements IFormModelUpdateListener {
	protected List<T> items;
	private boolean modelRendered=false;

	public ListEditor(String id, IModel<List<T>> model) {
		super(id, model);
	}

	protected abstract void onPopulateItem(EditorListItem<T> item);

	/**
	 * Adds a new item
	 * 
	 * @param value
	 * @return The new created ListItem
	 */
	public EditorListItem<T> addItem(T value) {
		items.add(value);
		EditorListItem<T> item = new EditorListItem<T>(newChildId(), items.size() - 1);
		add(item);
		onPopulateItem(item);
		return item;
	}

	/**
	 * Swaps position of to list items. Use this method instead of swap to keep the underlying model in sync.
	 * 
	 * @param idx1
	 *            index of first component to be swapped
	 * @param idx2
	 *            index of second component to be swapped
	 */
	@SuppressWarnings("unchecked")
	public void swapItems(int idx1, int idx2) {
		int size = size();
		if (idx1 < 0 || idx1 >= size) {
			throw new IndexOutOfBoundsException("Argument idx is out of bounds: " + idx1 + "<>[0,"
					+ size + ")");
		}

		if (idx2 < 0 || idx2 >= size) {
			throw new IndexOutOfBoundsException("Argument idx is out of bounds: " + idx2 + "<>[0,"
					+ size + ")");
		}

		if (idx1 == idx2) {
			return;
		}
		// Swap the index property of the ListItems
		((EditorListItem<T>) this.get(idx1)).setIndex(idx2);
		((EditorListItem<T>) this.get(idx2)).setIndex(idx1);

		swap(idx1, idx2);
		Collections.swap(items, idx1, idx2);
	}

	@Override
	protected void onBeforeRender() {
		if (!modelRendered) {
			items = new ArrayList<T>(getModelObject());
			for (int i = 0; i < items.size(); i++) {
				EditorListItem<T> li = new EditorListItem<T>(newChildId(), i);
				add(li);
				onPopulateItem(li);
			}
			modelRendered=true;
		}
		super.onBeforeRender();
	}

	public void updateModel() {
		setModelObject(items);
	}

	/**
	 * Gets model
	 * 
	 * @return model
	 */
	@SuppressWarnings("unchecked")
	public final IModel<List<T>> getModel() {
		return (IModel<List<T>>) getDefaultModel();
	}

	/**
	 * Sets model
	 * 
	 * @param model
	 */
	public final void setModel(IModel<List<T>> model) {
		setDefaultModel(model);
	}

	/**
	 * Gets model object
	 * 
	 * @return model object
	 */
	@SuppressWarnings("unchecked")
	public final List<T> getModelObject() {
		return (List<T>) getDefaultModelObject();
	}

	/**
	 * Sets model object
	 * 
	 * @param object
	 */
	public final void setModelObject(List<T> object) {
		setDefaultModelObject(object);
	}
	
	/**
	 * Forces a new rendering of the child elements after model data has been changed
	 */
	public final void reloadModel() {
		removeAll();
		modelRendered=false;
	}

	/**
	 * Gets the list of items in the ListEditor.
	 * 
	 * @return
	 * 		The list of items
	 */
	public final List<T> getList() {
		return items;
	}
	
	/**
	 * Returns a List with all child components that match the given id and class.
	 * 
	 * @param id
	 * 		ID of child components to return
	 * @param clazz
	 * 		Ther class of the components to return
	 * @return
	 */
	public <K extends Component> List<K> getComponentsById(final String id, Class<K> clazz) {
		final List<K> list = new ArrayList<K>(this.size());

		visitChildren(clazz, new IVisitor<K,Boolean>() {
			@Override
			public void component(K component, IVisit<Boolean> visit) {
				if (component.getId().equals(id)) {
					list.add(component);
					visit.dontGoDeeper();
				}
			}
		});

		return list;
	}
}
