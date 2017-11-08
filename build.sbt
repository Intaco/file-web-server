name := "web-server"

version := "1.0"

scalaVersion := "2.12.4"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.6",
  "org.apache.tika" % "tika-parsers" % "1.4"
)