name := "trafficsimulation-supervisor"

version := "1.0"

scalaVersion := "2.11.8"

assemblyJarName in assembly := "supervisor.jar"

mainClass in assembly := Some("miss.supervisor.SupervisorApp")
