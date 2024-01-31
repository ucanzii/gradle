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

package org.gradle.api.problems.internal

import org.gradle.api.problems.Severity
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Specification

class DefaultProblemReportTest extends Specification {
    def "unbound builder result is equal to original"() {
        def problem = createTestProblem(severity, additionalData)

        def newProblem = problem.toBuilder().build()
        expect:
        newProblem.category == problem.category
        newProblem.label == problem.label
        newProblem.additionalData == problem.additionalData
        newProblem.details == problem.details
        newProblem.exception == problem.exception
        newProblem.locations == problem.locations
        newProblem.severity == problem.severity
        newProblem.solutions == problem.solutions
        newProblem == problem

        where:
        severity         | additionalData
        Severity.WARNING | [:]
        Severity.ERROR   | [data1: "data2"]
    }

    def "unbound builder result with a change and check report"() {
        given:
        def emitter = Mock(ProblemEmitter)
        def problemReporter = new DefaultProblemReporter(emitter, [], "core")
        def problem = createTestProblem(Severity.WARNING, [:])
        def builder = problem.toBuilder()
        def newProblem = builder
            .solution("solution")
            .build()
        def operationId = new OperationIdentifier(1000L)

        when:
        problemReporter.report(newProblem, operationId)

        then:
        // We are not running this test as an integration test, so we won't have a BuildOperationId available,
        // i.e. the OperationId will be null
        1 * emitter.emit(newProblem, operationId)
        newProblem.category == problem.category
        newProblem.label == problem.label
        newProblem.additionalData == problem.additionalData
        newProblem.details == problem.details
        newProblem.exception == problem.exception
        newProblem.locations == problem.locations
        newProblem.severity == problem.severity
        newProblem.solutions == ["solution"]
        newProblem.class == DefaultProblemReport
    }

    private static createTestProblem(Severity severity, Map<String, String> additionalData) {
        new DefaultProblemReport("message",
            severity,
            [],
            Documentation.userManual("id"),
            "description",
            [],
            new RuntimeException("cause"),
            DefaultProblemCategory.create('a', 'b', 'c'),
            additionalData)
    }

    def "unbound basic builder result is DefaultProblem"() {
        given:
        def problem = new DefaultProblemReport("message",
            Severity.WARNING,
            [],
            Documentation.userManual("id"),
            "description",
            [],
            new RuntimeException("cause"),
            DefaultProblemCategory.create('a', 'b', 'c'),
            [:])


        when:
        def newProblem = problem.toBuilder().build()

        then:
        newProblem.class == DefaultProblemReport
    }
}
