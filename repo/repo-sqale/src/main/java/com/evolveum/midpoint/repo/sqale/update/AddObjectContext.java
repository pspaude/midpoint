/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.update;

import java.util.Objects;
import java.util.UUID;

import com.querydsl.core.QueryException;
import org.jetbrains.annotations.NotNull;
import org.postgresql.util.PSQLException;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.sqale.ContainerValueIdGenerator;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.SqaleUtils;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObject;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObjectType;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObjectMapping;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Add object operation context; used only for true add, not overwrite which is more like modify.
 */
public class AddObjectContext<S extends ObjectType, Q extends QObject<R>, R extends MObject> {

    private final SqaleRepoContext repositoryContext;
    private final PrismObject<S> object;

    private Q root;
    private QObjectMapping<S, Q, R> rootMapping;
    private MObjectType objectType;

    public AddObjectContext(
            @NotNull SqaleRepoContext repositoryContext,
            @NotNull PrismObject<S> object) {
        this.repositoryContext = repositoryContext;
        this.object = object;
    }

    /**
     * Inserts the object provided to the constructor and returns its OID.
     */
    public String execute()
            throws SchemaException, ObjectAlreadyExistsException {
        try (JdbcSession jdbcSession = repositoryContext.newJdbcSession().startTransaction()) {
            object.setVersion("1"); // initial add always uses 1 as version number
            initContexts();
            if (object.getOid() == null) {
                return addObjectWithoutOid(jdbcSession);
            } else {
                // this also handles overwrite after ObjectNotFoundException
                return addObjectWithOid(jdbcSession);
            }
        } catch (QueryException e) { // Querydsl exception, not ours
            Throwable cause = e.getCause();
            if (cause instanceof PSQLException) {
                SqaleUtils.handlePostgresException((PSQLException) cause);
            }
            throw e;
        }
    }

    public void executeReindexed(JdbcSession jdbcSession)
            throws SchemaException, ObjectAlreadyExistsException {
        try {
            initContexts();
            addObjectWithOid(jdbcSession);
        } catch (QueryException e) { // Querydsl exception, not ours
            Throwable cause = e.getCause();
            if (cause instanceof PSQLException) {
                SqaleUtils.handlePostgresException((PSQLException) cause);
            }
            throw e;
        }
    }

    private void initContexts() {
        Class<S> schemaObjectClass = object.getCompileTimeClass();
        objectType = MObjectType.fromSchemaType(schemaObjectClass);
        rootMapping = repositoryContext.getMappingBySchemaType(schemaObjectClass);
        root = rootMapping.defaultAlias();
    }

    private String addObjectWithOid(JdbcSession jdbcSession) throws SchemaException {
        long lastCid = new ContainerValueIdGenerator(object).generateForNewObject();
        S schemaObject = object.asObjectable();
        R row = rootMapping.toRowObjectWithoutFullObject(schemaObject, jdbcSession);
        row.containerIdSeq = lastCid + 1;
        rootMapping.setFullObject(row, schemaObject);

        UUID oid = jdbcSession.newInsert(root)
                // default populate mapper ignores null, that's good, especially for objectType
                .populate(row)
                .executeWithKey(root.oid);

        row.objectType = objectType; // sub-entities can use it, now it's safe to set it
        rootMapping.storeRelatedEntities(row, schemaObject, jdbcSession);

        jdbcSession.commit();
        return Objects.requireNonNull(oid, "OID of inserted object can't be null")
                .toString();
    }

    private String addObjectWithoutOid(JdbcSession jdbcSession) throws SchemaException {
        S schemaObject = object.asObjectable();
        R row = rootMapping.toRowObjectWithoutFullObject(schemaObject, jdbcSession);

        // first insert without full object, because we don't know the OID yet
        UUID oid = jdbcSession.newInsert(root)
                // default populate mapper ignores null, that's good, especially for objectType
                .populate(row)
                .executeWithKey(root.oid);
        String oidString =
                Objects.requireNonNull(oid, "OID of inserted object can't be null")
                        .toString();
        object.setOid(oidString);

        long lastCid = new ContainerValueIdGenerator(object).generateForNewObject();

        // now to update full object with known OID
        rootMapping.setFullObject(row, schemaObject);
        jdbcSession.newUpdate(root)
                .set(root.fullObject, row.fullObject)
                .set(root.containerIdSeq, lastCid + 1)
                .where(root.oid.eq(oid))
                .execute();

        row.oid = oid;
        row.objectType = objectType; // sub-entities can use it, now it's safe to set it
        rootMapping.storeRelatedEntities(row, schemaObject, jdbcSession);

        jdbcSession.commit();
        return oidString;
    }
}
