name := "miss-lab"

version := "1.0"

scalaVersion := "2.11.8"

val AkkaVersion = "2.4.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion
)
