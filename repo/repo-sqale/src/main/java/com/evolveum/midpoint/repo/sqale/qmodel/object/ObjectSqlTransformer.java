/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.object;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.xml.namespace.QName;

import com.querydsl.core.Tuple;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.SerializationOptions;
import com.evolveum.midpoint.repo.sqale.SqaleUtils;
import com.evolveum.midpoint.repo.sqale.qmodel.SqaleTransformerBase;
import com.evolveum.midpoint.repo.sqale.qmodel.assignment.AssignmentSqlTransformer;
import com.evolveum.midpoint.repo.sqale.qmodel.assignment.QAssignmentMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QUri;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.MReferenceType;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class ObjectSqlTransformer<S extends ObjectType, Q extends QObject<R>, R extends MObject>
        extends SqaleTransformerBase<S, Q, R> {

    public ObjectSqlTransformer(
            SqlTransformerSupport transformerSupport,
            QObjectMapping<S, Q, R> mapping) {
        super(transformerSupport, mapping);
    }

    @Override
    public S toSchemaObject(Tuple row, Q entityPath,
            Collection<SelectorOptions<GetOperationOptions>> options)
            throws SchemaException {

        PrismObject<S> prismObject;
        String serializedForm = new String(row.get(entityPath.fullObject), StandardCharsets.UTF_8);
        try {
            SqlTransformerSupport.ParseResult<S> result = transformerSupport.parsePrismObject(serializedForm);
            prismObject = result.prismObject;
            if (result.parsingContext.hasWarnings()) {
                logger.warn("Object {} parsed with {} warnings",
                        ObjectTypeUtil.toShortString(prismObject), result.parsingContext.getWarnings().size());
            }
        } catch (SchemaException | RuntimeException | Error e) {
            // This is a serious thing. We have corrupted XML in the repo. This may happen even
            // during system init. We want really loud and detailed error here.
            logger.error("Couldn't parse object {} {}: {}: {}\n{}",
                    mapping.schemaType().getSimpleName(), row.get(entityPath.oid),
                    e.getClass().getName(), e.getMessage(), serializedForm, e);
            throw e;
        }

        return prismObject.asObjectable();
    }

    /**
     * Override this to fill additional row attributes after calling this super version.
     *
     * *This must be called with active JDBC session* so it can create new {@link QUri} rows.
     * As this is intended for inserts *DO NOT* set {@link MObject#objectType} to any value,
     * it must be NULL otherwise the DB will complain about the value for the generated column.
     *
     * OID may be null, hence the method does NOT create any sub-entities, see {@link }
     */
    @SuppressWarnings("DuplicatedCode") // see comment for metadata lower
    @NotNull
    public R toRowObjectWithoutFullObject(S schemaObject, JdbcSession jdbcSession) {
        R row = mapping.newRowObject();

        row.oid = oidToUUid(schemaObject.getOid());

        setPolyString(schemaObject.getName(), o -> row.nameOrig = o, n -> row.nameNorm = n);

        // This is duplicate code with AssignmentSqlTransformer.toRowObject, but making interface
        // and needed setters (fields are not "interface-able") would create much more code.
        MetadataType metadata = schemaObject.getMetadata();
        if (metadata != null) {
            setReference(metadata.getCreatorRef(), jdbcSession,
                    o -> row.creatorRefTargetOid = o,
                    t -> row.creatorRefTargetType = t,
                    r -> row.creatorRefRelationId = r);
            row.createChannelId = processCacheableUri(metadata.getCreateChannel(), jdbcSession);
            row.createTimestamp = MiscUtil.asInstant(metadata.getCreateTimestamp());

            setReference(metadata.getModifierRef(), jdbcSession,
                    o -> row.modifierRefTargetOid = o,
                    t -> row.modifierRefTargetType = t,
                    r -> row.modifierRefRelationId = r);
            row.modifyChannelId = processCacheableUri(metadata.getModifyChannel(), jdbcSession);
            row.modifyTimestamp = MiscUtil.asInstant(metadata.getModifyTimestamp());
        }

        setReference(schemaObject.getTenantRef(), jdbcSession,
                o -> row.tenantRefTargetOid = o,
                t -> row.tenantRefTargetType = t,
                r -> row.tenantRefRelationId = r);
        row.lifecycleState = schemaObject.getLifecycleState();
        row.version = SqaleUtils.objectVersionAsInt(schemaObject);

        // TODO extensions stored inline (JSON)

        return row;
    }

    /**
     * Stores other entities related to the main object row like containers, references, etc.
     * This is not part of {@link #toRowObjectWithoutFullObject} because it requires know OID
     * which is not assured before calling that method.
     *
     * *Always call this super method first in overriding methods.*
     *
     * @param objectRow master row for added object
     * @param schemaObject schema objects for which the details are stored
     * @param jdbcSession JDBC session used to insert related rows
     */
    public void storeRelatedEntities(
            @NotNull MObject objectRow, @NotNull S schemaObject, @NotNull JdbcSession jdbcSession) {
        Objects.requireNonNull(objectRow.oid);

        MetadataType metadata = schemaObject.getMetadata();
        if (metadata != null) {
            storeRefs(objectRow, metadata.getCreateApproverRef(),
                    MReferenceType.OBJECT_CREATE_APPROVER, jdbcSession);
            storeRefs(objectRow, metadata.getModifyApproverRef(),
                    MReferenceType.OBJECT_MODIFY_APPROVER, jdbcSession);
        }

        List<TriggerType> triggers = schemaObject.getTrigger();
        if (!triggers.isEmpty()) {
            TriggerSqlTransformer transformer =
                    QTriggerMapping.INSTANCE.createTransformer(transformerSupport);
            triggers.forEach(t -> transformer.insert(t, objectRow, jdbcSession));
        }

        /*
        TODO
        subtype? it's obsolete already
        parentOrgRefs
        repo.setPolicySituation(RUtil.listToSet(jaxb.getPolicySituation()));

        repo.getTextInfoItems().addAll(RObjectTextInfo.createItemsSet(jaxb, repo, repositoryContext));
        for (OperationExecutionType opExec : jaxb.getOperationExecution()) {
            ROperationExecution rOpExec = new ROperationExecution(repo);
            ROperationExecution.fromJaxb(opExec, rOpExec, jaxb, repositoryContext, generatorResult);
            repo.getOperationExecutions().add(rOpExec);
        }
        */
        if (schemaObject instanceof AssignmentHolderType) {
            storeAssignmentHolderEntities(objectRow, (AssignmentHolderType) schemaObject, jdbcSession);
        }

        /* TODO EAV extensions
        if (jaxb.getExtension() != null) {
            copyExtensionOrAttributesFromJAXB(jaxb.getExtension().asPrismContainerValue(), repo, repositoryContext, RObjectExtensionType.EXTENSION, generatorResult);
        }
        */
    }

    private void storeAssignmentHolderEntities(
            MObject objectRow, AssignmentHolderType schemaObject, JdbcSession jdbcSession) {
        List<AssignmentType> assignments = schemaObject.getAssignment();
        if (!assignments.isEmpty()) {
            AssignmentSqlTransformer transformer =
                    QAssignmentMapping.INSTANCE.createTransformer(transformerSupport);
            assignments.forEach(assignment ->
                    transformer.insert(assignment, objectRow, jdbcSession));
        }

        storeRefs(objectRow, schemaObject.getRoleMembershipRef(),
                MReferenceType.ROLE_MEMBERSHIP, jdbcSession);
        storeRefs(objectRow, schemaObject.getDelegatedRef(),
                MReferenceType.DELEGATED, jdbcSession);
        storeRefs(objectRow, schemaObject.getArchetypeRef(),
                MReferenceType.ARCHETYPE, jdbcSession);
    }

    /**
     * Serializes schema object and sets {@link R#fullObject}.
     */
    public void setFullObject(R row, S schemaObject) throws SchemaException {
        row.fullObject = createFullObject(schemaObject);
    }

    public byte[] createFullObject(S schemaObject) throws SchemaException {
        if (schemaObject.getOid() == null || schemaObject.getVersion() == null) {
            throw new IllegalArgumentException(
                    "Serialized object must have assigned OID and version: " + schemaObject);
        }

        return transformerSupport.serializer()
                .itemsToSkip(fullObjectItemsToSkip())
                .options(SerializationOptions
                        .createSerializeReferenceNamesForNullOids()
                        .skipIndexOnly(true)
                        .skipTransient(true))
                .serialize(schemaObject.asPrismObject())
                .getBytes(StandardCharsets.UTF_8);
    }

    protected Collection<? extends QName> fullObjectItemsToSkip() {
        // TODO extend later, things like FocusType.F_JPEG_PHOTO, see ObjectUpdater#updateFullObject
        return Collections.emptyList();
    }
}
