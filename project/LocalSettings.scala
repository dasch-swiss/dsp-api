package org.knora

import sbt.*
import Keys.*

// Uncomment L11 and skip-worktree on this file to make warnings non-fatal locally:
// git update-index --skip-worktree project/LocalSettings.scala

object LocalSettings {
  val localScalacOptions: Seq[SettingsDefinition] = Seq(
    // scalacOptions -= "-Xfatal-warnings"
  )
}
