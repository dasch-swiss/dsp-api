/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.traits.Jsonable
import spray.json._

import java.util.UUID

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
 * Represents a payload that asks the Knora API server to create a new
 * administrative permission
 *
 * @param forProject     the project for which this permission is created.
 * @param forGroup       the group for which this permission is created.
 * @param hasPermissions the set of permissions.
 */
case class CreateAdministrativePermissionAPIRequestADM(
  id: Option[IRI] = None,
  forProject: IRI,
  forGroup: IRI,
  hasPermissions: Set[PermissionADM]
) extends PermissionsADMJsonProtocol {
  implicit protected val sf: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  id match {
    case Some(iri) => sf.validatePermissionIRI(iri)
    case None      => None
  }

  def toJsValue: JsValue = createAdministrativePermissionAPIRequestADMFormat.write(this)

  sf.validateAndEscapeProjectIri(forProject, throw BadRequestException(s"Invalid project IRI $forProject"))

  if (hasPermissions.isEmpty) throw BadRequestException("Permissions needs to be supplied.")

  if (!OntologyConstants.KnoraAdmin.BuiltInGroups.contains(forGroup)) {
    sf.validateGroupIri(forGroup, throw BadRequestException(s"Invalid group IRI $forGroup"))
  }

  def prepareHasPermissions: CreateAdministrativePermissionAPIRequestADM =
    copy(
      hasPermissions = PermissionsMessagesUtilADM.verifyHasPermissionsAP(hasPermissions)
    )
}

/**
 * Represents a payload that asks the Knora API server to create a new
 * default object access permission
 *
 * @param forProject       the project
 * @param forGroup         the group
 * @param forResourceClass the resource class
 * @param forProperty      the property
 * @param hasPermissions   the permissions
 */
case class CreateDefaultObjectAccessPermissionAPIRequestADM(
  id: Option[IRI] = None,
  forProject: IRI,
  forGroup: Option[IRI] = None,
  forResourceClass: Option[IRI] = None,
  forProperty: Option[IRI] = None,
  hasPermissions: Set[PermissionADM]
) extends PermissionsADMJsonProtocol {
  implicit protected val sf: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  id match {
    case Some(iri) => sf.validatePermissionIRI(iri)
    case None      => None
  }

  def toJsValue: JsValue = createDefaultObjectAccessPermissionAPIRequestADMFormat.write(this)

  sf.validateAndEscapeProjectIri(forProject, throw BadRequestException(s"Invalid project IRI $forProject"))

  forGroup match {
    case Some(iri: IRI) =>
      if (forResourceClass.isDefined)
        throw BadRequestException("Not allowed to supply groupIri and resourceClassIri together.")
      else if (forProperty.isDefined)
        throw BadRequestException("Not allowed to supply groupIri and propertyIri together.")
      else {
        if (!OntologyConstants.KnoraAdmin.BuiltInGroups.contains(iri)) {
          sf.validateOptionalGroupIri(
            forGroup,
            throw BadRequestException(s"Invalid group IRI ${forGroup.get}")
          )
        }
      }
    case None =>
      if (forResourceClass.isEmpty && forProperty.isEmpty) {
        throw BadRequestException(
          "Either a group, a resource class, a property, or a combination of resource class and property must be given."
        )
      }
  }

  forResourceClass match {
    case Some(iri) =>
      if (!sf.toSmartIri(iri).isKnoraEntityIri) {
        throw BadRequestException(s"Invalid resource class IRI: $iri")
      }
    case None => None
  }

  forProperty match {
    case Some(iri) =>
      if (!sf.toSmartIri(iri).isKnoraEntityIri) {
        throw BadRequestException(s"Invalid property IRI: $iri")
      }
    case None => None
  }

  if (hasPermissions.isEmpty) throw BadRequestException("Permissions needs to be supplied.")

  def prepareHasPermissions: CreateDefaultObjectAccessPermissionAPIRequestADM =
    copy(
      hasPermissions = PermissionsMessagesUtilADM.verifyHasPermissionsDOAP(hasPermissions)
    )
}

/**
 * Represents an API request payload that asks the Knora API server to update the group of a permission.
 *
 * @param forGroup the new group IRI.
 */
case class ChangePermissionGroupApiRequestADM(forGroup: IRI) extends PermissionsADMJsonProtocol {
  private val stringFormatter = StringFormatter.getInstanceForConstantOntologies

  if (forGroup.isEmpty) {
    throw BadRequestException(s"IRI of new group cannot be empty.")
  }
  stringFormatter.validateAndEscapeIri(forGroup, throw BadRequestException(s"Invalid IRI $forGroup is given."))

  def toJsValue: JsValue = changePermissionGroupApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update hasPermissions property of a permission.
 *
 * @param hasPermissions the new set of permission values.
 */
case class ChangePermissionHasPermissionsApiRequestADM(hasPermissions: Set[PermissionADM])
    extends PermissionsADMJsonProtocol {
  if (hasPermissions.isEmpty) {
    throw BadRequestException(s"hasPermissions cannot be empty.")
  }

  def toJsValue: JsValue = changePermissionHasPermissionsApiRequestADMFormat.write(this)

}

/**
 * Represents an API request payload that asks the Knora API server to update resourceClassIri of a doap permission.
 *
 * @param forResourceClass the new resource class IRI of the doap permission.
 */
case class ChangePermissionResourceClassApiRequestADM(forResourceClass: IRI) extends PermissionsADMJsonProtocol {
  if (forResourceClass.isEmpty) {
    throw BadRequestException(s"Resource class IRI cannot be empty.")
  }
  private val stringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeIri(
    forResourceClass,
    throw BadRequestException(s"Invalid resource class IRI $forResourceClass is given.")
  )

  def toJsValue: JsValue = changePermissionResourceClassApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update property of a doap permission.
 *
 * @param forProperty the new property IRI of the doap permission.
 */
case class ChangePermissionPropertyApiRequestADM(forProperty: IRI) extends PermissionsADMJsonProtocol {
  if (forProperty.isEmpty) {
    throw BadRequestException(s"Property IRI cannot be empty.")
  }
  private val stringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeIri(
    forProperty,
    throw BadRequestException(s"Invalid property IRI $forProperty is given.")
  )

  def toJsValue: JsValue = changePermissionPropertyApiRequestADMFormat.write(this)
}

/**
 * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
 */
sealed trait PermissionsResponderRequestADM extends KnoraRequestADM

/**
 * A message that requests the user's [[PermissionsDataADM]].
 *
 * @param projectIris            the projects the user is part of.
 * @param groupIris              the groups the user is member of.
 * @param isInProjectAdminGroups the projects for which the user is member of the ProjectAdmin group.
 * @param isInSystemAdminGroup   the flag denoting users membership in the SystemAdmin group.
 */
case class PermissionDataGetADM(
  projectIris: Seq[IRI],
  groupIris: Seq[IRI],
  isInProjectAdminGroups: Seq[IRI],
  isInSystemAdminGroup: Boolean,
  requestingUser: UserADM
) extends PermissionsResponderRequestADM {

  if (!requestingUser.isSystemUser) throw ForbiddenException("Permission data can only by queried by a SystemUser.")
}

/**
 * A message that requests all permissions defined inside a project.
 * A successful response will be a [[PermissionsForProjectGetResponseADM]].
 *
 * @param projectIri           the project for which the permissions are queried.
 * @param requestingUser       the user initiation the request.
 * @param apiRequestID         the API request ID.
 */
case class PermissionsForProjectGetRequestADM(
  projectIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
  ) {
    // not a system or project admin
    throw ForbiddenException("Permissions can only be queried by system and project admin.")
  }
}

/**
 * A message that requests update of a permission's group.
 * A successful response will be a [[PermissionItemADM]].
 *
 * @param permissionIri                the IRI of the permission to be updated.
 * @param changePermissionGroupRequest the request to update permission's group.
 * @param requestingUser               the user initiation the request.
 * @param apiRequestID                 the API request ID.
 */
case class PermissionChangeGroupRequestADM(
  permissionIri: IRI,
  changePermissionGroupRequest: ChangePermissionGroupApiRequestADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  if (!stringFormatter.isKnoraPermissionIriStr(permissionIri)) {
    throw BadRequestException(s"Invalid permission IRI $permissionIri is given.")
  }

}

/**
 * A message that requests update of a permission's hasPermissions property.
 * A successful response will be a [[PermissionItemADM]].
 *
 * @param permissionIri                         the IRI of the permission to be updated.
 * @param changePermissionHasPermissionsRequest the request to update hasPermissions.
 * @param requestingUser                        the user initiation the request.
 * @param apiRequestID                          the API request ID.
 */
case class PermissionChangeHasPermissionsRequestADM(
  permissionIri: IRI,
  changePermissionHasPermissionsRequest: ChangePermissionHasPermissionsApiRequestADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  if (!stringFormatter.isKnoraPermissionIriStr(permissionIri)) {
    throw BadRequestException(s"Invalid permission IRI $permissionIri is given.")
  }

}

/**
 * A message that requests update of a doap permission's resource class.
 * A successful response will be a [[PermissionItemADM]].
 *
 * @param permissionIri                        the IRI of the permission to be updated.
 * @param changePermissionResourceClassRequest the request to update permission's resource class.
 * @param requestingUser                       the user initiation the request.
 * @param apiRequestID                         the API request ID.
 */
case class PermissionChangeResourceClassRequestADM(
  permissionIri: IRI,
  changePermissionResourceClassRequest: ChangePermissionResourceClassApiRequestADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  if (!stringFormatter.isKnoraPermissionIriStr(permissionIri)) {
    throw BadRequestException(s"Invalid permission IRI $permissionIri is given.")
  }
}

/**
 * A message that requests update of a doap permission's resource class.
 * A successful response will be a [[PermissionItemADM]].
 *
 * @param permissionIri                   the IRI of the permission to be updated.
 * @param changePermissionPropertyRequest the request to update permission's property.
 * @param requestingUser                  the user initiation the request.
 * @param apiRequestID                    the API request ID.
 */
case class PermissionChangePropertyRequestADM(
  permissionIri: IRI,
  changePermissionPropertyRequest: ChangePermissionPropertyApiRequestADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  if (!stringFormatter.isKnoraPermissionIriStr(permissionIri)) {
    throw BadRequestException(s"Invalid permission IRI $permissionIri is given.")
  }
}

// Administrative Permissions

/**
 * A message that requests all administrative permissions defined inside a project.
 * A successful response will be a [[AdministrativePermissionsForProjectGetResponseADM]].
 *
 * @param projectIri     the project for which the administrative permissions are queried.
 * @param requestingUser the user initiation the request.
 * @param apiRequestID   the API request ID.
 */
case class AdministrativePermissionsForProjectGetRequestADM(
  projectIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
  ) {
    // not a system or project admin
    throw ForbiddenException("Administrative permission can only be queried by system and project admin.")
  }
}

/**
 * A message that requests an administrative permission object identified through his IRI.
 * A successful response will be a [[AdministrativePermissionGetResponseADM]] object.
 *
 * @param administrativePermissionIri the iri of the administrative permission object.
 * @param requestingUser              the user initiating the request.
 * @param apiRequestID                the API request ID.
 */
case class AdministrativePermissionForIriGetRequestADM(
  administrativePermissionIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validatePermissionIri(
    administrativePermissionIri,
    throw BadRequestException(s"Invalid permission IRI $administrativePermissionIri is given.")
  )
}

/**
 * A message that requests an administrative permission object identified by project and group.
 * A response will contain an optional [[AdministrativePermissionGetResponseADM]] object.
 *
 * @param projectIri     the project.
 * @param groupIri       the group.
 * @param requestingUser the user initiating the request.
 */
case class AdministrativePermissionForProjectGroupGetADM(projectIri: IRI, groupIri: IRI, requestingUser: UserADM)
    extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
    && !requestingUser.isSystemUser
  ) {
    // not a system admin
    throw ForbiddenException("Administrative permission can only be queried by system and project admin.")
  }
}

/**
 * A message that requests an administrative permission object identified by project and group.
 * A successful response will be an [[AdministrativePermissionGetResponseADM]] object.
 *
 * @param projectIri
 * @param groupIri
 * @param requestingUser
 */
case class AdministrativePermissionForProjectGroupGetRequestADM(projectIri: IRI, groupIri: IRI, requestingUser: UserADM)
    extends PermissionsResponderRequestADM {
  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
    && !requestingUser.isSystemUser
  ) {
    // not a system admin
    throw ForbiddenException("Administrative permission can only be queried by system and project admin.")
  }
}

/**
 * Create a single [[AdministrativePermissionADM]].
 *
 * @param createRequest        the API create request payload.
 * @param requestingUser       the requesting user.
 * @param apiRequestID         the API request ID.
 */
case class AdministrativePermissionCreateRequestADM(
  createRequest: CreateAdministrativePermissionAPIRequestADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {
  // check if the requesting user is allowed to add the administrative permission
  // Allowed are SystemAdmin, ProjectAdmin and SystemUser
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(createRequest.forProject)
    && !requestingUser.isSystemUser
  ) {
    // not a system admin
    throw ForbiddenException("A new administrative permission can only be added by system or project admin.")
  }
}

// Object Access Permissions

/**
 * A message that requests the object access permissions attached to a resource via the 'knora-base:hasPermissions' property.
 *
 * @param resourceIri the IRI of the resource.
 */
case class ObjectAccessPermissionsForResourceGetADM(resourceIri: IRI, requestingUser: UserADM)
    extends PermissionsResponderRequestADM {

  implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  if (!stringFormatter.toSmartIri(resourceIri).isKnoraResourceIri) {
    throw BadRequestException(s"Invalid resource IRI: $resourceIri")
  }

}

/**
 * A message that requests the object access permissions attached to a value via the 'knora-base:hasPermissions' property.
 *
 * @param valueIri the IRI of the value.
 */
case class ObjectAccessPermissionsForValueGetADM(valueIri: IRI, requestingUser: UserADM)
    extends PermissionsResponderRequestADM {

  implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  if (!stringFormatter.toSmartIri(valueIri).isKnoraValueIri) {
    throw BadRequestException(s"Invalid value IRI: $valueIri")
  }
}

// Default Object Access Permissions

/**
 * A message that requests all default object access permissions defined inside a project.
 * A successful response will be a list of [[DefaultObjectAccessPermissionADM]].
 *
 * @param projectIri     the project for which the default object access permissions are queried.
 * @param requestingUser the user initiating this request.
 * @param apiRequestID   the API request ID.
 */
case class DefaultObjectAccessPermissionsForProjectGetRequestADM(
  projectIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
  ) {
    // not a system or project admin
    throw ForbiddenException("Default object access permissions can only be queried by system and project admin.")
  }
}

/**
 * A message that requests an object access permission identified by project and either group / resource class / property.
 * A successful response will be a [[DefaultObjectAccessPermissionGetResponseADM]].
 *
 * @param projectIri       the project.
 * @param groupIri         the group.
 * @param resourceClassIri the resource class.
 * @param propertyIri      the property.
 * @param requestingUser   the user initiating this request.
 */
case class DefaultObjectAccessPermissionGetRequestADM(
  projectIri: IRI,
  groupIri: Option[IRI] = None,
  resourceClassIri: Option[IRI] = None,
  propertyIri: Option[IRI] = None,
  requestingUser: UserADM
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
    && !requestingUser.isSystemUser
  ) {
    // not a system admin
    throw ForbiddenException("Default object access permissions can only be queried by system and project admin.")
  }

  groupIri match {
    case Some(iri: IRI) =>
      if (resourceClassIri.isDefined)
        throw BadRequestException("Not allowed to supply groupIri and resourceClassIri together.")
      else if (propertyIri.isDefined)
        throw BadRequestException("Not allowed to supply groupIri and propertyIri together.")
      else Some(iri)
    case None =>
      if (resourceClassIri.isEmpty && propertyIri.isEmpty) {
        throw BadRequestException(
          "Either a group, a resource class, a property, or a combination of resource class and property must be given."
        )
      } else None
  }

  resourceClassIri match {
    case Some(iri) =>
      if (!stringFormatter.toSmartIri(iri).isKnoraEntityIri) {
        throw BadRequestException(s"Invalid resource class IRI: $iri")
      }
    case None => None
  }

  propertyIri match {
    case Some(iri) =>
      if (!stringFormatter.toSmartIri(iri).isKnoraEntityIri) {
        throw BadRequestException(s"Invalid property IRI: $iri")
      }
    case None => None
  }
}

/**
 * A message that requests a default object access permission object identified through his IRI.
 * A successful response will be an [[DefaultObjectAccessPermissionGetResponseADM]] object.
 *
 * @param defaultObjectAccessPermissionIri the iri of the default object access permission object.
 * @param requestingUser                   the user initiation the request.
 * @param apiRequestID                     the API request ID.
 */
case class DefaultObjectAccessPermissionForIriGetRequestADM(
  defaultObjectAccessPermissionIri: IRI,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validatePermissionIri(
    defaultObjectAccessPermissionIri,
    throw BadRequestException(s"Invalid permission IRI $defaultObjectAccessPermissionIri is given.")
  )
}

/**
 * A message that requests the default object access permissions string for a resource class inside a specific project.
 * A successful response will be a [[DefaultObjectAccessPermissionsStringResponseADM]].
 *
 * @param projectIri       the project for which the default object permissions need to be retrieved.
 * @param resourceClassIri the resource class which can also carry default object access permissions.
 * @param targetUser       the user for whom we calculate the permission
 * @param requestingUser   the requesting user.
 */
case class DefaultObjectAccessPermissionsStringForResourceClassGetADM(
  projectIri: IRI,
  resourceClassIri: IRI,
  targetUser: UserADM,
  requestingUser: UserADM
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
    && !requestingUser.isSystemUser
  ) {
    // not a system admin
    throw ForbiddenException("Default object access permissions can only be queried by system and project admin.")
  }

  if (!stringFormatter.toSmartIri(resourceClassIri).isKnoraEntityIri) {
    throw BadRequestException(s"Invalid resource class IRI: $resourceClassIri")
  }

  if (targetUser.isAnonymousUser) throw BadRequestException("Anonymous Users are not allowed.")
}

/**
 * A message that requests default object access permissions for a
 * resource class / property combination inside a specific project. A successful response will be a
 * [[DefaultObjectAccessPermissionsStringResponseADM]].
 *
 * @param projectIri       the project for which the default object permissions need to be retrieved.
 * @param resourceClassIri the resource class which can also cary default object access permissions.
 * @param propertyIri      the property type which can also cary default object access permissions.
 * @param targetUser       the user for whom we calculate the permission
 * @param requestingUser   the requesting user.
 */
case class DefaultObjectAccessPermissionsStringForPropertyGetADM(
  projectIri: IRI,
  resourceClassIri: IRI,
  propertyIri: IRI,
  targetUser: UserADM,
  requestingUser: UserADM
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

  // Check user's permission for the operation
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(projectIri)
    && !requestingUser.isSystemUser
  ) {
    // not a system admin
    throw ForbiddenException("Default object access permissions can only be queried by system and project admin.")
  }

  if (!stringFormatter.toSmartIri(resourceClassIri).isKnoraEntityIri) {
    throw BadRequestException(s"Invalid resource class IRI: $resourceClassIri")
  }

  if (!stringFormatter.toSmartIri(propertyIri).isKnoraEntityIri) {
    throw BadRequestException(s"Invalid property IRI: $propertyIri")
  }

  if (targetUser.isAnonymousUser) throw BadRequestException("Anonymous Users are not allowed.")
}

/**
 * Create a single [[DefaultObjectAccessPermissionADM]].
 *
 * @param createRequest  the create request.
 * @param requestingUser the requesting user.
 * @param apiRequestID   the API request ID.
 */
case class DefaultObjectAccessPermissionCreateRequestADM(
  createRequest: CreateDefaultObjectAccessPermissionAPIRequestADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends PermissionsResponderRequestADM {
  // check if the requesting user is allowed to add the default object access permission
  // Allowed are SystemAdmin, ProjectAdmin and SystemUser
  if (
    !requestingUser.isSystemAdmin
    && !requestingUser.permissions.isProjectAdmin(createRequest.forProject)
    && !requestingUser.isSystemUser
  ) {
    // not a system admin
    throw ForbiddenException("A new default object access permission can only be added by a system admin.")
  }
}

/**
 * A message that requests a permission (doap or ap) by its IRI.
 * A successful response will be an [[PermissionGetResponseADM]] object.
 *
 * @param permissionIri  the iri of the default object access permission object.
 * @param requestingUser the user initiation the request.
 */
case class PermissionByIriGetRequestADM(permissionIri: IRI, requestingUser: UserADM)
    extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validatePermissionIri(
    permissionIri,
    throw BadRequestException(s"Invalid permission IRI $permissionIri is given.")
  )
}

/**
 * A message that requests deletion of a permission identified through its IRI.
 * A successful response will be [[PermissionDeleteResponseADM]] with deleted=true.
 *
 * @param permissionIri               the iri of the permission object.
 * @param requestingUser              the user initiating the request.
 * @param apiRequestID                the API request ID.
 */
case class PermissionDeleteRequestADM(permissionIri: IRI, requestingUser: UserADM, apiRequestID: UUID)
    extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  stringFormatter.validatePermissionIri(
    permissionIri,
    throw BadRequestException(s"Invalid permission IRI $permissionIri is given.")
  )
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

// All Permissions for project
/**
 * Represents an answer to [[PermissionsForProjectForProjectGetRequestADM]].
 *
 * @param allPermissions the retrieved sequence of [[PermissionInfoADM]]
 */
case class PermissionsForProjectGetResponseADM(allPermissions: Set[PermissionInfoADM])
    extends KnoraResponseADM
    with PermissionsADMJsonProtocol {
  def toJsValue: JsValue = permissionsForProjectGetResponseADMFormat.write(this)
}

// All administrative Permissions for project
/**
 * Represents an answer to [[AdministrativePermissionsForProjectGetRequestADM]].
 *
 * @param administrativePermissions the retrieved sequence of [[AdministrativePermissionADM]]
 */
case class AdministrativePermissionsForProjectGetResponseADM(
  administrativePermissions: Seq[AdministrativePermissionADM]
) extends KnoraResponseADM
    with PermissionsADMJsonProtocol {
  def toJsValue: JsValue = administrativePermissionsForProjectGetResponseADMFormat.write(this)
}

// All Default Object Access Permissions for project
/**
 * Represents an answer to [[DefaultObjectAccessPermissionsForProjectGetRequestADM]]
 *
 * @param defaultObjectAccessPermissions the retrieved sequence of [[DefaultObjectAccessPermissionADM]]
 */
case class DefaultObjectAccessPermissionsForProjectGetResponseADM(
  defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionADM]
) extends KnoraResponseADM
    with PermissionsADMJsonProtocol {
  def toJsValue: JsValue = defaultObjectAccessPermissionsForProjectGetResponseADMFormat.write(this)
}

abstract class PermissionGetResponseADM(permissionItem: PermissionItemADM)
    extends KnoraResponseADM
    with PermissionsADMJsonProtocol

/**
 * Represents an answer to a request for getting a default object access permission.
 *
 * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionADM]].
 */
case class DefaultObjectAccessPermissionGetResponseADM(defaultObjectAccessPermission: DefaultObjectAccessPermissionADM)
    extends PermissionGetResponseADM(defaultObjectAccessPermission) {
  def toJsValue: JsValue = defaultObjectAccessPermissionGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a request for getting an administrative permission.
 *
 * @param administrativePermission the retrieved [[AdministrativePermissionADM]].
 */
case class AdministrativePermissionGetResponseADM(administrativePermission: AdministrativePermissionADM)
    extends PermissionGetResponseADM(administrativePermission) {
  def toJsValue: JsValue = administrativePermissionGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to [[AdministrativePermissionCreateRequestADM]].
 *
 * @param administrativePermission the newly created [[AdministrativePermissionADM]].
 */
case class AdministrativePermissionCreateResponseADM(administrativePermission: AdministrativePermissionADM)
    extends KnoraResponseADM
    with PermissionsADMJsonProtocol {
  def toJsValue = administrativePermissionCreateResponseADMFormat.write(this)
}

/**
 * Represents an answer to [[DefaultObjectAccessPermissionCreateRequestADM]].
 *
 * @param defaultObjectAccessPermission the newly created [[DefaultObjectAccessPermissionADM]].
 */
case class DefaultObjectAccessPermissionCreateResponseADM(
  defaultObjectAccessPermission: DefaultObjectAccessPermissionADM
) extends KnoraResponseADM
    with PermissionsADMJsonProtocol {
  def toJsValue: JsValue = defaultObjectAccessPermissionCreateResponseADMFormat.write(this)
}

/**
 * Represents default permissions for an object, formatted as the literal object of `knora-base:hasPermissions`.
 *
 * @param permissionLiteral a permission literal string.
 */
case class DefaultObjectAccessPermissionsStringResponseADM(permissionLiteral: String)

/**
 * Responds to deletion of a permission by returning a success message.
 *
 * @param permissionIri the IRI of the permission that is deleted.
 * @param deleted       status of delete operation.
 */
case class PermissionDeleteResponseADM(permissionIri: IRI, deleted: Boolean)
    extends KnoraResponseADM
    with PermissionsADMJsonProtocol {

  def toJsValue: JsValue = permissionDeleteResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * Represents a user's permission data. The permission data object is user centric and holds permission information
 * used during permission checking administrative operations and resource creation.
 *
 * @param groupsPerProject                    the groups the user belongs to for each project.
 * @param administrativePermissionsPerProject the user's administrative permissions for each project.
 */
case class PermissionsDataADM(
  groupsPerProject: Map[IRI, Seq[IRI]] = Map.empty[IRI, Seq[IRI]],
  administrativePermissionsPerProject: Map[IRI, Set[PermissionADM]] = Map.empty[IRI, Set[PermissionADM]]
) {

  /**
   * Returns [[PermissionsDataADM]] of the requested type.
   *
   * @return a [[PermissionsDataADM]]
   */
  def ofType(permissionProfileType: PermissionProfileType): PermissionsDataADM =
    permissionProfileType match {

      case PermissionProfileType.Restricted =>
        PermissionsDataADM(
          groupsPerProject = groupsPerProject,
          administrativePermissionsPerProject =
            Map.empty[IRI, Set[PermissionADM]] // remove administrative permission information
        )

      case PermissionProfileType.Full =>
        PermissionsDataADM(
          groupsPerProject = groupsPerProject,
          administrativePermissionsPerProject = administrativePermissionsPerProject
        )

      case _ => throw BadRequestException(s"The requested userProfileType: $permissionProfileType is invalid.")
    }

  /* Is the user a member of the SystemAdmin group */
  def isSystemAdmin: Boolean =
    groupsPerProject
      .getOrElse(OntologyConstants.KnoraAdmin.SystemProject, List.empty[IRI])
      .contains(OntologyConstants.KnoraAdmin.SystemAdmin)

  /* Is the user a member of the ProjectAdmin group in any project */
  def isProjectAdminInAnyProject(): Boolean =
    groupsPerProject.flatMap(_._2).toSeq.contains(OntologyConstants.KnoraAdmin.ProjectAdmin)

  /* Is the user a member of the ProjectAdmin group */
  def isProjectAdmin(projectIri: IRI): Boolean =
    groupsPerProject.getOrElse(projectIri, List.empty[IRI]).contains(OntologyConstants.KnoraAdmin.ProjectAdmin)

  /* Does the user have the 'ProjectAdminAllPermission' permission for the project */
  def hasProjectAdminAllPermissionFor(projectIri: IRI): Boolean =
    administrativePermissionsPerProject.get(projectIri) match {
      case Some(permissions) => permissions(PermissionADM.ProjectAdminAllPermission)
      case None              => false
    }

  /**
   * Given an operation, checks if the user is allowed to perform it.
   *
   * @param operation     the name of the operation.
   * @param insideProject the IRI of the project inside which the operation will be performed.
   * @return a boolean value.
   */
  def hasPermissionFor(
    operation: OperationADM,
    insideProject: IRI,
    objectAccessPermissions: Option[Set[PermissionADM]]
  ): Boolean =
    // println(s"hasPermissionFor - administrativePermissionsPerProject: ${administrativePermissionsPerProject}, operation: $operation, insideProject: $insideProject")
    if (this.isSystemAdmin) {
      /* A member of the SystemAdmin group is allowed to perform any operation */
      // println("TRUE: A member of the SystemAdmin group is allowed to perform any operation")
      true
    } else {
      operation match {
        case ResourceCreateOperation(resourceClassIri) =>
          this.administrativePermissionsPerProject.get(insideProject) match {
            case Some(set) =>
              set(PermissionADM.ProjectResourceCreateAllPermission) || set(
                PermissionADM.projectResourceCreateRestrictedPermission(resourceClassIri)
              )
            case None => {
              // println("FALSE: No administrative permissions defined for this project.")
              false
            }
          }
      }
    }

  /* custom equality implementation with additional debugging output */
  def canEqual(a: Any): Boolean = a.isInstanceOf[PermissionsDataADM]

  override def equals(that: Any): Boolean =
    that match {
      case that: PermissionsDataADM =>
        that.canEqual(this) && {

          val gppEqual = if (this.groupsPerProject.hashCode != that.groupsPerProject.hashCode) {
            println("groupsPerProject not equal")
            println(s"this (expected): ${this.groupsPerProject}")
            println(s"that (found): ${that.groupsPerProject}")
            false
          } else {
            true
          }

          val apppEqual =
            if (
              this.administrativePermissionsPerProject.hashCode != that.administrativePermissionsPerProject.hashCode
            ) {
              println("administrativePermissionsPerProject not equal")
              println(s"this (expected): ${this.administrativePermissionsPerProject}")
              println(s"that (found): ${that.administrativePermissionsPerProject}")
              false
            } else {
              true
            }

          gppEqual && apppEqual
        }
      case _ => false
    }

  def toSourceString: String =
    "PermissionDataV1( \n" +
      s"\t groupsPerProject = ${groupsPerProject.toString} \n" +
      s"\t administrativePermissionsPerProject = ${administrativePermissionsPerProject.toString} \n" +
      ")"
}

/**
 * Represents 'knora-base:AdministrativePermission'
 *
 * @param iri            the IRI of the permission.
 * @param permissionType the type of the permission.
 */
case class PermissionInfoADM(iri: IRI, permissionType: IRI) extends Jsonable with PermissionsADMJsonProtocol {

  def toJsValue: JsValue = permissionInfoADMFormat.write(this)
}

abstract class PermissionItemADM extends Jsonable with PermissionsADMJsonProtocol

/**
 * Represents object access permissions attached to a resource OR value via the
 * 'knora-base:hasPermission' property.
 *
 * @param forResource    the IRI of the resource.
 * @param forValue       the IRI of the value.
 * @param hasPermissions the permissions.
 */
case class ObjectAccessPermissionADM(
  forResource: Option[IRI] = None,
  forValue: Option[IRI] = None,
  hasPermissions: Set[PermissionADM]
) extends PermissionItemADM {

  def toJsValue: JsValue = objectAccessPermissionADMFormat.write(this)
}

/**
 * Represents 'knora-base:AdministrativePermission'
 *
 * @param iri            the IRI of the administrative permission.
 * @param forProject     the project this permission applies to.
 * @param forGroup       the group this permission applies to.
 * @param hasPermissions the administrative permissions.
 */
case class AdministrativePermissionADM(iri: IRI, forProject: IRI, forGroup: IRI, hasPermissions: Set[PermissionADM])
    extends PermissionItemADM {

  def toJsValue: JsValue = administrativePermissionADMFormat.write(this)
}

/**
 * Represents 'knora-base:DefaultObjectAccessPermission'
 *
 * @param iri              the permission IRI.
 * @param forProject       the project.
 * @param forGroup         the group.
 * @param forResourceClass the resource class.
 * @param forProperty      the property.
 * @param hasPermissions   the permissions.
 */
case class DefaultObjectAccessPermissionADM(
  iri: IRI,
  forProject: IRI,
  forGroup: Option[IRI] = None,
  forResourceClass: Option[IRI] = None,
  forProperty: Option[IRI] = None,
  hasPermissions: Set[PermissionADM]
) extends PermissionItemADM {

  /**
   * @return a simple string representing the permission which can be used as the cache key.
   */
  def cacheKey: String =
    PermissionsMessagesUtilADM.getDefaultObjectAccessPermissionADMKey(
      forProject,
      forGroup,
      forResourceClass,
      forProperty
    )

  def toJsValue: JsValue = defaultObjectAccessPermissionADMFormat.write(this)
}

/**
 * Case class representing a permission.
 *
 * @param name                  the name of the permission.
 * @param additionalInformation an optional IRI (e.g., group IRI, resource class IRI).
 */
case class PermissionADM(name: String, additionalInformation: Option[IRI] = None, permissionCode: Option[Int] = None)
    extends Jsonable
    with PermissionsADMJsonProtocol {

  def toJsValue: JsValue = permissionADMFormat.write(this)

  override def toString: String = name
}

/**
 * The permission companion object, used to create specific permissions.
 */
object PermissionADM {

  ///////////////////////////////////////////////////////////////////////////
  // Administrative Permissions
  ///////////////////////////////////////////////////////////////////////////

  val ProjectResourceCreateAllPermission: PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectResourceCreateAllPermission,
      additionalInformation = None,
      permissionCode = None
    )

  def projectResourceCreateRestrictedPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectResourceCreateRestrictedPermission,
      additionalInformation = Some(restriction),
      permissionCode = None
    )

  val ProjectAdminAllPermission: PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminAllPermission,
      additionalInformation = None,
      permissionCode = None
    )

  val ProjectAdminGroupAllPermission: PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminGroupAllPermission,
      additionalInformation = None,
      permissionCode = None
    )

  def projectAdminGroupRestrictedPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminGroupRestrictedPermission,
      additionalInformation = Some(restriction),
      permissionCode = None
    )

  val ProjectAdminRightsAllPermission: PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminRightsAllPermission,
      additionalInformation = None,
      permissionCode = None
    )

  ///////////////////////////////////////////////////////////////////////////
  // Object Access Permissions
  ///////////////////////////////////////////////////////////////////////////

  def changeRightsPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.ChangeRightsPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(8)
    )

  def deletePermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.DeletePermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(7)
    )

  def modifyPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.ModifyPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(6)
    )

  def viewPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.ViewPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(2)
    )

  def restrictedViewPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.RestrictedViewPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(1)
    )

}

/**
 * An abstract trait representing operations for which the user needs Administrative Permissions.
 */
sealed trait OperationADM

/* Creating a resource of a certain class */
case class ResourceCreateOperation(resourceClass: IRI) extends OperationADM

/**
 * Permission data types:
 * restricted: only group memberships, without administrative and default permissions.
 * full: everything
 *
 * Used in the 'ofType' method.
 */
sealed trait PermissionProfileType
object PermissionProfileType {
  case object Restricted extends PermissionProfileType
  case object Full       extends PermissionProfileType
}

/**
 * The permission type.
 */
sealed trait PermissionType
object PermissionType {
  case object OAP extends PermissionType {
    override def toString: String = "ObjectAccessPermission"
  }
  case object AP extends PermissionType {
    override def toString: String = "AdministrativePermission"
  }
  case object DOAP extends PermissionType {
    override def toString: String = "DefaultObjectAccessPermission"
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

trait PermissionsADMJsonProtocol
    extends SprayJsonSupport
    with DefaultJsonProtocol
    with ProjectsADMJsonProtocol
    with TriplestoreJsonProtocol {

  implicit object PermissionProfileTypeFormat extends JsonFormat[PermissionProfileType] {
    import PermissionProfileType._

    /**
     * Not implemented.
     */
    def read(jsonVal: JsValue): PermissionProfileType = ???

    /**
     * Converts a [[PermissionProfileType]] into [[JsValue]] for formatting as JSON.
     *
     * @param permissionProfileType the [[PermissionProfileType]] to be converted.
     * @return a [[JsValue]].
     */
    def write(permissionProfileType: PermissionProfileType): JsValue =
      permissionProfileType match {
        case Full =>
          JsObject {
            Map("permission_profile_type" -> "full".toJson)
          }
        case Restricted =>
          JsObject {
            Map("permission_profile_type" -> "restricted".toJson)
          }
      }
  }

  implicit val permissionADMFormat: JsonFormat[PermissionADM] =
    jsonFormat(PermissionADM.apply, "name", "additionalInformation", "permissionCode")

  implicit val permissionInfoADMFormat: JsonFormat[PermissionInfoADM] = lazyFormat(
    jsonFormat(PermissionInfoADM, "iri", "permissionType")
  )

  implicit val administrativePermissionADMFormat: JsonFormat[AdministrativePermissionADM] = lazyFormat(
    jsonFormat(AdministrativePermissionADM, "iri", "forProject", "forGroup", "hasPermissions")
  )

  implicit val objectAccessPermissionADMFormat: JsonFormat[ObjectAccessPermissionADM] =
    jsonFormat(ObjectAccessPermissionADM, "forResource", "forValue", "hasPermissions")

  implicit val defaultObjectAccessPermissionADMFormat: JsonFormat[DefaultObjectAccessPermissionADM] = lazyFormat(
    jsonFormat6(DefaultObjectAccessPermissionADM)
  )

  implicit val permissionsDataADMFormat: JsonFormat[PermissionsDataADM] = jsonFormat2(PermissionsDataADM)

  implicit val permissionsForProjectGetResponseADMFormat: RootJsonFormat[PermissionsForProjectGetResponseADM] =
    jsonFormat(PermissionsForProjectGetResponseADM, "permissions")

  implicit val administrativePermissionsForProjectGetResponseADMFormat
    : RootJsonFormat[AdministrativePermissionsForProjectGetResponseADM] =
    jsonFormat(AdministrativePermissionsForProjectGetResponseADM, "administrative_permissions")

  implicit val defaultObjectAccessPermissionsForProjectGetResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionsForProjectGetResponseADM, "default_object_access_permissions")

  implicit val administrativePermissionGetResponseADMFormat: RootJsonFormat[AdministrativePermissionGetResponseADM] =
    jsonFormat(AdministrativePermissionGetResponseADM, "administrative_permission")

  implicit val defaultObjectAccessPermissionGetResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionGetResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionGetResponseADM, "default_object_access_permission")

  implicit val createAdministrativePermissionAPIRequestADMFormat
    : RootJsonFormat[CreateAdministrativePermissionAPIRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(CreateAdministrativePermissionAPIRequestADM, "id", "forProject", "forGroup", "hasPermissions")
    )
  )

  implicit val createDefaultObjectAccessPermissionAPIRequestADMFormat
    : RootJsonFormat[CreateDefaultObjectAccessPermissionAPIRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(
        CreateDefaultObjectAccessPermissionAPIRequestADM,
        "id",
        "forProject",
        "forGroup",
        "forResourceClass",
        "forProperty",
        "hasPermissions"
      )
    )
  )

  implicit val administrativePermissionCreateResponseADMFormat
    : RootJsonFormat[AdministrativePermissionCreateResponseADM] = rootFormat(
    lazyFormat(jsonFormat(AdministrativePermissionCreateResponseADM, "administrative_permission"))
  )

  implicit val defaultObjectAccessPermissionCreateResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionCreateResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionCreateResponseADM, "default_object_access_permission")

  implicit val changePermissionGroupApiRequestADMFormat: RootJsonFormat[ChangePermissionGroupApiRequestADM] =
    jsonFormat(ChangePermissionGroupApiRequestADM, "forGroup")

  implicit val changePermissionHasPermissionsApiRequestADMFormat
    : RootJsonFormat[ChangePermissionHasPermissionsApiRequestADM] =
    jsonFormat(ChangePermissionHasPermissionsApiRequestADM, "hasPermissions")

  implicit val changePermissionResourceClassApiRequestADMFormat
    : RootJsonFormat[ChangePermissionResourceClassApiRequestADM] =
    jsonFormat(ChangePermissionResourceClassApiRequestADM, "forResourceClass")

  implicit val changePermissionPropertyApiRequestADMFormat: RootJsonFormat[ChangePermissionPropertyApiRequestADM] =
    jsonFormat(ChangePermissionPropertyApiRequestADM, "forProperty")

  implicit val permissionDeleteResponseADMFormat: RootJsonFormat[PermissionDeleteResponseADM] =
    jsonFormat(PermissionDeleteResponseADM, "permissionIri", "deleted")

}
