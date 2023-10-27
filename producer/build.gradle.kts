plugins {
    java
    idea
    id("io.freefair.lombok") version "8.4"
}

group = "com.bakdata.uni"

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
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

dependencies {
    implementation(group = "com.bakdata.kafka", name = "streams-bootstrap", version = "2.13.0")
    implementation(group = "com.google.guava", name = "guava", version = "31.1-jre")

    val confluentVersion = "7.4.1"
    implementation(group = "io.confluent", name = "kafka-json-schema-serializer", version = confluentVersion)
    implementation(group = "com.fasterxml.jackson.dataformat", name = "jackson-dataformat-csv", version = "2.15.2")

    val log4jVersion = "2.20.0"
    implementation(group = "org.apache.logging.log4j", name = "log4j-core", version = log4jVersion)
    implementation(group = "org.apache.logging.log4j", name = "log4j-slf4j-impl", version = log4jVersion)

    implementation(project(":common"))

    val junitVersion = "5.9.2"
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = junitVersion)
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = junitVersion)
    testImplementation(group = "org.assertj", name = "assertj-core", version = "3.24.2")

    val kafkaVersion = "3.4.0"
    testImplementation(group = "net.mguenther.kafka", name = "kafka-junit", version = kafkaVersion) {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
}
