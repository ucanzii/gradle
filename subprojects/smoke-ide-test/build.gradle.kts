import gradlebuild.integrationtests.tasks.SmokeIdeTest
import gradlebuild.integrationtests.addDependenciesAndConfigurations

plugins {
    id("gradlebuild.internal.java")
}

description = "Tests are checking Gradle behavior during IDE synchronization process"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    maven {
        url = uri("https://www.jetbrains.com/intellij-repository/releases")
        content {
//            includeGroup() TODO
        }
    }
    maven {
        url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
        content {
//            includeGroup() TODO
        }
    }
}

tasks.withType<GroovyCompile>().configureEach {
    options.release = 17
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

val smokeIdeTestSourceSet = sourceSets.create("smokeIdeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

addDependenciesAndConfigurations("smokeIde")
val smokeIdeTestImplementation: Configuration by configurations
val smokeIdeTestDistributionRuntimeOnly: Configuration by configurations

plugins.withType<IdeaPlugin> {
    with(model) {
        module {
            testSources.from(smokeIdeTestSourceSet.java.srcDirs, smokeIdeTestSourceSet.groovy.srcDirs)
            testResources.from(smokeIdeTestSourceSet.resources.srcDirs)
        }
    }
}

tasks.register<SmokeIdeTest>("smokeIdeTest").configure {
    group = "Verification"
    maxParallelForks = 1
    systemProperties["org.gradle.integtest.executer"] = "forking"
    testClassesDirs = smokeIdeTestSourceSet.output.classesDirs
    classpath = smokeIdeTestSourceSet.runtimeClasspath
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    smokeIdeTestImplementation(libs.gradleProfiler) {
        version {
            strictly("0.21.17-alpha-3")
            because("IDE provisioning requires special version of profiler compiled with Java 17")
        }

        // These deps are conflicting with the deps of `:distributions-full` project.
        exclude("org.jetbrains.kotlin")
        exclude("io.grpc")
    }
    smokeIdeTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
}
