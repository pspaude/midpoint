/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.task;

import com.evolveum.midpoint.repo.common.activity.state.ActivityItemProcessingStatistics;

import com.google.common.util.concurrent.AtomicDouble;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maintains selected statistical information related to processing items in a current activity execution.
 *
 * It is like a simplified version of {@link ActivityItemProcessingStatistics} that ignore previous activity executions
 * and different types of outcome (distinguishing only errors and non-errors).
 *
 * Must be thread safe.
 */
public class TransientActivityExecutionStatistics {

    /** Number of items processed (success + error + skip). */
    private final AtomicInteger itemsProcessed = new AtomicInteger();

    /** Number of items experiencing errors. */
    private final AtomicInteger errors = new AtomicInteger();

    /**
     * Time (in millis) spend while processing the items. Does NOT include time spent
     * in pre-processing nor while waiting in the queue.
     */
    private final AtomicDouble totalTimeProcessing = new AtomicDouble();

    /** The wall clock time when activity execution started. */
    protected volatile long startTimeMillis;

    // Note that we currently do not need the end timestamp.

    void recordExecutionStart() {
        this.startTimeMillis = System.currentTimeMillis();
    }

    void update(boolean isError, double duration) {
        itemsProcessed.incrementAndGet();
        if (isError) {
            errors.incrementAndGet();
        }
        totalTimeProcessing.addAndGet(duration);
    }

    final Double getAverageTime() {
        int count = getItemsProcessed();
        if (count > 0) {
            double total = totalTimeProcessing.get();
            return total / count;
        } else {
            return null;
        }
    }

    final double getProcessingTime() {
        return totalTimeProcessing.get();
    }

    Double getAverageWallClockTime(long now) {
        int count = getItemsProcessed();
        if (count > 0) {
            return (double) getWallClockTime(now) / count;
        } else {
            return null;
        }
    }

    Double getThroughput(long now) {
        Double wallAverageTime = getAverageWallClockTime(now);
        if (wallAverageTime != null) {
            return 60000.0 / wallAverageTime;
        } else {
            return null;
        }
    }

    final long getWallClockTime(long now) {
        return now - startTimeMillis;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public int getErrors() {
        return errors.get();
    }

    public int getItemsProcessed() {
        return itemsProcessed.get();
    }
}
