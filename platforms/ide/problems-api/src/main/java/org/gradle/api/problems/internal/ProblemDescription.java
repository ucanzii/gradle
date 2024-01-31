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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Describes a problem without context.
 * Can happen multiple times in a build.
 * ProblemContext describes its occurrences.
 * Potential key for grouping and deduping
 */
public interface ProblemDescription {

    /**
     * Returns the problem category.
     *
     * @return the problem category.
     */
    ProblemCategory getCategory();


    /**
     * The label of the problem.
     * <p>
     * Labels should be short and concise, so they fit approximately in a single line.
     */
    String getLabel();

    Severity getSeverity();

    @Nullable
    DocLink getDocumentationLink();

    List<String> getSolutions();

}
