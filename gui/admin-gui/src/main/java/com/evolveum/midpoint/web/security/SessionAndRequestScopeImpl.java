/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.security;

import com.evolveum.midpoint.web.security.util.SecurityUtils;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.*;

import javax.servlet.http.HttpServletRequest;

/**
 * @author skublik
 */

public class SessionAndRequestScopeImpl extends AbstractRequestAttributesScope {

    private SessionScope sessionScope;
    private RequestScope requestScope;

    public SessionAndRequestScopeImpl(){
        sessionScope = new SessionScope();
        requestScope = new RequestScope();
    }

    @Override
    protected int getScope() {
        if (isRestOrActuatorChannel()) {
            return RequestAttributes.SCOPE_REQUEST;
        }
        return RequestAttributes.SCOPE_SESSION;
    }

    @Override
    public String getConversationId() {
        return getCurrentScope().getConversationId();
    }

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        return getCurrentScope().get(name, objectFactory);
    }

    @Override
    @Nullable
    public Object remove(String name) {
        return getCurrentScope().remove(name);
    }

    private Scope getCurrentScope(){
        if (isRestOrActuatorChannel()) {
            return requestScope;
        }
        return sessionScope;
    }

    private boolean isRestOrActuatorChannel(){
        HttpServletRequest httpRequest = null;
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes)requestAttributes).getRequest();
            httpRequest = request;
        }
        return SecurityUtils.isRecordSessionLessAccessChannel(httpRequest);
    }
}
