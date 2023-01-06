package org.knora.webapi.slice.ontology.api.service
import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import zio.IO
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

case class RestCardinalityService(
  cardinalityService: CardinalityService,
  iriConverter: IriConverter,
  ontologyRepo: OntologyRepo
) {
  private val permissionService: PermissionService = PermissionService(ontologyRepo)
  private final case class PermissionService(ontologyRepo: OntologyRepo) {
    def hasOntologyWriteAccess(requestingUser: UserADM, ontologyIri: InternalIri): Task[Boolean] = {
      val userPermissions = requestingUser.permissions
      for {
        data           <- ontologyRepo.findOntologyBy(ontologyIri)
        projectIriMaybe = data.flatMap(_.ontologyMetadata.projectIri.map(_.toIri))
        hasPermission   = projectIriMaybe.exists(userPermissions.isSystemAdmin || userPermissions.isProjectAdmin(_))
      } yield hasPermission
    }
  }

  def canSetCardinality(classIri: IRI, propertyIri: IRI, cardinality: String, user: UserADM): Task[CanDoResponseV2] =
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
  val layer: URLayer[CardinalityService with IriConverter with OntologyRepo, RestCardinalityService] =
    ZLayer.fromFunction(RestCardinalityService.apply _)
}
