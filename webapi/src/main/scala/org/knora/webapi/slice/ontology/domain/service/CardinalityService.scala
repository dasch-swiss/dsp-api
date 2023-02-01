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
import org.knora.webapi.messages._
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
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
  private val predicateRepository: PredicateRepository,
  private val ontologyRepo: OntologyRepo,
  private val iriConverter: IriConverter
) extends CardinalityService {

  private case class CheckCardinalitySubject(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  )

  override def canSetCardinality(
    classIri: InternalIri,
    propertyIri: InternalIri,
    newCardinality: Cardinality
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    ZIO.ifZIO(isPartOfKnoraOntology(classIri))(
      onTrue = ZIO.succeed(Left(List(CanSetCardinalityCheckResult.KnoraOntologyCheckFailure))),
      onFalse = doCanSetCheck(CheckCardinalitySubject(classIri, propertyIri, newCardinality))
    )

  private val knoraAdminAndBaseOntologies = Seq(
    "http://www.knora.org/ontology/knora-base",
    "http://www.knora.org/ontology/knora-admin"
  ).map(InternalIri)

  private def isPartOfKnoraOntology(classIri: InternalIri): Task[Boolean] =
    iriConverter.getOntologyIriFromClassIri(classIri).map(knoraAdminAndBaseOntologies.contains)

  private def doCanSetCheck(
    check: CheckCardinalitySubject
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      a <- ontologyCheck(check)
      b <- checkCurrentCardinalityIsIncludedInNewCardinalityOrPersistentEntitiesAreCompatible(check)
    } yield joinOnLeftList(a, b)

  private def ontologyCheck(
    check: CheckCardinalitySubject
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      a <- superClassCheck(check)
      b <- subclassCheck(check)
    } yield joinOnLeft(a, b)

  private def superClassCheck(check: CheckCardinalitySubject) = {
    val superClasses                = ontologyRepo.findAllSuperClassesBy(check.classIri)
    val newCardinalityIsNotIncluded = (other: Cardinality) => check.newCardinality.isNotIncludedIn(other)
    canSetCheckFor(superClasses, check.propertyIri, newCardinalityIsNotIncluded, SuperClassCheckFailure)
  }

  private def toClassIris(subclasses: List[ReadClassInfoV2]): List[InternalIri] =
    subclasses.map(_.entityInfoContent.classIri.toInternalIri)

  private def subclassCheck(check: CheckCardinalitySubject) = {
    val subclasses =
      for {
        subclasses   <- ontologyRepo.findAllSubclassesBy(check.classIri)
        superClasses <- ontologyRepo.findAllSuperClassesBy(toClassIris(subclasses))
      } yield subclasses ::: superClasses
    val subclassCardinalityIsNotIncluded = (other: Cardinality) => other.isNotIncludedIn(check.newCardinality)
    canSetCheckFor(subclasses, check.propertyIri, subclassCardinalityIsNotIncluded, SubclassCheckFailure)
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

  private def checkCurrentCardinalityIsIncludedInNewCardinalityOrPersistentEntitiesAreCompatible(
    check: CheckCardinalitySubject
  ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
    for {
      isIncluded                  <- checkExistingCardinalityIsCompatible(check)
      isCompatibleWithPersistence <- checkPersistence(check)
    } yield
      if (isIncluded || isCompatibleWithPersistence) {
        Right(CanSetCardinalityCheckResult.Success)
      } else {
        Left(List(CanSetCardinalityCheckResult.CurrentClassFailure(check.classIri)))
      }

  private def checkExistingCardinalityIsCompatible(check: CheckCardinalitySubject) =
    for {
      propertySmartIri  <- iriConverter.asInternalSmartIri(check.propertyIri)
      classMaybe        <- ontologyRepo.findClassBy(check.classIri).map(_.map(_.entityInfoContent))
      currentCardinality = classMaybe.flatMap(getCardinalityForProperty(_, propertySmartIri)).getOrElse(Unbounded)
      isIncluded         = currentCardinality.isIncludedIn(check.newCardinality)
    } yield isIncluded

  private def checkPersistence(
    check: CheckCardinalitySubject
  ): Task[Boolean] =
    for {
      subclassIris <-
        ontologyRepo.findAllSubclassesBy(check.classIri).map(_.map(_.entityInfoContent.classIri.toInternalIri))
      instancesAndTheirUsage <-
        predicateRepository.getCountForPropertyUsedNumberOfTimesWithClass(
          check.propertyIri,
          check.classIri :: subclassIris
        )
      isCompatible: Boolean = instancesAndTheirUsage.forall { case (_, count) =>
                                check.newCardinality.isCountIncluded(count)
                              }
    } yield isCompatible

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
    StringFormatter with TriplestoreService with PredicateRepository with OntologyRepo with IriConverter,
    Nothing,
    CardinalityServiceLive
  ] = ZLayer.fromFunction(CardinalityServiceLive.apply _)
}
