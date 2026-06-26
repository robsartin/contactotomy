plugins {
    kotlin("jvm") version "2.0.21"
    // kotlinx.serialization for JSON-serializable rule sets (#6).
    kotlin("plugin.serialization") version "2.0.21"
    // Code coverage measurement + verification (ADR-0009 quality gates).
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    // Formatting enforced via ktlint through Spotless (ADR-0009).
    id("com.diffplug.spotless") version "7.0.2"
    // Compose Desktop UI toolkit + Compose compiler plugin (ADR-0006/0012).
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.robsartin.contactotomy"
version = "0.1.0"

repositories {
    mavenCentral()
    // Compose Desktop pulls transitive androidx.* artifacts published only
    // to Google's Maven repository.
    google()
}

dependencies {
    implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(compose.desktop.currentOs)

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.robsartin.contactotomy.ui.MainKt"
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// Coverage gates (ADR-0009): floors of 90% line and 65% branch, to be raised
// over time. koverVerify fails the build if either floor is not met.
kover {
    reports {
        // Exclude kotlinx.serialization-generated `$serializer` classes from
        // coverage: their synthetic branches would otherwise drag branch
        // coverage below the floor once @Serializable types are added (#6).
        filters {
            excludes {
                classes("*\$serializer")
                // Compose entry point and the real AWT file dialog are
                // UI plumbing exercised only by run-the-app, not unit tests.
                classes("com.robsartin.contactotomy.ui.MainKt")
                // Synthetic singletons holding the application{}/Window lambda
                // bodies referenced from MainKt; also run-the-app-only plumbing.
                classes("com.robsartin.contactotomy.ui.ComposableSingletons${'$'}MainKt*")
                classes("com.robsartin.contactotomy.ui.AwtFilePicker")
            }
        }
        verify {
            rule {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    minValue = 90
                }
            }
            rule {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                    minValue = 70
                }
            }
        }
    }
}

// Wire coverage verification into the standard `check` lifecycle so that
// `./gradlew check` runs tests + spotlessCheck + koverVerify + Konsist.
tasks.check {
    dependsOn(tasks.koverVerify)
}
