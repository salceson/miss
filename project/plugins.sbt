logLevel := Level.Warn

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

addSbtPlugin("pl.edu.agh.iet" % "akka-tracing-sbt" % "0.1")

resolvers += Resolver.url("Akka Tracing", url("https://dl.bintray.com/salceson/maven/"))(Resolver.ivyStylePatterns)
