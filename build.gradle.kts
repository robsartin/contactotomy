plugins {
    kotlin("jvm") version "2.0.21"
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
