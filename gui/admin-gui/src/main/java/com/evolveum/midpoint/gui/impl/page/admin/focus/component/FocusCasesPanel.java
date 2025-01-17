/*
 * Copyright (C) 2016-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.focus.component;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.impl.page.admin.AbstractObjectMainPanel;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.FocusDetailsModels;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.web.application.*;
import com.evolveum.midpoint.web.page.admin.server.CasesTablePanel;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.wf.util.QueryUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ContainerPanelConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType;

/**
 * @author mederly
 * @author semancik
 */
@PanelType(name = "tasks")
@PanelInstance(identifier = "tasks",
        status = ItemStatus.NOT_CHANGED,
        applicableFor = FocusType.class,
        display = @PanelDisplay(label = "pageAdminFocus.cases", icon = GuiStyleConstants.EVO_CASE_OBJECT_ICON, order = 50))
@Counter(provider = FocusCasesCounter.class)
public class FocusCasesPanel<F extends FocusType>
        extends AbstractObjectMainPanel<F, FocusDetailsModels<F>> {
    private static final long serialVersionUID = 1L;

    private static final String ID_TASK_TABLE = "taskTable";

    public FocusCasesPanel(String id, FocusDetailsModels<F> focusModel, ContainerPanelConfigurationType config) {
        super(id, focusModel, config);
    }

    protected void initLayout() {
        CasesTablePanel casesPanel = new CasesTablePanel(ID_TASK_TABLE) {
            private static final long serialVersionUID = 1L;

            @Override
            protected ObjectFilter getCasesFilter() {
                String oid = getObjectWrapper().getOid();
                return QueryUtils.filterForCasesOverObject(getPageBase().getPrismContext().queryFor(CaseType.class), oid)
                        .desc(ItemPath.create(CaseType.F_METADATA, MetadataType.F_CREATE_TIMESTAMP))
                        .buildFilter();
            }

            @Override
            protected boolean isDashboard() {
                return true;
            }

            @Override
            protected UserProfileStorage.TableId getTableId() {
                return UserProfileStorage.TableId.PAGE_CASE_CHILD_CASES_TAB;
            }
        };
        casesPanel.setOutputMarkupId(true);
        add(casesPanel);
    }
}
