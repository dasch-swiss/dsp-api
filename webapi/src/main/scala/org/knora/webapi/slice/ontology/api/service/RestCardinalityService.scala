/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api.service
import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

@accessible
trait RestCardinalityService {
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
      data           <- ontologyRepo.findOntologyBy(ontologyIri)
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

  def canSetCardinality(
    classIri: String,
    propertyIri: String,
    cardinality: String,
    user: UserADM
  ): Task[CanDoResponseV2] =
    for {
      classIri       <- iriConverter.asInternalIri(classIri).orElseFail(BadRequestException("Invalid classIri"))
      _              <- checkUserHasWriteAccessToOntologyOfClass(user, classIri)
      newCardinality <- parseCardinality(cardinality).orElseFail(BadRequestException(s"Unknown cardinality"))
      propertyIri    <- iriConverter.asInternalIri(propertyIri).orElseFail(BadRequestException("Invalid propertyIri"))
      result         <- cardinalityService.canSetCardinality(classIri, propertyIri, newCardinality)
    } yield CanDoResponseV2(result.isSuccess)

  private def checkUserHasWriteAccessToOntologyOfClass(user: UserADM, classIri: InternalIri): Task[Unit] = {
    val hasWriteAccess = for {
      ontologyIri    <- iriConverter.getOntologyIriFromClassIri(classIri)
      hasWriteAccess <- permissionService.hasOntologyWriteAccess(user, ontologyIri)
    } yield hasWriteAccess
    ZIO.ifZIO(hasWriteAccess)(
      onTrue = ZIO.unit,
      onFalse = ZIO.fail(ForbiddenException(s"User has no access to ontology"))
    )
  }

  private def parseCardinality(cardinality: String): IO[Option[Nothing], Cardinality] =
    ZIO.fromOption(Cardinality.fromString(cardinality))
}

object RestCardinalityService {
  val layer: ZLayer[CardinalityService with IriConverter with OntologyRepo, Nothing, RestCardinalityService] =
    ZLayer.fromFunction(RestCardinalityServiceLive.apply _)
}
