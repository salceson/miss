name := "trafficsimulation"

version := "1.0"

scalaVersion := "2.11.8"

lazy val common = project in file("common")

lazy val supervisor = (project in file("supervisor")) dependsOn common

lazy val worker = (project in file("worker")) dependsOn common

lazy val trafficsimulation = (project in file(".")).aggregate(common, supervisor, worker)
