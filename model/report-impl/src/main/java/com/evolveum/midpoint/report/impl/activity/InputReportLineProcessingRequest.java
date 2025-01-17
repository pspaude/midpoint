/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.report.impl.activity;

import com.evolveum.midpoint.repo.common.task.IterativeActivityExecution;
import com.evolveum.midpoint.repo.common.task.CorrelatableProcessingRequest;
import com.evolveum.midpoint.repo.common.task.GenericProcessingRequest;

import com.evolveum.midpoint.schema.statistics.IterationItemInformation;

import org.jetbrains.annotations.NotNull;

/**
 * Wrapper for input report line object.
 *
 * (This is needed for the activity framework to process {@link InputReportLine} objects.)
 */
public class InputReportLineProcessingRequest
        extends GenericProcessingRequest<InputReportLine>
        implements CorrelatableProcessingRequest {

    InputReportLineProcessingRequest(@NotNull InputReportLine item,
            @NotNull IterativeActivityExecution<InputReportLine, ?, ?, ?> activityExecution) {
        super(item.getLineNumber(), item, activityExecution);
    }

    @Override
    public Object getCorrelationValue() {
        return item.getCorrelationValue();
    }

    @Override
    public @NotNull IterationItemInformation getIterationItemInformation() {
        return new IterationItemInformation(
                "line #" + item.getLineNumber(),
                null,
                null,
                null);
    }
}
