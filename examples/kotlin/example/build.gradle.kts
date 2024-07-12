plugins {
    kotlin("jvm") version "1.9.23"
    application
    id("com.google.osdetector") version "1.7.3"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val os = osdetector.os
val classifier = osdetector.classifier
fun nettyTransport(): String {
    if (os == "osx")
        return "netty-transport-native-kqueue"
    else if (os == "linux")
        return "netty-transport-native-epoll"
    else
        throw Exception("Unsupported operating system $os")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.valkey:valkey-glide:1.0.1:$classifier")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

application {
    mainClass = "org.example.MainKt"
}
