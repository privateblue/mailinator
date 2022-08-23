val scala2Version = "2.13.8"
val logbackVersion = "1.2.11"
val catsVersion = "2.8.0"
val catsEffectVersion = "3.3.14"
val http4sVersion = "0.23.14"
val circeVersion = "0.14.2"
val akkaVersion = "2.6.19"
val junitVersion = "0.11"
val scalatestVersion = "3.2.12"

lazy val root = project
  .in(file("."))
  .settings(
    name := "mailinator",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala2Version,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.novocode" % "junit-interface" % junitVersion % "test",
      "org.scalatest" %% "scalatest" % scalatestVersion % "test",
      "org.scalatest" %% "scalatest-flatspec" % scalatestVersion % "test",
      "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test
    )
  )
