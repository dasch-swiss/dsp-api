/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*
import zio.metrics.Metric

import dsp.errors.NotFoundException
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.AssetAccess
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.MediaKind
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.repo.FileValuePermissionsQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * Decides what a caller may receive for a binary representation of a resource: the whole policy is
 * [[AssetAccess.from]], and this responder only gathers its three inputs.
 */
final class AssetPermissionsResponder(
  val knoraProjectService: KnoraProjectService,
  val triplestoreService: TriplestoreService,
) {

  def getAssetAccess(user: User)(filename: InternalFilename): Task[AssetAccess] =
    for {
      result <- triplestoreService.query(Select(FileValuePermissionsQuery.build(filename)))
      row    <- ZIO
               .fromOption(result.getFirstRow)
               .orElseFail(NotFoundException(s"No file value was found for filename $filename"))
      projectIri = row.getRequired("project", ProjectIri.from)
      permission = PermissionUtilADM.getUserPermissionADM(
                     entityCreator = row.getRequired("creator"),
                     entityProject = projectIri.value,
                     entityPermissionLiteral = row.getRequired("permissions"),
                     requestingUser = user,
                   )
      fileValueClasses = result.getCol("fileValueClass")
      access          <- mediaKindOf(fileValueClasses) match {
                  case Some(media) => decide(projectIri, permission, media)
                  case None        => failClosed(filename, fileValueClasses)
                }
    } yield access

  // The query projects DISTINCT, so a well-formed file value yields exactly one class. A second one means the
  // data disagrees with itself about what the asset is, and the media kind is then not a fact: this is a
  // repository, so it rejects rather than picking a row.
  private def mediaKindOf(fileValueClasses: Seq[String]): Option[MediaKind] =
    fileValueClasses match {
      case Seq(fileValueClass) => MediaKind.fromFileValueClass(fileValueClass)
      case _                   => None
    }

  // Only a clamped still image consumes the project's stored setting; every other decision answers without a
  // project lookup. `usesStoredRestrictedView` is derived from the policy function, so this stays one policy.
  private def decide(
    projectIri: ProjectIri,
    permission: Option[Permission.ObjectAccess],
    media: MediaKind,
  ): Task[AssetAccess] =
    if (AssetAccess.usesStoredRestrictedView(permission, media)) {
      knoraProjectService
        .findById(projectIri)
        .someOrFail(NotFoundException(s"No project found for IRI ${projectIri.value}"))
        .map(project => AssetAccess.from(permission, media, project.restrictedView))
    } else {
      ZIO.succeed(AssetAccess.from(permission, media, None))
    }

  // A class the ontology gained without a MediaKind, and a file value that carries more than one, must both
  // withhold everything rather than fall through to a permissive default, and both must be visible: a silent
  // catch-all would surface as mysterious denials with nothing to look at.
  private def failClosed(filename: InternalFilename, fileValueClasses: Seq[String]): Task[AssetAccess] =
    ZIO.logError(
      s"No single media kind for file value classes [${fileValueClasses.mkString(", ")}] of $filename; denying access",
    ) *> AssetPermissionsResponder.unresolvedFileValueClasses.increment.as(AssetAccess.failClosed)
}

object AssetPermissionsResponder {
  private val unresolvedFileValueClasses = Metric.counter("asset_access_unresolved_file_value_class")

  val layer = ZLayer.derive[AssetPermissionsResponder]
}
