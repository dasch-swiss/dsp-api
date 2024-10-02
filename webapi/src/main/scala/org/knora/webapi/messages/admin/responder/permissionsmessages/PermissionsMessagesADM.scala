/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import zio.Chunk
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.jsonDiscriminator
import zio.json.jsonField

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo

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
)
object CreateAdministrativePermissionAPIRequestADM {
  implicit val codec: JsonCodec[CreateAdministrativePermissionAPIRequestADM] =
    DeriveJsonCodec.gen[CreateAdministrativePermissionAPIRequestADM]
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
)
object CreateDefaultObjectAccessPermissionAPIRequestADM {
  implicit val codec: JsonCodec[CreateDefaultObjectAccessPermissionAPIRequestADM] =
    DeriveJsonCodec.gen[CreateDefaultObjectAccessPermissionAPIRequestADM]
}

/**
 * Represents an API request payload that asks the Knora API server to update the group of a permission.
 *
 * @param forGroup the new group IRI.
 */
case class ChangePermissionGroupApiRequestADM(forGroup: IRI) {

  if (forGroup.isEmpty) {
    throw BadRequestException(s"IRI of new group cannot be empty.")
  }
  Iri
    .validateAndEscapeIri(forGroup)
    .getOrElse(throw BadRequestException(s"Invalid IRI $forGroup is given."))
}
object ChangePermissionGroupApiRequestADM {
  implicit val codec: JsonCodec[ChangePermissionGroupApiRequestADM] =
    DeriveJsonCodec.gen[ChangePermissionGroupApiRequestADM]
}

/**
 * Represents an API request payload that asks the Knora API server to update hasPermissions property of a permission.
 *
 * @param hasPermissions the new set of permission values.
 */
case class ChangePermissionHasPermissionsApiRequestADM(hasPermissions: Set[PermissionADM])
object ChangePermissionHasPermissionsApiRequestADM {
  implicit val codec: JsonCodec[ChangePermissionHasPermissionsApiRequestADM] =
    DeriveJsonCodec.gen[ChangePermissionHasPermissionsApiRequestADM]
}

/**
 * Represents an API request payload that asks the Knora API server to update resourceClassIri of a doap permission.
 *
 * @param forResourceClass the new resource class IRI of the doap permission.
 */
case class ChangePermissionResourceClassApiRequestADM(forResourceClass: IRI) {
  if (forResourceClass.isEmpty) {
    throw BadRequestException(s"Resource class IRI cannot be empty.")
  }
  Iri
    .validateAndEscapeIri(forResourceClass)
    .getOrElse(
      throw BadRequestException(s"Invalid resource class IRI $forResourceClass is given."),
    )
}
object ChangePermissionResourceClassApiRequestADM {
  implicit val codec: JsonCodec[ChangePermissionResourceClassApiRequestADM] =
    DeriveJsonCodec.gen[ChangePermissionResourceClassApiRequestADM]
}

/**
 * Represents an API request payload that asks the Knora API server to update property of a doap permission.
 *
 * @param forProperty the new property IRI of the doap permission.
 */
case class ChangePermissionPropertyApiRequestADM(forProperty: IRI) {
  if (forProperty.isEmpty) {
    throw BadRequestException(s"Property IRI cannot be empty.")
  }
  Iri
    .validateAndEscapeIri(forProperty)
    .getOrElse(throw BadRequestException(s"Invalid property IRI $forProperty is given."))
}
object ChangePermissionPropertyApiRequestADM {
  implicit val codec: JsonCodec[ChangePermissionPropertyApiRequestADM] =
    DeriveJsonCodec.gen[ChangePermissionPropertyApiRequestADM]
}

/**
 * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
 */
sealed trait PermissionsResponderRequestADM extends KnoraRequestADM with RelayedMessage

// Default Object Access Permissions

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
 * A message that requests a permission (doap or ap) by its IRI.
 * A successful response will be an [[PermissionGetResponseADM]] object.
 *
 * @param permissionIri  the iri of the default object access permission object.
 * @param requestingUser the user initiation the request.
 */
case class PermissionByIriGetRequestADM(permissionIri: IRI, requestingUser: User)
    extends PermissionsResponderRequestADM {
  PermissionIri.from(permissionIri).fold(e => throw BadRequestException(e), _.value)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
 * All Permissions for project
 *
 * @param permissions the retrieved sequence of [[PermissionInfoADM]]
 */
case class PermissionsForProjectGetResponseADM(permissions: Set[PermissionInfoADM]) extends AdminKnoraResponseADM
object PermissionsForProjectGetResponseADM {
  implicit val codec: JsonCodec[PermissionsForProjectGetResponseADM] =
    DeriveJsonCodec.gen[PermissionsForProjectGetResponseADM]
}

/**
 * All administrative Permissions for project
 *
 * @param administrativePermissions the retrieved sequence of [[AdministrativePermissionADM]]
 */
case class AdministrativePermissionsForProjectGetResponseADM(
  @jsonField("administrative_permissions") administrativePermissions: Seq[AdministrativePermissionADM],
) extends AdminKnoraResponseADM
object AdministrativePermissionsForProjectGetResponseADM {
  implicit val codec: JsonCodec[AdministrativePermissionsForProjectGetResponseADM] =
    DeriveJsonCodec.gen[AdministrativePermissionsForProjectGetResponseADM]
}

/**
 * All Default Object Access Permissions for project
 *
 * @param defaultObjectAccessPermissions the retrieved sequence of [[DefaultObjectAccessPermissionADM]]
 */
case class DefaultObjectAccessPermissionsForProjectGetResponseADM(
  @jsonField("default_object_access_permissions") defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionADM],
) extends AdminKnoraResponseADM
object DefaultObjectAccessPermissionsForProjectGetResponseADM {
  implicit val codec: JsonCodec[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    DeriveJsonCodec.gen[DefaultObjectAccessPermissionsForProjectGetResponseADM]
}

@jsonDiscriminator("type") // required for zio-json, unfortunately zio json cannot infer it from the field names alone
sealed trait PermissionGetResponseADM extends AdminKnoraResponseADM
object PermissionGetResponseADM {
  implicit val codec: JsonCodec[PermissionGetResponseADM] = DeriveJsonCodec.gen[PermissionGetResponseADM]
}

/**
 * Represents an answer to a request for getting a default object access permission.
 *
 * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionADM]].
 */
case class DefaultObjectAccessPermissionGetResponseADM(
  @jsonField("default_object_access_permission") defaultObjectAccessPermission: DefaultObjectAccessPermissionADM,
) extends PermissionGetResponseADM()
object DefaultObjectAccessPermissionGetResponseADM {
  implicit val codec: JsonCodec[DefaultObjectAccessPermissionGetResponseADM] =
    DeriveJsonCodec.gen[DefaultObjectAccessPermissionGetResponseADM]
}

/**
 * Represents an answer to a request for getting an administrative permission.
 *
 * @param administrativePermission the retrieved [[AdministrativePermissionADM]].
 */
case class AdministrativePermissionGetResponseADM(
  @jsonField("administrative_permission") administrativePermission: AdministrativePermissionADM,
) extends PermissionGetResponseADM()
object AdministrativePermissionGetResponseADM {
  implicit val codec: JsonCodec[AdministrativePermissionGetResponseADM] =
    DeriveJsonCodec.gen[AdministrativePermissionGetResponseADM]
}

/**
 * Represents an answer to [[AdministrativePermissionCreateRequestADM]].
 *
 * @param administrativePermission the newly created [[AdministrativePermissionADM]].
 */
case class AdministrativePermissionCreateResponseADM(
  @jsonField("administrative_permission") administrativePermission: AdministrativePermissionADM,
) extends AdminKnoraResponseADM
object AdministrativePermissionCreateResponseADM {
  implicit val codec: JsonCodec[AdministrativePermissionCreateResponseADM] =
    DeriveJsonCodec.gen[AdministrativePermissionCreateResponseADM]
}

/**
 * Represents an answer to [[DefaultObjectAccessPermissionCreateRequestADM]].
 *
 * @param defaultObjectAccessPermission the newly created [[DefaultObjectAccessPermissionADM]].
 */
case class DefaultObjectAccessPermissionCreateResponseADM(
  @jsonField("default_object_access_permission") defaultObjectAccessPermission: DefaultObjectAccessPermissionADM,
) extends AdminKnoraResponseADM
object DefaultObjectAccessPermissionCreateResponseADM {
  implicit val codec: JsonCodec[DefaultObjectAccessPermissionCreateResponseADM] =
    DeriveJsonCodec.gen[DefaultObjectAccessPermissionCreateResponseADM]
}

/**
 * Represents default permissions for an object, formatted as the literal object of `knora-base:hasPermissions`.
 *
 * @param permissionLiteral a permission literal string.
 */
case class DefaultObjectAccessPermissionsStringResponseADM(permissionLiteral: String)
object DefaultObjectAccessPermissionsStringResponseADM {
  implicit val codec: JsonCodec[DefaultObjectAccessPermissionsStringResponseADM] =
    DeriveJsonCodec.gen[DefaultObjectAccessPermissionsStringResponseADM]
}

/**
 * Responds to deletion of a permission by returning a success message.
 *
 * @param permissionIri the IRI of the permission that is deleted.
 * @param deleted       status of delete operation.
 */
case class PermissionDeleteResponseADM(permissionIri: IRI, deleted: Boolean) extends AdminKnoraResponseADM
object PermissionDeleteResponseADM {
  implicit val codec: JsonCodec[PermissionDeleteResponseADM] = DeriveJsonCodec.gen[PermissionDeleteResponseADM]
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
    }

  /* Is the user a member of the SystemAdmin group */
  def isSystemAdmin: Boolean =
    groupsPerProject
      .getOrElse(KnoraProjectRepo.builtIn.SystemProject.id.value, List.empty[IRI])
      .contains(KnoraGroupRepo.builtIn.SystemAdmin.id.value)

  /* Is the user a member of the ProjectAdmin group in any project */
  def isProjectAdminInAnyProject(): Boolean =
    groupsPerProject.flatMap(_._2).toSeq.contains(KnoraGroupRepo.builtIn.ProjectAdmin.id.value)

  /* Is the user a member of the ProjectAdmin group */
  def isProjectAdmin(projectIri: IRI): Boolean =
    groupsPerProject.getOrElse(projectIri, List.empty[IRI]).contains(KnoraGroupRepo.builtIn.ProjectAdmin.id.value)

  /* Does the user have the 'ProjectAdminAllPermission' permission for the project */
  def hasProjectAdminAllPermissionFor(projectIri: IRI): Boolean =
    administrativePermissionsPerProject.get(projectIri) match {
      case Some(permissions) => permissions(PermissionADM.from(Permission.Administrative.ProjectAdminAll))
      case None              => false
    }

  /**
   * Given an operation, checks if the user is allowed to perform it.
   *
   * @param operation     the name of the operation.
   * @param insideProject the IRI of the project inside which the operation will be performed.
   * @return a boolean value.
   */
  def hasPermissionFor(operation: ResourceCreateOperation, insideProject: IRI): Boolean =
    if (this.isSystemAdmin) {
      /* A member of the SystemAdmin group is allowed to perform any operation */
      true
    } else {
      this.administrativePermissionsPerProject.get(insideProject) match {
        case Some(set) =>
          set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)) || set(
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateRestricted, operation.resourceClass),
          )
        case None => false
      }
    }

  /* custom equality implementation with additional debugging output */
  def canEqual(a: Any): Boolean = a.isInstanceOf[PermissionsDataADM]

  override def equals(that: Any): Boolean =
    that match {
      case that: PermissionsDataADM =>
        that.canEqual(this) &&
        this.groupsPerProject.hashCode == that.groupsPerProject.hashCode &&
        this.administrativePermissionsPerProject.hashCode == that.administrativePermissionsPerProject.hashCode
      case _ => false
    }

  def toSourceString: String =
    "PermissionDataV1( \n" +
      s"\t groupsPerProject = ${groupsPerProject.toString} \n" +
      s"\t administrativePermissionsPerProject = ${administrativePermissionsPerProject.toString} \n" +
      ")"
}

object PermissionsDataADM {
  implicit val codec: JsonCodec[PermissionsDataADM] = DeriveJsonCodec.gen[PermissionsDataADM]
}

/**
 * Represents 'knora-base:AdministrativePermission'
 *
 * @param iri            the IRI of the permission.
 * @param permissionType the type of the permission.
 */
case class PermissionInfoADM(iri: IRI, permissionType: IRI)
object PermissionInfoADM {
  implicit val codec: JsonCodec[PermissionInfoADM] = DeriveJsonCodec.gen[PermissionInfoADM]
}

abstract class PermissionItemADM

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
) extends PermissionItemADM
object ObjectAccessPermissionADM {
  implicit val codec: JsonCodec[ObjectAccessPermissionADM] = DeriveJsonCodec.gen[ObjectAccessPermissionADM]
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
    extends PermissionItemADM

object AdministrativePermissionADM {
  implicit val codec: JsonCodec[AdministrativePermissionADM] = DeriveJsonCodec.gen[AdministrativePermissionADM]
  def from(permission: AdministrativePermission): AdministrativePermissionADM =
    AdministrativePermissionADM(
      iri = permission.id.value,
      forProject = permission.forProject.value,
      forGroup = permission.forGroup.value,
      hasPermissions = permission.permissions.flatMap(PermissionADM.from).toSet,
    )
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
) extends PermissionItemADM
object DefaultObjectAccessPermissionADM {
  implicit val codec: JsonCodec[DefaultObjectAccessPermissionADM] =
    DeriveJsonCodec.gen[DefaultObjectAccessPermissionADM]
}

/**
 * Case class representing a permission.
 *
 * @param name                  the name of the permission.
 * @param additionalInformation an optional IRI (e.g., group IRI, resource class IRI).
 */
case class PermissionADM(name: String, additionalInformation: Option[IRI] = None, permissionCode: Option[Int] = None) {
  override def toString: String = name
}
object PermissionADM {
  implicit val codec: JsonCodec[PermissionADM] = DeriveJsonCodec.gen[PermissionADM]

  def from(permission: Permission): PermissionADM =
    PermissionADM(permission.token, None, codeFrom(permission))

  def from(permission: Permission, restriction: IRI): PermissionADM =
    PermissionADM(permission.token, Some(restriction), codeFrom(permission))

  private def codeFrom(permission: Permission) = permission match {
    case oa: Permission.ObjectAccess  => Some(oa.code)
    case _: Permission.Administrative => None
  }

  def from(part: AdministrativePermissionPart): Chunk[PermissionADM] =
    part match {
      case AdministrativePermissionPart.Simple(permission) => Chunk(PermissionADM.from(permission))
      case AdministrativePermissionPart.ResourceCreateRestricted(resourceClassIris) =>
        resourceClassIris.map(_.value).map(PermissionADM.from(part.permission, _))
      case AdministrativePermissionPart.ProjectAdminGroupRestricted(groupIris) =>
        groupIris.map(_.value).map(PermissionADM.from(part.permission, _))
    }
}

/* Creating a resource of a certain class */
case class ResourceCreateOperation(resourceClass: IRI)

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
