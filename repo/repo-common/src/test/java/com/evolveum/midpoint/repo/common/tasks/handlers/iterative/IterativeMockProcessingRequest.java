/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.tasks.handlers.iterative;

import com.evolveum.midpoint.repo.common.task.ItemProcessingRequest;
import com.evolveum.midpoint.repo.common.task.PlainIterativeActivityExecution;
import com.evolveum.midpoint.repo.common.util.OperationExecutionRecorderForTasks;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.statistics.IterationItemInformation;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationSituationType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class IterativeMockProcessingRequest extends ItemProcessingRequest<Integer> {

    IterativeMockProcessingRequest(@NotNull Integer item,
            @NotNull PlainIterativeActivityExecution<Integer, ?, ?, ?> activityExecution) {
        super(item, item, activityExecution);
    }

    @Override
    public void acknowledge(boolean release, OperationResult result) {
        // no-op
    }

    @Override
    public OperationExecutionRecorderForTasks.Target getOperationExecutionRecordingTarget() {
        return null;
    }

    @Override
    public String getObjectOidToRecordRetryTrigger() {
        return null;
    }

    @Override
    public @NotNull IterationItemInformation getIterationItemInformation() {
        return new IterationItemInformation(String.valueOf(item), null, ObjectType.COMPLEX_TYPE, null);
    }

    @Override
    public @Nullable String getItemOid() {
        return null;
    }

    @Override
    public @Nullable SynchronizationSituationType getSynchronizationSituationOnProcessingStart() {
        return SynchronizationSituationType.UNMATCHED; // just to test the statistics
    }
}
