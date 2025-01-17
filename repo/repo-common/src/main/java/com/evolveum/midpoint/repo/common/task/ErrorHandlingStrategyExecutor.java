/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.task;

import static com.evolveum.midpoint.repo.common.task.ErrorHandlingStrategyExecutor.FollowUpAction.*;
import static com.evolveum.midpoint.util.DebugUtil.lazy;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import com.evolveum.midpoint.repo.common.activity.Activity;
import com.evolveum.midpoint.schema.util.ExceptionUtil;

import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.MiscUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Executes iterative task error handling strategy.
 *
 * The strategy consists of a set of _entries_, which are evaluated against specific error that has occurred.
 * Each entry contains a description of _situation(s)_ and appropriate _reaction_.
 *
 * The main {@link #handleError(OperationResultStatus, Throwable, String, OperationResult)} method must be thread safe.
 *
 */
public class ErrorHandlingStrategyExecutor {

    private static final Trace LOGGER = TraceManager.getTrace(ErrorHandlingStrategyExecutor.class);

    // TODO deduplicate with the trigger handler class
    private static final String SHADOW_RECONCILE_TRIGGER_HANDLER_URI = "http://midpoint.evolveum.com/xml/ns/public/model/trigger/shadow-reconcile/handler-3";

    private static final Duration DEFAULT_INITIAL_RETRY_INTERVAL = XmlTypeConverter.createDuration("PT30M");

    @NotNull private final PrismContext prismContext;

    /**
     * Individual entries with some auxiliary information, namely how many times they were matched in the current run.
     * Sorted by order attribute.
     */
    @NotNull private final List<StrategyEntryInformation> strategyEntryInformationList;

    @NotNull private final RepositoryService repositoryService;

    @NotNull private final ErrorHandlingStrategyExecutor.FollowUpAction defaultFollowUpAction;

    public enum FollowUpAction {
        STOP, CONTINUE
    }

    ErrorHandlingStrategyExecutor(@NotNull Activity<?, ?> activity, @NotNull RunningTask task,
            @NotNull ErrorHandlingStrategyExecutor.FollowUpAction defaultFollowUpAction, @NotNull CommonTaskBeans beans) {
        this.prismContext = beans.prismContext;
        this.strategyEntryInformationList = getErrorHandlingStrategyEntryList(activity, task).stream()
                .map(StrategyEntryInformation::new)
                .collect(Collectors.toList());
        sortIfPossible(strategyEntryInformationList);
        this.repositoryService = beans.repositoryService;
        this.defaultFollowUpAction = defaultFollowUpAction;
    }

    private void sortIfPossible(List<StrategyEntryInformation> entries) {
        if (entries.stream().anyMatch(e -> e.getOrder() != null)) {
            entries.sort(Comparator.comparing(StrategyEntryInformation::getOrder, Comparator.nullsLast(Integer::compare)));
        } else {
            // There's no point in risking that the sort will change the ordering of the entries.
        }
    }

    /**
     * Decides whether to stop or continue processing.
     * @param status Failure status of the operation (fatalError, partialError).
     * @param exception Exception that occurred.
     * @param opResult Here we should report our operations.
     * @return true of we should stop
     *
     * This method must be thread safe!
     */
    @NotNull ErrorHandlingStrategyExecutor.FollowUpAction handleError(@NotNull OperationResultStatus status,
            @NotNull Throwable exception, @Nullable String triggerHolderOid, @NotNull OperationResult opResult) {
        ErrorCategoryType errorCategory = ExceptionUtil.getErrorCategory(exception);
        LOGGER.debug("Error category: {} for: {}", errorCategory, lazy(() -> MiscUtil.getClassWithMessage(exception)));

        for (StrategyEntryInformation entryInformation : strategyEntryInformationList) {
            if (matches(entryInformation.entry, status, errorCategory)) {
                return executeErrorHandlingReaction(entryInformation, triggerHolderOid, opResult);
            }
        }

        if (errorCategory == ErrorCategoryType.POLICY_THRESHOLD) {
            LOGGER.trace("Applying hardcoded default follow-up action for thresholds: STOP");
            return STOP;
        }

        return defaultFollowUpAction;
    }

    private boolean matches(@NotNull TaskErrorHandlingStrategyEntryType entry, @NotNull OperationResultStatus status,
            @NotNull ErrorCategoryType category) {
        TaskErrorSituationSelectorType situation = entry.getSituation();
        return situation == null ||
                statusMatches(situation.getStatus(), status) && categoryMatches(situation.getErrorCategory(), category);
    }

    private boolean statusMatches(List<OperationResultStatusType> filter, OperationResultStatus value) {
        return filter.isEmpty() || filter.contains(value.createStatusType());
    }

    private boolean categoryMatches(List<ErrorCategoryType> filter, ErrorCategoryType value) {
        return filter.isEmpty() || filter.contains(value);
    }

    private @NotNull ErrorHandlingStrategyExecutor.FollowUpAction executeErrorHandlingReaction(
            @NotNull ErrorHandlingStrategyExecutor.StrategyEntryInformation entry,
            @Nullable String triggerHolderOid, @NotNull OperationResult opResult) {

        if (entry.registerMatchAndCheckThreshold()) {
            return STOP;
        }

        TaskErrorReactionType reaction = entry.entry.getReaction();
        if (reaction == null) {
            return STOP;
        }

        if (reaction.getIgnore() != null) {
            return CONTINUE;
        } else if (reaction.getStop() != null) {
            return STOP;
        } else if (reaction.getRetryLater() != null) {
            return processRetryLater(reaction, triggerHolderOid, opResult);
        } else {
            return getDefaultAction(reaction);
        }
    }

    private FollowUpAction getDefaultAction(TaskErrorReactionType reaction) {
        if (reaction.getStopAfter() != null) {
            return CONTINUE;
        } else {
            return STOP;
        }
    }

    private FollowUpAction processRetryLater(TaskErrorReactionType reaction, @Nullable String shadowOid,
            @NotNull OperationResult opResult) {
        if (shadowOid == null) {
            LOGGER.warn("'retryLater' reaction was configured but there is no shadow to attach trigger to. Having to stop "
                    + "in order to avoid data loss.");
            return STOP;
        }
        try {
            createShadowSynchronizationTrigger(reaction.getRetryLater(), shadowOid, opResult);
        } catch (Throwable t) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't create shadow synchronization trigger for shadow {} "
                    + "-- live synchronization must stop", t, shadowOid);
            return STOP;
        }
        return CONTINUE;
    }

    private void createShadowSynchronizationTrigger(RetryLaterReactionType retryReaction, String shadowOid,
            OperationResult opResult) throws SchemaException, ObjectAlreadyExistsException, ObjectNotFoundException {
        TriggerType trigger = new TriggerType(prismContext)
                .handlerUri(SHADOW_RECONCILE_TRIGGER_HANDLER_URI)
                .timestamp(getFirstRetryTimestamp(retryReaction));
        PlannedOperationAttemptType firstAttempt = new PlannedOperationAttemptType(prismContext)
                .number(1)
                .interval(retryReaction.getNextInterval())
                .limit(retryReaction.getRetryLimit());
        ObjectTypeUtil.setExtensionContainerRealValues(prismContext, trigger.asPrismContainerValue(),
                SchemaConstants.MODEL_EXTENSION_PLANNED_OPERATION_ATTEMPT, firstAttempt);
        List<ItemDelta<?, ?>> modifications = prismContext.deltaFor(ShadowType.class)
                .item(ShadowType.F_TRIGGER)
                .add(trigger)
                .asItemDeltas();
        repositoryService.modifyObject(ShadowType.class, shadowOid, modifications, opResult);
    }

    private XMLGregorianCalendar getFirstRetryTimestamp(RetryLaterReactionType retryReaction) {
        return XmlTypeConverter.fromNow(defaultIfNull(retryReaction.getInitialInterval(), DEFAULT_INITIAL_RETRY_INTERVAL));
    }

    private List<TaskErrorHandlingStrategyEntryType> getErrorHandlingStrategyEntryList(Activity<?, ?> activity, Task task) {

        // The current way
        TaskErrorHandlingStrategyType strategyFromActivity = activity.getErrorHandlingStrategy();
        if (strategyFromActivity != null) {
            TaskErrorHandlingStrategyType clone = strategyFromActivity.clone();
            clone.asPrismContainerValue().freeze();
            return clone.getEntry();
        }

        // The 4.3.x way
        TaskErrorHandlingStrategyType strategyFromTask = task.getErrorHandlingStrategy();
        if (strategyFromTask != null) {
            TaskErrorHandlingStrategyType clone = strategyFromTask.clone();
            clone.asPrismContainerValue().freeze();
            return clone.getEntry();
        }

        // The 4.2.x way
        TaskErrorHandlingStrategyType strategyFromExtension =
                task.getExtensionContainerRealValueOrClone(SchemaConstants.MODEL_EXTENSION_LIVE_SYNC_ERROR_HANDLING_STRATEGY);
        if (strategyFromExtension != null) {
            TaskErrorHandlingStrategyType clone = strategyFromExtension.clone();
            clone.asPrismContainerValue().freeze();
            return clone.getEntry();
        }

        return emptyList();
    }

    private static class StrategyEntryInformation {
        @NotNull private final TaskErrorHandlingStrategyEntryType entry; // frozen (due to thread safety)
        @NotNull private final AtomicInteger matches = new AtomicInteger();

        private StrategyEntryInformation(TaskErrorHandlingStrategyEntryType entry) {
            this.entry = entry.clone();
            this.entry.asPrismContainerValue().freeze();
        }

        private boolean registerMatchAndCheckThreshold() {
            int occurred = matches.incrementAndGet();
            Integer limit = getStopAfter();
            if (limit == null || occurred < limit) {
                LOGGER.debug("Found match #{} of this error strategy entry. Limit is {}, not suspending the task",
                        occurred, limit);
                return false;
            } else {
                LOGGER.info("This error strategy entry matched {} times; limit is {}. Suspending the task.",
                        occurred, limit);
                return true;
            }
        }

        @Nullable
        private Integer getStopAfter() {
            return entry.getReaction() != null ?
                    entry.getReaction().getStopAfter() : null;
        }

        public Integer getOrder() {
            return entry.getOrder();
        }
    }
}
