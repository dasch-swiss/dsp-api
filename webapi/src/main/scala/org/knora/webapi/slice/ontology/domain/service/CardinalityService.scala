/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service
import akka.actor.ActorRef
import akka.util.Timeout
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CanDeleteCardinalitiesFromClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.DeleteCardinalitiesFromClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.queries.sparql._
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService

@accessible
trait CardinalityService {

  /**
   * Check if a specific cardinality may be set on a property in the context of a class.
   *
   * Setting a wider cardinality on a sub class is not possible if for the same property a stricter cardinality already exists in one of its super classes.
   *
   * @param classIri class to check
   * @param propertyIri property to check
   * @param newCardinality the newly desired cardinality
   * @return
   *    '''success''' a [[Boolean]] indicating whether a class's cardinalities can be widen.
   *
   *    '''error''' a [[Throwable]] indicating that something went wrong
   */
  def canSetCardinality(classIri: InternalIri, propertyIri: InternalIri, newCardinality: Cardinality): Task[Boolean]

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

  /**
   * Check if a property entity is used in resource instances. Returns `true` if
   * it is used, and `false` if it is not used.
   *
   * @param classIri the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   *
   * @return a [[Boolean]] denoting if the property entity is used.
   */
  def isPropertyUsedInResources(classIri: InternalIri, propertyIri: InternalIri): Task[Boolean]
}

final case class CardinalityServiceLive(
  private val actorDeps: ActorDeps,
  private val stringFormatter: StringFormatter,
  private val tripleStore: TriplestoreService,
  private val ontologyRepo: OntologyRepo,
  private val iriConverter: IriConverter
) extends CardinalityService {
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

  def isPropertyUsedInResources(classIri: InternalIri, propertyIri: InternalIri): Task[Boolean] = {
    val query = v2.txt.isPropertyUsed(propertyIri, classIri)
    tripleStore.sparqlHttpAsk(query.toString).map(_.result)
  }

  override def canSetCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[Boolean] =
    for {
      propSmartIri          <- iriConverter.asInternalSmartIri(propertyIri)
      classInfoMaybe        <- ontologyRepo.findClassBy(classIri)
      inheritedCardinalities = classInfoMaybe.flatMap(_.inheritedCardinalities.get(propSmartIri)).map(Cardinality.get)
    } yield inheritedCardinalities.forall { it =>
      val upper = it
      val newer = newCardinality
      val nToO  = newer.isStricter(upper)
      val oToN  = upper.isStricter(newer)
      !oToN
    }

}

object CardinalityService {
  val layer: ZLayer[
    ActorDeps with StringFormatter with TriplestoreService with OntologyRepo with IriConverter,
    Nothing,
    CardinalityServiceLive
  ] = ZLayer.fromFunction(CardinalityServiceLive.apply _)
}
