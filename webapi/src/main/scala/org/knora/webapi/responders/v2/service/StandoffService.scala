package org.knora.webapi.responders.v2.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.standoffmessages.{
  GetMappingRequestV2,
  GetMappingResponseV2,
  GetXSLTransformationRequestV2,
  GetXSLTransformationResponseV2
}

import scala.concurrent.Future

trait StandoffService {
  def getMappingRequestV2(req: GetMappingRequestV2): Future[GetMappingResponseV2]
  def getXSLTransformationRequestV2(req: GetXSLTransformationRequestV2): Future[GetXSLTransformationResponseV2]
}

final case class ActorStandoffService(rData: ResponderData) extends StandoffService {
  implicit val timeout: Timeout = rData.appConfig.defaultTimeoutAsDuration
  val appActor: ActorRef        = rData.appActor

  def getMappingRequestV2(req: GetMappingRequestV2): Future[GetMappingResponseV2] =
    appActor.ask(req).mapTo[GetMappingResponseV2]

  def getXSLTransformationRequestV2(req: GetXSLTransformationRequestV2): Future[GetXSLTransformationResponseV2] =
    appActor.ask(req).mapTo[GetXSLTransformationResponseV2]
}
