organization := "com.redbubble"

name := "finch-sangria"

enablePlugins(GitVersioning, GitBranchPrompt)

git.useGitDescribe := true

publishMavenStyle := false

bintrayOrganization := Some("redbubble")

bintrayPackageLabels := Seq("finch", "sangria", "graphql")

bintrayRepository := "finch-sangria-releases"

licenses += ("BSD-3-Clause", url("https://opensource.org/licenses/BSD-3-Clause"))

scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture",
  "-Xlint",
  //"-Yno-predef",
  //"-Ywarn-unused-import", // gives false positives
  "-Xfatal-warnings",
  "-Ywarn-value-discard",
  "-Ypartial-unification"

)

lazy val circeVersion = "0.8.0"
lazy val catsVersion = "0.9.0"
lazy val mouseVersion = "0.9"
lazy val finchVersion = "0.15.1"
lazy val sangriaVersion = "1.3.0"
lazy val sangriaCirceVersion = "1.1.0"
lazy val specsVersion = "3.9.5"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "com.github.benhutchison" %% "mouse" % mouseVersion,
  "com.github.finagle" %% "finch-core" % finchVersion,
  "com.github.finagle" %% "finch-circe" % finchVersion,
  "org.sangria-graphql" %% "sangria" % sangriaVersion,
  "org.sangria-graphql" %% "sangria-relay" % sangriaVersion,
  "org.sangria-graphql" %% "sangria-circe" % sangriaCirceVersion,
  "org.specs2" %% "specs2-core" % specsVersion % "test",
  "org.specs2" %% "specs2-scalacheck" % specsVersion % "test"
)
