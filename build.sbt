lazy val json4sVersion = "3.6.7"
lazy val slickVersion = "3.3.1"
lazy val akkaHttpVersion = "10.1.3"

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
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"   % "10.1.12",
      "com.typesafe.akka" %% "akka-stream" % "2.5.26",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.26",
      "de.heikoseeberger" %% "akka-http-json4s" % "1.33.0",
      "com.typesafe" % "config" % "1.4.0",
    )
  )
  

//  The 'version' setting is not set on purpose: its value is generated automatically by the sbt-dynver plugin
//  based on the git tag/sha. Here we're just tacking on the maven-compatible snapshot suffix if needed
lazy val versionSettings = Seq(
  // Adds -SNAPSHOT to the versions that are not on a tag
  // WARNING: there seems to be a bug in sbt-dynver at the time of writing, so the dynverSonatypeSnapshots won't
  // work if not set in ThisBuild
  dynverSonatypeSnapshots in ThisBuild := true,
  //Docker doesn't like `+` in version numbers
  version in ThisBuild ~= (_.replace('+', '_')),
  dynver in ThisBuild ~= (_.replace('+', '_'))
)
