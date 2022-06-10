package org.knora.webapi.testservices

import akka.actor.ActorSystem
import zio._

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import com.typesafe.config.ConfigFactory

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
  private def acquire() = ZIO.attemptBlocking {
    ActorSystem("Test Actor System Service", ConfigFactory.load())
  }.tap(_ => ZIO.logDebug(">>> Acquire Test Actor System Service <<<")).orDie

  /**
   * Releases the ActorSystem
   */
  private def release(system: ActorSystem) =
    (for {
      ec <- ZIO.executor.map(_.asExecutionContext)
      _  <- ZIO.fromFuture(implicit ec => system.terminate()).timeout(5.seconds).orDie
    } yield ()).tap(_ => ZIO.logDebug(">>> Release Test Actor System Service <<<"))

  val layer: ZLayer[Any, Nothing, TestActorSystemService] = {
    ZLayer.scoped {
      for {
        system <- ZIO.acquireRelease(acquire())(release(_))
      } yield TestActorSystemService(system)
    }
  }

}
