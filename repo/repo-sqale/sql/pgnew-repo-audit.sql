-- Copyright (C) 2010-2021 Evolveum and contributors
--
-- This work is dual-licensed under the Apache License 2.0
-- and European Union Public License. See LICENSE file for details.
--
-- USAGE NOTES: You can apply this to the main repository schema.
-- For separate audit use this in a separate database.
--
-- @formatter:off because of terribly unreliable IDEA reformat for SQL
-- Naming conventions:
-- M_ prefix is used for tables in main part of the repo, MA_ for audit tables (can be separate)
-- Constraints/indexes use table_column(s)_suffix convention, with PK for primary key,
-- FK foreign key, IDX for index, KEY for unique index.
-- TR is suffix for triggers.
-- Names are generally lowercase (despite prefix/suffixes above in uppercase ;-)).
-- Column names are Java style and match attribute names from M-classes (e.g. MAuditEvent).
--
-- Other notes:
-- TEXT is used instead of VARCHAR, see: https://dba.stackexchange.com/a/21496/157622

-- noinspection SqlResolveForFile @ operator-class/"gin__int_ops"

-- just in case PUBLIC schema was dropped (fastest way to remove all midpoint objects)
-- drop schema public cascade;
CREATE SCHEMA IF NOT EXISTS public;
-- CREATE EXTENSION IF NOT EXISTS pg_trgm; -- support for trigram indexes TODO for ext with LIKE and fulltext

-- region custom enum types
DO $$ BEGIN
    CREATE TYPE ObjectType AS ENUM (
        'ABSTRACT_ROLE',
        'ACCESS_CERTIFICATION_CAMPAIGN',
        'ACCESS_CERTIFICATION_DEFINITION',
        'ARCHETYPE',
        'ASSIGNMENT_HOLDER',
        'CASE',
        'CONNECTOR',
        'CONNECTOR_HOST',
        'DASHBOARD',
        'FOCUS',
        'FORM',
        'FUNCTION_LIBRARY',
        'GENERIC_OBJECT',
        'LOOKUP_TABLE',
        'NODE',
        'OBJECT',
        'OBJECT_COLLECTION',
        'OBJECT_TEMPLATE',
        'ORG',
        'REPORT',
        'REPORT_DATA',
        'RESOURCE',
        'ROLE',
        'SECURITY_POLICY',
        'SEQUENCE',
        'SERVICE',
        'SHADOW',
        'SYSTEM_CONFIGURATION',
        'TASK',
        'USER',
        'VALUE_POLICY');

    CREATE TYPE OperationResultStatusType AS ENUM ('SUCCESS', 'WARNING', 'PARTIAL_ERROR',
        'FATAL_ERROR', 'HANDLED_ERROR', 'NOT_APPLICABLE', 'IN_PROGRESS', 'UNKNOWN');
EXCEPTION WHEN duplicate_object THEN raise notice 'Main repo custom types already exist, OK...'; END $$;

CREATE TYPE AuditEventTypeType AS ENUM ('GET_OBJECT', 'ADD_OBJECT', 'MODIFY_OBJECT',
    'DELETE_OBJECT', 'EXECUTE_CHANGES_RAW', 'SYNCHRONIZATION', 'CREATE_SESSION',
    'TERMINATE_SESSION', 'WORK_ITEM', 'WORKFLOW_PROCESS_INSTANCE', 'RECONCILIATION',
    'SUSPEND_TASK', 'RESUME_TASK', 'RUN_TASK_IMMEDIATELY');

CREATE TYPE AuditEventStageType AS ENUM ('REQUEST', 'EXECUTION');

CREATE TYPE ChangeType AS ENUM ('ADD', 'MODIFY', 'DELETE');
-- endregion

-- region management tables
-- Key -> value config table for internal use.
CREATE TABLE IF NOT EXISTS m_global_metadata (
    name TEXT PRIMARY KEY,
    value TEXT
);
-- endregion

-- region AUDIT
CREATE TABLE ma_audit_event (
    id BIGSERIAL NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    eventIdentifier TEXT,
    eventType AuditEventTypeType,
    eventStage AuditEventStageType,
    sessionIdentifier TEXT,
    requestIdentifier TEXT,
    taskIdentifier TEXT,
    taskOid UUID,
    hostIdentifier TEXT,
    nodeIdentifier TEXT,
    remoteHostAddress TEXT,
    initiatorOid UUID,
    initiatorType ObjectType,
    initiatorName TEXT,
    attorneyOid UUID,
    attorneyName TEXT,
    targetOid UUID,
    targetType ObjectType,
    targetName TEXT,
    targetOwnerOid UUID,
    targetOwnerType ObjectType,
    targetOwnerName TEXT,
    channel TEXT, -- full URI, we do not want m_uri ID anymore
    outcome OperationResultStatusType,
    parameter TEXT,
    result TEXT,
    message TEXT,
    changedItemPaths TEXT[],
    resourceOids TEXT[],
    properties JSONB,
    -- ext JSONB, -- TODO extension container later

    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

CREATE INDEX ma_audit_event_timestamp_idx ON ma_audit_event (timestamp);
CREATE INDEX ma_audit_event_eventIdentifier_idx ON ma_audit_event (eventIdentifier);
CREATE INDEX ma_audit_event_sessionIdentifier_idx ON ma_audit_event (sessionIdentifier);
CREATE INDEX ma_audit_event_requestIdentifier_idx ON ma_audit_event (requestIdentifier);
-- This was originally eventStage + targetOid, but low variability eventStage can do more harm.
CREATE INDEX ma_audit_event_targetOid_idx ON ma_audit_event (targetOid);
-- TODO do we want to index every single column or leave the rest to full/partial scans?
-- Original repo/audit didn't have any more indexes either...
CREATE INDEX ma_audit_event_changedItemPaths_idx ON ma_audit_event USING gin(changeditempaths);
CREATE INDEX ma_audit_event_resourceOids_idx ON ma_audit_event USING gin(resourceOids);
CREATE INDEX ma_audit_event_properties_idx ON ma_audit_event USING gin(properties);

CREATE TABLE ma_audit_delta (
    recordId BIGINT NOT NULL, -- references ma_audit_event.id
    timestamp TIMESTAMPTZ NOT NULL, -- references ma_audit_event.timestamp
    checksum TEXT NOT NULL,
    delta BYTEA,
    deltaOid UUID,
    deltaType ChangeType,
    fullResult BYTEA,
    objectNameNorm TEXT,
    objectNameOrig TEXT,
    resourceOid UUID,
    resourceNameNorm TEXT,
    resourceNameOrig TEXT,
    status OperationResultStatusType,

    PRIMARY KEY (recordId, timestamp, checksum)
) PARTITION BY RANGE (timestamp);

/* Similar FK is created PER PARTITION only, see audit_create_monthly_partitions
   or *_default tables:
ALTER TABLE ma_audit_delta ADD CONSTRAINT ma_audit_delta_fk
    FOREIGN KEY (recordId, timestamp) REFERENCES ma_audit_event (id, timestamp)
        ON DELETE CASCADE;
*/

-- TODO: any unique combination within single recordId? name+oid+type perhaps?
CREATE TABLE ma_audit_ref (
    id BIGSERIAL NOT NULL, -- unique technical PK
    recordId BIGINT NOT NULL, -- references ma_audit_event.id
    timestamp TIMESTAMPTZ NOT NULL, -- references ma_audit_event.timestamp
    name TEXT, -- multiple refs can have the same name, conceptually it's a Map(name -> refs[])
    targetOid UUID,
    targetType ObjectType,
    targetNameOrig TEXT,
    targetNameNorm TEXT,

    PRIMARY KEY (id, timestamp) -- real PK must contain partition key (timestamp)
) PARTITION BY RANGE (timestamp);

/* Similar FK is created PER PARTITION only
ALTER TABLE ma_audit_ref ADD CONSTRAINT ma_audit_ref_fk
    FOREIGN KEY (recordId, timestamp) REFERENCES ma_audit_event (id, timestamp)
        ON DELETE CASCADE;
*/
CREATE INDEX ma_audit_ref_recordId_timestamp_idx ON ma_audit_ref (recordId, timestamp);

-- Default tables used when no timestamp range partitions are created:
CREATE TABLE ma_audit_event_default PARTITION OF ma_audit_event DEFAULT;
CREATE TABLE ma_audit_delta_default PARTITION OF ma_audit_delta DEFAULT;
CREATE TABLE ma_audit_ref_default PARTITION OF ma_audit_ref DEFAULT;

ALTER TABLE ma_audit_delta_default ADD CONSTRAINT ma_audit_delta_default_fk
    FOREIGN KEY (recordId, timestamp) REFERENCES ma_audit_event_default (id, timestamp)
        ON DELETE CASCADE;
ALTER TABLE ma_audit_ref_default ADD CONSTRAINT ma_audit_ref_default_fk
    FOREIGN KEY (recordId, timestamp) REFERENCES ma_audit_event_default (id, timestamp)
        ON DELETE CASCADE;
-- endregion

-- region Schema versioning and upgrading
/*
See notes at the end of main repo schema.
This is necessary only when audit is separate, but is safe to run any time.
*/
CREATE OR REPLACE PROCEDURE apply_change(changeNumber int, change TEXT, force boolean = false)
    LANGUAGE plpgsql
AS $$
DECLARE
    lastChange int;
BEGIN
    SELECT value INTO lastChange FROM m_global_metadata WHERE name = 'schemaChangeNumber';

    -- change is executed if the changeNumber is newer - or if forced
    IF lastChange IS NULL OR lastChange < changeNumber OR force THEN
        EXECUTE change;
        RAISE NOTICE 'Change #% executed!', changeNumber;

        IF lastChange IS NULL THEN
            INSERT INTO m_global_metadata (name, value) VALUES ('schemaChangeNumber', changeNumber);
        ELSIF changeNumber > lastChange THEN
            -- even with force we never want to set lower change number, hence the IF above
            UPDATE m_global_metadata SET value = changeNumber WHERE name = 'schemaChangeNumber';
        END IF;
        COMMIT;
    ELSE
        RAISE NOTICE 'Change #% skipped, last change #% is newer!', changeNumber, lastChange;
    END IF;
END $$;
-- endregion

---------------------------------------------------------------------------------
-- The rest of the file can be omitted if partitioning is not required or desired

-- https://www.postgresql.org/docs/current/runtime-config-query.html#GUC-ENABLE-PARTITIONWISE-JOIN
DO $$ BEGIN
    EXECUTE 'ALTER DATABASE ' || current_database() || ' SET enable_partitionwise_join TO on';
END; $$;

-- region partition creation procedures
CREATE OR REPLACE PROCEDURE audit_create_monthly_partitions(futureCount int)
    LANGUAGE plpgsql
AS $$
DECLARE
    dateFrom TIMESTAMPTZ = date_trunc('month', current_timestamp);
    dateTo TIMESTAMPTZ;
    tableSuffix TEXT;
BEGIN
    FOR i IN 1..futureCount loop
        dateTo := dateFrom + interval '1 month';
        tableSuffix := to_char(dateFrom, 'YYYYMM');

        BEGIN
            -- PERFORM = select without using the result
            PERFORM ('ma_audit_event_' || tableSuffix)::regclass;
            RAISE NOTICE 'Tables for partition % already exist, OK...', tableSuffix;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Creating partitions for range: % - %', dateFrom, dateTo;

            -- values FROM are inclusive (>=), TO are exclusive (<)
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF ma_audit_event FOR VALUES FROM (%L) TO (%L);',
                    'ma_audit_event_' || tableSuffix, dateFrom, dateTo);
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF ma_audit_delta FOR VALUES FROM (%L) TO (%L);',
                    'ma_audit_delta_' || tableSuffix, dateFrom, dateTo);
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF ma_audit_ref FOR VALUES FROM (%L) TO (%L);',
                    'ma_audit_ref_' || tableSuffix, dateFrom, dateTo);

            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (recordId, timestamp)' ||
                    ' REFERENCES %I (id, timestamp) ON DELETE CASCADE',
                    'ma_audit_delta_' || tableSuffix,
                    'ma_audit_delta_' || tableSuffix || '_fk',
                    'ma_audit_event_' || tableSuffix);
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (recordId, timestamp)' ||
                    ' REFERENCES %I (id, timestamp) ON DELETE CASCADE',
                    'ma_audit_ref_' || tableSuffix,
                    'ma_audit_ref_' || tableSuffix || '_fk',
                    'ma_audit_event_' || tableSuffix);
        END;

        dateFrom := dateTo;
    END loop;
END $$;
-- endregion

/*
IMPORTANT: Only default partitions are created in this script!
Use something like this, if you desire monthly partitioning:
call audit_create_monthly_partitions(12);

This creates 12 monthly partitions into the future.
It can be safely called multiple times, so you can run it again anytime in the future.
If you forget to run, audit events will go to default partition so no data is lost,
however it may be complicated to organize it into proper partitions after the fact.

For Quartz tables see:
repo/task-quartz-impl/src/main/resources/com/evolveum/midpoint/task/quartzimpl/execution/tables_postgres.sql

Try this to see recent audit events with the real table where they are stored:
select tableoid::regclass::text AS table_name, *
from ma_audit_event
order by id desc
limit 50;
*/
