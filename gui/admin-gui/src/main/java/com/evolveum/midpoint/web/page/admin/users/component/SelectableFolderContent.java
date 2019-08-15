/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.page.admin.users.component;

import java.util.Optional;

import com.evolveum.midpoint.web.component.util.TreeSelectableBean;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.Folder;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;

/**
 * @author lazyman
 */
public class SelectableFolderContent extends Folder<TreeSelectableBean<OrgType>> {
	private static final long serialVersionUID = 1L;

    private AbstractTree tree;
    private IModel<TreeSelectableBean<OrgType>> selected;

    public SelectableFolderContent(String id, AbstractTree<TreeSelectableBean<OrgType>> tree, IModel<TreeSelectableBean<OrgType>> model,
                                   IModel<TreeSelectableBean<OrgType>> selected) {
        super(id, tree, model);

        this.tree = tree;
        this.selected = selected;
    }

    @Override
    protected IModel<?> newLabelModel(final IModel<TreeSelectableBean<OrgType>> model) {
        return new IModel<String>() {
        	private static final long serialVersionUID = 1L;

            @Override
            public String getObject() {
                TreeSelectableBean<OrgType> dto = model.getObject();
            	return WebComponentUtil.getEffectiveName(dto.getValue(), OrgType.F_DISPLAY_NAME);

            }
        };
    }

    @Override
    protected void onClick(Optional<AjaxRequestTarget> optionalTarget) {
        if (selected.getObject() != null) {
            tree.updateNode(selected.getObject(), optionalTarget.get());
        }

        TreeSelectableBean<OrgType> dto = getModelObject();
        selected.setObject(dto);
        tree.updateNode(dto, optionalTarget.get());
    }

    @Override
    protected boolean isClickable() {
        return true;
    }

    @Override
    protected boolean isSelected() {
    	SelectableBean<OrgType> dto = getModelObject();
        return dto.equals(selected.getObject());
    }

    @Override
    protected String getSelectedStyleClass() {
        return null;
    }
}
