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
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
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
    classIri: IRI,
    user: UserADM,
    propertyIri: Option[IRI],
    newCardinality: Option[String]
  ): Task[CanDoResponseV2] =
    (propertyIri, newCardinality) match {
      case (None, Some(_))                           => ZIO.fail(missingQueryParamValue(propertyIriKey))
      case (Some(_), None)                           => ZIO.fail(missingQueryParamValue(newCardinalityKey))
      case (None, None)                              => canReplaceCardinality(classIri, user)
      case (Some(propertyIri), Some(newCardinality)) => canSetCardinality(classIri, propertyIri, newCardinality, user)
    }

  def canReplaceCardinality(classIri: IRI, user: UserADM): Task[CanDoResponseV2]

  def canSetCardinality(
    classIri: IRI,
    propertyIri: IRI,
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

case class RestCardinalityServiceLive(
  cardinalityService: CardinalityService,
  iriConverter: IriConverter,
  ontologyRepo: OntologyRepo
) extends RestCardinalityService {

  private val permissionService: PermissionService = PermissionService(ontologyRepo)

  def canReplaceCardinality(classIri: IRI, user: UserADM): Task[CanDoResponseV2] =
    for {
      classIri <- iriConverter.asInternalIri(classIri).orElseFail(invalidQueryParamValue(classIriKey))
      _        <- checkUserHasWriteAccessToOntologyOfClass(user, classIri)
      result   <- cardinalityService.canReplaceCardinality(classIri)
    } yield toResponse(result)

  private def toResponse(result: CanReplaceCardinalityCheckResult): CanDoResponseV2 = result match {
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
    classIri: IRI,
    propertyIri: IRI,
    cardinality: String,
    user: UserADM
  ): Task[CanDoResponseV2] =
    for {
      classIri       <- iriConverter.asInternalIri(classIri).orElseFail(invalidQueryParamValue(classIriKey))
      _              <- checkUserHasWriteAccessToOntologyOfClass(user, classIri)
      newCardinality <- parseCardinality(cardinality).orElseFail(invalidQueryParamValue(newCardinalityKey))
      propertyIri    <- iriConverter.asInternalIri(propertyIri).orElseFail(invalidQueryParamValue(propertyIriKey))
      result         <- cardinalityService.canSetCardinality(classIri, propertyIri, newCardinality)
      response       <- toResponse(result)
    } yield response

  private def parseCardinality(cardinality: String): IO[Option[Nothing], Cardinality] =
    ZIO.fromOption(Cardinality.fromString(cardinality))

  private def toResponse(
    result: Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]
  ): Task[CanDoResponseV2] = result match {
    case Right(_)       => ZIO.succeed(CanDoResponseV2.yes)
    case Left(failures) => ZIO.foreach(failures)(toExternalErrorMessage).map(_.mkString(" ")).map(CanDoResponseV2.no(_))
  }

  private def toExternalErrorMessage(f: CanSetCardinalityCheckResult.Failure): Task[String] =
    f match {
      case a: CanSetCardinalityCheckResult.SubclassCheckFailure =>
        ZIO
          .foreach(a.subClasses)(iriConverter.asExternalIri)
          .map(_.mkString(","))
          .map(sc => s"${a.reason}. Please fix subclasses first: $sc.")
      case b: CanSetCardinalityCheckResult.SuperClassCheckFailure =>
        ZIO
          .foreach(b.superClasses)(iriConverter.asExternalIri)
          .map(_.mkString(","))
          .map(sc => s"${b.reason}. Please fix super-classes first: $sc.")
      case c: CanSetCardinalityCheckResult.CurrentClassFailure =>
        iriConverter
          .asExternalIri(c.currentClassIri)
          .map(classIri => s"${c.reason} Please fix cardinalities for class $classIri first.")
      case d => ZIO.succeed(d.reason)
    }
}

object RestCardinalityService {
  val classIriKey: String       = "classIri"
  val propertyIriKey: String    = "propertyIri"
  val newCardinalityKey: String = "newCardinality"
  val layer: ZLayer[CardinalityService with IriConverter with OntologyRepo, Nothing, RestCardinalityService] =
    ZLayer.fromFunction(RestCardinalityServiceLive.apply _)
}
