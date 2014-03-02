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

import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.AbstractReadOnlyModel;


public class EditorListItem<T> extends Item<T>
{
	private static final long serialVersionUID = 1L;

	public EditorListItem(String id, int index)
    {
        super(id, index);
        setModel(new ListItemModel());
    }

    private class ListItemModel extends AbstractReadOnlyModel<T>
    {
		private static final long serialVersionUID = 1L;

		@SuppressWarnings("unchecked")
        @Override
        public T getObject()
        {
            return ((ListEditor<T>)EditorListItem.this.getParent()).items.get(getIndex());
        }
    }
}
