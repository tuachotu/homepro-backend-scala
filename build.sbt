name := "http"

version := "0.1.0"

scalaVersion := "3.5.0"

// https://mvnrepository.com/artifact/io.netty/netty-all
libraryDependencies += "io.netty" % "netty-all" % "4.1.113.Final"

// logging

// https://mvnrepository.com/artifact/org.slf4j/slf4j-api
libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.16"
// https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.12"
// https://mvnrepository.com/artifact/net.logstash.logback/logstash-logback-encoder
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "8.0"
// https://mvnrepository.com/artifact/com.typesafe/config
libraryDependencies += "com.typesafe" % "config" % "1.4.3"




// Enable some compiler options
scalacOptions ++= Seq("-deprecation", "-feature")