/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.application;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.AssignmentHolderDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.FocusDetailsModels;
import com.evolveum.midpoint.gui.impl.page.admin.focus.component.FocusProjectionsPanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;

import java.util.List;

public class FocusProjectionsCounter<F extends FocusType> extends SimpleCounter<FocusDetailsModels<F>, F> {

    public FocusProjectionsCounter() {
        super();
    }

    @Override
    public int count(FocusDetailsModels<F> objectDetailsModels, PageBase pageBase) {
        if (objectDetailsModels.getProjectionModel().isLoaded()) {
            return objectDetailsModels.getProjectionModel().getObject().size();
        }

        PrismObjectWrapper<F> assignmentHolderWrapper = objectDetailsModels.getObjectWrapperModel().getObject();
        F object = assignmentHolderWrapper.getObject().asObjectable();

        return WebComponentUtil.countLinkForNonDeadShadows(object.getLinkRef());
    }
}
