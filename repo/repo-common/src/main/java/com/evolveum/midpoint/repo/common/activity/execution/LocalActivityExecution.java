/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.activity.execution;

import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinition;
import com.evolveum.midpoint.repo.common.activity.handlers.ActivityHandler;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractActivityWorkStateType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultStatusType;

import org.jetbrains.annotations.NotNull;

import static com.evolveum.midpoint.schema.result.OperationResultStatus.IN_PROGRESS;
import static com.evolveum.midpoint.schema.result.OperationResultStatus.UNKNOWN;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityRealizationStateType.IN_PROGRESS_LOCAL;

/**
 * The "real" execution of an activity - i.e. not a delegation nor a distribution.
 *
 * Responsibilities at this level of abstraction:
 *
 * 1. records execution start/stop + item progress in the tree state overview,
 * 2. records execution start/stop in the item processing statistics (execution records),
 * 3. updates progress information (clears uncommitted on start).
 */
public abstract class LocalActivityExecution<
        WD extends WorkDefinition,
        AH extends ActivityHandler<WD, AH>,
        BS extends AbstractActivityWorkStateType> extends AbstractActivityExecution<WD, AH, BS> {

    private static final long DEFAULT_TREE_PROGRESS_UPDATE_INTERVAL_FOR_STANDALONE = 9000;
    private static final long DEFAULT_TREE_PROGRESS_UPDATE_INTERVAL_FOR_WORKERS = 60000;

    @NotNull private OperationResultStatus currentResultStatus = UNKNOWN;

    protected LocalActivityExecution(@NotNull ExecutionInstantiationContext<WD, AH> context) {
        super(context);
    }

    @Override
    protected @NotNull ActivityExecutionResult executeInternal(OperationResult result)
            throws ActivityExecutionException {

        updateStateOnExecutionStart(result);
        ActivityExecutionResult executionResult;
        try {
            getRunningTask().setExcludedFromStalenessChecking(isExcludedFromStalenessChecking());
            executionResult = executeLocal(result);
        } catch (Exception e) {
            executionResult = ActivityExecutionResult.handleException(e, this);
        }
        getRunningTask().setExcludedFromStalenessChecking(false);

        updateStateOnExecutionFinish(result, executionResult);
        return executionResult;
    }

    private void updateStateOnExecutionStart(OperationResult result) throws ActivityExecutionException {
        initializeCurrentResultStatusOnStart();

        getTreeStateOverview().recordLocalExecutionStart(this, result);

        if (supportsExecutionRecords()) {
            activityState.getLiveStatistics().getLiveItemProcessing().recordExecutionStart(startTimestamp);
        }
        activityState.getLiveProgress().clearUncommitted();

        if (activityState.getRealizationState() != IN_PROGRESS_LOCAL) {
            activityState.setRealizationState(IN_PROGRESS_LOCAL);
            activityState.recordRealizationStart(startTimestamp);
        }

        activityState.setResultStatus(IN_PROGRESS);
        activityState.recordExecutionStart(startTimestamp);
        activityState.flushPendingTaskModificationsChecked(result);
    }

    private void updateStateOnExecutionFinish(OperationResult result, ActivityExecutionResult executionResult)
            throws ActivityExecutionException {
        noteEndTimestampIfNone();
        activityState.setExecutionEndTimestamp(endTimestamp);

        setCurrentResultStatus(executionResult.getOperationResultStatus());

        getTreeStateOverview().recordLocalExecutionFinish(this, executionResult, result);

        if (supportsExecutionRecords()) {
            activityState.getLiveStatistics().getLiveItemProcessing()
                    .recordExecutionEnd(startTimestamp, endTimestamp);
        }

        // The state is flushed upstream
    }

    private boolean supportsExecutionRecords() {
        // Temporary solution: activities that have persistent/semi-persistent state are those that execute in short cycles
        // (like live sync, various scanners, and so on). We usually do not want to store execution records for these.
        return doesSupportStatistics() && activityStateDefinition.isSingleRealization();
    }

    protected abstract @NotNull ActivityExecutionResult executeLocal(OperationResult result)
            throws ActivityExecutionException, CommonException;

    /** Updates item progress in the tree overview. Assumes that the activity execution is still in progress. */
    public void updateItemProgressInTreeOverviewIfTimePassed(OperationResult result)
            throws SchemaException, ObjectNotFoundException {
        getTreeStateOverview().updateItemProgressIfTimePassed(
                this,
                getStateOverviewProgressUpdateInterval(),
                result);
    }

    private long getStateOverviewProgressUpdateInterval() {
        Long configuredValue = getActivity().getReportingDefinition().getStateOverviewProgressUpdateInterval();
        if (configuredValue != null) {
            return configuredValue;
        } else if (isWorker()) {
            return DEFAULT_TREE_PROGRESS_UPDATE_INTERVAL_FOR_WORKERS;
        } else {
            return DEFAULT_TREE_PROGRESS_UPDATE_INTERVAL_FOR_STANDALONE;
        }
    }

    public boolean shouldUpdateProgressInStateOverview() {
        var mode = getActivity().getReportingDefinition().getStateOverviewProgressUpdateMode();
        switch (mode) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            case FOR_NON_LOCAL_ACTIVITIES:
                return !getRunningTask().isRoot();
            default:
                throw new AssertionError(mode);
        }
    }

    /**
     * Initializes current execution status when activity execution starts.
     * The default behavior is to set IN_PROGRESS here.
     */
    private void initializeCurrentResultStatusOnStart() {
        setCurrentResultStatus(IN_PROGRESS);
    }

    public @NotNull OperationResultStatus getCurrentResultStatus() {
        return currentResultStatus;
    }

    public @NotNull OperationResultStatusType getCurrentResultStatusBean() {
        return OperationResultStatus.createStatusType(currentResultStatus);
    }

    public void setCurrentResultStatus(@NotNull OperationResultStatus currentResultStatus) {
        this.currentResultStatus = currentResultStatus;
    }

    /** True if the task is excluded from staleness checking while running this activity. */
    public boolean isExcludedFromStalenessChecking() {
        return false;
    }
}
