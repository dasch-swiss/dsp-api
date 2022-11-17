package org.knora.webapi.responders.v2.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.v2.responder.valuemessages.{
  GenerateSparqlToCreateMultipleValuesRequestV2,
  GenerateSparqlToCreateMultipleValuesResponseV2
}

import scala.concurrent.Future

trait SparqlService {
  def generateSparqlToCreateMultipleValuesRequestV2(
    req: GenerateSparqlToCreateMultipleValuesRequestV2
  ): Future[GenerateSparqlToCreateMultipleValuesResponseV2]
  def sparqlExtendedConstructRequest(req: SparqlExtendedConstructRequest): Future[SparqlExtendedConstructResponse]
  def sparqlSelectRequest(req: SparqlSelectRequest): Future[SparqlSelectResult]
  def sparqlUpdateRequest(req: SparqlUpdateRequest): Future[SparqlUpdateResponse]
}

final case class ActorSparqlService(rData: ResponderData) extends SparqlService {
  implicit val timeout: Timeout = rData.appConfig.defaultTimeoutAsDuration
  val appActor: ActorRef        = rData.appActor

  def sparqlExtendedConstructRequest(req: SparqlExtendedConstructRequest): Future[SparqlExtendedConstructResponse] =
    appActor.ask(req).mapTo[SparqlExtendedConstructResponse]

  def sparqlSelectRequest(req: SparqlSelectRequest): Future[SparqlSelectResult] =
    appActor.ask(req).mapTo[SparqlSelectResult]

  def generateSparqlToCreateMultipleValuesRequestV2(
    req: GenerateSparqlToCreateMultipleValuesRequestV2
  ): Future[GenerateSparqlToCreateMultipleValuesResponseV2] =
    appActor.ask(req).mapTo[GenerateSparqlToCreateMultipleValuesResponseV2]

  def sparqlUpdateRequest(req: SparqlUpdateRequest): Future[SparqlUpdateResponse] =
    appActor.ask(req).mapTo[SparqlUpdateResponse]
}
