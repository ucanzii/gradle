/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.OutputFilesRepository
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.TreeType

class CleanupStaleOutputsStepTest extends StepSpec<WorkspaceContext> {
    def cleanupRegistry = Mock(BuildOutputCleanupRegistry)
    def deleter = Mock(Deleter)
    def outputChangeListener = Mock(OutputChangeListener)
    def outputFilesRepository = Mock(OutputFilesRepository)

    def step = new CleanupStaleOutputsStep<>(
        buildOperationExecutor,
        cleanupRegistry,
        deleter,
        outputChangeListener,
        outputFilesRepository,
        delegate)

    def delegateResult = Mock(Result)

    def "#description is cleaned up: #cleanedUp"() {
        def target = file("target")
        creator(target)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        _ * work.shouldCleanupStaleOutputs() >> true
        _ * work.visitOutputs(_) { UnitOfWork.OutputVisitor visitor ->
            visitor.visitOutputProperty("output", TreeType.FILE, new UnitOfWork.FinalizedOutputFileValueSupplier(target, TestFiles.fixed(target)))
        }

        _ * cleanupRegistry.isOutputOwnedByBuild(target) >> ownedByBuild
        _ * outputFilesRepository.isGeneratedByGradle(target) >> generatedByGradle

        then:
        if (cleanedUp) {
            1 * outputChangeListener.invalidateCachesFor({ Iterable<String> paths ->
                paths ==~ [target.absolutePath]
            })
            1 * deleter.deleteRecursively(target)
        }

        then:
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        where:
        description                                          | cleanedUp | ownedByBuild | generatedByGradle | creator
        "file owned by and generated by build"               | false     | true         | true              | { it.createFile() }
        "file owned by but not generated by build"           | true      | true         | false             | { it.createFile() }
        "dir owned by but not generated by build"            | true      | true         | false             | { it.createDir() }
        "missing file owned by but not generated by build"   | false     | true         | false             | {}
        "symlink owned by but not generated by build"        | true      | true         | false             | { it.createLink(it.parentFile.createFile("existing.txt")) }
        "broken symlink owned by but not generated by build" | true      | true         | false             | { it.createLink(it.parentFile.file("missing.txt")) }
        "file not owned by but generated by build"           | false     | false        | true              | { it.createFile() }
        "file not owned by nor generated by build"           | false     | false        | false             | { it.createFile() }
    }

    def "does not remove any files when work does not allow it"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        _ * work.shouldCleanupStaleOutputs() >> false
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }
}
