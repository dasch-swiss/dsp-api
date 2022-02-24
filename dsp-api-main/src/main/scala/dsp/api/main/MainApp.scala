package dsp.api.main

import zio.ZIPApp

object MainApp extends ZIOApp {
  val lookedupProfile: RIO[SchemaRepo, UserProfile] =
    for {
      profile <- SchemaRepo.lookup("user1")
    } yield profile

  printLine(lookedupProfile)
}
