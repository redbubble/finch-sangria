organization := "com.redbubble"

lazy val buildSettings = Seq(
  name := "finch-sangria",
  scalaVersion := "2.12.4"
)

bintrayOrganization := Some("redbubble")

bintrayRepository := "open-source"

bintrayPackageLabels := Seq("finch", "sangria", "graphql")

licenses += ("BSD New", url("https://opensource.org/licenses/BSD-3-Clause"))

scalacOptions ++= Seq(
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

scalacOptions in Test ++= Seq("-Yrangepos")

resolvers ++= Seq(
  Resolver.jcenterRepo,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  "Twitter" at "http://maven.twttr.com"
)

lazy val rbUtilsVersion = "0.2.9"
lazy val catsVersion = "1.0.1"
lazy val mouseVersion = "0.9"
lazy val circeVersion = "0.9.0"
lazy val finchVersion = "0.16.0"
lazy val sangriaVersion = "1.3.3"
lazy val sangriaCirceVersion = "1.1.1"
lazy val specsVersion = "4.0.2"
lazy val slf4jVersion = "1.7.25"

libraryDependencies ++= Seq(
  "com.redbubble" %% "rb-scala-utils" % rbUtilsVersion,
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
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "org.specs2" %% "specs2-core" % specsVersion % "test",
  "org.specs2" %% "specs2-scalacheck" % specsVersion % "test"
)
