plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val newpipeVersion = libs.versions.newpipe.get()

dependencies {
    // The interface lives in the app's class loader; it must not be bundled into an OTA update.
    compileOnly(project(":engine-api"))
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(libs.newpipe.extractor)
    testImplementation(project(":engine-api"))
    testImplementation(libs.junit)
}

tasks.processResources {
    inputs.property("newpipe", newpipeVersion)
    filesMatching("engine.properties") { expand("newpipe" to newpipeVersion) }
}

/**
 * Everything the engine needs at run time in one jar (extractor, jsoup, rhino, protobuf ...), without
 * the shared interface. tools/build-ota.sh converts this jar to dex with d8 and signs it.
 */
tasks.register<Jar>("otaJar") {
    group = "distribution"
    archiveFileName.set("engine-classes.jar")
    destinationDirectory.set(layout.buildDirectory.dir("ota"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    exclude(
        "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF",
        "module-info.class", "META-INF/versions/**", "META-INF/LICENSE*", "META-INF/NOTICE*",
    )
}
