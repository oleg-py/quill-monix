inThisBuild(Seq(
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.12", "2.12.4"),
  scalacOptions += "-language:higherKinds"
))

lazy val `quill-monix-jdbc` = project
  .in(file("quill-monix-jdbc"))
  .settings(
    fork in Test := true,
    libraryDependencies ++= Seq(
      "io.monix" %% "monix-eval" % "3.0.0-RC2",
      "com.zaxxer" % "HikariCP" % "2.7.4",
      "io.getquill" %% "quill-sql" % "2.3.3"
    )
  )
