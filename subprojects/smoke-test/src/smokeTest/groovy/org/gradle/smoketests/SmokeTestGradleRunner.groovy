/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.BuildOperationTreeFixture
import org.gradle.integtests.fixtures.BuildOperationTreeQueries
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidPluginMetadataException
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.slf4j.LoggerFactory

import javax.annotation.Nullable
import java.util.function.Function

class SmokeTestGradleRunner extends GradleRunner {
    private static final LOGGER = LoggerFactory.getLogger(SmokeTestGradleRunner)

    private final DefaultGradleRunner delegate

    private String buildOperationTracePath
    private final List<String> expectedDeprecationWarnings = []
    private final List<String> maybeExpectedDeprecationWarnings = []
    private boolean ignoreDeprecationWarnings

    SmokeTestGradleRunner(DefaultGradleRunner delegate) {
        this.delegate = delegate
    }

    @Override
    SmokeTestBuildResult build() {
        execute(GradleRunner::build)
    }

    @Override
    SmokeTestBuildResult buildAndFail() {
        execute(GradleRunner::buildAndFail)
    }

    @Override
    SmokeTestBuildResult run() throws InvalidRunnerConfigurationException {
        execute(GradleRunner::run)
    }

    private SmokeTestBuildResult execute(Function<GradleRunner, BuildResult> action) {

        if (buildOperationTracePath != null) {
            doEnableBuildOperationTracing()
        }

        def result = action.apply(delegate)

        // TODO: Use problems API to verify deprecation warnings instead of parsing output.
        verifyDeprecationWarnings(result)

        if (buildOperationTracePath == null) {
            return new SmokeTestBuildResult(result, null)
        }

        def operations = new BuildOperationTreeFixture(BuildOperationTrace.readPartialTree(buildOperationTracePath))
        new SmokeTestBuildResult(result, operations)
    }

    private void doEnableBuildOperationTracing() {
        // TODO: Should we filter using the stable/public build operation class names?
        // This means we need to load classes and do an instanceof when we filter
        String buildOperationFilter = [
            "org.gradle.configurationcache.StoreDetails",
            "org.gradle.configurationcache.LoadDetails",
        ].join(BuildOperationTrace.FILTER_SEPARATOR)

        delegate.withArguments(delegate.getArguments() + [
            "-D${BuildOperationTrace.SYSPROP}=${buildOperationTracePath}".toString(),
            "-D${BuildOperationTrace.FILTER_SYSPROP}=${buildOperationFilter}".toString()
        ])
    }

    SmokeTestGradleRunner withBuildOperationTracing(String buildOperationTracePath) {
        this.buildOperationTracePath = buildOperationTracePath
        return this
    }

    SmokeTestGradleRunner withoutBuildOperationTracing() {
        buildOperationTracePath = null
        return this
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called.
     *
     * @param warning the text of the warning to match.
     * @param followup how are we planning to resolve the deprecation before it turns into a breakage;
     *      typically a URL pointing to an issue with the relevant third-party plugin. The actual value
     *      is ignored, the parameter is only present to remind us that a followup is necessary, and
     *      to record how it will happen.
     */
    SmokeTestGradleRunner expectDeprecationWarning(String warning, String followup) {
        if (followup == null || followup.isBlank()) {
            throw new IllegalArgumentException("Follow up is required! Did you mean to expect a legacy deprecation warning instead?")
        }
        expectedDeprecationWarnings.add(warning)
        return this
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called if the given condition is true.
     *
     * @param condition only expect the warning to be produced when this condition is {@code true}.
     * @param warning the text of the warning to match.
     * @param followup how are we planning to resolve the deprecation before it turns into a breakage;
     *      typically a URL pointing to an issue with the relevant third-party plugin. The actual value
     *      is ignored, the parameter is only present to remind us that a followup is necessary, and
     *      to record how it will happen.
     */
    SmokeTestGradleRunner expectDeprecationWarningIf(boolean condition, String warning, String followup) {
        if (condition) {
            expectDeprecationWarning(warning, followup)
        }
        return this
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin. The assumption is that the deprecation has already
     * been fixed in a later version of the plugin, and thus no followup is needed.
     *
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner expectLegacyDeprecationWarning(String warning) {
        expectedDeprecationWarnings.add(warning)
        return this
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin if the given condition is true.
     * The assumption is that the deprecation has already been fixed in a later version of the plugin,
     * and thus no followup is needed.
     *
     * @param condition only expect the warning to be produced when this condition is {@code true}.
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner expectLegacyDeprecationWarningIf(boolean condition, String warning) {
        if (condition) {
            expectLegacyDeprecationWarning(warning)
        }
        return this
    }

    /**
     * Maybe expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin. The assumption is that the deprecation has already
     * been fixed in a later version of the plugin, and thus no followup is needed.
     *
     * Does not fail the test if the warning does not appear in the output.
     *
     * WARNING: Only use for warnings that occurs intermittently. For example a deprecation warning for a function
     * that is only called once per Gradle daemon from a third party plugin.
     *
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner maybeExpectLegacyDeprecationWarning(String warning) {
        maybeExpectedDeprecationWarnings.add(warning)
        return this
    }

    /**
     * Maybe expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin if the given condition is true.
     * The assumption is that the deprecation has already been fixed in a later version of the plugin,
     * and thus no followup is needed.
     *
     * Does not fail the test if the warning does not appear in the output.
     *
     * WARNING: Only use for warnings that occurs intermittently. For example a deprecation warning for a function
     * that is only called once per Gradle daemon from a third party plugin.
     *
     * @param condition only expect the warning to be produced when this condition is {@code true}.
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner maybeExpectLegacyDeprecationWarningIf(boolean condition, String warning) {
        if (condition) {
            maybeExpectLegacyDeprecationWarning(warning)
        }
        return this
    }

    SmokeTestGradleRunner ignoreDeprecationWarnings(String reason) {
        LOGGER.warn("Ignoring deprecation warnings because: {}", reason)
        ignoreDeprecationWarnings = true
        return this
    }

    def <U extends BaseDeprecations, T> SmokeTestGradleRunner deprecations(
        @DelegatesTo.Target Class<U> deprecationClass,
        @DelegatesTo(
            genericTypeIndex = 0,
            strategy = Closure.DELEGATE_FIRST)
            Closure<T> closure) {
        deprecationClass.newInstance(this).tap(closure)
        return this
    }

    def <T> SmokeTestGradleRunner deprecations(
        @DelegatesTo(
            value = BaseDeprecations.class,
            strategy = Closure.DELEGATE_FIRST)
            Closure<T> closure) {
        return deprecations(BaseDeprecations, closure)
    }

    private void verifyDeprecationWarnings(BuildResult result) {
        if (ignoreDeprecationWarnings) {
            return
        }
        def lines = result.output.readLines()
        def remainingWarnings = new ArrayList<>(expectedDeprecationWarnings + maybeExpectedDeprecationWarnings)
        def totalExpectedDeprecations = remainingWarnings.size()
        int foundDeprecations = 0
        lines.eachWithIndex { String line, int lineIndex ->
            if (remainingWarnings.remove(line)) {
                foundDeprecations++
                return
            }
            assert !line.contains("has been deprecated"), "Found an unexpected deprecation warning on line ${lineIndex + 1}: $line"
        }
        remainingWarnings.removeAll(maybeExpectedDeprecationWarnings)
        assert remainingWarnings.empty, "Expected ${totalExpectedDeprecations} deprecation warnings, found ${foundDeprecations} deprecation warnings. Did not match the following:\n${remainingWarnings.collect { " - $it" }.join("\n")}"
        expectedDeprecationWarnings.clear()
    }

    @Override
    SmokeTestGradleRunner withGradleVersion(String versionNumber) {
        delegate.withGradleVersion(versionNumber)
        return this
    }

    @Override
    SmokeTestGradleRunner withGradleInstallation(File installation) {
        delegate.withGradleInstallation(installation)
        return this
    }

    @Override
    SmokeTestGradleRunner withGradleDistribution(URI distribution) {
        delegate.withGradleDistribution(distribution)
        return this
    }

    @Override
    SmokeTestGradleRunner withTestKitDir(File testKitDir) {
        delegate.withTestKitDir(testKitDir)
        return this
    }

    @Override
    File getProjectDir() {
        return delegate.getProjectDir()
    }

    @Override
    SmokeTestGradleRunner withProjectDir(File projectDir) {
        delegate.withProjectDir(projectDir)
        return this
    }

    @Override
    List<String> getArguments() {
        return delegate.getArguments()
    }

    @Override
    SmokeTestGradleRunner withArguments(List<String> arguments) {
        delegate.withArguments(arguments)
        return this
    }

    @Override
    SmokeTestGradleRunner withArguments(String... arguments) {
        delegate.withArguments(arguments)
        return this
    }

    @Override
    List<? extends File> getPluginClasspath() {
        return delegate.getPluginClasspath()
    }

    @Override
    SmokeTestGradleRunner withPluginClasspath() throws InvalidPluginMetadataException {
        delegate.withPluginClasspath()
        return this
    }

    @Override
    SmokeTestGradleRunner withPluginClasspath(Iterable<? extends File> classpath) {
        delegate.withPluginClasspath(classpath)
        return this
    }

    @Override
    boolean isDebug() {
        return delegate.isDebug()
    }

    @Override
    SmokeTestGradleRunner withDebug(boolean flag) {
        delegate.withDebug(flag)
        return this
    }

    @Override
    @Nullable
    Map<String, String> getEnvironment() {
        return delegate.getEnvironment()
    }

    @Override
    SmokeTestGradleRunner withEnvironment(@Nullable Map<String, String> environmentVariables) {
        delegate.withEnvironment(environmentVariables)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardStdOutput(Writer writer) {
        delegate.forwardStdOutput(writer)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardStdError(Writer writer) {
        delegate.forwardStdError(writer)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardOutput() {
        delegate.forwardOutput()
        return this
    }

    List<String> getJvmArguments() {
        return delegate.getJvmArguments()
    }

    SmokeTestGradleRunner withJvmArguments(List<String> jvmArguments) {
        delegate.withJvmArguments(jvmArguments)
        return this
    }

    SmokeTestGradleRunner withJvmArguments(String... jvmArguments) {
        delegate.withJvmArguments(Arrays.asList(jvmArguments))
        return this
    }

    class SmokeTestBuildResult implements BuildResult {

        private final BuildResult delegate
        private final BuildOperationTreeQueries operations

        SmokeTestBuildResult(BuildResult delegate, @Nullable BuildOperationTreeQueries operations) {
            this.delegate = delegate
            this.operations = operations
        }

        @Override
        String getOutput() {
            return delegate.output
        }

        @Override
        List<BuildTask> getTasks() {
            return delegate.tasks
        }

        @Override
        List<BuildTask> tasks(TaskOutcome outcome) {
            return delegate.tasks(outcome)
        }

        @Override
        List<String> taskPaths(TaskOutcome outcome) {
            return delegate.taskPaths(outcome)
        }

        @Override
        BuildTask task(String taskPath) {
            return delegate.task(taskPath)
        }

        void assertConfigurationCacheStateStored() {
            assertBuildOperationTracePresent()
            new ConfigurationCacheBuildOperationsFixture(operations).assertStateStored()
        }

        void assertConfigurationCacheStateLoaded() {
            assertBuildOperationTracePresent()
            new ConfigurationCacheBuildOperationsFixture(operations).assertStateLoaded()
        }

        private void assertBuildOperationTracePresent() {
            assert buildOperationTracePath != null, "Build operation trace was not captured"
        }
    }
}
