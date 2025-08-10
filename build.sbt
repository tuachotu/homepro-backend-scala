name := "http"

version := "0.1.0"

scalaVersion := "3.5.0"

// Dependencies
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.2.10" cross CrossVersion.for3Use2_13,        // Correct Akka HTTP version
  "com.typesafe.akka" %% "akka-stream" % "2.6.20" cross CrossVersion.for3Use2_13,       // Correct Akka Streams version
  "io.spray" %% "spray-json" % "1.3.6",
  "org.slf4j" % "slf4j-api" % "2.0.16",
  "ch.qos.logback" % "logback-classic" % "1.5.12",
  "net.logstash.logback" % "logstash-logback-encoder" % "8.0",
  "com.typesafe" % "config" % "1.4.3",
  "com.google.firebase" % "firebase-admin" % "9.4.2",
  "org.postgresql" % "postgresql" % "42.7.4",
  "com.zaxxer" % "HikariCP" % "5.1.0",
  "ch.megard" %% "akka-http-cors" % "1.1.3",
  "software.amazon.awssdk" % "s3" % "2.27.21",                                          // AWS S3 SDK
  "software.amazon.awssdk" % "url-connection-client" % "2.27.21"                       // HTTP client for S3
)

// Compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-encoding", "utf8"
)
// Assembly settings
Compile / run / mainClass := Some("com.tuachotu.HomeProMain")

// Assembly settings
assembly / mainClass := Some("com.tuachotu.HomeProMain")

// Merge strategy for assembly conflicts
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs@_*) =>
    (xs map {_.toLowerCase}) match {
      case "services" :: xs =>
        MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.discard
    }
  case PathList("reference.conf")         => MergeStrategy.concat
  case PathList("application.conf")       => MergeStrategy.concat
  case "logback.xml"                      => MergeStrategy.first
  case x                                  => MergeStrategy.first
}
