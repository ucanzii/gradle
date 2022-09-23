package configurations

import common.Os.LINUX
import common.buildToolGradleParameters
import common.customGradle
import common.gradleWrapper
import common.requiresNotEc2Agent
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import model.CIBuildModel
import model.Stage

class Gradleception(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    id("${model.projectId}_Gradleception")
    name = "Gradleception - Java8 Linux"
    description = "Builds Gradle with the version of Gradle which is currently under development (twice)"

    params {
        param("env.JAVA_HOME", LINUX.buildJavaHome())
    }

    requirements {
        // Gradleception is a heavy build which runs ~40m on EC2 agents but only ~20m on Hetzner agents
        requiresNotEc2Agent()
    }

    features {
        publishBuildStatusToGithub(model)
    }

    failureConditions {
        javaCrash = false
    }

    val buildScanTagForType = buildScanTag("Gradleception")
    val defaultParameters = (buildToolGradleParameters() + listOf(buildScanTagForType)).joinToString(separator = " ")

    applyDefaults(model, this, ":distributions-full:install", notQuick = true, extraParameters = "-Pgradle_installPath=dogfood-first $buildScanTagForType", extraSteps = {
        localGradle {
            name = "BUILD_WITH_BUILT_GRADLE"
            tasks = "clean :distributions-full:install"
            gradleHome = "%teamcity.build.checkoutDir%/dogfood-first"
            gradleParams = "-Pgradle_installPath=dogfood-second -PignoreIncomingBuildReceipt=true $defaultParameters"
        }
        localGradle {
            name = "QUICKCHECK_WITH_GRADLE_BUILT_BY_GRADLE"
            tasks = "clean sanityCheck test distributionsIntegTests:forkingIntegTest"
            gradleHome = "%teamcity.build.checkoutDir%/dogfood-second"
            gradleParams = defaultParameters
        }
    })
})

fun BuildSteps.localGradle(init: GradleBuildStep.() -> Unit): GradleBuildStep =
    customGradle(init) {
        param("ui.gradleRunner.gradle.wrapper.useWrapper", "false")
        buildFile = ""
    }
