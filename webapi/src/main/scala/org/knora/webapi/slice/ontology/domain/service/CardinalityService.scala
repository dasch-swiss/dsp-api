/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality.Unbounded
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.IsInUseCheckFailure
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.SubclassCheckFailure
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.SuperClassCheckFailure
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.util.EitherUtil.joinOnLeft
import org.knora.webapi.util.EitherUtil.joinOnLeftList

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
    newCardinality: Cardinality,
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
}

object ChangeCardinalityCheckResult {

  sealed trait ChangeCardinalityCheckResult {
    def isSuccess: Boolean
    def isFailure: Boolean                     = !isSuccess
    def failureAffectedIris: List[InternalIri] = List.empty
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

    case object Success extends Success with CanReplaceCardinalityCheckResult

    trait Failure extends ChangeCardinalityCheckResult.Failure with CanReplaceCardinalityCheckResult

    case object IsInUseCheckFailure extends CanReplaceCardinalityCheckResult.Failure {
      override val reason: String = "Cardinality is in use."
    }

    case object KnoraOntologyCheckFailure
        extends ChangeCardinalityCheckResult.KnoraOntologyCheckFailure
        with CanReplaceCardinalityCheckResult.Failure
  }

  object CanSetCardinalityCheckResult {
    sealed trait CanSetCardinalityCheckResult extends ChangeCardinalityCheckResult

    case object Success extends Success with CanSetCardinalityCheckResult

    trait Failure extends ChangeCardinalityCheckResult.Failure with CanSetCardinalityCheckResult

    final case class SuperClassCheckFailure(superClasses: List[InternalIri])
        extends CanSetCardinalityCheckResult.Failure {
      val reason: String                                  = "The new cardinality is not included in the cardinality of a super-class."
      override val failureAffectedIris: List[InternalIri] = superClasses
    }

    final case class SubclassCheckFailure(subClasses: List[InternalIri]) extends CanSetCardinalityCheckResult.Failure {
      val reason: String                                  = "The new cardinality does not include the cardinality of a subclass."
      override val failureAffectedIris: List[InternalIri] = subClasses
    }

    final case class CurrentClassFailure(override val failureAffectedIris: List[InternalIri])
        extends CanSetCardinalityCheckResult.Failure {
      val reason: String = "The cardinality of the current class is not included in the new cardinality."
    }

    case object KnoraOntologyCheckFailure
        extends ChangeCardinalityCheckResult.KnoraOntologyCheckFailure
        with CanSetCardinalityCheckResult.Failure
  }
}

final case class CardinalityServiceLive(
  private val stringFormatter: StringFormatter,
  private val tripleStore: TriplestoreService,
  private val predicateRepository: PredicateRepository,
  private val ontologyRepo: OntologyRepo,
  private val iriConverter: IriConverter,
) extends CardinalityService {

  private case class CheckCardinalitySubject(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality,
  )

  override def canSetCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality,
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    ZIO.ifZIO(isPartOfKnoraOntology(classIri))(
      onTrue = ZIO.succeed(Left(List(CanSetCardinalityCheckResult.KnoraOntologyCheckFailure))),
      onFalse = doCanSetCheck(CheckCardinalitySubject(classIri, propertyIri, newCardinality)),
    )

  private val knoraAdminAndBaseOntologies = Seq(
    "http://www.knora.org/ontology/knora-base",
    "http://www.knora.org/ontology/knora-admin",
  ).map(InternalIri.apply)

  private def isPartOfKnoraOntology(classIri: InternalIri): Task[Boolean] =
    iriConverter.getOntologyIriFromClassIri(classIri).map(knoraAdminAndBaseOntologies.contains)

  private def doCanSetCheck(
    check: CheckCardinalitySubject,
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      a <- ontologyCheck(check)
      b <- checkIsCurrentCardinalityIncludedOrPersistentEntitiesAreCompatible(check)
    } yield joinOnLeftList(a, b)

  private def ontologyCheck(
    check: CheckCardinalitySubject,
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      a <- superClassCheck(check)
      b <- subclassCheck(check)
    } yield joinOnLeft(a, b)

  private def superClassCheck(check: CheckCardinalitySubject) = {
    val superClasses                = ontologyRepo.findAllSuperClassesBy(check.classIri)
    val newCardinalityIsNotIncluded = (other: Cardinality) => check.newCardinality.isNotIncludedIn(other)
    canSetCheckFor(superClasses, check.propertyIri, newCardinalityIsNotIncluded, SuperClassCheckFailure.apply)
  }

  private def toClassIris(subclasses: List[ReadClassInfoV2]): List[InternalIri] =
    subclasses.map(_.entityInfoContent.classIri.toInternalIri)

  private def subclassCheck(check: CheckCardinalitySubject) = {
    val subclasses =
      for {
        subclasses   <- ontologyRepo.findAllSubclassesBy(check.classIri)
        superClasses <- ontologyRepo.findAllSuperClassesBy(toClassIris(subclasses), upToClass = check.classIri)
      } yield subclasses ::: superClasses
    val subclassCardinalityIsNotIncluded = (other: Cardinality) => other.isNotIncludedIn(check.newCardinality)
    canSetCheckFor(subclasses, check.propertyIri, subclassCardinalityIsNotIncluded, SubclassCheckFailure.apply)
  }

  private def canSetCheckFor(
    getClasses: Task[List[ReadClassInfoV2]],
    propertyIri: InternalIri,
    predicate: Cardinality => Boolean,
    errorFactory: List[InternalIri] => CanSetCardinalityCheckResult.Failure,
  ): ZIO[Any, Throwable, Either[CanSetCardinalityCheckResult.Failure, CanSetCardinalityCheckResult.Success.type]] =
    getClasses
      .flatMap(filterPropertyCardinalityIsCompatible(propertyIri, predicate))
      .map {
        case Nil        => Right(CanSetCardinalityCheckResult.Success)
        case subClasses => Left(errorFactory.apply(subClasses))
      }

  private def filterPropertyCardinalityIsCompatible(
    propertyIri: InternalIri,
    predicate: Cardinality => Boolean,
  ): List[ReadClassInfoV2] => Task[List[InternalIri]] = { classes =>
    for {
      propSmartIri           <- iriConverter.asInternalSmartIri(propertyIri)
      classesInfo             = classes.map(_.entityInfoContent)
      classesAndCardinalities =
        classesInfo.flatMap(it => getCardinalityForProperty(it, propSmartIri).map(c => (it.classIri.toInternalIri, c)))
      filteredClasses = classesAndCardinalities.filter { case (_, cardinality) => predicate.apply(cardinality) }
    } yield filteredClasses.map { case (classIri, _) => classIri }
  }

  private def getCardinalityForProperty(classInfo: ClassInfoContentV2, propertyIri: SmartIri): Option[Cardinality] =
    classInfo.directCardinalities.get(propertyIri).map(_.cardinality)

  private def checkIsCurrentCardinalityIncludedOrPersistentEntitiesAreCompatible(
    check: CheckCardinalitySubject,
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    ZIO
      .ifZIO(checkIsCurrentCardinalityIncluded(check))(
        onTrue = ZIO.succeed(List.empty),
        onFalse = getInstancesWhichAreNonCompliantWithNewCardinality(check),
      )
      .map(nonCompliantInstances =>
        if (nonCompliantInstances.isEmpty) {
          Right(CanSetCardinalityCheckResult.Success)
        } else {
          Left(List(CanSetCardinalityCheckResult.CurrentClassFailure(check.classIri :: nonCompliantInstances)))
        },
      )

  private def checkIsCurrentCardinalityIncluded(check: CheckCardinalitySubject) =
    for {
      propertySmartIri  <- iriConverter.asInternalSmartIri(check.propertyIri)
      classMaybe        <- ontologyRepo.findClassBy(check.classIri).map(_.map(_.entityInfoContent))
      currentCardinality = classMaybe.flatMap(getCardinalityForProperty(_, propertySmartIri)).getOrElse(Unbounded)
      isIncluded         = currentCardinality.isIncludedIn(check.newCardinality)
    } yield isIncluded

  private def getInstancesWhichAreNonCompliantWithNewCardinality(
    check: CheckCardinalitySubject,
  ): Task[List[InternalIri]] =
    for {
      subclassIris           <- ontologyRepo.findAllSubclassesBy(check.classIri).map(toClassIris)
      instancesAndTheirUsage <-
        predicateRepository.getCountForPropertyUsedNumberOfTimesWithClasses(
          check.propertyIri,
          check.classIri :: subclassIris,
        )
      nonCompliantInstances = instancesAndTheirUsage.filter { case (_, count) =>
                                !check.newCardinality.isCountIncluded(count)
                              }.map(_._1)
    } yield nonCompliantInstances

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
      val query = twirl.queries.sparql.v2.txt.isEntityUsed(classIri, ignoreKnoraConstraints = true)
      tripleStore
        .query(Ask(query))
        .map {
          case true  => IsInUseCheckFailure
          case false => CanReplaceCardinalityCheckResult.Success
        }
    }

    ZIO.ifZIO(isPartOfKnoraOntology(classIri))(
      onTrue = ZIO.succeed(CanReplaceCardinalityCheckResult.KnoraOntologyCheckFailure),
      onFalse = doCheck,
    )
  }
}

object CardinalityService {
  val layer = ZLayer.derive[CardinalityServiceLive]
}
