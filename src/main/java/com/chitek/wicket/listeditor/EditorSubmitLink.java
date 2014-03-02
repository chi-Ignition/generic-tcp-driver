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

import java.util.List;

import org.apache.wicket.markup.html.form.SubmitLink;

/**
 * Class for edit links to be used with the list editor.
 * This class allows to find the ListEditor instance instead of having to pass it into the link.
 * 
 */
@SuppressWarnings("serial")
public abstract class EditorSubmitLink extends SubmitLink
{
    private transient EditorListItem< ? > parent;

    public EditorSubmitLink(String id)
    {
    	super(id);
    	setDefaultFormProcessing(false);
    }

    protected final EditorListItem< ? > getItem()
    {
        if (parent == null)
        {
            parent = findParent(EditorListItem.class);
        }
        return parent;
    }

    protected final List< ? > getList()
    {
        return getEditor().items;
    }
    
    /**
     * Calls ListEditor.addItem, added for convenience
     * 
     * @param value
     * @return The new created ListItem
     */
	@SuppressWarnings("unchecked")
	protected final <T> EditorListItem<T> addItem(T value) {
    	return ((ListEditor<T>)getEditor()).addItem(value);
    	
    }

    @SuppressWarnings("unchecked")
	protected final <T> ListEditor< T > getEditor()
    {
        return (ListEditor< T >)getItem().getParent();
    }


    @Override
    protected void onDetach()
    {
        parent = null;
        super.onDetach();
    }

}
