plugins {
  maven
  `java-library`
  kotlin("jvm") version "1.3.61"
}

tasks.named<Wrapper>("wrapper") {
  gradleVersion = "6.5.1"
}

repositories {
  jcenter()
}
dependencies {
  implementation(kotlin("stdlib"))
}
