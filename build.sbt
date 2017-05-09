import CommonSettings._
import pl.edu.agh.iet.akka_tracing.sbt.AkkaTracingPlugin

name := "trafficsimulation"

version := "1.0"

scalaVersion := "2.11.8"

lazy val common = (project in file("common"))
  .settings(commonSettings)
  .enablePlugins(AkkaTracingPlugin)

lazy val supervisor = (project in file("supervisor"))
  .dependsOn(common)
  .settings(commonSettings)

lazy val worker = (project in file("worker"))
  .dependsOn(common)
  .settings(commonSettings)

lazy val trafficsimulation = (project in file(".")).aggregate(common, supervisor, worker)
