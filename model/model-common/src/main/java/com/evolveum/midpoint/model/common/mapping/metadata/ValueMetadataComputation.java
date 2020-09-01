/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.common.mapping.metadata;

import java.util.*;
import java.util.Objects;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.common.ModelCommonBeans;
import com.evolveum.midpoint.model.common.mapping.MappingEvaluationEnvironment;
import com.evolveum.midpoint.model.common.mapping.metadata.builtin.BuiltinMetadataMapping;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.repo.common.expression.Source;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import org.jetbrains.annotations.Nullable;

/**
 * Computation of value metadata.
 *
 * It is used currently in two contexts:
 * 1. During expression evaluation where zero, one, or more input values are combined to form zero, one, or multiple output vales.
 * 2. During consolidation where a set of the same values (possibly with different metadata) are combined into single value
 *    with given metadata (that have to be derived from the constituents).
 *
 * Preliminary implementation. For example,
 * - it does no real consolidation: it simply adds all values into respective items;
 * - it works with simplified computation model: its input is simply a list of input values (regardless of their
 *   parent item).
 */
abstract public class ValueMetadataComputation {

    private static final Trace LOGGER = TraceManager.getTrace(ValueMetadataComputation.class);

    private static final String OP_EXECUTE = ValueMetadataComputation.class.getName() + ".execute";

    /**
     * Metadata processing specification: how should we compute the resulting metadata?
     */
    @NotNull private final ItemValueMetadataProcessingSpec processingSpec;

    /**
     * Mapping specification - present only for transformational situations.
     */
    @Nullable private final MappingSpecificationType mappingSpecification;

    /**
     * Context desc + now + task.
     */
    private final MappingEvaluationEnvironment env;

    /**
     * The operation result (actual).
     */
    private OperationResult result;

    /**
     * Necessary beans.
     */
    @NotNull private final ModelCommonBeans beans;

    /**
     * Definition of ValueMetadataType container.
     */
    @NotNull private final PrismContainerDefinition<ValueMetadataType> metadataDefinition;

    /**
     * Result of the computation: the metadata.
     */
    @NotNull private final PrismContainerValue<ValueMetadataType> outputMetadata;

    ValueMetadataComputation(@NotNull ItemValueMetadataProcessingSpec processingSpec,
            @Nullable MappingSpecificationType mappingSpecification,
            @NotNull ModelCommonBeans beans, MappingEvaluationEnvironment env) {
        this.processingSpec = processingSpec;
        this.mappingSpecification = mappingSpecification;
        this.beans = beans;
        this.env = env;
        this.metadataDefinition = Objects.requireNonNull(
                beans.prismContext.getSchemaRegistry().findContainerDefinitionByCompileTimeClass(ValueMetadataType.class),
                "No definition of value metadata container");
        //noinspection unchecked
        this.outputMetadata = new ValueMetadataType(beans.prismContext).asPrismContainerValue();
    }

    @NotNull
    public ValueMetadataType execute(OperationResult parentResult) throws CommunicationException, ObjectNotFoundException, SchemaException,
            SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
        result = parentResult.createMinorSubresult(OP_EXECUTE);
        try {
            logStart();
            processCustomMappings();
            processBuiltinMappings();
            recordOutput();
            return outputMetadata.asContainerable();
        } catch (Throwable t) {
            result.recordFatalError(t);
            throw t;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    private void recordOutput() {
        result.addReturn("summary", outputMetadata.toString()); // temporary
    }

    abstract void logStart();

    private void processCustomMappings()
            throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException,
            ConfigurationException, ExpressionEvaluationException {
        for (MetadataMappingType mappingBean : processingSpec.getMappings()) {
            MetadataMappingImpl<?, ?> mapping = createMapping(mappingBean);
            mapping.evaluate(env.task, result);
            appendValues(mapping.getOutputPath(), mapping.getOutputTriple());
        }
    }

    private void appendValues(ItemPath outputPath, PrismValueDeltaSetTriple<?> outputTriple) throws SchemaException {
        ItemDelta<?, ?> itemDelta = beans.prismContext.deltaFor(ValueMetadataType.class)
                .item(outputPath)
                .add(outputTriple.getNonNegativeValues())
                .asItemDelta();
        itemDelta.applyTo(outputMetadata);
    }

    private MetadataMappingImpl<?, ?> createMapping(MetadataMappingType mappingBean) throws SchemaException {
        MetadataMappingBuilder<?, ?> builder = beans.metadataMappingEvaluator.mappingFactory
                .createMappingBuilder(mappingBean, env.contextDescription);
        createSources(builder, mappingBean);
        createCustomMappingVariables(builder, mappingBean);
        builder.targetContext(metadataDefinition)
                .now(env.now)
                .conditionMaskOld(false); // We are not interested in old values (deltas are irrelevant in metadata mappings).
        return builder.build();
    }

    // TODO unify with parsing data mapping sources (MappingParser class)
    private void createSources(MetadataMappingBuilder<?, ?> builder, MetadataMappingType mappingBean) throws SchemaException {
        for (VariableBindingDefinitionType sourceDef : mappingBean.getSource()) {
            ItemPath sourcePath = getSourcePath(sourceDef);
            QName sourceName = getSourceName(sourceDef, sourcePath);
            ItemDefinition sourceDefinition = getAdaptedSourceDefinition(sourcePath);
            Item sourceItem = sourceDefinition.instantiate();
            //noinspection unchecked
            sourceItem.addAll(getSourceValues(sourcePath));
            //noinspection unchecked
            Source<?, ?> source = new Source<>(sourceItem, null, null, sourceName, sourceDefinition);
            source.recompute();
            builder.additionalSource(source);
        }
    }

    void createCustomMappingVariables(MetadataMappingBuilder<?,?> builder, MetadataMappingType mappingBean) {
    }

    @NotNull
    private MutableItemDefinition getAdaptedSourceDefinition(ItemPath sourcePath) {
        ItemDefinition sourceDefinition =
                Objects.requireNonNull(metadataDefinition.findItemDefinition(sourcePath),
                        () -> "No definition for '" + sourcePath + "' in " + env.contextDescription);
        MutableItemDefinition sourceDefinitionMultivalued =
                sourceDefinition.clone().toMutable();
        sourceDefinitionMultivalued.setMaxOccurs(-1);
        return sourceDefinitionMultivalued;
    }

    abstract Collection<?> getSourceValues(ItemPath sourcePath);

    QName getSourceName(VariableBindingDefinitionType sourceDef, ItemPath sourcePath) {
        return sourceDef.getName() != null ? sourceDef.getName() : ItemPath.toName(sourcePath.last());
    }

    ItemPath getSourcePath(VariableBindingDefinitionType sourceDef) {
        return Objects.requireNonNull(sourceDef.getPath(), () -> "No source path in " + env.contextDescription)
                .getItemPath();
    }

    private void processBuiltinMappings() throws SchemaException {
        for (BuiltinMetadataMapping mapping : beans.metadataMappingEvaluator.getBuiltinMappings()) {
            if (isApplicable(mapping)) {
                LOGGER.trace("Applying built-in metadata mapping: {}", mapping.getClass().getSimpleName());
                applyBuiltinMapping(mapping);
            }
        }
    }

    abstract void applyBuiltinMapping(BuiltinMetadataMapping mapping) throws SchemaException;

    private boolean isApplicable(BuiltinMetadataMapping mapping) throws SchemaException {
        return processingSpec.isFullProcessing(mapping.getTargetPath());
    }

    public MetadataMappingScopeType getScope() {
        return processingSpec.getScope();
    }

    public MappingEvaluationEnvironment getEnv() {
        return env;
    }

    public String getContextDescription() {
        return env.contextDescription;
    }

    public @Nullable MappingSpecificationType getMappingSpecification() {
        return mappingSpecification;
    }

    public @NotNull PrismContainerValue<ValueMetadataType> getOutputMetadataValue() {
        return outputMetadata;
    }

    public @NotNull ValueMetadataType getOutputMetadataValueBean() {
        return outputMetadata.asContainerable();
    }
}
