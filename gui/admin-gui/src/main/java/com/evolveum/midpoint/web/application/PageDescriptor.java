/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.application;

import org.apache.wicket.request.mapper.parameter.IPageParametersEncoder;
import org.apache.wicket.request.mapper.parameter.PageParametersEncoder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author lazyman
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PageDescriptor {

    Url[] urls() default {};

    Class<? extends IPageParametersEncoder> encoder() default PageParametersEncoder.class;

    AuthorizationAction[] action() default {};

    /**
     * Permit access to all users (even non-authenticated users)
     */
    boolean permitAll() default false;

    /**
     * Indicate login page, Permit access to all users (even non-authenticated users)
     */
    boolean loginPage() default false;

    /**
     * If set to true, page is available only if the experimental features are turned on.
     * Also, the link in the sidebar panel (on the left) is visible for experimental pages only
     * if the experimental featires are on.
     */
    boolean experimental() default false;

}
