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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableList
import org.gradle.internal.hash.TestHashCodes

class ResolveIncrementalCachingStateStepTest extends AbstractResolveCachingStateStepTest<IncrementalChangesContext, ResolveIncrementalCachingStateStep<IncrementalChangesContext>> {
    @Override
    ResolveIncrementalCachingStateStep<IncrementalChangesContext> createStep() {
        return new ResolveIncrementalCachingStateStep<>(buildCache, delegate)
    }

    def "uses cache key from incremental state when available"() {
        def cacheKey = TestHashCodes.hashCodeFrom(1234)
        delegateResult.executionReasons >> ImmutableList.of()
        delegateResult.reusedOutputOriginMetadata >> Optional.empty()
        delegateResult.afterExecutionOutputState >> Optional.empty()

        when:
        step.execute(work, context)
        then:
        _ * buildCache.enabled >> buildCacheEnabled
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)

        _ * context.cacheKey >> Optional.of(cacheKey)
        _ * context.validationProblems >> ImmutableList.of()
        1 * delegate.execute(work, { CachingContext context ->
            def buildCacheKey = buildCacheEnabled
                ? context.cachingState.whenEnabled().get().key
                : context.cachingState.whenDisabled().get().key.get()
            buildCacheKey.hashCode == cacheKey.toString()
        }) >> delegateResult

        where:
        buildCacheEnabled << [true, false]
    }
}
