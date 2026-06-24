plugins {
    // 0.9.0+ is required for Gradle Daemon JVM auto-provisioning (Gradle 8.13+),
    // which lets gradle/gradle-daemon-jvm.properties download a JDK 21 launcher
    // portably (no machine-specific org.gradle.java.home in gradle.properties).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "contactotomy"
