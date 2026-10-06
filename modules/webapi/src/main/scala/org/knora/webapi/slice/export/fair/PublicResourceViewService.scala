/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import zio.*

import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.resources.service.ReadResourcesService

enum AccessLevel {
  case FullOpen
  case Restricted
}

final case class PublicResourceView(
  resource: ReadResourceV2,
  project: Project,
  accessLevel: AccessLevel,
  fileValue: Option[ReadValueV2],
)

enum PublicResourceResult {
  case Public(view: PublicResourceView)
  case NotPublic
}

/**
 * Reads a resource as the anonymous user, whatever the credentials of the request, and classifies
 * whether and how it may be published.
 */
final case class PublicResourceViewService(private val readResources: ReadResourcesService) {

  def read(ref: ResourceLandingRef): Task[PublicResourceResult] =
    readResources
      .getResourcesWithDeletedResource(
        resourceIris = Seq(ref.resourceIri),
        versionDate = ref.version,
        targetSchema = ApiV2Complex,
        schemaOptions = Set.empty,
        requestingUser = KnoraSystemInstances.Users.AnonymousUser,
      )
      .map(seq => seq.resources.headOption)
      .catchSome { case _: ForbiddenException | _: NotFoundException =>
        ZIO.none
      }
      .flatMap {
        case None           => ZIO.succeed(PublicResourceResult.NotPublic)
        case Some(resource) =>
          ZIO
            .when(PublicResourceViewService.fileValues(resource).size > 1)(
              ZIO.logWarning(
                s"Resource ${resource.resourceIri.value} has more than one file value; publishing no file metadata",
              ),
            )
            .as(PublicResourceViewService.classify(resource, ref.version))
      }
}

object PublicResourceViewService {

  val layer: URLayer[ReadResourcesService, PublicResourceViewService] = ZLayer.derive[PublicResourceViewService]

  private[fair] def fileValues(resource: ReadResourceV2): Seq[ReadValueV2] =
    resource.values.values.flatten.filter(_.valueContent.isInstanceOf[FileValueContentV2]).toSeq

  private[fair] def classify(
    resource: ReadResourceV2,
    version: Option[VersionDate],
  ): PublicResourceResult = {
    // Deleted now means not public, even when an older version is asked for.
    val beforeCreation = version.exists(_.value.isBefore(resource.creationDate))
    if (resource.deletionInfo.isDefined || beforeCreation) PublicResourceResult.NotPublic
    else {
      // More than one file value: fail closed, the builder must not pick one.
      val fileValue = fileValues(resource) match {
        case Seq(single) => Some(single)
        case _           => None
      }
      val levels      = resource.userPermission +: fileValue.map(_.userPermission).toSeq
      val accessLevel =
        if (levels.forall(_ >= ObjectAccess.View)) AccessLevel.FullOpen else AccessLevel.Restricted
      PublicResourceResult.Public(PublicResourceView(resource, resource.projectADM, accessLevel, fileValue))
    }
  }
}
