lazy val json4sVersion = "3.6.7"

val AkkaVersion = "2.5.31"
lazy val root = (project in file("."))
  .enablePlugins(
    JavaServerAppPackaging,
    AshScriptPlugin,
    DockerPlugin,
  )
  .settings(versionSettings)
  .settings(
    name := "chatroom",
    organization in ThisBuild := "com.github.dafutils",
    scalaVersion in ThisBuild := "2.13.3",
    crossPaths in ThisBuild := false,
    parallelExecution in Test in ThisBuild := false,
    fork in ThisBuild := true,
    publishArtifact in ThisBuild in packageDoc := false,
    publishArtifact in ThisBuild in packageSrc := false,
    dockerExposedPorts ++= Seq(9000),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"   % "10.1.12",
      "com.lightbend.akka" %% "akka-stream-alpakka-hbase" % "2.0.1",
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "de.heikoseeberger" %% "akka-http-json4s" % "1.33.0",
      "com.typesafe" % "config" % "1.4.0",
      "org.json4s" % "json4s-core_2.12" % "3.7.0-M4"
    ),
    Global / onChangedBuildSource := ReloadOnSourceChanges
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
