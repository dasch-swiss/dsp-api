package dsp.api.main

import dsp.schema.repo.SchemaRepo
import dsp.schema.repo.SchemaRepoLive
import zio.Console.printLine
import zio._

object MainApp extends ZIOAppDefault {
  val effect: ZIO[SchemaRepo, Nothing, Unit] =
    for {
      profile <- SchemaRepo.lookup("user1").orDie
      // _       <- printLine(profile).orDie
      // _       <- printLine(42).orDie
    } yield ()

  val mainApp: UIO[Unit] = effect.provide(SchemaRepoLive.layer)
  def run: UIO[Unit]     = mainApp
}
