/*
 * Copyright (c) 2010-2013 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.task.api;

import com.evolveum.midpoint.schema.result.OperationResult;

import com.evolveum.midpoint.schema.result.OperationResultStatus;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Objects;

import static com.evolveum.midpoint.task.api.TaskRunResult.TaskRunResultStatus.FINISHED;

/**
 * Single-purpose class to return task run results.
 *
 * More than one value is returned, therefore it is
 * bundled into a class.
 *
 * @author Radovan Semancik
 *
 */
public class TaskRunResult implements Serializable {

    public enum TaskRunResultStatus {
        /**
         * The task run has finished.
         *
         * This does not necessarily mean that the task itself is finished. For single tasks this means that
         * the task is finished, but it is different for recurring tasks. Such a task will run again after
         * it sleeps for a while (or after the scheduler will start it again).
         */
        FINISHED,

        /**
         * The run has failed.
         *
         * The error is permanent. Unless the administrator does something to recover from the situation, there is no point in
         * re-trying the run. Usual case of this error is task misconfiguration.
         */
        PERMANENT_ERROR,

        /**
         * Temporary failure during the run.
         *
         * The error is temporary. The situation may change later when the conditions will be more "favorable".
         * It makes sense to retry the run. Usual cases of this error are network timeouts.
         *
         * For single-run tasks we SUSPEND them on such occasion. So the administrator can release them after
         * correcting the problem.
         */
        TEMPORARY_ERROR,

        /**
         * Task run hasn't finished but nevertheless it must end (for now). An example of such a situation is
         * when the long-living task run execution is requested to stop (e.g. when suspending the task or
         * shutting down the node).
         */
        INTERRUPTED,

        /**
         * Task has entered waiting state. TODO. EXPERIMENTAL.
         */
        IS_WAITING
    }

    protected Long progress; // null means "do not update, take whatever is in the task"
    protected TaskRunResultStatus runResultStatus;
    @Deprecated protected OperationResult operationResult;
    protected OperationResultStatus operationResultStatus;

    /**
     * @return the progress
     */
    public Long getProgress() {
        return progress;
    }
    /**
     * @param progress the progress to set
     */
    public void setProgress(Long progress) {
        this.progress = progress;
    }
    /**
     * @return the status
     */
    public TaskRunResultStatus getRunResultStatus() {
        return runResultStatus;
    }
    /**
     * @param status the status to set
     */
    public void setRunResultStatus(TaskRunResultStatus status) {
        this.runResultStatus = status;
    }

    @Deprecated
    public OperationResult getOperationResult() {
        return operationResult;
    }

    @Deprecated
    public void setOperationResult(OperationResult operationResult) {
        this.operationResult = operationResult;
    }

    public OperationResultStatus getOperationResultStatus() {
        return operationResultStatus;
    }

    public void setOperationResultStatus(OperationResultStatus operationResultStatus) {
        this.operationResultStatus = operationResultStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskRunResult)) return false;
        TaskRunResult that = (TaskRunResult) o;
        return Objects.equals(progress, that.progress) &&
                runResultStatus == that.runResultStatus &&
                Objects.equals(operationResult, that.operationResult) &&
                operationResultStatus == that.operationResultStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(progress, runResultStatus, operationResult, operationResultStatus);
    }

    @Override
    public String toString() {
        return "TaskRunResult(progress=" + progress + ", status="
                + runResultStatus + ", result status=" + operationResultStatus
                + ")";
    }

    @NotNull public static TaskRunResult createFailureTaskRunResult(RunningTask task, String message, Throwable t) {
        TaskRunResult runResult = createRunResult(task);
        if (t != null) {
            runResult.getOperationResult().recordFatalError(message, t);
        } else {
            runResult.getOperationResult().recordFatalError(message);
        }
        runResult.setOperationResultStatus(OperationResultStatus.FATAL_ERROR);
        runResult.setRunResultStatus(TaskRunResultStatus.PERMANENT_ERROR);
        return runResult;
    }

    @NotNull public static TaskRunResult createSuccessTaskRunResult(RunningTask task) {
        TaskRunResult runResult = createRunResult(task);
        runResult.getOperationResult().recordSuccess();
        runResult.setOperationResultStatus(OperationResultStatus.SUCCESS);
        runResult.setRunResultStatus(TaskRunResultStatus.FINISHED);
        return runResult;
    }

    @NotNull public static TaskRunResult createInterruptedTaskRunResult(RunningTask task) {
        TaskRunResult runResult = createRunResult(task);
        runResult.getOperationResult().recordSuccess(); // TODO ok?
        runResult.setOperationResultStatus(OperationResultStatus.SUCCESS); // TODO ok?
        runResult.setRunResultStatus(TaskRunResultStatus.INTERRUPTED);
        return runResult;
    }

    @NotNull public static TaskRunResult createFromTaskException(RunningTask task, TaskException e) {
        TaskRunResult runResult = createRunResult(task);
        runResult.getOperationResult().recordStatus(e.getOpResultStatus(), e.getFullMessage(), e.getCause());
        runResult.setOperationResultStatus(e.getOpResultStatus());
        runResult.setRunResultStatus(e.getRunResultStatus());
        return runResult;
    }

    private static TaskRunResult createRunResult(RunningTask task) {
        TaskRunResult runResult = new TaskRunResult();
        runResult.setOperationResult(
                Objects.requireNonNullElseGet(
                        task.getResult(),
                        () -> new OperationResult(TaskConstants.OP_EXECUTE_HANDLER)));
        return runResult;
    }

    public static TaskRunResult createNotApplicableTaskRunResult() {
        TaskRunResult runResult = new TaskRunResult();
        runResult.setRunResultStatus(FINISHED);
        runResult.setOperationResultStatus(OperationResultStatus.NOT_APPLICABLE);
        return runResult;
    }
}
