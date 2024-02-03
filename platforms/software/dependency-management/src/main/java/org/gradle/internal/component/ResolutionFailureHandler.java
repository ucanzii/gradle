/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.component;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.transform.AmbiguousArtifactTransformException;
import org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.problems.Problems;
import org.gradle.internal.component.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.DescriberSelector;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.gradle.internal.exceptions.StyledException.style;

// TODO: Register all failures with the Problems API

/**
 * Provides a central location for logging and reporting failures appearing during
 * each stage of resolution, including during the variant selection process.
 *
 * All variant selection failures encountered during selection by the {@link GraphVariantSelector} or
 * {@link AttributeMatchingArtifactVariantSelector}
 * should be routed through this class.
 *
 * This class reports failures to the {@link Problems} service.  It is a
 * Gradle managed type, and so it can serve as an injection point for any types wishing to be notified or respond
 * to the variant selection process.
 */
public class ResolutionFailureHandler {
    public static final String DEFAULT_MESSAGE_PREFIX = "Review the variant matching algorithm at ";

    private static final String AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at ";
    private static final String INCOMPATIBLE_VARIANTS_PREFIX = "Incompatible variant errors are explained in more detail at ";
    private static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";
    private static final String AMBIGUOUS_TRANSFORMATION_PREFIX = "Transformation failures are explained in more detail at ";

    private static final String AMBIGUOUS_VARIANTS_SECTION = "sub:variant-ambiguity";
    private static final String NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match";
    private static final String INCOMPATIBLE_VARIANTS_SECTION = "sub:variant-incompatible";
    private static final String AMBIGUOUS_TRANSFORMATION_SECTION = "sub:transform-ambiguity";

    private final DocumentationRegistry documentationRegistry;

    public ResolutionFailureHandler(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    private void suggestSpecificDocumentation(AbstractVariantSelectionException exception, String prefix, String section) {
        exception.addResolution(prefix + documentationRegistry.getDocumentationFor("variant_model", section) + ".");
    }

    private void suggestReviewAlgorithm(AbstractVariantSelectionException exception) {
        exception.addResolution(DEFAULT_MESSAGE_PREFIX + documentationRegistry.getDocumentationFor("variant_attributes", "sec:abm_algorithm") + ".");
    }


    // region Artifact Variant Selection Failures
    public NoMatchingArtifactVariantsException noMatchingArtifactVariantFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, String variantSetName, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> candidateVariants) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        AttributeDescriber describer = DescriberSelector.selectDescriber(requestedAttributes, schema);
        String message = buildNoMatchingVariantsFailureMsg(resolutionCandidateAssessor, variantSetName, candidateVariants, describer);
        NoMatchingArtifactVariantsException e = new NoMatchingArtifactVariantsException(message);
        suggestSpecificDocumentation(e, NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION);
        suggestReviewAlgorithm(e);
        return e;
    }

    public AmbiguousArtifactVariantsException ambiguousArtifactVariantsFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, String selectedComponentName, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> matchingVariants, Set<ResolvedVariant> discardedVariants) {
        AttributeDescriber describer = DescriberSelector.selectDescriber(requestedAttributes, schema);
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        String message = buildMultipleMatchingVariantsFailureMsg(resolutionCandidateAssessor, describer, selectedComponentName, matchingVariants, discardedVariants);
        AmbiguousArtifactVariantsException e = new AmbiguousArtifactVariantsException(message);
        suggestSpecificDocumentation(e, AMBIGUOUS_VARIANTS_PREFIX, AMBIGUOUS_VARIANTS_SECTION);
        suggestReviewAlgorithm(e);
        return e;
    }

    public AmbiguousArtifactTransformException ambiguousArtifactTransformationFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, String selectedComponentName, ImmutableAttributes requestedAttributes, List<TransformedVariant> transformedVariants) {
        String message = buildAmbiguousTransformMsg(selectedComponentName, requestedAttributes, transformedVariants);
        AmbiguousArtifactTransformException e = new AmbiguousArtifactTransformException(message);
        suggestSpecificDocumentation(e, AMBIGUOUS_TRANSFORMATION_PREFIX, AMBIGUOUS_TRANSFORMATION_SECTION);
        suggestReviewAlgorithm(e);
        return e;
    }

    // TODO: Unify this failure in the exception hierarchy with the others
    public BrokenResolvedArtifactSet unknownArtifactVariantSelectionFailure(AttributesSchema schema, ArtifactVariantSelectionException t) {
        BrokenResolvedArtifactSet e = new BrokenResolvedArtifactSet(t);
        // TODO: unify this with the other failures in the hierarchy so that basic resolution can be added
        //addBasicResolution(e);
        return e;
    }

    public BrokenResolvedArtifactSet unknownArtifactVariantSelectionFailure(AttributesSchema schema, ResolvedVariantSet producer, Exception cause) {
        String message = buildUnknownArtifactVariantFailureMsg(producer);
        ArtifactVariantSelectionException e = new ArtifactVariantSelectionException(message, cause);
        // This is the catch-all error type and there's nothing more specific to add here
        suggestReviewAlgorithm(e);
        return unknownArtifactVariantSelectionFailure(schema, e);
    }

    public IncompatibleArtifactVariantsException incompatibleArtifactVariantsFailure(AttributesSchemaInternal schema, ComponentState selectedComponent, Set<NodeState> incompatibleNodes) {
        String message = buildIncompatibleArtifactVariantsFailureMsg(selectedComponent, incompatibleNodes);
        IncompatibleArtifactVariantsException e = new IncompatibleArtifactVariantsException(message);
        suggestSpecificDocumentation(e, INCOMPATIBLE_VARIANTS_PREFIX, INCOMPATIBLE_VARIANTS_SECTION);
        suggestReviewAlgorithm(e);
        return e;
    }

    private String buildUnknownArtifactVariantFailureMsg(ResolvedVariantSet producer) {
        return String.format("Could not select a variant of %s that matches the consumer attributes.", producer.asDescribable().getDisplayName());
    }

    private String buildNoMatchingVariantsFailureMsg(
        ResolutionCandidateAssessor resolutionCandidateAssessor,
        String producerDisplayName,
        Collection<? extends ResolvedVariant> candidates,
        AttributeDescriber describer
    ) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants of " + style(StyledTextOutput.Style.Info, producerDisplayName) + " match the consumer attributes");
        formatter.startChildren();
        for (ResolvedVariant variant : candidates) {
            String candidateName = variant.asDescribable().getCapitalizedDisplayName();
            AssessedCandidate assessedCandidate = resolutionCandidateAssessor.assessCandidate(candidateName, variant.getAttributes().asImmutable());
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private String buildMultipleMatchingVariantsFailureMsg(ResolutionCandidateAssessor resolutionCandidateAssessor, AttributeDescriber describer, String producerDisplayName, List<? extends ResolvedVariant> variants, Set<ResolvedVariant> discarded) {
        TreeFormatter formatter = new TreeFormatter();
        if (resolutionCandidateAssessor.getRequestedAttributes().isEmpty()) {
            formatter.node("More than one variant of " + producerDisplayName + " matches the consumer attributes");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(resolutionCandidateAssessor.getRequestedAttributes().asMap()) + ". However we cannot choose between the following variants of " + producerDisplayName);
        }
        formatter.startChildren();
        for (ResolvedVariant variant : variants) {
            String candidateName = variant.asDescribable().getCapitalizedDisplayName();
            AssessedCandidate assessedCandidate = resolutionCandidateAssessor.assessCandidate(candidateName, variant.getAttributes().asImmutable());
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer);
        }
        formatter.endChildren();
        if (!discarded.isEmpty()) {
            formatter.node("The following variants were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(v -> v.asDescribable().getCapitalizedDisplayName()))
                .forEach(discardedVariant -> {
                    String candidateName = discardedVariant.asDescribable().getCapitalizedDisplayName();
                    AssessedCandidate assessedCandidate = resolutionCandidateAssessor.assessCandidate(candidateName, discardedVariant.getAttributes().asImmutable());
                    formatter.node(discardedVariant.asDescribable().getCapitalizedDisplayName());
                    formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
                });
            formatter.endChildren();
        }
        return formatter.toString();
    }

    private String buildAmbiguousTransformMsg(String producerDisplayName, AttributeContainerInternal requested, List<TransformedVariant> candidates) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Found multiple transforms that can produce a variant of " + producerDisplayName + " with requested attributes");
        formatSortedAttributes(formatter, requested);
        formatter.node("Found the following transforms");

        Comparator<TransformedVariant> variantComparator =
            Comparator.<TransformedVariant, String>comparing(x -> x.getTransformChain().getDisplayName())
                .thenComparing(x -> x.getAttributes().toString());
        Map<ResolvedVariant, List<TransformedVariant>> variantToTransforms = candidates.stream().collect(Collectors.groupingBy(
            TransformedVariant::getRoot,
            () -> new TreeMap<>(Comparator.comparing(variant -> variant.asDescribable().getDisplayName())),
            Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted(variantComparator).collect(Collectors.toList()))));

        formatter.startChildren();
        for (Map.Entry<ResolvedVariant, List<TransformedVariant>> entry : variantToTransforms.entrySet()) {
            formatter.node("From '" + entry.getKey().asDescribable().getDisplayName() + "'");
            formatter.startChildren();
            formatter.node("With source attributes");
            formatSortedAttributes(formatter, entry.getKey().getAttributes());
            formatter.node("Candidate transform(s)");
            formatter.startChildren();
            for (TransformedVariant variant : entry.getValue()) {
                formatter.node("Transform '" + variant.getTransformChain().getDisplayName() + "' producing attributes:");
                formatSortedAttributes(formatter, variant.getAttributes());
            }
            formatter.endChildren();
            formatter.endChildren();
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private void formatSortedAttributes(TreeFormatter formatter, AttributeContainer attributes) {
        formatter.startChildren();
        for (Attribute<?> attribute : Ordering.usingToString().sortedCopy(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'");
        }
        formatter.endChildren();
    }

    private String buildIncompatibleArtifactVariantsFailureMsg(ComponentState selected, Set<NodeState> incompatibleNodes) {
        StringBuilder sb = new StringBuilder("Multiple incompatible variants of ")
            .append(selected.getId())
            .append(" were selected:\n");
        ArrayList<NodeState> sorted = Lists.newArrayList(incompatibleNodes);
        sorted.sort(Comparator.comparing(NodeState::getNameWithVariant));
        for (NodeState node : sorted) {
            sb.append("   - Variant ").append(node.getNameWithVariant()).append(" has attributes ");
            formatAttributes(sb, node.getMetadata().getAttributes());
            sb.append("\n");
        }
        return sb.toString();
    }

    private void formatAttributes(StringBuilder sb, ImmutableAttributes attributes) {
        ImmutableSet<Attribute<?>> keySet = attributes.keySet();
        List<Attribute<?>> sorted = Lists.newArrayList(keySet);
        sorted.sort(Comparator.comparing(Attribute::getName));
        boolean space = false;
        sb.append("{");
        for (Attribute<?> attribute : sorted) {
            if (space) {
                sb.append(", ");
            }
            sb.append(attribute.getName()).append("=").append(attributes.getAttribute(attribute));
            space = true;
        }
        sb.append("}");
    }
    // endregion Artifact Variant Selection Failures

    // region Graph Variant Selection Failures
    public AmbiguousGraphVariantsException ambiguousGraphVariantsFailure(
        AttributesSchemaInternal schema, AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        List<? extends VariantGraphResolveState> matchingVariants,
        ComponentGraphResolveMetadata targetComponent, boolean variantAware,
        @Nullable Set<VariantGraphResolveState> discarded
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        AttributeDescriber describer = DescriberSelector.selectDescriber(requestedAttributes, schema);
        String message = buildAmbiguousGraphVariantsFailureMsg(resolutionCandidateAssessor, new StyledDescriber(describer), matchingVariants, targetComponent, variantAware, discarded);
        AmbiguousGraphVariantsException e = new AmbiguousGraphVariantsException(message);
        e.addResolution(AMBIGUOUS_VARIANTS_PREFIX + documentationRegistry.getDocumentationFor("variant_model", AMBIGUOUS_VARIANTS_SECTION + "."));
        suggestReviewAlgorithm(e);
        return e;
    }

    public IncompatibleGraphVariantsException incompatibleGraphVariantsFailure(
        AttributesSchemaInternal schema,
        AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        ComponentGraphResolveMetadata targetComponent,
        ConfigurationGraphResolveState targetConfiguration,
        boolean variantAware
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        AttributeDescriber describer = DescriberSelector.selectDescriber(requestedAttributes, schema);
        String message = buildIncompatibleGraphVariantsFailureMsg(resolutionCandidateAssessor, targetComponent, targetConfiguration, variantAware, describer);
        IncompatibleGraphVariantsException e = new IncompatibleGraphVariantsException(message);
        e.addResolution(INCOMPATIBLE_VARIANTS_PREFIX + documentationRegistry.getDocumentationFor("variant_model", INCOMPATIBLE_VARIANTS_SECTION) + ".");
        suggestReviewAlgorithm(e);
        return e;
    }

    public NoMatchingGraphVariantsException noMatchingGraphVariantFailure(
        AttributesSchemaInternal schema,
        AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        ComponentGraphResolveMetadata targetComponent,
        GraphSelectionCandidates candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        AttributeDescriber describer = DescriberSelector.selectDescriber(requestedAttributes, schema);
        String message = buildNoMatchingGraphVariantSelectionFailureMsg(resolutionCandidateAssessor, new StyledDescriber(describer), targetComponent, candidates);
        NoMatchingGraphVariantsException e = new NoMatchingGraphVariantsException(message);
        suggestReviewAlgorithm(e);
        e.addResolution(NO_MATCHING_VARIANTS_PREFIX + documentationRegistry.getDocumentationFor("variant_model", NO_MATCHING_VARIANTS_SECTION + "."));
        return e;
    }

    public NoMatchingCapabilitiesException noMatchingCapabilitiesFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, List<? extends VariantGraphResolveState> candidates) {
        String message = buildNoMatchingCapabilitiesFailureMsg(targetComponent, requestedCapabilities, candidates);
        NoMatchingCapabilitiesException e = new NoMatchingCapabilitiesException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    public ConfigurationNotFoundException configurationNotFoundFailure(AttributesSchemaInternal schema, String targetConfigurationName, ComponentIdentifier targetComponentId) {
        String message = buildConfigurationNotFoundFailureMsg(targetConfigurationName, targetComponentId);
        ConfigurationNotFoundException e = new ConfigurationNotFoundException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    /**
     * This is a special case where a configuration is requested by name in a target component, so there is no relevant schema to provide to this handler method.
     */
    public ExternalConfigurationNotFoundException externalConfigurationNotFoundFailure(ComponentIdentifier fromComponent, String fromConfigurationName, ComponentIdentifier toComponent, String toConfigurationName) {
        String message = buildExternalConfigurationNotFoundFailureMsg(fromComponent, fromConfigurationName, toConfigurationName, toComponent);
        ExternalConfigurationNotFoundException e = new ExternalConfigurationNotFoundException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    public ConfigurationNotConsumableException configurationNotConsumableFailure(AttributesSchemaInternal schema, String targetComponentName, String targetConfigurationName) {
        String message = buildConfigurationNotConsumableFailureMsg(targetComponentName, targetConfigurationName);
        ConfigurationNotConsumableException e = new ConfigurationNotConsumableException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    private String buildConfigurationNotFoundFailureMsg(String targetConfigurationName, ComponentIdentifier targetComponentId) {
        return String.format("A dependency was declared on configuration '%s' which is not declared in the descriptor for %s.", targetConfigurationName, targetComponentId.getDisplayName());
    }

    private String buildExternalConfigurationNotFoundFailureMsg(ComponentIdentifier fromComponent, String fromConfiguration, String toConfiguration, ComponentIdentifier toComponent) {
        return String.format("%s declares a dependency from configuration '%s' to configuration '%s' which is not declared in the descriptor for %s.", StringUtils.capitalize(fromComponent.getDisplayName()), fromConfiguration, toConfiguration, toComponent.getDisplayName());
    }

    private String buildConfigurationNotConsumableFailureMsg(String targetComponentName, String targetConfigurationName) {
        return String.format("Selected configuration '" + targetConfigurationName + "' on '" + targetComponentName + "' but it can't be used as a project dependency because it isn't intended for consumption by other components.");
    }

    private String buildAmbiguousGraphVariantsFailureMsg(
        ResolutionCandidateAssessor resolutionCandidateAssessor,
        AttributeDescriber describer,
        List<? extends VariantGraphResolveState> matches,
        ComponentGraphResolveMetadata targetComponent, boolean variantAware,
        @Nullable Set<VariantGraphResolveState> discarded
    ) {
        Map<String, VariantGraphResolveState> ambiguousVariants = new TreeMap<>();
        for (VariantGraphResolveState match : matches) {
            ambiguousVariants.put(match.getName(), match);
        }
        TreeFormatter formatter = new TreeFormatter();
        String configTerm = variantAware ? "variants" : "configurations";
        if (resolutionCandidateAssessor.getRequestedAttributes().isEmpty()) {
            formatter.node("Cannot choose between the following " + configTerm + " of ");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(resolutionCandidateAssessor.getRequestedAttributes().asMap()) + ". However we cannot choose between the following " + configTerm + " of ");
        }
        formatter.append(style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName()));
        formatter.startChildren();
        for (String configuration : ambiguousVariants.keySet()) {
            formatter.node(configuration);
        }
        formatter.endChildren();
        formatter.node("All of them match the consumer attributes");
        // We're sorting the names of the variants and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (VariantGraphResolveState ambiguousVariant : ambiguousVariants.values()) {
            AssessedCandidate assessedCandidate = resolutionCandidateAssessor.assessCandidate(ambiguousVariant.getName(), ambiguousVariant.getAttributes().asImmutable());
            formatUnselectable(assessedCandidate, formatter, targetComponent, ambiguousVariant.getMetadata(), variantAware, true, describer);
        }
        formatter.endChildren();
        if (discarded != null && !discarded.isEmpty()) {
            formatter.node("The following " + configTerm + " were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(VariantGraphResolveState::getName))
                .forEach(discardedConf -> {
                    AssessedCandidate assessedCandidate = resolutionCandidateAssessor.assessCandidate(discardedConf.getName(), discardedConf.getAttributes().asImmutable());
                    formatUnselectable(assessedCandidate, formatter, targetComponent, discardedConf.getMetadata(), variantAware, false, describer);
                });
            formatter.endChildren();
        }

        return formatter.toString();
    }

    private String buildIncompatibleGraphVariantsFailureMsg(
        ResolutionCandidateAssessor resolutionCandidateAssessor,
        ComponentGraphResolveMetadata targetComponent,
        ConfigurationGraphResolveState targetConfiguration,
        boolean variantAware,
        AttributeDescriber describer
    ) {
        TreeFormatter formatter = new TreeFormatter();
        String candidateName = targetConfiguration.getName();
        AssessedCandidate assessedCandidate = resolutionCandidateAssessor.assessCandidate(candidateName, targetConfiguration.asVariant().getMetadata().getAttributes());
        formatter.node("Configuration '" + candidateName + "' in " + style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName()) + " does not match the consumer attributes");
        formatUnselectable(assessedCandidate, formatter, targetComponent, targetConfiguration.asVariant().getMetadata(), variantAware, false, describer);
        return formatter.toString();
    }

    private String buildNoMatchingGraphVariantSelectionFailureMsg(
        ResolutionCandidateAssessor resolutionCandidateAssessor,
        AttributeDescriber describer,
        final ComponentGraphResolveMetadata targetComponent,
        GraphSelectionCandidates candidates
    ) {
        boolean variantAware = candidates.isUseVariants();
        List<? extends VariantGraphResolveMetadata> variants = extractVariants(candidates);

        TreeFormatter formatter = new TreeFormatter();
        String targetVariantText = style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName());
        if (resolutionCandidateAssessor.getRequestedAttributes().isEmpty()) {
            formatter.node("Unable to find a matching " + (variantAware ? "variant" : "configuration") + " of " + targetVariantText);
        } else {
            formatter.node("No matching " + (variantAware ? "variant" : "configuration") + " of " + targetVariantText + " was found. The consumer was configured to find " + describer.describeAttributeSet(resolutionCandidateAssessor.getRequestedAttributes().asMap()) + " but:");
        }
        formatter.startChildren();
        if (variants.isEmpty()) {
            formatter.node("None of the " + (variantAware ? "variants" : "consumable configurations") + " have attributes.");
        } else {
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (VariantGraphResolveMetadata variant : variants) {
                AssessedCandidate assessedCandidate = resolutionCandidateAssessor.assessCandidate(variant.getName(), variant.getAttributes());
                formatUnselectable(assessedCandidate, formatter, targetComponent, variant, variantAware, false, describer);
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private String buildNoMatchingCapabilitiesFailureMsg(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, List<? extends VariantGraphResolveState> candidates) {
        StringBuilder sb = new StringBuilder("Unable to find a variant of ");
        sb.append(targetComponent.getId()).append(" providing the requested ");
        sb.append(CapabilitiesSupport.prettifyCapabilities(targetComponent, requestedCapabilities));
        sb.append(":\n");
        for (VariantGraphResolveState candidate : candidates) {
            sb.append("   - Variant ").append(candidate.getName()).append(" provides ");
            sb.append(CapabilitiesSupport.sortedCapabilityList(targetComponent, candidate.getCapabilities().asSet())).append("\n");
        }
        return sb.toString();
    }

    private void formatUnselectable(
        AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        ComponentGraphResolveMetadata targetComponent,
        VariantGraphResolveMetadata variant,
        boolean variantAware,
        boolean ambiguous,
        AttributeDescriber describer
    ) {
        if (variantAware) {
            formatter.node("Variant '");
        } else {
            formatter.node("Configuration '");
        }
        formatter.append(variant.getName());
        formatter.append("'");
        if (variantAware) {
            formatter.append(" " + CapabilitiesSupport.prettifyCapabilities(targetComponent, variant.getCapabilities().asSet()));
        }
        if (ambiguous) {
            formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer);
        } else {
            formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
        }
    }

    private void formatAttributeMatchesForIncompatibility(
        AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        // None of the nullability warnings are relevant here because the attribute values are only retrieved from collections that will contain them
        Map<Attribute<?>, ?> compatibleAttrs = assessedCandidate.getCompatibleAttributes().stream()
            .collect(Collectors.toMap(ResolutionCandidateAssessor.AssessedAttribute::getAttribute, ResolutionCandidateAssessor.AssessedAttribute::getProvided, (a, b) -> a));
        Map<Attribute<?>, ?> incompatibleAttrs = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(Collectors.toMap(ResolutionCandidateAssessor.AssessedAttribute::getAttribute, ResolutionCandidateAssessor.AssessedAttribute::getProvided, (a, b) -> a));
        Map<Attribute<?>, ?> incompatibleConsumerAttrs = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(Collectors.toMap(ResolutionCandidateAssessor.AssessedAttribute::getAttribute, ResolutionCandidateAssessor.AssessedAttribute::getRequested, (a, b) -> a));
        List<String> otherValues = assessedCandidate.getOnlyOnConsumerAttributes().stream()
            .map(assessedAttribute -> "Doesn't say anything about " + describer.describeMissingAttribute(assessedAttribute.getAttribute(), assessedAttribute.getRequested()))
            .sorted()
            .collect(Collectors.toList());

        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        if (!incompatibleAttrs.isEmpty()) {
            formatter.node("Incompatible because this component declares " + style(StyledTextOutput.Style.FailureHeader, describer.describeAttributeSet(incompatibleAttrs)) + " and the consumer needed <FailureHeader>" + describer.describeAttributeSet(incompatibleConsumerAttrs) + "</FailureHeader>");
        }
        formatAttributeSection(formatter, "Other compatible attribute", otherValues);
        formatter.endChildren();
    }

    private void formatAttributeMatchesForAmbiguity(
        AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        // None of the nullability warnings are relevant here because the attribute values are only retrieved from collections that will contain them
        Map<Attribute<?>, ?> compatibleAttrs = assessedCandidate.getCompatibleAttributes().stream()
            .collect(Collectors.toMap(ResolutionCandidateAssessor.AssessedAttribute::getAttribute, ResolutionCandidateAssessor.AssessedAttribute::getProvided, (a, b) -> a));
        List<String> onlyOnProducer = assessedCandidate.getOnlyOnProducerAttributes().stream()
            .map(assessedAttribute -> "Provides " + describer.describeExtraAttribute(assessedAttribute.getAttribute(), assessedAttribute.getProvided()) + " but the consumer didn't ask for it")
            .collect(Collectors.toList());
        List<String> onlyOnConsumer = assessedCandidate.getOnlyOnConsumerAttributes().stream()
            .map(assessedAttribute -> "Doesn't say anything about " + describer.describeMissingAttribute(assessedAttribute.getAttribute(), assessedAttribute.getRequested()))
            .collect(Collectors.toList());

        List<String> other = new ArrayList<>(onlyOnProducer.size() + onlyOnConsumer.size());
        other.addAll(onlyOnProducer);
        other.addAll(onlyOnConsumer);
        other.sort(String::compareTo);

        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        formatAttributeSection(formatter, "Unmatched attribute", other);
        formatter.endChildren();
    }

    private void formatAttributeSection(TreeFormatter formatter, String section, List<String> values) {
        if (!values.isEmpty()) {
            if (values.size() > 1) {
                formatter.node(section + "s");
            } else {
                formatter.node(section);
            }
            formatter.startChildren();
            values.forEach(formatter::node);
            formatter.endChildren();
        }
    }

    /**
     * Extracts variant metadata from the given {@link GraphSelectionCandidates}.
     *
     * @param candidates the candidates to extract variants from
     * @return the extracted variants, sorted by name
     */
    private List<? extends VariantGraphResolveMetadata> extractVariants(GraphSelectionCandidates candidates) {
        final List<? extends VariantGraphResolveMetadata> variants;
        if (candidates.isUseVariants()) {
            variants = candidates.getVariants().stream()
                .map(VariantGraphResolveState::getMetadata)
                .collect(Collectors.toList());
        } else {
            variants = new ArrayList<>(candidates.getCandidateConfigurations()); // Need non-immutable copy to sort
        }

        variants.sort(Comparator.comparing(VariantGraphResolveMetadata::getName));
        return variants;
    }
    // endregion Graph Variant Selection Failures
}
