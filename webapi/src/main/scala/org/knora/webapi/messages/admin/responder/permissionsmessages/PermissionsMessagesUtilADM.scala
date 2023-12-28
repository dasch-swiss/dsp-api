/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.AdministrativePermissionAbbreviations
import org.knora.webapi.messages.OntologyConstants.KnoraBase.*
import org.knora.webapi.slice.admin.domain.model.PermissionIri

/**
 * Providing helper methods.
 */
object PermissionsMessagesUtilADM {

  val PermissionTypeAndCodes: Map[String, Int] = Map(
    RestrictedViewPermission -> 1,
    ViewPermission           -> 2,
    ModifyPermission         -> 6,
    DeletePermission         -> 7,
    ChangeRightsPermission   -> 8
  )

  ////////////////////
  // Helper Methods //
  ////////////////////



  /**
   * For administrative permission we only need the name parameter of each PermissionADM given in hasPermissions collection.
   * This method, validates the content of hasPermissions collection by only keeping the values of name params.
   * @param hasPermissions       Set of the permissions.
   */
  def verifyHasPermissionsAP(hasPermissions: Set[PermissionADM]): Set[PermissionADM] = {
    val updatedPermissions = hasPermissions.map { permission =>
      if (!AdministrativePermissionAbbreviations.contains(permission.name))
        throw BadRequestException(
          s"Invalid value for name parameter of hasPermissions: ${permission.name}, it should be one of " +
            s"${AdministrativePermissionAbbreviations.toString}"
        )
      PermissionADM(
        name = permission.name,
        additionalInformation = None,
        permissionCode = None
      )
    }
    updatedPermissions
  }



  def checkPermissionIri(iri: IRI): IRI = PermissionIri.from(iri).fold(e => throw BadRequestException(e), _.value)
}
