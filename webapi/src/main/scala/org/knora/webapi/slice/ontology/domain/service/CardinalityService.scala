/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.queries
import org.knora.webapi.queries.sparql._
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.CanReplaceCheckSuccess
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.IsInUseCheckFailure
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.CanSetCardinalityCheckResult
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
   *    '''success''' a [[CanSetCardinalityCheckResult]] indicating whether a class's cardinalities can be set.
   *
   *    '''error''' a [[Throwable]] indicating that something went wrong,
   */
  def canSetCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[CanSetCardinalityCheckResult]

  /**
   * Check if on a specific class any cardinality may be replaced.
   *
   * Replacing an existing cardinality is only possible if the class is not in use yet.
   *
   * @param classIri class to check
   * @return
   *    '''success''' a [[CanReplaceCardinalityCheckResult]] indicating whether a class's cardinalities can be replaced.
   *
   *    '''error''' a [[Throwable]] indicating that something went wrong.
   */
  def canReplaceCardinality(classIri: InternalIri): Task[CanReplaceCardinalityCheckResult]

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

object ChangeCardinalityCheckResult {

  sealed trait ChangeCardinalityCheckResult {
    def isSuccess: Boolean
  }

  trait Success extends ChangeCardinalityCheckResult {
    override final val isSuccess: Boolean = true
  }
  trait Failure extends ChangeCardinalityCheckResult {
    override final val isSuccess: Boolean = false
    val reason: String
  }

  abstract class KnoraOntologyCheckFailure extends Failure {
    override final val reason: String = "A base class exists which is more restrictive."
  }

  object CanReplaceCardinalityCheckResult {
    sealed trait CanReplaceCardinalityCheckResult extends ChangeCardinalityCheckResult
    final case object CanReplaceCheckSuccess      extends Success with CanReplaceCardinalityCheckResult
    final case object IsInUseCheckFailure extends Failure with CanReplaceCardinalityCheckResult {
      override val reason: String = "Cardinality is in use"
    }
    final case object KnoraOntologyCheckFailure
        extends ChangeCardinalityCheckResult.KnoraOntologyCheckFailure
        with CanReplaceCardinalityCheckResult
  }

  object CanSetCardinalityCheckResult {
    sealed trait CanSetCardinalityCheckResult extends ChangeCardinalityCheckResult
    final case object CanSetCheckSuccess      extends Success with CanSetCardinalityCheckResult
    final case object BaseClassCheckFailure extends Failure with CanSetCardinalityCheckResult {
      val reason: String = "A base class exists which is more restrictive."
    }
    final case object KnoraOntologyCheckFailure
        extends ChangeCardinalityCheckResult.KnoraOntologyCheckFailure
        with CanSetCardinalityCheckResult
  }
}
final case class CardinalityServiceLive(
  private val stringFormatter: StringFormatter,
  private val tripleStore: TriplestoreService,
  private val ontologyRepo: OntologyRepo,
  private val iriConverter: IriConverter
) extends CardinalityService {

  def isPropertyUsedInResources(classIri: InternalIri, propertyIri: InternalIri): Task[Boolean] = {
    val query = v2.txt.isPropertyUsed(propertyIri, classIri)
    tripleStore.sparqlHttpAsk(query.toString).map(_.result)
  }

  override def canSetCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[CanSetCardinalityCheckResult] =
    ZIO.ifZIO(isPartOfKnoraOntology(classIri))(
      onTrue = ZIO.succeed(CanSetCardinalityCheckResult.KnoraOntologyCheckFailure),
      onFalse = doesSuperClassExistWithStricterCardinality(classIri, propertyIri, newCardinality).map {
        case false => CanSetCardinalityCheckResult.CanSetCheckSuccess
        case true  => CanSetCardinalityCheckResult.BaseClassCheckFailure
      }
    )

  private val knoraAdminAndBaseOntologies = Seq(
    "http://www.knora.org/ontology/knora-base",
    "http://www.knora.org/ontology/knora-admin"
  ).map(InternalIri)

  private def isPartOfKnoraOntology(classIri: InternalIri): Task[Boolean] =
    iriConverter.getOntologyIriFromClassIri(classIri).map(knoraAdminAndBaseOntologies.contains)

  private def doesSuperClassExistWithStricterCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ) =
    for {
      propSmartIri          <- iriConverter.asInternalSmartIri(propertyIri)
      classInfoMaybe        <- ontologyRepo.findClassBy(classIri)
      inheritedCardinalities = classInfoMaybe.flatMap(_.inheritedCardinalities.get(propSmartIri)).map(Cardinality.get)
    } yield inheritedCardinalities.forall(_.isStricterThan(newCardinality))

  /**
   * Check if a specific cardinality may be replaced.
   *
   * Replacing an existing cardinality on a class is only possible if it is not in use.
   *
   * @param classIri class to check
   * @return
   *    '''success''' a [[CanSetCardinalityCheckResult]] indicating whether a class's cardinalities can be set.
   *
   *    '''error''' a [[Throwable]] indicating that something went wrong,
   */
  override def canReplaceCardinality(classIri: InternalIri): Task[CanReplaceCardinalityCheckResult] = {
    val doCheck: Task[CanReplaceCardinalityCheckResult] = {
      // ignoreKnoraConstraints: It is OK if a property refers to the class
      // via knora-base:subjectClassConstraint or knora-base:objectClassConstraint.
      val query = queries.sparql.v2.txt.isEntityUsed(classIri, ignoreKnoraConstraints = true)
      tripleStore
        .sparqlHttpSelect(query.toString())
        .map(_.results.bindings)
        .map {
          case seq if seq.isEmpty => CanReplaceCheckSuccess
          case _                  => IsInUseCheckFailure
        }
    }

    ZIO.ifZIO(isPartOfKnoraOntology(classIri))(
      onTrue = ZIO.succeed(CanReplaceCardinalityCheckResult.KnoraOntologyCheckFailure),
      onFalse = doCheck
    )
  }
}

object CardinalityService {
  val layer: ZLayer[
    IriConverter with OntologyRepo with TriplestoreService with StringFormatter,
    Nothing,
    CardinalityServiceLive
  ] = ZLayer.fromFunction(CardinalityServiceLive.apply _)
}
