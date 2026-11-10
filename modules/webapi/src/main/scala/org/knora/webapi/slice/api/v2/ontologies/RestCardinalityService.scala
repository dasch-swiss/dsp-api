/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.ontologies

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException.invalidQueryParamValue
import dsp.errors.BadRequestException.missingQueryParamValue
import dsp.errors.NotFoundException
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
import org.knora.webapi.messages.util.rdf.JsonLDArray
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.util.rdf.JsonLDValue
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.slice.api.v2.ontologies.RestCardinalityService.classIriKey
import org.knora.webapi.slice.api.v2.ontologies.RestCardinalityService.newCardinalityKey
import org.knora.webapi.slice.api.v2.ontologies.RestCardinalityService.propertyIriKey
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.ProjectAdminGrant
import org.knora.webapi.slice.common.api.ProjectCandidate
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

final class RestCardinalityService(
  auth: AuthorizationRestService,
  cardinalityService: CardinalityService,
  iriConverter: IriConverter,
  ontologyRepo: OntologyRepo,
) {

  private val canSetResponsePrefix: String = s"${KnoraApiV2PrefixExpansion}canSetCardinality"

  def canChangeCardinality(
    classIri: String,
    candidate: ProjectCandidate,
    propertyIri: Option[String],
    newCardinality: Option[String],
  ): Task[CanDoResponseV2] =
    (propertyIri, newCardinality) match {
      case (None, Some(_))                           => ZIO.fail(missingQueryParamValue(propertyIriKey))
      case (Some(_), None)                           => ZIO.fail(missingQueryParamValue(newCardinalityKey))
      case (None, None)                              => canReplaceCardinality(classIri, candidate)
      case (Some(propertyIri), Some(newCardinality)) =>
        canSetCardinality(classIri, propertyIri, newCardinality, candidate)
    }

  def canReplaceCardinality(classIri: String, candidate: ProjectCandidate): Task[CanDoResponseV2] =
    for {
      classIri <- iriConverter.asResourceClassIri(classIri).orElseFail(invalidQueryParamValue(classIriKey))
      _        <- authorizeForClass(candidate, classIri)
      result   <- cardinalityService.canReplaceCardinality(classIri.smartIri)
    } yield toCanDoResponseV2(result)

  private def toCanDoResponseV2(result: CanReplaceCardinalityCheckResult): CanDoResponseV2 = result match {
    case CanReplaceCardinalityCheckResult.Success          => CanDoResponseV2.yes
    case failure: CanReplaceCardinalityCheckResult.Failure => CanDoResponseV2.no(failure.reason)
  }

  private def authorizeForClass(candidate: ProjectCandidate, classIri: ResourceClassIri): Task[ProjectAdminGrant] =
    for {
      _          <- OntologyHelpers.checkOntologyIriForUpdate(classIri.ontologyIri)
      onto       <- ontologyRepo.findById(classIri.ontologyIri).someOrFail(NotFoundException.notfound(classIri.ontologyIri))
      projectIri <- ZIO.fromOption(onto.projectIri).orElseFail(NotFoundException.notfound(onto.ontologyIri))
      grant      <- auth.authorizeProjectAdminById(candidate, projectIri)
    } yield grant

  def canSetCardinality(
    classIri: String,
    propertyIri: String,
    cardinality: String,
    candidate: ProjectCandidate,
  ): Task[CanDoResponseV2] =
    for {
      classIri       <- iriConverter.asResourceClassIri(classIri).orElseFail(invalidQueryParamValue(classIriKey))
      _              <- authorizeForClass(candidate, classIri)
      newCardinality <- parseCardinality(cardinality).orElseFail(invalidQueryParamValue(newCardinalityKey))
      propertyIri    <- iriConverter.asPropertyIri(propertyIri).orElseFail(invalidQueryParamValue(propertyIriKey))
      result         <- cardinalityService.canSetCardinality(classIri, propertyIri, newCardinality)
      response       <- toCanDoResponseV2(result)
    } yield response

  private def parseCardinality(cardinality: String): IO[Option[Nothing], Cardinality] =
    ZIO.fromOption(Cardinality.fromString(cardinality))

  private def toCanDoResponseV2(
    result: Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type],
  ): Task[CanDoResponseV2] = result match {
    case Right(_)       => ZIO.succeed(CanDoResponseV2.yes)
    case Left(failures) =>
      for {
        reason  <- ZIO.foreach(failures)(toExternalErrorMessage).map(_.mkString(" "))
        context <- ZIO.foreach(failures)(toExternalContext)
      } yield CanDoResponseV2.no(
        reason,
        JsonLDObject(Map(s"${canSetResponsePrefix}CheckFailure" -> JsonLDArray(context))),
      )
  }

  private def toExternalErrorMessage(failure: CanSetCardinalityCheckResult.Failure): Task[String] = {
    val externalIris = getExternalIris(failure).map(_.mkString(","))
    failure match {
      case _: CanSetCardinalityCheckResult.SubclassCheckFailure =>
        externalIris.map(iris => s"${failure.reason} Please fix subclasses first: $iris.")
      case _: CanSetCardinalityCheckResult.SuperClassCheckFailure =>
        externalIris.map(iris => s"${failure.reason} Please fix super-classes first: $iris.")
      case _: CanSetCardinalityCheckResult.CurrentClassFailure =>
        externalIris.map(classIri => s"${failure.reason} Please fix cardinalities for class $classIri first.")
      case _ => externalIris.map(iris => s"${failure.reason}. Affected IRIs: $iris.")
    }
  }

  private def getExternalIris(failure: CanSetCardinalityCheckResult.Failure): Task[List[String]] =
    ZIO.foreach(failure.failureAffectedIris)(iriConverter.asExternalIri)

  private def toExternalContext(failure: CanSetCardinalityCheckResult.Failure): Task[JsonLDObject] =
    for {
      externalIrisValues <- getExternalIris(failure).map(iris => JsonLDArray(iris.map(iriValue)))
      messageKey          = getMessageKeyKey(failure)
    } yield JsonLDObject(
      Map[String, JsonLDValue](
        messageKey -> externalIrisValues,
      ),
    )

  private def getMessageKeyKey(failure: CanSetCardinalityCheckResult.Failure): String = {
    val middle = failure match {
      case CanSetCardinalityCheckResult.CurrentClassFailure(_)    => "Persistence"
      case CanSetCardinalityCheckResult.KnoraOntologyCheckFailure => "KnoraOntology"
      case CanSetCardinalityCheckResult.SubclassCheckFailure(_)   => "OntologySubclass"
      case CanSetCardinalityCheckResult.SuperClassCheckFailure(_) => "OntologySuperClass"
      case _                                                      => ""
    }
    s"$canSetResponsePrefix${middle}CheckFailed"
  }

  private def iriValue(iri: String) = JsonLDObject(Map("@id" -> JsonLDString(iri)))
}

object RestCardinalityService {
  val classIriKey: String       = "classIri"
  val propertyIriKey: String    = "propertyIri"
  val newCardinalityKey: String = "newCardinality"

  private[ontologies] val layer = ZLayer.derive[RestCardinalityService]
}
