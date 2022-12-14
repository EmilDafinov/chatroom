import sbt.librarymanagement

lazy val json4sVersion = "3.7.0-M4"

val AkkaVersion = "2.5.31"
val scalatestVersuib = "3.2.0"

val AkkaHttpVersion = "10.1.12"
lazy val root = (project in file("."))
  .enablePlugins(
    JavaServerAppPackaging,
    DockerPlugin,
  )
  .settings(versionSettings)
  .settings(
    name := "chatroom",
    organization := "com.github.dafutils",
    scalaVersion := "2.12.11",
    resolvers += MavenRepo("hmrc", "https://hmrc.bintray.com/releases"),
    crossPaths in ThisBuild := false,
    parallelExecution in Test in ThisBuild := false,
    fork in ThisBuild := true,
    publishArtifact in ThisBuild in packageDoc := false,
    publishArtifact in ThisBuild in packageSrc := false,
    dockerExposedPorts ++= Seq(9000),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-hbase" % "2.0.1",
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "log4j" % "log4j" % "1.2.17",
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "de.heikoseeberger" %% "akka-http-json4s" % "1.33.0",
      "com.typesafe" % "config" % "1.4.0",
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ext" % json4sVersion,
      "uk.gov.hmrc" %% "emailaddress" % "3.4.0",
      "org.scalatest" %% "scalatest" % scalatestVersuib % Test,
      "org.scalatest" %% "scalatest-wordspec" % scalatestVersuib % Test,
      "org.scalatest" %% "scalatest-shouldmatchers" % scalatestVersuib % Test,
      "org.scalatestplus" %% "mockito-3-3" % "3.2.0.0" % Test,
      "org.mockito" % "mockito-core" % "3.4.4" % Test,
      "com.typesafe.akka" %% "akka-testkit" % "2.5.21" % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.26" % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test
      
    ),
    dockerBaseImage := "openjdk:11.0.7-jre",
  )
  

//  The 'version' setting is not set on purpose: its value is generated automatically by the sbt-dynver plugin
//  based on the git tag/sha. Here we're just tacking on the maven-compatible snapshot suffix if needed
lazy val versionSettings = Seq(
  // Adds -SNAPSHOT to the versions that are not on a tag
  // WARNING: there seems to be a bug in sbt-dynver at the time of writing, so the dynverSonatypeSnapshots won't
  // work if not set in ThisBuild
  dynverSonatypeSnapshots in ThisBuild := true,
  //Docker doesn't like `+` in version numbers
  dynverSeparator in ThisBuild := "-"
)

