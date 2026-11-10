// Čisti Kotlin modul bez Androida: duša, pravila, kasnije paketi i modeli.
// Android aplikacija dolazi u fazi 5 i koristi ovaj modul kao zavisnost.
plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
