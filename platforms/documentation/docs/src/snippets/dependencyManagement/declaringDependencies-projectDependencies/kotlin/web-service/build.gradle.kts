plugins {
    id("java-library")
}
// tag::project-dependencies[]
dependencies {
    implementation(project(":utils"))
    implementation(project(":api"))
}
// end::project-dependencies[]
