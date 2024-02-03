plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public and internal 'core' Gradle APIs with implementation"

configurations {
    register("reports")
}

tasks.classpathManifest {
    optionalProjects.add("gradle-kotlin-dsl")
    // The gradle-runtime-api-info.jar is added by a 'distributions-...' project if it is on the (integration test) runtime classpath.
    // It contains information services in ':core' need to reason about the complete Gradle distribution.
    // To allow parts of ':core' code to be instantiated in unit tests without relying on this functionality, the dependency is optional.
    optionalProjects.add("gradle-runtime-api-info")
}

// Instrumentation interceptors for tests
// Separated from the test source set since we don't support incremental annotation processor with Java/Groovy joint compilation
sourceSets {
    val testInterceptors = create("testInterceptors") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
    getByName("test") {
        compileClasspath += testInterceptors.output
        runtimeClasspath += testInterceptors.output
    }
}
val testInterceptorsImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

errorprone {
    disabledChecks.addAll(
        "BadImport", // 3 occurrences
        "BadInstanceof", // 6 occurrences (this is from generated code)
        "BoxedPrimitiveEquality", // 3 occurrences
        "DefaultCharset", // 4 occurrences
        "EmptyBlockTag", // 4 occurrences
        "Finally", // 1 occurrences
        "HidingField", // 1 occurrences
        "IdentityHashMapUsage", // 1 occurrences
        "ImmutableEnumChecker", // 2 occurrences
        "InconsistentCapitalization", // 2 occurrences
        "InlineFormatString", // 2 occurrences
        "InlineMeSuggester", // 1 occurrences
        "InvalidBlockTag", // 1 occurrences
        "InvalidInlineTag", // 1 occurrences
        "InvalidLink", // 2 occurrences
        "MissingCasesInEnumSwitch", // 1 occurrences
        "MixedMutabilityReturnType", // 1 occurrences
        "ModifyCollectionInEnhancedForLoop", // 1 occurrences
        "MutablePublicArray", // 2 occurrences
        "NonApiType", // 1 occurrences
        "NonCanonicalType", // 16 occurrences
        "NotJavadoc", // 1 occurrences
        "OperatorPrecedence", // 5 occurrences
        "OptionalMapUnusedValue", // 1 occurrences
        "ProtectedMembersInFinalClass", // 1 occurrences
        "ReferenceEquality", // 2 occurrences
        "ReturnValueIgnored", // 1 occurrences
        "SameNameButDifferent", // 11 occurrences
        "StreamResourceLeak", // 6 occurrences
        "StringCaseLocaleUsage", // 11 occurrences
        "StringSplitter", // 2 occurrences
        "TypeParameterShadowing", // 1 occurrences
        "TypeParameterUnusedInFormals", // 2 occurrences
        "UndefinedEquals", // 1 occurrences
        "UnnecessaryLambda", // 1 occurrences
        "UnnecessaryParentheses", // 1 occurrences
        "UnrecognisedJavadocTag", // 1 occurrences
        "UnusedMethod", // 18 occurrences
        "UnusedVariable", // 8 occurrences
    )
}

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":base-services-groovy"))
    api(project(":build-cache"))
    api(project(":build-cache-base"))
    api(project(":build-cache-packaging"))
    api(project(":build-operations"))
    api(project(":build-option"))
    api(project(":cli"))
    api(project(":core-api"))
    api(project(":enterprise-logging"))
    api(project(":enterprise-operations"))
    api(project(":execution"))
    api(project(":file-collections"))
    api(project(":file-temp"))
    api(project(":file-watching"))
    api(project(":files"))
    api(project(":functional"))
    api(project(":hashing"))
    api(project(":internal-instrumentation-api"))
    api(project(":jvm-services"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":native"))
    api(project(":normalization-java"))
    api(project(":persistent-cache"))
    api(project(":problems-api"))
    api(project(":process-services"))
    api(project(":resources"))
    api(project(":snapshots"))
    api(project(":worker-processes"))

    api(libs.ant)
    api(libs.asm)
    api(libs.asmTree)
    api(libs.commonsCompress)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.nativePlatform)

    implementation(project(":input-tracking"))
    implementation(project(":model-groovy"))

    implementation(libs.asmCommons)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.fastutil)
    implementation(libs.groovyAnt)
    implementation(libs.groovyJson)
    implementation(libs.groovyTemplates)
    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
    implementation(libs.tomlj)
    implementation(libs.xmlApis)

    compileOnly(libs.futureKotlin("stdlib")) {
        because("it needs to forward calls from instrumented code to the Kotlin standard library")
    }

    // Libraries that are not used in this project but required in the distribution
    runtimeOnly(libs.groovyAstbuilder)
    runtimeOnly(libs.groovyConsole)
    runtimeOnly(libs.groovyDateUtil)
    runtimeOnly(libs.groovyDatetime)
    runtimeOnly(libs.groovyDoc)
    runtimeOnly(libs.groovyNio)
    runtimeOnly(libs.groovySql)
    runtimeOnly(libs.groovyTest)

    // The bump to SSHD 2.10.0 causes a global exclusion for `groovy-ant` -> `ant-junit`, so forcing it back in here
    // TODO investigate why we depend on SSHD as a platform for internal-integ-testing
    runtimeOnly(libs.antJunit)

    testImplementation(project(":platform-jvm"))
    testImplementation(project(":platform-native"))
    testImplementation(project(":testing-base"))
    testImplementation(libs.jsoup)
    testImplementation(libs.log4jToSlf4j)
    testImplementation(libs.jclToSlf4j)

    testFixturesCompileOnly(libs.jetbrainsAnnotations)

    testFixturesApi(project(":base-services")) {
        because("test fixtures expose Action")
    }
    testFixturesApi(project(":base-services-groovy")) {
        because("test fixtures expose AndSpec")
    }
    testFixturesApi(project(":core-api")) {
        because("test fixtures expose Task")
    }
    testFixturesApi(project(":logging")) {
        because("test fixtures expose Logger")
    }
    testFixturesApi(project(":model-core")) {
        because("test fixtures expose IConventionAware")
    }
    testFixturesApi(project(":build-cache")) {
        because("test fixtures expose BuildCacheController")
    }
    testFixturesApi(project(":execution")) {
        because("test fixtures expose OutputChangeListener")
    }
    testFixturesApi(project(":native")) {
        because("test fixtures expose FileSystem")
    }
    testFixturesApi(project(":file-collections")) {
        because("test fixtures expose file collection types")
    }
    testFixturesApi(project(":file-temp")) {
        because("test fixtures expose temp file types")
    }
    testFixturesApi(project(":resources")) {
        because("test fixtures expose file resource types")
    }
    testFixturesApi(testFixtures(project(":persistent-cache"))) {
        because("test fixtures expose cross-build cache factory")
    }
    testFixturesApi(project(":process-services")) {
        because("test fixtures expose exec handler types")
    }
    testFixturesApi(testFixtures(project(":hashing"))) {
        because("test fixtures expose test hash codes")
    }
    testFixturesApi(testFixtures(project(":snapshots"))) {
        because("test fixtures expose file snapshot related functionality")
    }
    testFixturesImplementation(project(":build-option"))
    testFixturesImplementation(project(":enterprise-operations"))
    testFixturesImplementation(project(":messaging"))
    testFixturesImplementation(project(":normalization-java"))
    testFixturesImplementation(project(":persistent-cache"))
    testFixturesImplementation(project(":snapshots"))
    testFixturesImplementation(libs.ant)
    testFixturesImplementation(libs.asm)
    testFixturesImplementation(libs.groovyAnt)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(project(":internal-instrumentation-api"))
    testFixturesImplementation(libs.ivy)
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(project(":dependency-management")) {
        because("Used in VersionCatalogErrorMessages for org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.getExcludedNames")
    }

    testFixturesRuntimeOnly(project(":plugin-use")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(project(":workers")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(project(":composite-builds")) {
        because("We always need a BuildStateRegistry service implementation")
    }

    testImplementation(project(":dependency-management"))

    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":execution")))

    integTestImplementation(project(":workers"))
    integTestImplementation(project(":dependency-management"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":plugins"))
    integTestImplementation(project(":war"))
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(testFixtures(project(":native")))
    integTestImplementation(testFixtures(project(":file-temp")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Some tests utilise the 'java-gradle-plugin' and with that TestKit, some also use the 'war' plugin")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))

    annotationProcessor(project(":internal-instrumentation-processor"))
    annotationProcessor(platform(project(":distributions-dependencies")))

    testInterceptorsImplementation(platform(project(":distributions-dependencies")))
    "testInterceptorsAnnotationProcessor"(project(":internal-instrumentation-processor"))
    "testInterceptorsAnnotationProcessor"(platform(project(":distributions-dependencies")))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreAnnotationProcessing() // Without this, javac will complain about unclaimed annotations
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

tasks.test {
    setForkEvery(200)
}

tasks.compileTestGroovy {
    groovyOptions.fork("memoryInitialSize" to "128M", "memoryMaximumSize" to "1G")
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
