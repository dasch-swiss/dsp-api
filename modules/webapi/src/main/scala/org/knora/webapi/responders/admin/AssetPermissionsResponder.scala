/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.repo.FileValuePermissionsQuery
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.api.admin.model.ProjectRestrictedViewSettingsADM
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * Responds to requests for information about binary representations of resources, and returns responses in Knora API
 * ADM format.
 */
final class AssetPermissionsResponder(
  val knoraProjectService: KnoraProjectService,
  val triplestoreService: TriplestoreService,
) {

  def getPermissionCodeAndProjectRestrictedViewSettings(user: User)(
    filename: InternalFilename,
  ): Task[PermissionCodeAndProjectRestrictedViewSettings] =
    for {
      result <- triplestoreService.query(Select(FileValuePermissionsQuery.build(filename)))
      row    <- ZIO
               .fromOption(result.getFirstRow)
               .orElseFail(NotFoundException(s"No file value was found for filename $filename"))
      projectIri     = row.getRequired("project", ProjectIri.from)
      permissionCode = PermissionUtilADM
                         .getUserPermissionADM(
                           entityCreator = row.getRequired("creator"),
                           entityProject = projectIri.value,
                           entityPermissionLiteral = row.getRequired("permissions"),
                           requestingUser = user,
                         )
                         .map(_.code)
                         .getOrElse(0)
      response <- buildResponse(projectIri, permissionCode)
    } yield response

  // Permission code 1 needs the project only for its restricted-view settings; it is resolved by the IRI
  // returned from the permission query, so the lookup is EntityCache-served (findById). Every other code
  // answers without any project lookup.
  private def buildResponse(
    projectIri: ProjectIri,
    permissionCode: Int,
  ): Task[PermissionCodeAndProjectRestrictedViewSettings] =
    permissionCode match {
      case 1 =>
        knoraProjectService
          .findById(projectIri)
          .someOrFail(NotFoundException(s"No project found for IRI ${projectIri.value}"))
          .map(project =>
            PermissionCodeAndProjectRestrictedViewSettings(
              permissionCode,
              restrictedViewSettings = Some(ProjectRestrictedViewSettingsADM.from(project.restrictedView)),
            ),
          )
      case _ =>
        ZIO.succeed(PermissionCodeAndProjectRestrictedViewSettings(permissionCode, restrictedViewSettings = None))
    }
}

object AssetPermissionsResponder {
  val layer = ZLayer.derive[AssetPermissionsResponder]
}
