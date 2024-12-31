name := "http"

version := "0.1.0"

scalaVersion := "3.5.0"

// Dependencies
libraryDependencies ++= Seq(
  // Netty
  "io.netty" % "netty-all" % "4.1.113.Final",

  // Logging
  "org.slf4j" % "slf4j-api" % "2.0.16",
  "ch.qos.logback" % "logback-classic" % "1.5.12",
  "net.logstash.logback" % "logstash-logback-encoder" % "8.0",
  "com.typesafe" % "config" % "1.4.3"
)

// Compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-encoding", "utf8"
)

// Assembly settings
assembly / mainClass := Some("com.tuachotu.http.HomeProMain")

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
