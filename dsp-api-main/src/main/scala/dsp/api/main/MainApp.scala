package dsp.api.main

import dsp.schema.repo.{SchemaRepo, SchemaRepoLive}
import zio.Console.printLine
import zio._

object MainApp extends ZIOAppDefault {
  val effect: ZIO[Console with SchemaRepo, Nothing, Unit] =
    for {
      profile <- SchemaRepo.lookup("user1").orDie
      _       <- printLine(profile).orDie
      _       <- printLine(42).orDie
    } yield ()

  val mainApp: ZIO[Any, Nothing, Unit] = effect.provide(Console.live ++ SchemaRepoLive.layer)
  def run: ZIO[Any, Nothing, Unit]     = mainApp
}
