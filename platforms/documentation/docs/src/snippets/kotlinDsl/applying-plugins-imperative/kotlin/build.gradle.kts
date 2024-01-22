// TODO:Finalize Upload Removal - Issue #21439
// tag::script[]
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:2.7.8")
    }
}

apply(plugin = "java-library")
apply(plugin = "jacoco")
apply(plugin = "org.springframework.boot")
// end::script[]
