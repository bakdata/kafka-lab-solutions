description = "Common libraries for the Kafka lab"

plugins {
    `java-library`
    id("com.github.davidmc24.gradle.plugin.avro") version "1.6.0"
}

group = "com.bakdata.uni"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("org.apache.avro:avro:1.11.1")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
    }
}
