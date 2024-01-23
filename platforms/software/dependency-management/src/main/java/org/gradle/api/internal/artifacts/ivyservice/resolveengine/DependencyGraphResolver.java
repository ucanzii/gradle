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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dsl.ModuleReplacementsData;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.CachingDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.LastCandidateCapabilityResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.RejectRemainingCandidates;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.UserConfiguredCapabilityResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;

import javax.inject.Inject;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator.NO_OP;

/**
 * Resolves a {@link ResolveContext} and visits the resulting graph. Essentially, this
 * class is a {@link DependencyGraphBuilder} executor.
 */
public class DependencyGraphResolver {
    private final DependencyMetadataFactory dependencyMetadataFactory;
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;
    private final Instantiator instantiator;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final ModuleComponentGraphResolveStateFactory moduleResolveStateFactory;
    private final DependencyGraphBuilder dependencyGraphBuilder;

    @Inject
    public DependencyGraphResolver(
        DependencyMetadataFactory dependencyMetadataFactory,
        VersionComparator versionComparator,
        VersionParser versionParser,
        InstantiatorFactory instantiatorFactory,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        ModuleComponentGraphResolveStateFactory moduleResolveStateFactory,
        DependencyGraphBuilder dependencyGraphBuilder
    ) {
        this.dependencyMetadataFactory = dependencyMetadataFactory;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.instantiator = instantiatorFactory.decorateScheme().instantiator();
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.moduleResolveStateFactory = moduleResolveStateFactory;
        this.dependencyGraphBuilder = dependencyGraphBuilder;
    }

    /**
     * Perform a graph resolution, visiting the resolved graph with the provided visitor.
     */
    public void resolve(
        RootComponentMetadataBuilder.RootComponentState rootComponent,
        List<? extends DependencyMetadata> syntheticDependencies,
        Spec<? super DependencyMetadata> edgeFilter,
        AttributesSchemaInternal consumerSchema,
        DependencyToComponentIdResolver componentIdResolver,
        ComponentMetaDataResolver componentMetaDataResolver,
        ModuleReplacementsData moduleReplacements,
        ImmutableActionSet<DependencySubstitutionInternal> dependencySubstitutionRule,
        ConflictResolution conflictResolution,
        CapabilitiesResolutionInternal capabilitiesResolutionRules,
        boolean failingOnDynamicVersions,
        boolean failingOnChangingVersions,
        DependencyGraphVisitor modelVisitor
    ) {
        ComponentMetaDataResolver clientModuleResolver = new ClientModuleResolver(
            componentMetaDataResolver,
            dependencyMetadataFactory,
            moduleResolveStateFactory
        );

        DependencySubstitutionApplicator substitutionApplicator = createDependencySubstitutionApplicator(dependencySubstitutionRule);
        ModuleConflictResolver<ComponentState> moduleConflictResolver = createModuleConflictResolver(conflictResolution);
        List<CapabilitiesConflictHandler.Resolver> capabilityConflictResolvers = createCapabilityConflictResolvers(capabilitiesResolutionRules);

        dependencyGraphBuilder.resolve(
            rootComponent,
            syntheticDependencies,
            edgeFilter,
            consumerSchema,
            componentIdResolver,
            clientModuleResolver,
            moduleReplacements,
            substitutionApplicator,
            moduleConflictResolver,
            capabilityConflictResolvers,
            conflictResolution,
            failingOnDynamicVersions,
            failingOnChangingVersions,
            modelVisitor
        );
    }

    private DependencySubstitutionApplicator createDependencySubstitutionApplicator(ImmutableActionSet<DependencySubstitutionInternal> dependencySubstitutionRule) {
        if (dependencySubstitutionRule.isEmpty()) {
            return NO_OP;
        }

        return new CachingDependencySubstitutionApplicator(new DefaultDependencySubstitutionApplicator(componentSelectionDescriptorFactory, dependencySubstitutionRule, instantiator));
    }

    private ModuleConflictResolver<ComponentState> createModuleConflictResolver(ConflictResolution conflictResolution) {
        ModuleConflictResolver<ComponentState> moduleConflictResolver = new LatestModuleConflictResolver<>(versionComparator, versionParser);
        if (conflictResolution != ConflictResolution.preferProjectModules) {
            return moduleConflictResolver;
        }
        return new ProjectDependencyForcingResolver<>(moduleConflictResolver);
    }

    private static List<CapabilitiesConflictHandler.Resolver> createCapabilityConflictResolvers(CapabilitiesResolutionInternal capabilitiesResolutionRules) {
        return ImmutableList.of(
            new UserConfiguredCapabilityResolver(capabilitiesResolutionRules),
            new LastCandidateCapabilityResolver(),
            new RejectRemainingCandidates()
        );
    }
}
