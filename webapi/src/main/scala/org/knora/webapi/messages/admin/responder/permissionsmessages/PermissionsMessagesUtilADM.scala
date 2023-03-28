/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import dsp.errors.BadRequestException
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.AdministrativePermissionAbbreviations
import org.knora.webapi.messages.OntologyConstants.KnoraBase._

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
   * Validates the parameters of the `hasPermissions` collections of a DOAP.
   *
   * @param hasPermissions       Set of the permissions.
   */
  private def validateDOAPHasPermissions(hasPermissions: Set[PermissionADM]): Unit =
    hasPermissions.foreach { permission =>
      if (permission.additionalInformation.isEmpty) {
        throw BadRequestException(s"additionalInformation of a default object access permission type cannot be empty.")
      }
      if (permission.name.nonEmpty && !EntityPermissionAbbreviations.contains(permission.name))
        throw BadRequestException(
          s"Invalid value for name parameter of hasPermissions: ${permission.name}, it should be one of " +
            s"${EntityPermissionAbbreviations.toString}"
        )
      if (permission.permissionCode.nonEmpty) {
        val code = permission.permissionCode.get
        if (!PermissionTypeAndCodes.values.toSet.contains(code)) {
          throw BadRequestException(
            s"Invalid value for permissionCode parameter of hasPermissions: $code, it should be one of " +
              s"${PermissionTypeAndCodes.values.toString}"
          )
        }
      }
      if (permission.permissionCode.isEmpty && permission.name.isEmpty) {
        throw BadRequestException(
          s"One of permission code or permission name must be provided for a default object access permission."
        )
      }
      if (permission.permissionCode.nonEmpty && permission.name.nonEmpty) {
        val code = permission.permissionCode.get
        if (PermissionTypeAndCodes(permission.name) != code) {
          throw BadRequestException(
            s"Given permission code $code and permission name ${permission.name} are not consistent."
          )
        }
      }
    }

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

  /**
   * For default object access permission, we need to make sure that the value given for the permissionCode matches
   * the value of name parameter.
   * This method, validates the content of hasPermissions collection by verifying that both permissionCode and name
   * indicate the same type of permission.
   *
   * @param hasPermissions       Set of the permissions.
   */
  def verifyHasPermissionsDOAP(hasPermissions: Set[PermissionADM]): Set[PermissionADM] = {
    validateDOAPHasPermissions(hasPermissions)
    hasPermissions.map { permission =>
      val code: Int = permission.permissionCode match {
        case None       => PermissionTypeAndCodes(permission.name)
        case Some(code) => code
      }
      val name = permission.name.isEmpty match {
        case true =>
          val nameCodeSet: Option[(String, Int)] = PermissionTypeAndCodes.find { case (_, code) =>
            code == permission.permissionCode.get
          }
          nameCodeSet.get._1
        case false => permission.name
      }
      PermissionADM(
        name = name,
        additionalInformation = permission.additionalInformation,
        permissionCode = Some(code)
      )
    }
  }
}
