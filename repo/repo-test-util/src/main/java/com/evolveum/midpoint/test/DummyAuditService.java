/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.assertEquals;

import java.util.*;
import javax.xml.datatype.Duration;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.audit.api.*;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CleanupPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Dummy audit service that only remembers the audit messages in runtime.
 * Only for use in tests.
 *
 * @author semancik
 */
public class DummyAuditService implements AuditService, DebugDumpable {

    private static DummyAuditService instance = null;

    private final List<AuditEventRecord> records = new ArrayList<>();

    // This is to be able to be able to disable this service for some tests e.g. to prevent memory leaks
    // TODO consider introducing auto-cleanup mechanism for this
    private boolean enabled = true;

    public static DummyAuditService getInstance() {
        if (instance == null) {
            instance = new DummyAuditService();
        }
        return instance;
    }

    @Override
    public synchronized void audit(AuditEventRecord record, Task task, OperationResult result) {
        if (enabled) {
            for (AuditEventRecord storedRecord : records) {
                // do not allow to store two records with same eventIdentifier, maybe change to map?
                if (storedRecord.getEventIdentifier().equals(record.getEventIdentifier())) {
                    throw new IllegalStateException("Cannot add audit record with same event identifier");
                }
            }
            records.add(record.clone());
        }
    }

    @Override
    public synchronized void cleanupAudit(CleanupPolicyType policy, OperationResult parentResult) {
        Validate.notNull(policy, "Cleanup policy must not be null.");
        Validate.notNull(parentResult, "Operation result must not be null.");

        if (policy.getMaxAge() == null) {
            return;
        }

        Duration duration = policy.getMaxAge();
        if (duration.getSign() > 0) {
            duration = duration.negate();
        }
        long minValue = duration.getTimeInMillis(new Date());

        Iterator<AuditEventRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            AuditEventRecord record = iterator.next();
            Long timestamp = record.getTimestamp();
            if (timestamp == null) {
                continue;
            }

            if (timestamp < minValue) {
                iterator.remove();
            }
        }
    }

    public List<AuditEventRecord> getRecords() {
        return records;
    }

    public synchronized void clear() {
        records.clear();
    }

    /**
     * Asserts that there is a request message followed by execution message.
     */
    public void assertSimpleRecordSanity() {
        Iterator<AuditEventRecord> iterator = records.iterator();
        int num = 0;
        int numRequests = 0;
        int numExecutions = 0;
        String requestIdentifier = null;
        while (iterator.hasNext()) {
            AuditEventRecord record = iterator.next();
            num++;
            assertRecordSanity("" + num + "th record", record);

            if (record.getEventStage() == AuditEventStage.REQUEST) {
                numRequests++;
                requestIdentifier = record.getRequestIdentifier();
                assert requestIdentifier != null : "No request identifier in audit record " + record;
            }
            if (record.getEventStage() == AuditEventStage.EXECUTION) {
                assert numRequests > 0 : "Encountered execution stage audit record without any preceding request: " + record;
                numExecutions++;
                assert requestIdentifier != null : "Execution before request? " + record;
                assert requestIdentifier.equals(record.getRequestIdentifier()) : "Request identifier mismatch between request and execution in " + record + " expected: " + requestIdentifier + ", was " + record.getRequestIdentifier();
            }
        }
        assert numRequests <= numExecutions : "Strange number of requests and executions; " + numRequests + " requests, " + numExecutions + " executions";
    }

    private void assertRecordSanity(String recordDesc, AuditEventRecord record) {
        assert record != null : "Null audit record (" + recordDesc + ")";
        assert !StringUtils.isEmpty(record.getEventIdentifier()) : "No event identifier in audit record (" + recordDesc + ")";
        assert !StringUtils.isEmpty(record.getTaskIdentifier()) : "No task identifier in audit record (" + recordDesc + ")";
    }

    public void assertRecords(int expectedNumber) {
        assert records.size() == expectedNumber
                : "Unexpected number of audit records; expected " + expectedNumber + " but was " + records.size();
    }

    public void assertExecutionRecords(int expectedNumber) {
        List<AuditEventRecord> executionRecords = getExecutionRecords();
        assertEquals("Wrong # of execution records", expectedNumber, executionRecords.size());
    }

    public List<AuditEventRecord> getRecordsOfType(AuditEventType type) {
        List<AuditEventRecord> retval = new ArrayList<>();
        for (AuditEventRecord record : records) {
            if (record.getEventType() == type) {
                retval.add(record);
            }
        }
        return retval;
    }

    public void assertRecords(AuditEventType type, int expectedNumber) {
        List<AuditEventRecord> filtered = getRecordsOfType(type);
        assert filtered.size() == expectedNumber : "Unexpected number of audit records of type " + type + "; expected " + expectedNumber +
                " but was " + filtered.size();
    }

    public AuditEventRecord getRequestRecord() {
        assertSingleBatch();
        AuditEventRecord requestRecord = records.get(0);
        assert requestRecord != null : "The first audit record is null";
        assert requestRecord.getEventStage() == AuditEventStage.REQUEST : "The first audit record is not request, it is " + requestRecord;
        return requestRecord;
    }

    public AuditEventRecord getExecutionRecord(int index) {
        assertSingleBatch();
        AuditEventRecord executionRecord = records.get(index + 1);
        assert executionRecord != null : "The " + index + "th audit execution record is null";
        assert executionRecord.getEventStage() == AuditEventStage.EXECUTION : "The " + index + "th audit execution record is not execution, it is " + executionRecord;
        return executionRecord;
    }

    public List<AuditEventRecord> getExecutionRecords() {
        assertSingleBatch();
        return records.subList(1, records.size());
    }

    private void assertSingleBatch() {
        assert records.size() > 1 : "Expected at least two audit records but got " + records.size();
        Iterator<AuditEventRecord> iterator = records.iterator();
        AuditEventRecord requestRecord = iterator.next();
        if (requestRecord.getEventType() == AuditEventType.CREATE_SESSION) {
            requestRecord = iterator.next();
        }
        assert requestRecord.getEventStage() == AuditEventStage.REQUEST : "Expected first record to be request, it was " + requestRecord.getEventStage() + " instead: " + requestRecord;
        while (iterator.hasNext()) {
            AuditEventRecord executionRecord = iterator.next();
            if (executionRecord.getEventType() == AuditEventType.TERMINATE_SESSION) {
                break;
            }
            assert executionRecord.getEventStage() == AuditEventStage.EXECUTION : "Expected following record to be execution, it was " + executionRecord.getEventStage() + " instead: " + executionRecord;
        }
    }

    public void assertAnyRequestDeltas() {
        AuditEventRecord requestRecord = getRequestRecord();
        Collection<ObjectDeltaOperation<? extends ObjectType>> requestDeltas = requestRecord.getDeltas();
        assert !requestDeltas.isEmpty() : "Expected some deltas in audit request record but found none";
    }

    public Collection<ObjectDeltaOperation<? extends ObjectType>> getExecutionDeltas() {
        return getExecutionDeltas(0);
    }

    public Collection<ObjectDeltaOperation<? extends ObjectType>> getExecutionDeltas(int index) {
        AuditEventRecord executionRecord = getExecutionRecord(index);
        Collection<ObjectDeltaOperation<? extends ObjectType>> deltas = executionRecord.getDeltas();
        assert deltas != null : "Execution audit record has null deltas";
        return deltas;
    }

    public ObjectDeltaOperation<?> getExecutionDelta(int index) {
        Collection<ObjectDeltaOperation<? extends ObjectType>> deltas = getExecutionDeltas(index);
        assert deltas.size() == 1 : "Execution audit record has more than one deltas, it has " + deltas.size();
        return deltas.iterator().next();
    }

    public <O extends ObjectType> ObjectDeltaOperation<O> getExecutionDelta(
            int index, ChangeType changeType, Class<O> typeClass) {
        for (ObjectDeltaOperation<? extends ObjectType> deltaOp : getExecutionDeltas(index)) {
            ObjectDelta<? extends ObjectType> delta = deltaOp.getObjectDelta();
            if (delta.getObjectTypeClass() == typeClass && delta.getChangeType() == changeType) {
                //noinspection unchecked
                return (ObjectDeltaOperation<O>) deltaOp;
            }
        }
        return null;
    }

    public void assertExecutionDeltaAdd() {
        ObjectDeltaOperation<?> delta = getExecutionDelta(0);
        assert delta.getObjectDelta().isAdd() : "Execution audit record is not add, it is " + delta;
    }

    public void assertExecutionSuccess() {
        assertExecutionOutcome(OperationResultStatus.SUCCESS);
    }

    public void assertExecutionOutcome(OperationResultStatus expectedStatus) {
        List<AuditEventRecord> executionRecords = getExecutionRecords();
        for (AuditEventRecord executionRecord : executionRecords) {
            assert executionRecord.getOutcome() == expectedStatus :
                    "Expected execution outcome " + expectedStatus + " in audit record but it was " + executionRecord.getOutcome();
        }
    }

    public void assertExecutionOutcome(int index, OperationResultStatus expectedStatus) {
        List<AuditEventRecord> executionRecords = getExecutionRecords();
        AuditEventRecord executionRecord = executionRecords.get(index);
        assert executionRecord.getOutcome() == expectedStatus :
                "Expected execution outcome " + expectedStatus + " in audit execution record ("
                        + index + ") but it was " + executionRecord.getOutcome();
    }

    public void assertExecutionMessage() {
        List<AuditEventRecord> executionRecords = getExecutionRecords();
        for (AuditEventRecord executionRecord : executionRecords) {
            assert !StringUtils.isEmpty(executionRecord.getMessage()) :
                    "Expected execution message in audit record but there was none; in " + executionRecord;
        }
    }

    public void assertExecutionMessage(int index) {
        List<AuditEventRecord> executionRecords = getExecutionRecords();
        AuditEventRecord executionRecord = executionRecords.get(index);
        assert !StringUtils.isEmpty(executionRecord.getMessage()) :
                "Expected execution message in audit record but there was none; in " + executionRecord;
    }

    public void assertExecutionMessage(int index, String message) {
        List<AuditEventRecord> executionRecords = getExecutionRecords();
        AuditEventRecord executionRecord = executionRecords.get(index);
        assertEquals("Wrong message in execution record " + index, message, executionRecord.getMessage());
    }

    public void assertNoRecord() {
        assert records.isEmpty() : "Expected no audit record but some sneaked in: " + records;
    }

    public <O extends ObjectType> ObjectDeltaOperation<O> assertHasDelta(
            ChangeType expectedChangeType, Class<O> expectedClass) {
        return assertHasDelta(null, 0, expectedChangeType, expectedClass);
    }

    public <O extends ObjectType> ObjectDeltaOperation<O> assertHasDelta(
            ChangeType expectedChangeType, Class<O> expectedClass, OperationResultStatus expextedResult) {
        return assertHasDelta(null, 0, expectedChangeType, expectedClass, expextedResult);
    }

    public <O extends ObjectType> ObjectDeltaOperation<O> assertHasDelta(
            int index, ChangeType expectedChangeType, Class<O> expectedClass) {
        return assertHasDelta(null, index, expectedChangeType, expectedClass);
    }

    public <O extends ObjectType> ObjectDeltaOperation<O> assertHasDelta(
            int index, ChangeType expectedChangeType, Class<O> expectedClass, OperationResultStatus expextedResult) {
        return assertHasDelta(null, index, expectedChangeType, expectedClass, expextedResult);
    }

    public <O extends ObjectType> ObjectDeltaOperation<O> assertHasDelta(
            String message, int index, ChangeType expectedChangeType, Class<O> expectedClass) {
        return assertHasDelta(message, index, expectedChangeType, expectedClass, null);
    }

    public <O extends ObjectType> ObjectDeltaOperation<O> assertHasDelta(
            String message, int index, ChangeType expectedChangeType, Class<O> expectedClass, OperationResultStatus expectedResult) {
        ObjectDeltaOperation<O> deltaOp = getExecutionDelta(index, expectedChangeType, expectedClass);
        assert deltaOp != null : (message == null ? "" : message + ": ") + "Delta for " + expectedClass + " of type " + expectedChangeType + " was not found in audit trail";
        if (expectedResult != null) {
            assertEquals((message == null ? "" : message + ": ") + "Delta for " + expectedClass + " of type " + expectedChangeType + " has unexpected result",
                    deltaOp.getExecutionResult().getStatus(), expectedResult);
        }
        return deltaOp;
    }

    public void assertExecutionDeltas(int expectedNumber) {
        assertExecutionDeltas(0, expectedNumber);
    }

    public void assertExecutionDeltas(int index, int expectedNumber) {
        assertEquals("Wrong number of execution deltas in audit trail (index " + index + ")", expectedNumber, getExecutionDeltas(index).size());
    }

    public void assertCustomColumn(String nameOfProperty, String value) {
        for (AuditEventRecord record : records) {
            Map<String, String> properties = record.getCustomColumnProperty();
            if (properties.containsKey(nameOfProperty) && properties.get(nameOfProperty).equals(value)) {
                return;
            }
        }
        assert false : "Custom column property " + nameOfProperty + " with value " + value + " not found in audit records";
    }

    public void assertResourceOid(String oid) {
        for (AuditEventRecord record : records) {
            Set<String> resourceOid = record.getResourceOids();
            if (resourceOid.contains(oid)) {
                return;
            }
        }
        assert false : "Resource oid " + oid + " not found in audit records";
    }

    public void assertTarget(String expectedOid, AuditEventStage stage) {
        Collection<PrismReferenceValue> targets = new ArrayList<>();
        for (AuditEventRecord record : records) {
            PrismReferenceValue target = record.getTargetRef();
            if (stage == null || stage == record.getEventStage()) {
                if (target != null && expectedOid.equals(target.getOid())) {
                    return;
                }
                if (target != null) {
                    targets.add(target);
                }
            }
        }
        assert false : "Target " + expectedOid + " not found in audit records (stage=" + stage + "); found " + targets;
    }

    public void assertTarget(String expectedOid) {
        assertTarget(expectedOid, AuditEventStage.REQUEST);
        assertTarget(expectedOid, AuditEventStage.EXECUTION);
    }

    @SafeVarargs
    public final <O extends ObjectType, T> void assertPropertyReplace(
            ChangeType expectedChangeType, Class<O> expectedClass,
            ItemPath propPath, T... expectedValues) {
        assertPropertyReplace(null, 0, expectedChangeType, expectedClass, propPath, expectedValues);
    }

    @SafeVarargs
    public final <O extends ObjectType, T> void assertPropertyReplace(
            int index, ChangeType expectedChangeType,
            Class<O> expectedClass, ItemPath propPath, T... expectedValues) {
        assertPropertyReplace(null, index, expectedChangeType, expectedClass, propPath, expectedValues);
    }

    @SafeVarargs
    public final <O extends ObjectType, T> void assertPropertyReplace(
            String message, int index, ChangeType expectedChangeType,
            Class<O> expectedClass, ItemPath propPath, T... expectedValues) {

        ObjectDeltaOperation<O> deltaOp = getExecutionDelta(index, expectedChangeType, expectedClass);
        assert deltaOp != null : (message == null ? ""
                : message + ": ") + "Delta for " + expectedClass + " of type " + expectedChangeType + " was not found in audit trail";

        PropertyDelta<Object> propDelta = deltaOp.getObjectDelta().findPropertyDelta(propPath);
        assert propDelta != null
                : "No property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType;

        Collection<PrismPropertyValue<Object>> valuesToReplace = propDelta.getValuesToReplace();
        assert valuesToReplace != null
                : "No values to replace in property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType;

        if (expectedValues == null || expectedValues.length == 0) {
            if (valuesToReplace.isEmpty()) {
                return;
            } else {
                assert false : (message == null ? "" : message + ": ") + "Empty values to replace in property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType + ", expected " + Arrays.toString(expectedValues);
            }
        }
        PrismAsserts.assertValues((message == null ? "" : message + ": ") + "Wrong values to replace in property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType, valuesToReplace, expectedValues);
    }

    @SafeVarargs
    public final <O extends ObjectType, T> void assertOldValue(ChangeType expectedChangeType,
            Class<O> expectedClass, ItemPath propPath, T... expectedValues) {
        assertOldValue(null, 0, expectedChangeType, expectedClass, propPath, expectedValues);
    }

    @SafeVarargs
    public final <O extends ObjectType, T> void assertOldValue(
            int index, ChangeType expectedChangeType,
            Class<O> expectedClass, ItemPath propPath, T... expectedValues) {
        assertOldValue(null, index, expectedChangeType, expectedClass, propPath, expectedValues);
    }

    @SafeVarargs
    public final <O extends ObjectType, T> void assertOldValue(
            String message, int index, ChangeType expectedChangeType,
            Class<O> expectedClass, ItemPath propPath, T... expectedValues) {
        ObjectDeltaOperation<O> deltaOp = getExecutionDelta(index, expectedChangeType, expectedClass);
        assert deltaOp != null
                : (message == null ? "" : message + ": ") + "Delta for " + expectedClass +
                " of type " + expectedChangeType + " was not found in audit trail";

        PropertyDelta<Object> propDelta = deltaOp.getObjectDelta().findPropertyDelta(propPath);
        assert propDelta != null :
                "No property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType;

        Collection<PrismPropertyValue<Object>> estimatedOldValues = propDelta.getEstimatedOldValues();
        if (expectedValues == null || expectedValues.length == 0) {
            if (estimatedOldValues == null || estimatedOldValues.isEmpty()) {
                return;
            } else {
                assert false : (message == null ? "" : message + ": ") + "Empty old values in property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType + ", expected " + Arrays.toString(expectedValues);
            }
        }
        assert estimatedOldValues != null && !estimatedOldValues.isEmpty() : "No old values in property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType;
        PrismAsserts.assertValues((message == null ? "" : message + ": ") + "Wrong old values in property delta for " + propPath + " in Delta for " + expectedClass + " of type " + expectedChangeType, estimatedOldValues, expectedValues);
    }

    /**
     * Checks that the first record is login and the last is logout.
     */
    public void assertLoginLogout() {
        assertLoginLogout(null);
    }

    /**
     * Checks that the first record is login and the last is logout.
     */
    public void assertLoginLogout(String expectedChannel) {
        AuditEventRecord firstRecord = records.get(0);
        assertEquals("Wrong type of first audit record: " + firstRecord.getEventType(), AuditEventType.CREATE_SESSION, firstRecord.getEventType());
        assertEquals("Wrong outcome of first audit record: " + firstRecord.getOutcome(), OperationResultStatus.SUCCESS, firstRecord.getOutcome());
        AuditEventRecord lastRecord = records.get(records.size() - 1);
        assertEquals("Wrong type of last audit record: " + lastRecord.getEventType(), AuditEventType.TERMINATE_SESSION, lastRecord.getEventType());
        assertEquals("Wrong outcome of last audit record: " + lastRecord.getOutcome(), OperationResultStatus.SUCCESS, lastRecord.getOutcome());
        // TODO fix "login" "logout" auditing
        assertEquals("Audit session ID does not match", firstRecord.getSessionIdentifier(), lastRecord.getSessionIdentifier());
        assertThat(firstRecord.getEventIdentifier())
                .withFailMessage("Same login and logout event IDs")
                .isNotEqualTo(lastRecord.getEventIdentifier());
        if (expectedChannel != null) {
            assertEquals("Wrong channel in first audit record", expectedChannel, firstRecord.getChannel());
            assertEquals("Wrong channel in last audit record", expectedChannel, lastRecord.getChannel());
        }
    }

    public void assertFailedLogin(String expectedChannel) {
        AuditEventRecord firstRecord = records.get(0);
        assertEquals("Wrong type of first audit record: " + firstRecord.getEventType(), AuditEventType.CREATE_SESSION, firstRecord.getEventType());
        assertEquals("Wrong outcome of first audit record: " + firstRecord.getOutcome(), OperationResultStatus.FATAL_ERROR, firstRecord.getOutcome());
        if (expectedChannel != null) {
            assertEquals("Wrong channel in first audit record", expectedChannel, firstRecord.getChannel());
        }
    }

    public void assertFailedProxyLogin(String expectedChannel) {
        AuditEventRecord firstRecord = records.get(0);
        assertEquals("Wrong type of first audit record (service authN): " + firstRecord.getEventType(), AuditEventType.CREATE_SESSION, firstRecord.getEventType());
        assertEquals("Wrong outcome of first audit record (service authN): " + firstRecord.getOutcome(), OperationResultStatus.SUCCESS, firstRecord.getOutcome());
        if (expectedChannel != null) {
            assertEquals("Wrong channel in first audit record", expectedChannel, firstRecord.getChannel());
        }
        AuditEventRecord secondRecord = records.get(1);
        assertEquals("Wrong type of second audit record (proxy authN): " + secondRecord.getEventType(), AuditEventType.CREATE_SESSION, secondRecord.getEventType());
        assertEquals("Wrong outcome of second audit record (proxy authN): " + secondRecord.getOutcome(), OperationResultStatus.FATAL_ERROR, secondRecord.getOutcome());
        if (expectedChannel != null) {
            assertEquals("Wrong channel in second audit record (proxy authN)", expectedChannel, secondRecord.getChannel());
        }
    }

    @Override
    public String toString() {
        return "DummyAuditService(" + records + ")";
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        DebugUtil.indentDebugDump(sb, indent);
        sb.append("DummyAuditService: ").append(records.size()).append(" records\n");
        DebugUtil.debugDump(sb, records, indent + 1, false);
        return sb.toString();
    }

    @Override
    public boolean supportsRetrieval() {
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int countObjects(
            @Nullable ObjectQuery query,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull OperationResult parentResult) {
        throw new UnsupportedOperationException("countObjects not supported");
    }

    @Override
    @NotNull
    public SearchResultList<AuditEventRecordType> searchObjects(
            @Nullable ObjectQuery query,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull OperationResult parentResult) {
        throw new UnsupportedOperationException("searchObjects not supported");
    }

    @Override
    public SearchResultMetadata searchObjectsIterative(
            @Nullable ObjectQuery query,
            @NotNull AuditResultHandler handler,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull OperationResult parentResult) throws SchemaException {
        throw new UnsupportedOperationException("searchObjectsIterative not supported");
    }
}
