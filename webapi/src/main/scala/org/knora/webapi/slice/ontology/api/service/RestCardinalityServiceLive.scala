/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api.service

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import dsp.errors.BadRequestException.invalidQueryParamValue
import dsp.errors.BadRequestException.missingQueryParamValue
import dsp.errors.ForbiddenException
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.JsonLDArray
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.util.rdf.JsonLDValue
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService.classIriKey
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService.newCardinalityKey
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService.propertyIriKey
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

@accessible
trait RestCardinalityService {

  def canChangeCardinality(
    classIri: String,
    user: UserADM,
    propertyIri: Option[String],
    newCardinality: Option[String]
  ): Task[CanDoResponseV2] =
    (propertyIri, newCardinality) match {
      case (None, Some(_))                           => ZIO.fail(missingQueryParamValue(propertyIriKey))
      case (Some(_), None)                           => ZIO.fail(missingQueryParamValue(newCardinalityKey))
      case (None, None)                              => canReplaceCardinality(classIri, user)
      case (Some(propertyIri), Some(newCardinality)) => canSetCardinality(classIri, propertyIri, newCardinality, user)
    }

  def canReplaceCardinality(classIri: String, user: UserADM): Task[CanDoResponseV2]

  def canSetCardinality(
    classIri: String,
    propertyIri: String,
    cardinality: String,
    user: UserADM
  ): Task[CanDoResponseV2]
}

private final case class PermissionService(ontologyRepo: OntologyRepo) {
  def hasOntologyWriteAccess(user: UserADM, ontologyIri: InternalIri): Task[Boolean] = {
    val permissions = user.permissions
    for {
      data           <- ontologyRepo.findById(ontologyIri)
      projectIriMaybe = data.flatMap(_.ontologyMetadata.projectIri.map(_.toIri))
      hasPermission   = projectIriMaybe.exists(permissions.isSystemAdmin || permissions.isProjectAdmin(_))
    } yield hasPermission
  }
}
object RestCardinalityService {
  val classIriKey: String       = "classIri"
  val propertyIriKey: String    = "propertyIri"
  val newCardinalityKey: String = "newCardinality"
}

case class RestCardinalityServiceLive(
  cardinalityService: CardinalityService,
  iriConverter: IriConverter,
  ontologyRepo: OntologyRepo
) extends RestCardinalityService {

  private val permissionService: PermissionService = PermissionService(ontologyRepo)
  private val canSetResponsePrefix: String         = s"${KnoraApiV2PrefixExpansion}canSetCardinality"

  def canReplaceCardinality(classIri: String, user: UserADM): Task[CanDoResponseV2] =
    for {
      classIri <- iriConverter.asInternalIri(classIri).orElseFail(invalidQueryParamValue(classIriKey))
      _        <- checkUserHasWriteAccessToOntologyOfClass(user, classIri)
      result   <- cardinalityService.canReplaceCardinality(classIri)
    } yield toCanDoResponseV2(result)

  private def toCanDoResponseV2(result: CanReplaceCardinalityCheckResult): CanDoResponseV2 = result match {
    case CanReplaceCardinalityCheckResult.Success          => CanDoResponseV2.yes
    case failure: CanReplaceCardinalityCheckResult.Failure => CanDoResponseV2.no(failure.reason)
  }

  private def checkUserHasWriteAccessToOntologyOfClass(user: UserADM, classIri: InternalIri): Task[Unit] = {
    val hasWriteAccess = for {
      ontologyIri    <- iriConverter.getOntologyIriFromClassIri(classIri)
      hasWriteAccess <- permissionService.hasOntologyWriteAccess(user, ontologyIri)
    } yield hasWriteAccess
    ZIO.ifZIO(hasWriteAccess)(
      onTrue = ZIO.unit,
      onFalse = ZIO.fail(ForbiddenException(s"User has no write access to ontology"))
    )
  }

  def canSetCardinality(
    classIri: String,
    propertyIri: String,
    cardinality: String,
    user: UserADM
  ): Task[CanDoResponseV2] =
    for {
      classIri       <- iriConverter.asInternalIri(classIri).orElseFail(invalidQueryParamValue(classIriKey))
      _              <- checkUserHasWriteAccessToOntologyOfClass(user, classIri)
      newCardinality <- parseCardinality(cardinality).orElseFail(invalidQueryParamValue(newCardinalityKey))
      propertyIri    <- iriConverter.asInternalIri(propertyIri).orElseFail(invalidQueryParamValue(propertyIriKey))
      result         <- cardinalityService.canSetCardinality(classIri, propertyIri, newCardinality)
      response       <- toCanDoResponseV2(result)
    } yield response

  private def parseCardinality(cardinality: String): IO[Option[Nothing], Cardinality] =
    ZIO.fromOption(Cardinality.fromString(cardinality))

  private def toCanDoResponseV2(
    result: Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]
  ): Task[CanDoResponseV2] = result match {
    case Right(_) => ZIO.succeed(CanDoResponseV2.yes)
    case Left(failures) =>
      for {
        reason  <- ZIO.foreach(failures)(toExternalErrorMessage).map(_.mkString(" "))
        context <- ZIO.foreach(failures)(toExternalContext)
      } yield CanDoResponseV2.no(
        reason,
        JsonLDObject(Map(s"${canSetResponsePrefix}CheckFailure" -> JsonLDArray(context)))
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
        messageKey -> externalIrisValues
      )
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

object RestCardinalityServiceLive {
  val layer: ZLayer[CardinalityService with IriConverter with OntologyRepo, Nothing, RestCardinalityService] =
    ZLayer.fromFunction(RestCardinalityServiceLive.apply _)
}
