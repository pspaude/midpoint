/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.accesscert;

import static com.evolveum.midpoint.util.MiscUtil.asXMLGregorianCalendar;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationWorkItemType.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.sqale.SqaleQueryContext;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QContainerMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QUserMapping;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.mapping.ResultListRowTransformer;
import com.evolveum.midpoint.repo.sqlbase.mapping.TableRelationResolver;
import com.evolveum.midpoint.schema.SchemaService;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractWorkItemOutputType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationWorkItemType;

/**
 * Mapping between {@link QAccessCertificationWorkItem} and {@link AccessCertificationWorkItemType}.
 */
public class QAccessCertificationWorkItemMapping
        extends QContainerMapping<AccessCertificationWorkItemType, QAccessCertificationWorkItem, MAccessCertificationWorkItem, MAccessCertificationCase> {

    public static final String DEFAULT_ALIAS_NAME = "acwi";

    private static QAccessCertificationWorkItemMapping instance;

    public static QAccessCertificationWorkItemMapping init(
            @NotNull SqaleRepoContext repositoryContext) {
        if (needsInitialization(instance, repositoryContext)) {
            instance = new QAccessCertificationWorkItemMapping(repositoryContext);
        }
        return get();
    }

    public static QAccessCertificationWorkItemMapping get() {
        return Objects.requireNonNull(instance);
    }

    private QAccessCertificationWorkItemMapping(@NotNull SqaleRepoContext repositoryContext) {
        super(QAccessCertificationWorkItem.TABLE_NAME, DEFAULT_ALIAS_NAME,
                AccessCertificationWorkItemType.class, QAccessCertificationWorkItem.class, repositoryContext);

        addRelationResolver(PrismConstants.T_PARENT,
                // mapping supplier is used to avoid cycles in the initialization code
                TableRelationResolver.usingJoin(
                        QAccessCertificationCaseMapping::getAccessCertificationCaseMapping,
                        (q, p) -> q.ownerOid.eq(p.ownerOid)
                                .and(q.accessCertCaseCid.eq(p.cid))));

        addItemMapping(F_CLOSE_TIMESTAMP, timestampMapper(q -> q.closeTimestamp));
        // TODO: iteration -> campaignIteration
        addItemMapping(F_ITERATION, integerMapper(q -> q.campaignIteration));
        addNestedMapping(F_OUTPUT, AbstractWorkItemOutputType.class)
                .addItemMapping(AbstractWorkItemOutputType.F_OUTCOME, stringMapper(q -> q.outcome));
        addItemMapping(F_OUTPUT_CHANGE_TIMESTAMP, timestampMapper(q -> q.outputChangeTimestamp));
        addRefMapping(F_PERFORMER_REF,
                q -> q.performerRefTargetOid,
                q -> q.performerRefTargetType,
                q -> q.performerRefRelationId,
                QUserMapping::getUserMapping);

        addRefMapping(F_ASSIGNEE_REF,
                QAccessCertificationWorkItemReferenceMapping
                        .initForCaseWorkItemAssignee(repositoryContext));
        addRefMapping(F_CANDIDATE_REF,
                QAccessCertificationWorkItemReferenceMapping
                        .initForCaseWorkItemCandidate(repositoryContext));

        addItemMapping(F_STAGE_NUMBER, integerMapper(q -> q.stageNumber));

    }

    @Override
    public AccessCertificationWorkItemType toSchemaObject(MAccessCertificationWorkItem row) {
        AccessCertificationWorkItemType acwi = new AccessCertificationWorkItemType(prismContext())
                .id(row.cid)
                .closeTimestamp(asXMLGregorianCalendar(row.closeTimestamp))
                .iteration(row.campaignIteration)
                .outputChangeTimestamp(asXMLGregorianCalendar(row.outputChangeTimestamp))
                .performerRef(objectReference(row.performerRefTargetOid,
                        row.performerRefTargetType, row.performerRefRelationId))
                .stageNumber(row.stageNumber);

        if (row.outcome != null) {
            acwi.output(new AbstractWorkItemOutputType(prismContext()).outcome(row.outcome));
        }

        return acwi;
    }

    @Override
    protected QAccessCertificationWorkItem newAliasInstance(String alias) {
        return new QAccessCertificationWorkItem(alias);
    }

    @Override
    public MAccessCertificationWorkItem newRowObject() {
        return new MAccessCertificationWorkItem();
    }

    @Override
    public MAccessCertificationWorkItem newRowObject(MAccessCertificationCase ownerRow) {
        MAccessCertificationWorkItem row = newRowObject();
        row.ownerOid = ownerRow.ownerOid;
        row.accessCertCaseCid = ownerRow.cid;
        return row;
    }

    // about duplication see the comment in QObjectMapping.toRowObjectWithoutFullObject
    @SuppressWarnings("DuplicatedCode")
    public MAccessCertificationWorkItem insert(
            AccessCertificationWorkItemType workItem,
            MAccessCertificationCase caseRow,
            JdbcSession jdbcSession) {
        MAccessCertificationWorkItem row = initRowObject(workItem, caseRow);

        row.closeTimestamp = MiscUtil.asInstant(workItem.getCloseTimestamp());
        // TODO: iteration -> campaignIteration
        row.campaignIteration = workItem.getIteration();

        AbstractWorkItemOutputType output = workItem.getOutput();
        if (output != null) {
            row.outcome = output.getOutcome();
        }

        row.outputChangeTimestamp = MiscUtil.asInstant(workItem.getOutputChangeTimestamp());

        setReference(workItem.getPerformerRef(),
                o -> row.performerRefTargetOid = o,
                t -> row.performerRefTargetType = t,
                r -> row.performerRefRelationId = r);

        row.stageNumber = workItem.getStageNumber();

        insert(row, jdbcSession);

        storeRefs(row, workItem.getAssigneeRef(),
                QAccessCertificationWorkItemReferenceMapping.getForCaseWorkItemAssignee(), jdbcSession);
        storeRefs(row, workItem.getCandidateRef(),
                QAccessCertificationWorkItemReferenceMapping.getForCaseWorkItemCandidate(), jdbcSession);

        return row;
    }

    @Override
    public ResultListRowTransformer<AccessCertificationWorkItemType, QAccessCertificationWorkItem, MAccessCertificationWorkItem> createRowTransformer(
            SqlQueryContext<AccessCertificationWorkItemType, QAccessCertificationWorkItem, MAccessCertificationWorkItem> sqlQueryContext,
            JdbcSession jdbcSession) {
        Map<UUID, PrismObject<AccessCertificationCampaignType>> cache = new HashMap<>();
        return (tuple, entityPath, options) -> {
            MAccessCertificationWorkItem row = Objects.requireNonNull(tuple.get(entityPath));
            UUID ownerOid = row.ownerOid;
            PrismObject<AccessCertificationCampaignType> owner = cache.get(ownerOid);
            // FIXME: Should we load cases we need, instead of all cases?
            options = SchemaService.get().getOperationOptionsBuilder().retrieve().build();
            if (owner == null) {
                owner = ((SqaleQueryContext<?, ?, ?>) sqlQueryContext).loadObject(jdbcSession, AccessCertificationCampaignType.class, ownerOid, options);
                cache.put(ownerOid, owner);
            }
            PrismContainer<AccessCertificationCaseType> caseContainer = owner.findContainer(AccessCertificationCampaignType.F_CASE);
            if (caseContainer == null) {
                throw new SystemException("Campaign" + owner + " has no cases even if it should have " + tuple);
            }
            PrismContainerValue<AccessCertificationCaseType> aCase = caseContainer.findValue(row.accessCertCaseCid);
            if (aCase == null) {
                throw new SystemException("Campaign " + owner + " has no cases with ID " + row.accessCertCaseCid);
            }
            PrismContainer<AccessCertificationWorkItemType> container = aCase.findContainer(AccessCertificationCaseType.F_WORK_ITEM);
            if (container == null) {
                throw new SystemException("Campaign " + owner + "has no work item for case with ID " + row.accessCertCaseCid);
            }
            PrismContainerValue<AccessCertificationWorkItemType> value = container.findValue(row.cid);
            if (value == null) {
                throw new SystemException("Campaign " + owner + "has no work item with ID " + row.cid);
            }
            return value.asContainerable();
        };
    }
}
