/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.Describable;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * A utility class used by {@link ResolutionFailureHandler} to assess and classify
 * how the attributes of candidate variants during a single attempt at dependency resolution
 * align with the requested attributes.
 */
/* package */ final class ResolutionCandidateAssessor {
    private final ImmutableAttributes requestedAttributes;
    private final AttributeMatcher attributeMatcher;

    public ResolutionCandidateAssessor(AttributeContainerInternal requestedAttributes, AttributeMatcher attributeMatcher) {
        this.requestedAttributes = requestedAttributes.asImmutable();
        this.attributeMatcher = attributeMatcher;
    }

    public ImmutableAttributes getRequestedAttributes() {
        return requestedAttributes;
    }

    public AssessedCandidate assessCandidate(
        String candidateName,
        ImmutableAttributes candidateAttributes
    ) {
        Set<String> alreadyAssessed = new HashSet<>(candidateAttributes.keySet().size());
        ImmutableList.Builder<AssessedAttribute<?>> compatible = ImmutableList.builder();
        ImmutableList.Builder<AssessedAttribute<?>> incompatible = ImmutableList.builder();
        ImmutableList.Builder<AssessedAttribute<?>> onlyOnConsumer = ImmutableList.builder();
        ImmutableList.Builder<AssessedAttribute<?>> onlyOnProducer = ImmutableList.builder();

        Sets.union(requestedAttributes.getAttributes().keySet(), candidateAttributes.getAttributes().keySet()).stream()
            .sorted(Comparator.comparing(Attribute::getName))
            .forEach(attribute -> classifyAttribute(requestedAttributes, candidateAttributes, attributeMatcher, attribute, alreadyAssessed, compatible, incompatible, onlyOnConsumer, onlyOnProducer));

        return new AssessedCandidate(candidateName, candidateAttributes, compatible.build(), incompatible.build(), onlyOnConsumer.build(), onlyOnProducer.build());
    }

    private void classifyAttribute(ImmutableAttributes requestedAttributes, ImmutableAttributes candidateAttributes, AttributeMatcher attributeMatcher,
                                   Attribute<?> attribute, Set<String> alreadyAssessed,
                                   ImmutableList.Builder<AssessedAttribute<?>> compatible, ImmutableList.Builder<AssessedAttribute<?>> incompatible,
                                   ImmutableList.Builder<AssessedAttribute<?>> onlyOnConsumer, ImmutableList.Builder<AssessedAttribute<?>> onlyOnProducer) {
        if (alreadyAssessed.add(attribute.getName())) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<?> consumerValue = requestedAttributes.findEntry(attributeName);
            AttributeValue<?> producerValue = candidateAttributes.findEntry(attributeName);

            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    compatible.add(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                } else {
                    incompatible.add(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                }
            } else if (consumerValue.isPresent()) {
                onlyOnConsumer.add(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), null));
            } else if (producerValue.isPresent()) {
                onlyOnProducer.add(new AssessedAttribute<>(attribute, null, Cast.uncheckedCast(producerValue.get())));
            }
        }
    }

    /**
     * An immutable data class that holds information about a single variant which was a candidate for matching during resolution.
     *
     * This includes classifying its attributes into lists of compatible, incompatible, and absent attributes.  Each candidate
     * is assessed within the context of a resolution, so this is a non-{@code static} class implicitly linked to the assessor
     * that produced it.
     */
    public final class AssessedCandidate implements Describable {
        private final String name;
        private final ImmutableAttributes candidateAttributes;

        private final ImmutableList<AssessedAttribute<?>> compatible;
        private final ImmutableList<AssessedAttribute<?>> incompatible;
        private final ImmutableList<AssessedAttribute<?>> onlyOnConsumer;
        private final ImmutableList<AssessedAttribute<?>> onlyOnProducer;

        private AssessedCandidate(String name, ImmutableAttributes attributes, ImmutableList<AssessedAttribute<?>> compatible, ImmutableList<AssessedAttribute<?>> incompatible, ImmutableList<AssessedAttribute<?>> onlyOnConsumer, ImmutableList<AssessedAttribute<?>> onlyOnProducer) {
            this.name = name;
            this.candidateAttributes = attributes;
            this.compatible = compatible;
            this.incompatible = incompatible;
            this.onlyOnConsumer = onlyOnConsumer;
            this.onlyOnProducer = onlyOnProducer;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        public ImmutableAttributes getAllCandidateAttributes() {
            return candidateAttributes;
        }

        public ImmutableAttributes getAllRequestedAttributes() {
            return requestedAttributes;
        }

        public ImmutableList<AssessedAttribute<?>> getCompatibleAttributes() {
            return compatible;
        }

        public ImmutableList<AssessedAttribute<?>> getIncompatibleAttributes() {
            return incompatible;
        }

        public ImmutableList<AssessedAttribute<?>> getOnlyOnConsumerAttributes() {
            return onlyOnConsumer;
        }

        public ImmutableList<AssessedAttribute<?>> getOnlyOnProducerAttributes() {
            return onlyOnProducer;
        }
    }

    /**
     * An immutable data class that records a single attribute, its requested value, and its provided value
     * for a given resolution attempt.
     */
    public static final class AssessedAttribute<T> {
        private final Attribute<T> attribute;

        @Nullable
        private final T requested;
        @Nullable
        private final T provided;

        private AssessedAttribute(Attribute<T> attribute, @Nullable T requested, @Nullable T provided) {
            this.attribute = attribute;
            this.requested = requested;
            this.provided = provided;
        }

        public Attribute<T> getAttribute() {
            return attribute;
        }

        @Nullable
        public T getRequested() {
            return requested;
        }

        @Nullable
        public T getProvided() {
            return provided;
        }

        @Override
        public String toString() {
            return "{name=" + attribute.getName() +
                ", type=" + attribute.getType() +
                ", requested=" + requested +
                ", provided=" + provided +
                '}';
        }
    }
}
