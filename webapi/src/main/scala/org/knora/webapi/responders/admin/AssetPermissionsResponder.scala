/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
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
    shortcode: Shortcode,
    filename: String,
  ): Task[PermissionCodeAndProjectRestrictedViewSettings] =
    for {
      internalFilename <- ZIO.fromEither(InternalFilename.from(filename)).mapError(e => NotFoundException(e))
      result           <- triplestoreService.query(Select(FileValuePermissionsQuery.build(internalFilename)))
      row              <- ZIO
               .fromOption(result.getFirstRow)
               .orElseFail(NotFoundException(s"No file value was found for filename $filename"))
      permissionCode = PermissionUtilADM
                         .getUserPermissionADM(
                           entityCreator = row.getRequired("creator"),
                           entityProject = row.getRequired("project"),
                           entityPermissionLiteral = row.getRequired("permissions"),
                           requestingUser = user,
                         )
                         .map(_.code)
                         .getOrElse(0)
      response <- buildResponse(shortcode, permissionCode)
    } yield response

  private def buildResponse(
    shortcode: Shortcode,
    permissionCode: Int,
  ): Task[PermissionCodeAndProjectRestrictedViewSettings] =
    knoraProjectService
      .findByShortcode(shortcode)
      .someOrFail(NotFoundException(s"No project found for shortcode ${shortcode.value}"))
      .map(project =>
        permissionCode match {
          case 1 =>
            PermissionCodeAndProjectRestrictedViewSettings(
              permissionCode,
              restrictedViewSettings = Some(ProjectRestrictedViewSettingsADM.from(project.restrictedView)),
            )
          case _ => PermissionCodeAndProjectRestrictedViewSettings(permissionCode, restrictedViewSettings = None)
        },
      )
}

object AssetPermissionsResponder {
  val layer = ZLayer.derive[AssetPermissionsResponder]
}
