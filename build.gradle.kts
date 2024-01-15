plugins {
  java
  distribution
}

group = "coop.ethereumclassic"
version = "1.0-SNAPSHOT"

dependencies {
  implementation("org.apache.beam:beam-runners-google-cloud-dataflow-java:2.53.0")
  implementation("org.apache.beam:beam-sdks-java-extensions-google-cloud-platform-core:2.53.0")
  implementation("org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.53.0")
  runtimeOnly("ch.qos.logback:logback-classic:1.2.13")
  runtimeOnly("org.apache.beam:beam-runners-direct-java:2.53.0")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

distributions {
  create("deployment") {
    contents {
      from(tasks.jar)
      into("libs") {
        from(configurations.runtimeClasspath)
      }
    }
  }
}

repositories {
  mavenCentral()
  maven("https://packages.confluent.io/maven/")
}

tasks.jar {
  manifest {
    attributes["Main-Class"] = "coop.ethereumclassic.etl.google.dataflow.EthereumEtlTransformer"
    attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(" ") { it.name }
  }
}