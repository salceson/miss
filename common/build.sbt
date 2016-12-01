name := "trafficsimulation-common"

version := "1.0"

scalaVersion := "2.11.8"

val AkkaVersion = "2.4.2"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

scalacOptions in Test ++= Seq("-Yrangepos")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
  "org.specs2" %% "specs2-core" % "3.0" % "test",
  "org.scala-lang.modules" %% "scala-swing" % "1.0.1"
)

assemblyJarName in assembly := "common.jar"
