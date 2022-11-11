package org.knora.webapi.responders.v2.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2

import scala.concurrent.Future

trait SearchService {
  def gravsearchRequestV2(req: GravsearchRequestV2): Future[ReadResourcesSequenceV2]
}

final case class ActorSearchService(rData: ResponderData) extends SearchService {
  implicit val timeout: Timeout = rData.appConfig.defaultTimeoutAsDuration
  val appActor: ActorRef        = rData.appActor

  def gravsearchRequestV2(req: GravsearchRequestV2): Future[ReadResourcesSequenceV2] =
    appActor.ask(req).mapTo[ReadResourcesSequenceV2]
}
