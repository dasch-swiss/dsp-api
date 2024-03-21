/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.traits.Jsonable
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User

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
  hasPermissions: Set[PermissionADM],
) extends PermissionsADMJsonProtocol {
  def toJsValue: JsValue = createAdministrativePermissionAPIRequestADMFormat.write(this)
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
  hasPermissions: Set[PermissionADM],
) extends PermissionsADMJsonProtocol {
  def toJsValue: JsValue = createDefaultObjectAccessPermissionAPIRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update the group of a permission.
 *
 * @param forGroup the new group IRI.
 */
case class ChangePermissionGroupApiRequestADM(forGroup: IRI) extends PermissionsADMJsonProtocol {

  if (forGroup.isEmpty) {
    throw BadRequestException(s"IRI of new group cannot be empty.")
  }
  Iri
    .validateAndEscapeIri(forGroup)
    .getOrElse(throw BadRequestException(s"Invalid IRI $forGroup is given."))

  def toJsValue: JsValue = changePermissionGroupApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update hasPermissions property of a permission.
 *
 * @param hasPermissions the new set of permission values.
 */
case class ChangePermissionHasPermissionsApiRequestADM(hasPermissions: Set[PermissionADM])
    extends PermissionsADMJsonProtocol {
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
  Iri
    .validateAndEscapeIri(forResourceClass)
    .getOrElse(
      throw BadRequestException(s"Invalid resource class IRI $forResourceClass is given."),
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
  Iri
    .validateAndEscapeIri(forProperty)
    .getOrElse(throw BadRequestException(s"Invalid property IRI $forProperty is given."))

  def toJsValue: JsValue = changePermissionPropertyApiRequestADMFormat.write(this)
}

/**
 * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
 */
sealed trait PermissionsResponderRequestADM extends KnoraRequestADM with RelayedMessage

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
  requestingUser: User,
) extends PermissionsResponderRequestADM {

  if (!requestingUser.isSystemUser) throw ForbiddenException("Permission data can only by queried by a SystemUser.")
}

// Administrative Permissions

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
  requestingUser: User,
  apiRequestID: UUID,
) extends PermissionsResponderRequestADM {
  PermissionIri.from(administrativePermissionIri).fold(msg => throw BadRequestException(msg), _ => ())
}

/**
 * A message that requests an administrative permission object identified by project and group.
 * A response will contain an optional [[AdministrativePermissionGetResponseADM]] object.
 *
 * @param projectIri     the project.
 * @param groupIri       the group.
 * @param requestingUser the user initiating the request.
 */
case class AdministrativePermissionForProjectGroupGetADM(projectIri: IRI, groupIri: IRI, requestingUser: User)
    extends PermissionsResponderRequestADM {
  ProjectIri.from(projectIri).getOrElse(throw BadRequestException(s"Invalid project IRI $projectIri"))

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

// Object Access Permissions

/**
 * A message that requests the object access permissions attached to a resource via the 'knora-base:hasPermissions' property.
 *
 * @param resourceIri the IRI of the resource.
 */
case class ObjectAccessPermissionsForResourceGetADM(resourceIri: IRI, requestingUser: User)
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
case class ObjectAccessPermissionsForValueGetADM(valueIri: IRI, requestingUser: User)
    extends PermissionsResponderRequestADM {

  implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  if (!stringFormatter.toSmartIri(valueIri).isKnoraValueIri) {
    throw BadRequestException(s"Invalid value IRI: $valueIri")
  }
}

// Default Object Access Permissions

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
  requestingUser: User,
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  ProjectIri.from(projectIri).getOrElse(throw BadRequestException(s"Invalid project IRI $projectIri"))

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
          "Either a group, a resource class, a property, or a combination of resource class and property must be given.",
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
  requestingUser: User,
  apiRequestID: UUID,
) extends PermissionsResponderRequestADM {
  PermissionsMessagesUtilADM.checkPermissionIri(defaultObjectAccessPermissionIri)
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
  targetUser: User,
  requestingUser: User,
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  ProjectIri.from(projectIri).getOrElse(throw BadRequestException(s"Invalid project IRI $projectIri"))

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
  targetUser: User,
  requestingUser: User,
) extends PermissionsResponderRequestADM {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  ProjectIri.from(projectIri).getOrElse(throw BadRequestException(s"Invalid project IRI $projectIri"))

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
 * A message that requests a permission (doap or ap) by its IRI.
 * A successful response will be an [[PermissionGetResponseADM]] object.
 *
 * @param permissionIri  the iri of the default object access permission object.
 * @param requestingUser the user initiation the request.
 */
case class PermissionByIriGetRequestADM(permissionIri: IRI, requestingUser: User)
    extends PermissionsResponderRequestADM {
  PermissionsMessagesUtilADM.checkPermissionIri(permissionIri)
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
    extends AdminKnoraResponseADM
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
  administrativePermissions: Seq[AdministrativePermissionADM],
) extends AdminKnoraResponseADM
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
  defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionADM],
) extends AdminKnoraResponseADM
    with PermissionsADMJsonProtocol {
  def toJsValue: JsValue = defaultObjectAccessPermissionsForProjectGetResponseADMFormat.write(this)
}

sealed trait PermissionGetResponseADM extends AdminKnoraResponseADM with PermissionsADMJsonProtocol

/**
 * Represents an answer to a request for getting a default object access permission.
 *
 * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionADM]].
 */
case class DefaultObjectAccessPermissionGetResponseADM(defaultObjectAccessPermission: DefaultObjectAccessPermissionADM)
    extends PermissionGetResponseADM() {
  def toJsValue: JsValue = defaultObjectAccessPermissionGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a request for getting an administrative permission.
 *
 * @param administrativePermission the retrieved [[AdministrativePermissionADM]].
 */
case class AdministrativePermissionGetResponseADM(administrativePermission: AdministrativePermissionADM)
    extends PermissionGetResponseADM() {
  def toJsValue: JsValue = administrativePermissionGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to [[AdministrativePermissionCreateRequestADM]].
 *
 * @param administrativePermission the newly created [[AdministrativePermissionADM]].
 */
case class AdministrativePermissionCreateResponseADM(administrativePermission: AdministrativePermissionADM)
    extends AdminKnoraResponseADM
    with PermissionsADMJsonProtocol {
  def toJsValue = administrativePermissionCreateResponseADMFormat.write(this)
}

/**
 * Represents an answer to [[DefaultObjectAccessPermissionCreateRequestADM]].
 *
 * @param defaultObjectAccessPermission the newly created [[DefaultObjectAccessPermissionADM]].
 */
case class DefaultObjectAccessPermissionCreateResponseADM(
  defaultObjectAccessPermission: DefaultObjectAccessPermissionADM,
) extends AdminKnoraResponseADM
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
    extends AdminKnoraResponseADM
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
  administrativePermissionsPerProject: Map[IRI, Set[PermissionADM]] = Map.empty[IRI, Set[PermissionADM]],
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
            Map.empty[IRI, Set[PermissionADM]], // remove administrative permission information
        )

      case PermissionProfileType.Full =>
        PermissionsDataADM(
          groupsPerProject = groupsPerProject,
          administrativePermissionsPerProject = administrativePermissionsPerProject,
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
  def hasPermissionFor(operation: OperationADM, insideProject: IRI): Boolean =
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
                PermissionADM.projectResourceCreateRestrictedPermission(resourceClassIri),
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
  hasPermissions: Set[PermissionADM],
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
  hasPermissions: Set[PermissionADM],
) extends PermissionItemADM {

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
      permissionCode = None,
    )

  def projectResourceCreateRestrictedPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectResourceCreateRestrictedPermission,
      additionalInformation = Some(restriction),
      permissionCode = None,
    )

  val ProjectAdminAllPermission: PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminAllPermission,
      additionalInformation = None,
      permissionCode = None,
    )

  val ProjectAdminGroupAllPermission: PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminGroupAllPermission,
      additionalInformation = None,
      permissionCode = None,
    )

  def projectAdminGroupRestrictedPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminGroupRestrictedPermission,
      additionalInformation = Some(restriction),
      permissionCode = None,
    )

  val ProjectAdminRightsAllPermission: PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraAdmin.ProjectAdminRightsAllPermission,
      additionalInformation = None,
      permissionCode = None,
    )

  ///////////////////////////////////////////////////////////////////////////
  // Object Access Permissions
  ///////////////////////////////////////////////////////////////////////////

  def changeRightsPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.ChangeRightsPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(8),
    )

  def deletePermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.DeletePermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(7),
    )

  def modifyPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.ModifyPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(6),
    )

  def viewPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.ViewPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(2),
    )

  def restrictedViewPermission(restriction: IRI): PermissionADM =
    PermissionADM(
      name = OntologyConstants.KnoraBase.RestrictedViewPermission,
      additionalInformation = Some(restriction),
      permissionCode = Some(1),
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
    import PermissionProfileType.*

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

  implicit val permissionInfoADMFormat: JsonFormat[PermissionInfoADM] =
    lazyFormat(jsonFormat(PermissionInfoADM.apply, "iri", "permissionType"))

  implicit val administrativePermissionADMFormat: JsonFormat[AdministrativePermissionADM] =
    lazyFormat(jsonFormat(AdministrativePermissionADM.apply, "iri", "forProject", "forGroup", "hasPermissions"))

  implicit val objectAccessPermissionADMFormat: JsonFormat[ObjectAccessPermissionADM] =
    jsonFormat(ObjectAccessPermissionADM.apply, "forResource", "forValue", "hasPermissions")

  implicit val defaultObjectAccessPermissionADMFormat: JsonFormat[DefaultObjectAccessPermissionADM] =
    lazyFormat(jsonFormat6(DefaultObjectAccessPermissionADM.apply))

  implicit val permissionsDataADMFormat: JsonFormat[PermissionsDataADM] =
    jsonFormat2(PermissionsDataADM.apply)

  implicit val permissionsForProjectGetResponseADMFormat: RootJsonFormat[PermissionsForProjectGetResponseADM] =
    jsonFormat(PermissionsForProjectGetResponseADM.apply, "permissions")

  implicit val administrativePermissionsForProjectGetResponseADMFormat
    : RootJsonFormat[AdministrativePermissionsForProjectGetResponseADM] =
    jsonFormat(AdministrativePermissionsForProjectGetResponseADM.apply, "administrative_permissions")

  implicit val defaultObjectAccessPermissionsForProjectGetResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionsForProjectGetResponseADM.apply, "default_object_access_permissions")

  implicit val administrativePermissionGetResponseADMFormat: RootJsonFormat[AdministrativePermissionGetResponseADM] =
    jsonFormat(AdministrativePermissionGetResponseADM.apply, "administrative_permission")

  implicit val defaultObjectAccessPermissionGetResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionGetResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionGetResponseADM.apply, "default_object_access_permission")

  implicit val permissionGetResponseADMFormat: RootJsonFormat[PermissionGetResponseADM] =
    new RootJsonFormat[PermissionGetResponseADM] {
      def write(response: PermissionGetResponseADM): JsValue =
        response match {
          case admin: AdministrativePermissionGetResponseADM =>
            administrativePermissionGetResponseADMFormat.write(admin)
          case default: DefaultObjectAccessPermissionGetResponseADM =>
            defaultObjectAccessPermissionGetResponseADMFormat.write(default)
        }
      def read(json: JsValue): PermissionGetResponseADM = throw new UnsupportedOperationException("Not implemented.")
    }

  implicit val createAdministrativePermissionAPIRequestADMFormat
    : RootJsonFormat[CreateAdministrativePermissionAPIRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(CreateAdministrativePermissionAPIRequestADM.apply, "id", "forProject", "forGroup", "hasPermissions"),
    ),
  )

  implicit val createDefaultObjectAccessPermissionAPIRequestADMFormat
    : RootJsonFormat[CreateDefaultObjectAccessPermissionAPIRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(
        CreateDefaultObjectAccessPermissionAPIRequestADM.apply,
        "id",
        "forProject",
        "forGroup",
        "forResourceClass",
        "forProperty",
        "hasPermissions",
      ),
    ),
  )

  implicit val administrativePermissionCreateResponseADMFormat
    : RootJsonFormat[AdministrativePermissionCreateResponseADM] = rootFormat(
    lazyFormat(jsonFormat(AdministrativePermissionCreateResponseADM.apply, "administrative_permission")),
  )

  implicit val defaultObjectAccessPermissionCreateResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionCreateResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionCreateResponseADM.apply, "default_object_access_permission")

  implicit val changePermissionGroupApiRequestADMFormat: RootJsonFormat[ChangePermissionGroupApiRequestADM] =
    jsonFormat(ChangePermissionGroupApiRequestADM.apply, "forGroup")

  implicit val changePermissionHasPermissionsApiRequestADMFormat
    : RootJsonFormat[ChangePermissionHasPermissionsApiRequestADM] =
    jsonFormat(ChangePermissionHasPermissionsApiRequestADM.apply, "hasPermissions")

  implicit val changePermissionResourceClassApiRequestADMFormat
    : RootJsonFormat[ChangePermissionResourceClassApiRequestADM] =
    jsonFormat(ChangePermissionResourceClassApiRequestADM.apply, "forResourceClass")

  implicit val changePermissionPropertyApiRequestADMFormat: RootJsonFormat[ChangePermissionPropertyApiRequestADM] =
    jsonFormat(ChangePermissionPropertyApiRequestADM.apply, "forProperty")

  implicit val permissionDeleteResponseADMFormat: RootJsonFormat[PermissionDeleteResponseADM] =
    jsonFormat(PermissionDeleteResponseADM.apply, "permissionIri", "deleted")

}
