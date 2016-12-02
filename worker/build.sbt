name := "trafficsimulation-worker"

version := "1.0"

scalaVersion := "2.11.8"

assemblyJarName in assembly := "worker.jar"

mainClass in assembly := Some("miss.worker.WorkerApp")
