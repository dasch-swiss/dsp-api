package org.knora.webapi.responders.v2.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.store.sipimessages.{SipiGetTextFileRequest, SipiGetTextFileResponse}
import org.knora.webapi.messages.util.ResponderData

import scala.concurrent.Future

trait SipiService {
  def sipiGetTextFileRequest(req: SipiGetTextFileRequest): Future[SipiGetTextFileResponse]
}

final case class ActorSipiService(rData: ResponderData) extends SipiService {
  implicit val timeout: Timeout = rData.appConfig.defaultTimeoutAsDuration
  val appActor: ActorRef        = rData.appActor

  def sipiGetTextFileRequest(req: SipiGetTextFileRequest): Future[SipiGetTextFileResponse] =
    appActor.ask(req).mapTo[SipiGetTextFileResponse]
}
