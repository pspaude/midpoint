/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.delta.item;

import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Path;

import com.evolveum.midpoint.repo.sqale.SqaleUpdateContext;

/**
 * @param <T> type of real value after optional conversion ({@link #convertRealValue(Object)}
 * to match the column (attribute) type in the row bean (M-type)
 * @param <P> type of the corresponding path in the Q-type
 */
public class SinglePathItemDeltaProcessor<T, P extends Path<T>>
        extends ItemDeltaSingleValueProcessor<T> {

    protected final P path;

    public SinglePathItemDeltaProcessor(
            SqaleUpdateContext<?, ?, ?> context, Function<EntityPath<?>, P> rootToQueryItem) {
        super(context);
        this.path = rootToQueryItem.apply(context.path());
    }

    @Override
    public void setValue(T value) {
        context.set(path, value);
    }

    @Override
    public void delete() {
        context.set(path, null);
    }
}