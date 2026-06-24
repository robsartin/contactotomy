plugins {
    kotlin("jvm") version "2.0.21"
    // Code coverage measurement + verification (ADR-0009 quality gates).
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    // Formatting enforced via ktlint through Spotless (ADR-0009).
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.robsartin.contactotomy"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
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

// Coverage gates (ADR-0009): floors of 80% line and 60% branch, to be raised
// over time. koverVerify fails the build if either floor is not met.
kover {
    reports {
        verify {
            rule {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    minValue = 80
                }
            }
            rule {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                    minValue = 60
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
