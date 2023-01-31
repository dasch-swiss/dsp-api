/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.queries
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.queries.sparql._
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality.Unbounded
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.IsInUseCheckFailure
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.SubclassCheckFailure
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.SuperClassCheckFailure
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.EitherUtil.joinOnLeft
import org.knora.webapi.util.EitherUtil.joinOnLeftList

@accessible
trait CardinalityService {

  /**
   * Checks if a specific cardinality may be set on a property in the context of a class.
   *
   * Setting a wider cardinality on a sub class is not possible if for the same property a stricter cardinality already exists in one of its super classes.
   *
   * @param classIri       class to check
   * @param propertyIri    property to check
   * @param newCardinality the new cardinality
   * @return
   * '''success''' a [[CanSetCardinalityCheckResult]] indicating whether a class's cardinalities can be set.
   *
   * '''error''' a [[Throwable]] indicating that something went wrong,
   */
  def canSetCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]]

  /**
   * Checks if on a specific class any cardinality may be replaced.
   *
   * Replacing an existing cardinality is only possible if the class is not in use yet.
   *
   * @deprecated This check is kept only for maintaining the API to be backwards compatible.
   *             When checking for a specific cardinality use [[canSetCardinality(InternalIri, InternalIri, Cardinality)]]
   * @param classIri class to check
   * @return
   * '''success''' a [[CanReplaceCardinalityCheckResult]] indicating whether a class's cardinalities can be replaced.
   *
   * '''error''' a [[Throwable]] indicating that something went wrong.
   */
  def canReplaceCardinality(classIri: InternalIri): Task[CanReplaceCardinalityCheckResult]

  /**
   * Checks if a property entity is used in resource instances. Returns `true` if
   * it is used, and `false` if it is not used.
   *
   * @param classIri    the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   * @return a [[Boolean]] denoting if the property entity is used.
   */
  def isPropertyUsedInResources(classIri: InternalIri, propertyIri: InternalIri): Task[Boolean]
}

object ChangeCardinalityCheckResult {

  sealed trait ChangeCardinalityCheckResult {
    def isSuccess: Boolean

    def isFailure: Boolean = !isSuccess
  }

  trait Success extends ChangeCardinalityCheckResult {
    override final val isSuccess: Boolean = true
  }

  trait Failure extends ChangeCardinalityCheckResult {
    override final val isSuccess: Boolean = false
    val reason: String
  }

  abstract class KnoraOntologyCheckFailure extends Failure {
    override final val reason: String = "Knora ontologies are write protected."
  }

  object CanReplaceCardinalityCheckResult {
    sealed trait CanReplaceCardinalityCheckResult extends ChangeCardinalityCheckResult

    final case object Success extends Success with CanReplaceCardinalityCheckResult

    trait Failure extends ChangeCardinalityCheckResult.Failure with CanReplaceCardinalityCheckResult

    final case object IsInUseCheckFailure extends CanReplaceCardinalityCheckResult.Failure {
      override val reason: String = "Cardinality is in use."
    }

    final case object KnoraOntologyCheckFailure
        extends ChangeCardinalityCheckResult.KnoraOntologyCheckFailure
        with CanReplaceCardinalityCheckResult.Failure
  }

  object CanSetCardinalityCheckResult {
    sealed trait CanSetCardinalityCheckResult extends ChangeCardinalityCheckResult

    final case object Success extends Success with CanSetCardinalityCheckResult

    trait Failure extends ChangeCardinalityCheckResult.Failure with CanSetCardinalityCheckResult

    final case class SuperClassCheckFailure(superClasses: List[InternalIri])
        extends CanSetCardinalityCheckResult.Failure {
      val reason: String = "The new cardinality is not included in the cardinality of a super-class."
    }

    final case class SubclassCheckFailure(subClasses: List[InternalIri]) extends CanSetCardinalityCheckResult.Failure {
      val reason: String = "The new cardinality does not include the cardinality of a subclass."
    }

    final case class CurrentClassFailure(currentClassIri: InternalIri) extends CanSetCardinalityCheckResult.Failure {
      val reason: String = "The cardinality of the current class is not included in the new cardinality."
    }

    final case object KnoraOntologyCheckFailure
        extends ChangeCardinalityCheckResult.KnoraOntologyCheckFailure
        with CanSetCardinalityCheckResult.Failure
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
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    ZIO.ifZIO(isPartOfKnoraOntology(classIri))(
      onTrue = ZIO.succeed(Left(List(CanSetCardinalityCheckResult.KnoraOntologyCheckFailure))),
      onFalse = doCanSetCheck(classIri, propertyIri, newCardinality)
    )

  private val knoraAdminAndBaseOntologies = Seq(
    "http://www.knora.org/ontology/knora-base",
    "http://www.knora.org/ontology/knora-admin"
  ).map(InternalIri)

  private def isPartOfKnoraOntology(classIri: InternalIri): Task[Boolean] =
    iriConverter.getOntologyIriFromClassIri(classIri).map(knoraAdminAndBaseOntologies.contains)

  private def doCanSetCheck(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      a <- ontologyCheck(classIri, propertyIri, newCardinality)
      b <- currentCardinalityIfSetIsIncludedInNewCardinality(classIri, propertyIri, newCardinality)
    } yield joinOnLeftList(a, b)

  private def ontologyCheck(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      a <- superClassCheck(classIri, propertyIri, newCardinality)
      b <- subclassCheck(classIri, propertyIri, newCardinality)
    } yield joinOnLeft(a, b)

  private def superClassCheck(classIri: InternalIri, propertyIri: InternalIri, newCardinality: Cardinality) = {
    val superClasses                = ontologyRepo.findAllSuperClassesBy(classIri)
    val newCardinalityIsNotIncluded = (other: Cardinality) => newCardinality.isNotIncludedIn(other)
    canSetCheckFor(superClasses, propertyIri, newCardinalityIsNotIncluded, SuperClassCheckFailure)
  }

  private def toClassIris(subclasses: List[ReadClassInfoV2]): List[InternalIri] =
    subclasses.map(_.entityInfoContent.classIri.toInternalIri)

  private def subclassCheck(classIri: InternalIri, propertyIri: InternalIri, newCardinality: Cardinality) = {
    val subclasses =
      for {
        subclasses <- ontologyRepo.findAllSubclassesBy(classIri)
        superC     <- ontologyRepo.findAllSuperClassesBy(toClassIris(subclasses))
      } yield subclasses ::: superC
    val subclassCardinalityIsNotIncluded = (other: Cardinality) => other.isNotIncludedIn(newCardinality)
    canSetCheckFor(subclasses, propertyIri, subclassCardinalityIsNotIncluded, SubclassCheckFailure)
  }

  private def canSetCheckFor(
    getClasses: Task[List[ReadClassInfoV2]],
    propertyIri: InternalIri,
    predicate: Cardinality => Boolean,
    errorFactory: List[InternalIri] => CanSetCardinalityCheckResult.Failure
  ): ZIO[Any, Throwable, Either[CanSetCardinalityCheckResult.Failure, CanSetCardinalityCheckResult.Success.type]] =
    getClasses
      .flatMap(filterPropertyCardinalityIsCompatible(propertyIri, predicate))
      .map {
        case Nil        => Right(CanSetCardinalityCheckResult.Success)
        case subClasses => Left(errorFactory.apply(subClasses))
      }

  private def filterPropertyCardinalityIsCompatible(
    propertyIri: InternalIri,
    predicate: Cardinality => Boolean
  ): List[ReadClassInfoV2] => Task[List[InternalIri]] = { classes =>
    for {
      propSmartIri <- iriConverter.asInternalSmartIri(propertyIri)
      classesInfo   = classes.map(_.entityInfoContent)
      classesAndCardinalities =
        classesInfo.flatMap(it => getCardinalityForProperty(it, propSmartIri).map(c => (it.classIri.toInternalIri, c)))
      filteredClasses = classesAndCardinalities.filter { case (_, cardinality) => predicate.apply(cardinality) }
    } yield filteredClasses.map { case (classIri, _) => classIri }
  }

  private def getCardinalityForProperty(classInfo: ClassInfoContentV2, propertyIri: SmartIri): Option[Cardinality] =
    classInfo.directCardinalities.get(propertyIri).map(_.cardinality)

  private def currentCardinalityIfSetIsIncludedInNewCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      propertySmartIri  <- iriConverter.asInternalSmartIri(propertyIri)
      classMaybe        <- ontologyRepo.findClassBy(classIri).map(_.map(_.entityInfoContent))
      currentCardinality = classMaybe.flatMap(getCardinalityForProperty(_, propertySmartIri)).getOrElse(Unbounded)
      isIncluded         = currentCardinality.isIncludedIn(newCardinality)
      isNotInUse        <- isPropertyUsedInResources(classIri, propertyIri).map(!_)
    } yield
      if (isNotInUse || isIncluded) {
        Right(CanSetCardinalityCheckResult.Success)
      } else {
        Left(List(CanSetCardinalityCheckResult.CurrentClassFailure(classIri)))
      }

  /**
   * Checks if a specific cardinality may be replaced.
   *
   * Replacing an existing cardinality on a class is only possible if it is not in use.
   *
   * @param classIri class to check
   * @return
   * '''success''' a [[CanSetCardinalityCheckResult]] indicating whether a class's cardinalities can be set.
   *
   * '''error''' a [[Throwable]] indicating that something went wrong,
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
          case seq if seq.isEmpty => CanReplaceCardinalityCheckResult.Success
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
    IriConverter with OntologyRepo with StringFormatter with TriplestoreService,
    Nothing,
    CardinalityServiceLive
  ] = ZLayer.fromFunction(CardinalityServiceLive.apply _)
}
