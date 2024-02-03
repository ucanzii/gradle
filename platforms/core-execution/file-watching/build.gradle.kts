plugins {
    id("gradlebuild.distribution.api-java")
}

description = "File system watchers for keeping the VFS up-to-date"

errorprone {
    disabledChecks.addAll(
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(project(":snapshots"))
    api(project(":build-operations"))
    api(project(":files"))
    api(project(":base-annotations"))

    api(libs.jsr305)
    api(libs.nativePlatform)
    api(libs.nativePlatformFileEvents)
    api(libs.slf4jApi)
    implementation(project(":functional"))

    implementation(libs.guava)

    testImplementation(project(":process-services"))
    testImplementation(project(":resources"))
    testImplementation(project(":persistent-cache"))
    testImplementation(project(":build-option"))
    testImplementation(project(":enterprise-operations"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":file-collections")))
    testImplementation(testFixtures(project(":tooling-api")))
    testImplementation(testFixtures(project(":launcher")))
    testImplementation(testFixtures(project(":snapshots")))

    testImplementation(libs.commonsIo)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
