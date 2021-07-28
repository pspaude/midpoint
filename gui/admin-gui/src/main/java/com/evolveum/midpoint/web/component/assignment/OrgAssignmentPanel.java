/*
 * Copyright (C) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.assignment;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.web.application.PanelDescription;
import com.evolveum.midpoint.web.application.PanelDisplay;

@PanelDescription(panelIdentifier = "orgAssignments", identifier = "orgAssignments", applicableFor = FocusType.class)
@PanelDisplay(label = "Org")
public class OrgAssignmentPanel extends AbstractRoleAssignmentPanel {

    private static final long serialVersionUID = 1L;

    protected static final String DOT_CLASS = OrgAssignmentPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_TARGET_REF_OBJECT = DOT_CLASS + "loadAssignmentTargetRefObject";

    private ContainerPanelConfigurationType containerPanelConfigurationType;

    public OrgAssignmentPanel(String id, IModel<PrismContainerWrapper<AssignmentType>> assignmentContainerWrapperModel) {
        super(id, assignmentContainerWrapperModel);
    }

    public OrgAssignmentPanel(String id, IModel<PrismContainerWrapper<AssignmentType>> assignmentContainerWrapperModel, ContainerPanelConfigurationType config) {
        super(id, assignmentContainerWrapperModel, config);
    }


    protected QName getAssignmentType() {
        return OrgType.COMPLEX_TYPE;
    }

}