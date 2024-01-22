plugins {
    id("java-library")
}

subprojects {
    apply(plugin = "java-library")
}

// tag::configureTask[]
val configureJar = tasks.register("configureJar") {
    doLast {
        tasks.jar.get().manifest {
            val classPath = configurations.runtimeClasspath.get().map { it.name }.joinToString(" ")
            attributes("Class-Path" to classPath)
        }
    }
}
tasks.jar { dependsOn(configureJar) }

// end::configureTask[]
