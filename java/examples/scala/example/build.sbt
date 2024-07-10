ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.3"

lazy val root = (project in file("."))
  .settings(
    name := "example"
  )

// TODO: Get classifier using https://github.com/phdata/sbt-os-detector if/when https://repository.phdata.io/artifactory/libs-release works again
val os = System.getProperty("os.name").toLowerCase
val platformClassifier = {
  (os, System.getProperty("os.arch").toLowerCase) match {
    case (mac, arm) if mac.contains("mac") && arm.contains("arm") => "osx-aarch_64"
    case (mac, x86) if mac.contains("mac") && x86.contains("x86") => "osx-x86_64"
    case (linux, arm) if linux.contains("linux") && arm.contains("arm") => "linux-aarch_64"
    case (linux, x86) if linux.contains("linux") && x86.contains("x86") => "linux-x86_64"
    case (osName, archName) => throw new RuntimeException(s"Unsupported platform $osName $archName")
  }
}

val nativeTransport = {
  os match {
    case mac if mac.contains("mac") => "netty-transport-native-kqueue"
    case linux if linux.contains("linux") => "netty-transport-native-epoll"
    case osName => throw new RuntimeException(s"Unknown operating system $osName")
  }
}

libraryDependencies += "io.netty" % nativeTransport % "4.1.100.Final" classifier platformClassifier
libraryDependencies += "com.google.protobuf" % "protobuf-java" % "4.27.1"
libraryDependencies += "io.netty" % "netty-handler" % "4.1.100.Final"
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.13.0"
libraryDependencies += "io.valkey" % "valkey-glide" % "1.0.0" classifier platformClassifier


