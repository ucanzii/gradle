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

package org.gradle.api.flow;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;

/**
 * Porcelain layer on top of {@link FlowProviders} and {@link FlowScope}.
 *
 * @since 8.7
 */
@Incubating
public interface BuildScopedFlows {

    /**
     * Connects the given {@link ControlFlowAction control flow action} to the {@link FlowProviders#getBeforeProject() beforeProject flow}.
     *
     * @param action control flow action implementation
     * @param configure configuration for the given {@link ControlFlowAction control flow action} parameters
     * @param <P> the parameters defined by the given {@link FlowAction control flow action} type.
     * @return a {@link ControlFlowRegistration} object representing the registered action.
     * @see FlowScope#register(Class, Action)
     * @since 8.7
     */
    <P extends FlowParameters, C extends ControlFlowAction<? super Project, P>> ControlFlowRegistration<C> beforeProject(
        Class<C> action,
        Action<? super ControlFlowActionSpec<Project, P>> configure
    );

}
