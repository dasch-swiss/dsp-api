package org.knora.webapi.testservices

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import zio._

final case class TestActorSystemService(actorSystem: ActorSystem) {

  /**
   * Get ActorSystem
   */
  val getActorSystem: ActorSystem = actorSystem
}

object TestActorSystemService {

  /**
   * Acquires an ActorSystem
   */
  private def acquire() = (for {
    ec <- ZIO.executor.map(_.asExecutionContext)
    system <- ZIO.attempt(
                ActorSystem("TestActorSystemService", Some(ConfigFactory.load()), None, Some(ec))
              )

  } yield system).tap(_ => ZIO.logDebug(">>> Acquire Test Actor System Service <<<")).orDie

  /**
   * Releases the ActorSystem
   */
  private def release(system: ActorSystem) =
    (for {
      _  <- ZIO.fromFuture(_ => system.terminate()).timeout(5.seconds).orDie
    } yield ()).tap(_ => ZIO.logDebug(">>> Release Test Actor System Service <<<"))

  val layer: ZLayer[Any, Nothing, TestActorSystemService] =
    ZLayer.scoped {
      for {
        system <- ZIO.acquireRelease(acquire())(release(_))
      } yield TestActorSystemService(system)
    }

}
