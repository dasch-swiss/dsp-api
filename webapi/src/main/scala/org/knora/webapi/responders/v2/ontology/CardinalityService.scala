package org.knora.webapi.responders.v2.ontology
import akka.actor.ActorRef
import akka.util.Timeout
import zio.Task
import zio.ZIO
import zio.ZLayer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CanDeleteCardinalitiesFromClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.DeleteCardinalitiesFromClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.ActorDeps

trait CardinalityService {

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   *
   * @param deleteCardinalitiesFromClassRequest the requested cardinalities to be deleted.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[CanDoResponseV2]] indicating whether a class's cardinalities can be deleted.
   */
  def canDeleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: CanDeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  ): Task[CanDoResponseV2]

  /**
   * Deletes the supplied cardinalities from a class, if the referenced properties are not used in instances
   * of the class and any subclasses.
   *
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   *
   * @param deleteCardinalitiesFromClassRequest the requested cardinalities to be deleted.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def deleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  ): Task[ReadOntologyV2]
}

final case class CardinalityServiceLive(private val actorDeps: ActorDeps, private val stringFormatter: StringFormatter)
    extends CardinalityService {
  private implicit val ec: ExecutionContext = actorDeps.executionContext
  private implicit val timeout: Timeout     = actorDeps.timeout
  private implicit val sf: StringFormatter  = stringFormatter

  private val appActor: ActorRef = actorDeps.appActor

  private def toTask[A]: Future[A] => Task[A] = f => ZIO.fromFuture(_ => f)

  override def canDeleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: CanDeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  ): Task[CanDoResponseV2] =
    toTask(
      CardinalityHandler.canDeleteCardinalitiesFromClass(
        appActor,
        deleteCardinalitiesFromClassRequest,
        internalClassIri,
        internalOntologyIri
      )
    )

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Deletes the supplied cardinalities from a class, if the referenced properties are not used in instances
   * of the class and any subclasses.
   *
   * @param deleteCardinalitiesFromClassRequest the requested cardinalities to be deleted.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  override def deleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  ): Task[ReadOntologyV2] = toTask(
    CardinalityHandler.deleteCardinalitiesFromClass(
      appActor,
      deleteCardinalitiesFromClassRequest,
      internalClassIri,
      internalOntologyIri
    )
  )
}

object CardinalityService {
  val layer = ZLayer.fromFunction(CardinalityServiceLive.apply _)
}
