package org.knora.webapi.responders
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import zio._

import org.knora.webapi.messages.ResponderRequest

/**
 * This trait encapsulates the [[akka.pattern.ask]] into the zio world
 */
trait ActorToZioBridge {

  /**
   * Sends a message to the "appActor" [[org.knora.webapi.core.actors.RoutingActor]] using the [[akka.pattern.ask]],
   * casts and returns the response to the expected return type `R` as [[Task]].
   *
   * @param message The message sent to the actor
   *
   * @tparam R The type of the expected success value
   * @return A Task containing either the success `R` or the failure [[Throwable]],
   *         will fail during runtime with a [[ClassCastException]] if the `R` does not correspond
   *         to the response of the message being sent due to the untyped nature of the ask pattern
   */
  def askAppActor[R: Tag](message: ResponderRequest): Task[R]

}

final case class ActorToZioBridgeLive(actorDeps: ActorDeps) extends ActorToZioBridge {
  private implicit val timeout: Timeout = actorDeps.timeout
  private val appActor: ActorRef        = actorDeps.appActor

  override def askAppActor[R: Tag](message: ResponderRequest): Task[R] =
    ZIO.fromFuture(implicit ec => appActor.ask(message).map(_.asInstanceOf[R]))
}

object ActorToZioBridge {
  val live: URLayer[ActorDeps, ActorToZioBridgeLive] = ZLayer.fromFunction(ActorToZioBridgeLive.apply _)
}
