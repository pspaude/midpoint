/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.dialog;

import org.apache.wicket.Component;
import org.apache.wicket.model.StringResourceModel;

public interface Popupable {

    int getWidth();
    int getHeight();
    String getWidthUnit();
    String getHeightUnit();
    Component getComponent();

}
