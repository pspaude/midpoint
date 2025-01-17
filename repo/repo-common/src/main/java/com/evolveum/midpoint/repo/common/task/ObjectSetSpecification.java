/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.task;

import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.common.activity.definition.ObjectSetSpecificationProvider;
import com.evolveum.midpoint.repo.common.activity.definition.ResourceObjectSetSpecificationProvider;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinition;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SelectorQualifiedGetOptionsType;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;

/**
 * This is a specification of objects to be processed in more-or-less raw form. It is collected
 * either from activity work definition, or from (legacy) task object ref + extension.
 * It unifies repository objects and resource objects.
 *
 * IMPORTANT POINTS: FIXME UPDATE THIS
 *
 * 1. This information is not internalized yet. So there is e.g. {@link QName} for object type and
 * {@link QueryType} for object query.
 *
 * 2. This information should not reflect preferences/updates from the activity itself. A slight
 * exception is the object type where a default may be provided. (It is important e.g. for further
 * parsing of the query bean.)
 */
public interface ObjectSetSpecification {

    static @NotNull ObjectSetSpecification fromWorkDefinition(WorkDefinition workDefinition) {
        if (workDefinition instanceof ObjectSetSpecificationProvider) {
            return new RepositoryObjectSetSpecificationImpl(
                    ((ObjectSetSpecificationProvider) workDefinition).getObjectSetSpecification());
        } else if (workDefinition instanceof ResourceObjectSetSpecificationProvider) {
            return new ResourceObjectSetSpecificationImpl(
                    ((ResourceObjectSetSpecificationProvider) workDefinition).getResourceObjectSetSpecification());
        } else {
            throw new IllegalArgumentException("Work definition contains neither object set nor resource object set: " +
                    workDefinition);
        }
    }

    QName getObjectType();

    SelectorQualifiedGetOptionsType getSearchOptionsBean();

}
