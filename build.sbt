inThisBuild(Seq(
  version := "0.1",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.12", "2.12.4"),
))

lazy val `quill-monix-jdbc` = project
  .in(file("quill-monix-jdbc"))
  .settings(
    fork in Test := true,
    libraryDependencies ++= Seq(
      "io.monix" %% "monix-eval" % "3.0.0-RC1",
      "io.getquill" %% "quill-jdbc" % "2.3.3"
    )
  )
