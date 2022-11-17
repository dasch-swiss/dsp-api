package org.knora.webapi.responders.v2.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  EntityInfoGetRequestV2,
  EntityInfoGetResponseV2,
  OntologyMetadataGetByIriRequestV2,
  ReadOntologyMetadataV2
}

import scala.concurrent.Future

trait OntologyService {
  def entityInfoGetRequestV2(req: EntityInfoGetRequestV2): Future[EntityInfoGetResponseV2]
  def ontologyMetadataGetByIriRequestV2(req: OntologyMetadataGetByIriRequestV2): Future[ReadOntologyMetadataV2]
}

final case class ActorOntologyService(rData: ResponderData) extends OntologyService {
  implicit val timeout: Timeout = rData.appConfig.defaultTimeoutAsDuration
  val appActor: ActorRef        = rData.appActor

  def entityInfoGetRequestV2(req: EntityInfoGetRequestV2): Future[EntityInfoGetResponseV2] =
    appActor.ask(req).mapTo[EntityInfoGetResponseV2]

  def ontologyMetadataGetByIriRequestV2(req: OntologyMetadataGetByIriRequestV2): Future[ReadOntologyMetadataV2] =
    appActor.ask(req).mapTo[ReadOntologyMetadataV2]
}
