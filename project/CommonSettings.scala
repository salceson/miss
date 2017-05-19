import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._

object CommonSettings {
  private val AkkaVersion = "2.4.17"

  val commonDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
    "org.specs2" %% "specs2-core" % "3.0" % "test",
    "org.scala-lang.modules" %% "scala-swing" % "1.0.1",
    "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.0"
  )

  val commonTestScalacSettings: Seq[String] = Seq("-Yrangepos")

  val commonResolvers: Seq[MavenRepository] = Seq(
    "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
  )

  val commonSettings: Seq[Def.Setting[_]] = Seq(
    resolvers ++= commonResolvers,
    scalacOptions in Test ++= commonTestScalacSettings,
    libraryDependencies ++= commonDependencies,
    assemblyMergeStrategy in assembly := {
      case PathList(ps @ _*) if ps.last == "aop.xml" => MergeStrategy.deduplicate
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList(ps @ _*) if ps.last.endsWith(".conf") => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )
}
