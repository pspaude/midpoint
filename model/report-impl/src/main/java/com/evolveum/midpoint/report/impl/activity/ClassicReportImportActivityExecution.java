/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.report.impl.activity;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import com.evolveum.midpoint.repo.common.activity.execution.ExecutionInstantiationContext;
import com.evolveum.midpoint.repo.common.task.*;
import com.evolveum.midpoint.report.impl.ReportServiceImpl;

import com.evolveum.midpoint.report.impl.controller.ImportController;
import com.evolveum.midpoint.schema.expression.VariablesMap;

import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractActivityWorkStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityItemCountingOptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityOverallItemCountingOptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportType;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.CommonException;

import org.jetbrains.annotations.Nullable;

/**
 * Activity execution for report import.
 */
class ClassicReportImportActivityExecution
        extends PlainIterativeActivityExecution
        <InputReportLine,
                ClassicReportImportWorkDefinition,
                ClassicReportImportActivityHandler,
                AbstractActivityWorkStateType> {

    private static final Trace LOGGER = TraceManager.getTrace(ClassicReportImportActivityExecution.class);

    @NotNull private final ImportActivitySupport support;

    /** The report service Spring bean. */
    @NotNull private final ReportServiceImpl reportService;

    /** Parsed VariablesMap for lines of file. */
    private List<VariablesMap> variables;

    private ImportController controller;

    ClassicReportImportActivityExecution(
            @NotNull ExecutionInstantiationContext<ClassicReportImportWorkDefinition, ClassicReportImportActivityHandler> activityExecution) {
        super(activityExecution, "Report import");
        reportService = activityExecution.getActivity().getHandler().reportService;
        support = new ImportActivitySupport(this);
    }

    @Override
    public ActivityReportingOptions getDefaultReportingOptions() {
        return super.getDefaultReportingOptions()
                .defaultDetermineOverallSize(ActivityOverallItemCountingOptionType.ALWAYS)
                .defaultDetermineBucketSize(ActivityItemCountingOptionType.NEVER);
    }

    @Override
    public void beforeExecution(OperationResult result) throws CommonException, ActivityExecutionException {
        support.beforeExecution(result);
        ReportType report = support.getReport();

        support.stateCheck(result);

        controller = new ImportController(
                report, reportService, support.existCollectionConfiguration() ? support.getCompiledCollectionView(result) : null);
        controller.initialize();
        try {
            variables = controller.parseColumnsAsVariablesFromFile(support.getReportData());
        } catch (IOException e) {
            LOGGER.error("Couldn't read content of imported file", e);
        }
    }

    @Override
    public @Nullable Integer determineOverallSize(OperationResult result) throws CommonException {
        return variables.size();
    }

    @Override
    public void iterateOverItemsInBucket(OperationResult result) {
        BiConsumer<Integer, VariablesMap> handler = (lineNumber, variables) -> {
            InputReportLine line = new InputReportLine(lineNumber, variables);

            coordinator.submit(
                    new InputReportLineProcessingRequest(line, this),
                    result);
        };
        AtomicInteger sequence = new AtomicInteger(1);
        for (VariablesMap variablesMap : variables) {
            handler.accept(sequence.getAndIncrement(), variablesMap);
        }
    }

    @Override
    public boolean processItem(@NotNull ItemProcessingRequest<InputReportLine> request, @NotNull RunningTask workerTask,
            OperationResult result)
            throws CommonException, ActivityExecutionException {
        InputReportLine line = request.getItem();
        controller.handleDataRecord(line, workerTask, result);
        return true;
    }

    @Override
    public @NotNull ErrorHandlingStrategyExecutor.FollowUpAction getDefaultErrorAction() {
        return ErrorHandlingStrategyExecutor.FollowUpAction.CONTINUE;
    }
}
