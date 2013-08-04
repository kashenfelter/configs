name := "configs-std"

description := "Configs instances for standard classes"

libraryDependencies ++= Seq(
  Dependencies.scalaTest % "test",
  Dependencies.scalaCheck % "test"
)

Publish.settings

ideaBasePackage := Some("com.github.kxbmap.configs")
