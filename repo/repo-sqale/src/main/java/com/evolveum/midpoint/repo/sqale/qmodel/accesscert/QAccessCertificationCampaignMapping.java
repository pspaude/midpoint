/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.accesscert;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.SqaleUtils;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QUserMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QAssignmentHolderMapping;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType;
import com.querydsl.core.Tuple;

/**
 * Mapping between {@link QAccessCertificationCampaign}
 * and {@link AccessCertificationCampaignType}.
 */
public class QAccessCertificationCampaignMapping
        extends QAssignmentHolderMapping<AccessCertificationCampaignType,
        QAccessCertificationCampaign, MAccessCertificationCampaign> {

    public static final String DEFAULT_ALIAS_NAME = "acc";
    private static QAccessCertificationCampaignMapping instance;

    // Explanation in class Javadoc for SqaleTableMapping
    public static QAccessCertificationCampaignMapping initAccessCertificationCampaignMapping(
            @NotNull SqaleRepoContext repositoryContext) {
        instance = new QAccessCertificationCampaignMapping(repositoryContext);
        return instance;
    }

    // Explanation in class Javadoc for SqaleTableMapping
    public static QAccessCertificationCampaignMapping getAccessCertificationCampaignMapping() {
        return Objects.requireNonNull(instance);
    }

    private QAccessCertificationCampaignMapping(@NotNull SqaleRepoContext repositoryContext) {
        super(QAccessCertificationCampaign.TABLE_NAME, DEFAULT_ALIAS_NAME,
                AccessCertificationCampaignType.class, QAccessCertificationCampaign.class,
                repositoryContext);

        addRefMapping(F_DEFINITION_REF,
                q -> q.definitionRefTargetOid,
                q -> q.definitionRefTargetType,
                q -> q.definitionRefRelationId,
                QAccessCertificationDefinitionMapping::get);
        addItemMapping(F_END_TIMESTAMP,
                timestampMapper(q -> q.endTimestamp));
        addItemMapping(F_HANDLER_URI, uriMapper(q -> q.handlerUriId));
        // TODO: iteration -> campaignIteration
        addItemMapping(F_ITERATION, integerMapper(q -> q.campaignIteration));
        addRefMapping(F_OWNER_REF,
                q -> q.ownerRefTargetOid,
                q -> q.ownerRefTargetType,
                q -> q.ownerRefRelationId,
                QUserMapping::getUserMapping);
        addItemMapping(F_STAGE_NUMBER, integerMapper(q -> q.stageNumber));
        addItemMapping(F_START_TIMESTAMP,
                timestampMapper(q -> q.startTimestamp));
        addItemMapping(F_STATE, enumMapper(q -> q.state));

        addContainerTableMapping(F_CASE,
                QAccessCertificationCaseMapping.initAccessCertificationCaseMapping(repositoryContext),
                joinOn((o, acase) -> o.oid.eq(acase.ownerOid)));
    }


    @Override
    protected Collection<? extends QName> fullObjectItemsToSkip() {
        return Collections.singletonList(F_CASE);
    }

    @Override
    protected QAccessCertificationCampaign newAliasInstance(String alias) {
        return new QAccessCertificationCampaign(alias);
    }

    @Override
    public MAccessCertificationCampaign newRowObject() {
        return new MAccessCertificationCampaign();
    }

    @Override
    public @NotNull MAccessCertificationCampaign toRowObjectWithoutFullObject(
            AccessCertificationCampaignType schemaObject, JdbcSession jdbcSession) {
        MAccessCertificationCampaign row =
                super.toRowObjectWithoutFullObject(schemaObject, jdbcSession);

        setReference(schemaObject.getDefinitionRef(),
                o -> row.definitionRefTargetOid = o,
                t -> row.definitionRefTargetType = t,
                r -> row.definitionRefRelationId = r);
        row.endTimestamp =
                MiscUtil.asInstant(schemaObject.getEndTimestamp());
        row.handlerUriId = processCacheableUri(schemaObject.getHandlerUri());
        // TODO
        row.campaignIteration = schemaObject.getIteration();
        setReference(schemaObject.getOwnerRef(),
                o -> row.ownerRefTargetOid = o,
                t -> row.ownerRefTargetType = t,
                r -> row.ownerRefRelationId = r);
        row.stageNumber = schemaObject.getStageNumber();
        row.startTimestamp =
                MiscUtil.asInstant(schemaObject.getStartTimestamp());
        row.state = schemaObject.getState();

        return row;
    }

    @Override
    public void storeRelatedEntities(
            @NotNull MAccessCertificationCampaign row, @NotNull AccessCertificationCampaignType schemaObject,
            @NotNull JdbcSession jdbcSession) throws SchemaException {
        super.storeRelatedEntities(row, schemaObject, jdbcSession);

        List<AccessCertificationCaseType> cases = schemaObject.getCase();
        if (!cases.isEmpty()) {
            for (AccessCertificationCaseType c : cases) {
                QAccessCertificationCaseMapping.getAccessCertificationCaseMapping().insert(c, row, jdbcSession);
            }
        }
    }

    @Override
    public AccessCertificationCampaignType toSchemaObject(Tuple result, QAccessCertificationCampaign root,
            Collection<SelectorOptions<GetOperationOptions>> options, @NotNull JdbcSession jdbcSession,
            boolean forceFull) throws SchemaException {
        AccessCertificationCampaignType base = super.toSchemaObject(result, root, options, jdbcSession, forceFull);
        if(forceFull || SelectorOptions.hasToLoadPath(F_CASE, options)) {
            loadCases(base, options, jdbcSession, forceFull);
        }
        return base;
    }

    private void loadCases(AccessCertificationCampaignType base, Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull JdbcSession jdbcSession, boolean forceFull) throws SchemaException {
        QAccessCertificationCaseMapping casesMapping = QAccessCertificationCaseMapping.getAccessCertificationCaseMapping();
        PrismContainer<AccessCertificationCaseType> cases = base.asPrismObject().findOrCreateContainer(F_CASE);
        cases.setIncomplete(false);
        QAccessCertificationCase qcase = casesMapping.defaultAlias();
        List<Tuple> rows = jdbcSession.newQuery()
            .from(qcase)
            .select(casesMapping.selectExpressions(qcase, options))
            .where(qcase.ownerOid.eq(SqaleUtils.oidToUUid(base.getOid())))
            .fetch();
        for (Tuple row : rows) {
            AccessCertificationCaseType c = casesMapping.toSchemaObject(row, qcase, options, jdbcSession, forceFull);
            cases.add(c.asPrismContainerValue());
        }
    }
}
