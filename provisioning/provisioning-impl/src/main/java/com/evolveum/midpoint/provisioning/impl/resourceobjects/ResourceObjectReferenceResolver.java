/*
 * Copyright (c) 2015-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.impl.resourceobjects;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.provisioning.impl.ProvisioningContext;
import com.evolveum.midpoint.provisioning.impl.shadows.manager.ShadowManager;
import com.evolveum.midpoint.provisioning.impl.shadows.ShadowsFacade;

import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.ucf.api.AttributesToReturn;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceObjectIdentification;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectReferenceResolutionFrequencyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ReadCapabilityType;

/**
 * Resolves resource objects (also) with the help of the repository / shadow manager.
 *
 * Intentionally not a public class.
 *
 * @author semancik
 */
@Component
class ResourceObjectReferenceResolver {

    private static final Trace LOGGER = TraceManager.getTrace(ResourceObjectReferenceResolver.class);

    @Autowired private PrismContext prismContext;
    @Autowired private ExpressionFactory expressionFactory;
    @Autowired private ShadowManager shadowManager;
    @Autowired private ShadowsFacade shadowsFacade;

    @Autowired
    @Qualifier("cacheRepositoryService")
    private RepositoryService repositoryService;

    PrismObject<ShadowType> resolve(ProvisioningContext ctx, ResourceObjectReferenceType resourceObjectReference,
            QName objectClass, final String desc, OperationResult result)
                    throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
                    SecurityViolationException, ExpressionEvaluationException {
        if (resourceObjectReference == null) {
            return null;
        }
        ObjectReferenceType shadowRef = resourceObjectReference.getShadowRef();
        if (shadowRef != null && shadowRef.getOid() != null) {
            if (resourceObjectReference.getResolutionFrequency() == null
                    || resourceObjectReference.getResolutionFrequency() == ResourceObjectReferenceResolutionFrequencyType.ONCE) {
                PrismObject<ShadowType> shadow = repositoryService.getObject(ShadowType.class, shadowRef.getOid(), null, result);
                shadowsFacade.applyDefinition(shadow, result);
                return shadow;
            }
        } else if (resourceObjectReference.getResolutionFrequency() == ResourceObjectReferenceResolutionFrequencyType.NEVER) {
            throw new ObjectNotFoundException("No shadowRef OID in "+desc+" and resolution frequency set to NEVER");
        }
        if (resourceObjectReference.getObjectClass() != null) {
            objectClass = resourceObjectReference.getObjectClass();
            if (objectClass.getNamespaceURI() == null) {
                objectClass = new QName(ResourceTypeUtil.getResourceNamespace(ctx.getResource()), objectClass.getLocalPart());
            }
        }
        ProvisioningContext subctx = ctx.spawn(objectClass);
        // Use "raw" definitions from the original schema to avoid endless loops
        subctx.setUseRefinedDefinition(false);
        subctx.assertDefinition();

        ObjectQuery refQuery = prismContext.getQueryConverter().createObjectQuery(ShadowType.class, resourceObjectReference.getFilter());
        // No variables. At least not now. We expect that mostly constants will be used here.
        VariablesMap variables = new VariablesMap();
        ObjectQuery evaluatedRefQuery = ExpressionUtil.evaluateQueryExpressions(refQuery, variables, MiscSchemaUtil.getExpressionProfile(), expressionFactory, prismContext, desc, ctx.getTask(), result);
        ObjectFilter baseFilter = ObjectQueryUtil.createResourceAndObjectClassFilter(ctx.getResource().getOid(), objectClass, prismContext);
        ObjectFilter filter = prismContext.queryFactory().createAnd(baseFilter, evaluatedRefQuery.getFilter());
        ObjectQuery query = prismContext.queryFactory().createQuery(filter);

        // TODO: implement "repo" search strategies, don't forget to apply definitions

        final Holder<PrismObject<ShadowType>> shadowHolder = new Holder<>();
        ResultHandler<ShadowType> handler = (shadow, objResult) -> {
            if (shadowHolder.getValue() != null) {
                throw new IllegalStateException("More than one search results for " + desc);
            }
            shadowHolder.setValue(shadow);
            return true;
        };

        shadowsFacade.searchObjectsIterative(subctx, query, null, handler, result);

        // TODO: implement storage of OID (ONCE search frequency)

        return shadowHolder.getValue();
    }

    /**
     * Resolve primary identifier from a collection of identifiers that may contain only secondary identifiers.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Collection<? extends ResourceAttribute<?>> resolvePrimaryIdentifier(ProvisioningContext ctx,
            Collection<? extends ResourceAttribute<?>> identifiers, final String desc, OperationResult result)
                    throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
                    ExpressionEvaluationException {
        if (identifiers == null) {
            return null;
        }
        Collection<ResourceAttribute<?>> secondaryIdentifiers = ShadowUtil.getSecondaryIdentifiers(identifiers, ctx.getObjectClassDefinition());
        PrismObject<ShadowType> repoShadow = shadowManager.lookupShadowBySecondaryIds(ctx, secondaryIdentifiers, result);
        if (repoShadow == null) {
            return null;
        }
        shadowsFacade.applyDefinition(repoShadow, result);
        PrismContainer<Containerable> attributesContainer = repoShadow.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributesContainer == null) {
            return null;
        }
        RefinedObjectClassDefinition ocDef = ctx.getObjectClassDefinition();
        Collection primaryIdentifiers = new ArrayList<>();
        for (PrismProperty property: attributesContainer.getValue().getProperties()) {
            if (ocDef.isPrimaryIdentifier(property.getElementName())) {
                RefinedAttributeDefinition<?> attrDef = ocDef.findAttributeDefinition(property.getElementName());
                if (attrDef == null) {
                    throw new IllegalStateException("No definition for attribute " + property);
                }
                ResourceAttribute primaryIdentifier = attrDef.instantiate();
                primaryIdentifier.setRealValue(property.getRealValue());
                primaryIdentifiers.add(primaryIdentifier);
            }
        }
        LOGGER.trace("Resolved identifiers {} to primary identifiers {} (object class {})", identifiers, primaryIdentifiers, ocDef);
        return primaryIdentifiers;
    }

    /**
     * @param oidToReportAsNotFound When we know the shadow OID corresponding to the object being fetched, we should provide
     * it here. It is stored in {@link ObjectNotFoundException} should that be thrown.
     */
    PrismObject<ShadowType> fetchResourceObject(ProvisioningContext ctx,
            Collection<? extends ResourceAttribute<?>> identifiers,
            AttributesToReturn attributesToReturn,
            @Nullable String oidToReportAsNotFound,
            OperationResult parentResult) throws ObjectNotFoundException,
            CommunicationException, SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
        ConnectorInstance connector = ctx.getConnector(ReadCapabilityType.class, parentResult);
        RefinedObjectClassDefinition objectClassDefinition = ctx.getObjectClassDefinition();

        try {

            ReadCapabilityType readCapability = ctx.getEffectiveCapability(ReadCapabilityType.class);
            if (readCapability == null) {
                throw new UnsupportedOperationException("Resource does not support 'read' operation: " + ctx.toHumanReadableDescription());
            }

            if (Boolean.TRUE.equals(readCapability.isCachingOnly())) {
                return ctx.getOriginalShadow();
            }

            ResourceObjectIdentification identification = ResourceObjectIdentification.create(objectClassDefinition, identifiers);
            ResourceObjectIdentification resolvedIdentification = resolvePrimaryIdentifiers(ctx, identification, parentResult);
            resolvedIdentification.validatePrimaryIdenfiers();
            return connector.fetchObject(resolvedIdentification, attributesToReturn, ctx, parentResult);
        } catch (ObjectNotFoundException e) {
            // Not finishing the result because we did not create it! (The same for other catch clauses.)
            parentResult.recordFatalErrorNotFinish("Object not found. Identifiers: " + identifiers + ". Reason: " + e.getMessage(), e);
            throw new ObjectNotFoundException("Object not found. identifiers=" + identifiers + ", objectclass="+
                        PrettyPrinter.prettyPrint(objectClassDefinition.getTypeName())+": "
                    + e.getMessage(), e, oidToReportAsNotFound);
        } catch (CommunicationException e) {
            parentResult.recordFatalErrorNotFinish("Error communication with the connector " + connector
                    + ": " + e.getMessage(), e);
            throw e;
        } catch (GenericFrameworkException e) {
            parentResult.recordFatalErrorNotFinish(
                    "Generic error in the connector " + connector + ". Reason: " + e.getMessage(), e);
            throw new GenericConnectorException("Generic error in the connector " + connector + ". Reason: "
                    + e.getMessage(), e);
        } catch (SchemaException ex) {
            parentResult.recordFatalErrorNotFinish("Can't get resource object, schema error: " + ex.getMessage(), ex);
            throw ex;
        } catch (ExpressionEvaluationException ex) {
            parentResult.recordFatalErrorNotFinish("Can't get resource object, expression error: " + ex.getMessage(), ex);
            throw ex;
        } catch (ConfigurationException e) {
            parentResult.recordFatalErrorNotFinish(e);
            throw e;
        }
    }

    /**
     * Resolve primary identifier from a collection of identifiers that may contain only secondary identifiers.
     */
    @SuppressWarnings("unchecked")
    private ResourceObjectIdentification resolvePrimaryIdentifiers(ProvisioningContext ctx,
            ResourceObjectIdentification identification, OperationResult result)
            throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
            ExpressionEvaluationException {
        if (identification == null) {
            return null;
        }
        if (identification.hasPrimaryIdentifiers()) {
            return identification;
        }
        Collection<ResourceAttribute<?>> secondaryIdentifiers = (Collection<ResourceAttribute<?>>) identification.getSecondaryIdentifiers();
        PrismObject<ShadowType> repoShadow = shadowManager.lookupShadowBySecondaryIds(ctx, secondaryIdentifiers, result);
        if (repoShadow == null) {
            // TODO: we should attempt resource search here
            throw new ObjectNotFoundException("No repository shadow for "+secondaryIdentifiers+", cannot resolve identifiers");
        }
        shadowsFacade.applyDefinition(repoShadow, result);
        PrismContainer<Containerable> attributesContainer = repoShadow.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributesContainer == null) {
            throw new SchemaException("No attributes in "+repoShadow+", cannot resolve identifiers "+secondaryIdentifiers);
        }
        RefinedObjectClassDefinition ocDef = ctx.getObjectClassDefinition();
        Collection primaryIdentifiers = new ArrayList<>();
        for (PrismProperty<?> property: attributesContainer.getValue().getProperties()) {
            if (ocDef.isPrimaryIdentifier(property.getElementName())) {
                RefinedAttributeDefinition<?> attrDef = ocDef.findAttributeDefinition(property.getElementName());
                if (attrDef == null) {
                    throw new IllegalStateException("No definition for attribute " + property);
                }
                @SuppressWarnings("rawtypes")
                ResourceAttribute primaryIdentifier = attrDef.instantiate();
                primaryIdentifier.setRealValue(property.getRealValue());
                primaryIdentifiers.add(primaryIdentifier);
            }
        }
        LOGGER.trace("Resolved {} to primary identifiers {} (object class {})", identification, primaryIdentifiers, ocDef);
        return new ResourceObjectIdentification(identification.getObjectClassDefinition(), primaryIdentifiers,
                identification.getSecondaryIdentifiers());
    }
}
