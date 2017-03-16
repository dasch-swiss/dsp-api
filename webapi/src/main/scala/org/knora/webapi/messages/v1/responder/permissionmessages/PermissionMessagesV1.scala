/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v1.responder.permissionmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import net.sf.saxon.functions.ConstantFunction.True
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionDataType.PermissionProfileType
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectV1JsonProtocol
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.util.MessageUtil
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
  */
sealed trait PermissionsResponderRequestV1 extends KnoraRequestV1

/**
  * A message that requests the user's [[PermissionDataV1]].
  *
  * @param projectIris            the projects the user is part of.
  * @param groupIris              the groups the user is member of.
  * @param isInProjectAdminGroups the projects for which the user is member of the ProjectAdmin group.
  * @param isInSystemAdminGroup   the flag denoting users membership in the SystemAdmin group.
  */
case class PermissionDataGetV1(projectIris: Seq[IRI],
                               groupIris: Seq[IRI],
                               isInProjectAdminGroups: Seq[IRI],
                               isInSystemAdminGroup: Boolean
                              ) extends PermissionsResponderRequestV1

/**
  * A message that requests the creation of permissions (administrative and default) for a certain project
  * based on a predefined template. These permissions can be applied to a newly created or an exesting project.
  * In the case of an existing project, this operation behaves destructive, in the sense that all existing permissions
  * attached to a project are deleted, before any new permissions are created.
  *
  * @param projectIri          the IRI of the project.
  * @param permissionsTemplate the permissions template.
  */
//case class TemplatePermissionsCreateRequestV1(projectIri: IRI, permissionsTemplate: PermissionsTemplate, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1


// Administrative Permissions

/**
  * A message that requests all administrative permissions defined inside a project.
  * A successful response will contain a list of [[AdministrativePermissionV1]].
  *
  * @param projectIri    the project for which the administrative permissions are queried.
  * @param userProfileV1 the user initiation the request.
  */
case class AdministrativePermissionsForProjectGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an administrative permission object identified through his IRI.
  * A successful response will contain an [[AdministrativePermissionV1]] object.
  *
  * @param administrativePermissionIri the iri of the administrative permission object.
  * @param userProfileV1               the user initiation the request.
  */
case class AdministrativePermissionForIriGetRequestV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an administrative permission object identified by project and group.
  * A response will contain an optional [[AdministrativePermissionV1]] object.
  *
  * @param projectIri the project.
  * @param groupIri   the group.
  */
case class AdministrativePermissionForProjectGroupGetV1(projectIri: IRI, groupIri: IRI) extends PermissionsResponderRequestV1

/**
  * A message that requests an administrative permission object identified by project and group.
  * A successful response will be an [[AdministrativePermissionForProjectGroupGetResponseV1]] object.
  *
  * @param projectIri
  * @param groupIri
  * @param userProfileV1
  */
case class AdministrativePermissionForProjectGroupGetRequestV1(projectIri: IRI, groupIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1


/**
  * Create a single [[AdministrativePermissionV1]].
  *
  * @param newAdministrativePermissionV1
  */
case class AdministrativePermissionCreateRequestV1(newAdministrativePermissionV1: NewAdministrativePermissionV1, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * Delete a single [[AdministrativePermissionV1]]
  *
  * @param administrativePermissionIri
  */
case class AdministrativePermissionDeleteRequestV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * Update a single [[AdministrativePermissionV1]]
  *
  * @param userProfileV1
  */
case class AdministrativePermissionUpdateRequestV1(userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1


// Object Access Permissions

/**
  * A message that requests the object access permissions attached to a resource via the 'knora-base:hasPermissions' property.
  *
  * @param resourceIri the IRI of the resource.
  */
case class ObjectAccessPermissionsForResourceGetV1(resourceIri: IRI) extends PermissionsResponderRequestV1

/**
  * A message that requests the object access permissions attached to a value via the 'knora-base:hasPermissions' property.
  *
  * @param valueIri the IRI of the value.
  */
case class ObjectAccessPermissionsForValueGetV1(valueIri: IRI) extends PermissionsResponderRequestV1


// Default Object Access Permissions

/**
  * A message that requests all default object access permissions defined inside a project.
  * A successful response will be a list of [[DefaultObjectAccessPermissionV1]].
  *
  * @param projectIri    the project for which the default object access permissions are queried.
  * @param userProfileV1 the user initiating this request.
  */
case class DefaultObjectAccessPermissionsForProjectGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an object access permission identified by project and either group / resource class / property.
  * A successful response will be a [[DefaultObjectAccessPermissionV1]].
  *
  * @param projectIRI       the project.
  * @param groupIRI         the group.
  * @param resourceClassIRI the resource class.
  * @param propertyIRI      the property.
  */
case class DefaultObjectAccessPermissionGetV1(projectIRI: IRI, groupIRI: Option[IRI], resourceClassIRI: Option[IRI], propertyIRI: Option[IRI]) extends PermissionsResponderRequestV1

/**
  * A message that requests an object access permission identified by project and either group / resource class / property.
  * A successful response will be a [[DefaultObjectAccessPermissionGetResponseV1]].
  *
  * @param projectIRI       the project.
  * @param groupIRI         the group.
  * @param resourceClassIRI the resource class.
  * @param propertyIRI      the property.
  * @param userProfile      the user initiating this request.
  */
case class DefaultObjectAccessPermissionGetRequestV1(projectIRI: IRI, groupIRI: Option[IRI], resourceClassIRI: Option[IRI], propertyIRI: Option[IRI], userProfile: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests a default object access permission object identified through his IRI.
  * A successful response will be an [[DefaultObjectAccessPermissionV1]] object.
  *
  * @param defaultObjectAccessPermissionIri the iri of the default object access permission object.
  * @param userProfileV1                    the user initiation the request.
  */
case class DefaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1


/**
  * A message that requests the default object access permissions string for a resource class inside a specific project. A successful response will be a
  * [[DefaultObjectAccessPermissionsStringResponseV1]].
  *
  * @param projectIri       the project for which the default object permissions need to be retrieved.
  * @param resourceClassIri the resource class which can also cary default object access permissions.
  */
case class DefaultObjectAccessPermissionsStringForResourceClassGetV1(projectIri: IRI, resourceClassIri: IRI, permissionData: PermissionDataV1) extends PermissionsResponderRequestV1

/**
  * A message that requests default object access permissions for a resource class inside a specific project. A successful response will be a
  * [[DefaultObjectAccessPermissionsStringResponseV1]].
  *
  * @param projectIri  the project for which the default object permissions need to be retrieved.
  * @param propertyIri the property type which can also cary default object access permissions.
  */
case class DefaultObjectAccessPermissionsStringForPropertyGetV1(projectIri: IRI, propertyIri: IRI, permissionData: PermissionDataV1) extends PermissionsResponderRequestV1

/**
  * Create a single [[DefaultObjectAccessPermissionV1]].
  *
  * @param newDefaultObjectAccessPermissionV1
  * @param userProfileV1
  */
case class DefaultObjectAccessPermissionCreateRequestV1(newDefaultObjectAccessPermissionV1: NewDefaultObjectAccessPermissionV1, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * Delete a single [[DefaultObjectAccessPermissionV1]]
  *
  * @param defaultObjectAccessPermissionIri
  */
case class DefaultObjectAccessPermissionDeleteRequestV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * Update a single [[DefaultObjectAccessPermissionV1]].
  *
  * @param userProfileV1
  */
case class DefaultObjectAccessPermissionUpdateRequestV1(userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

// Administrative Permissions

/**
  * Represents an answer to [[AdministrativePermissionsForProjectGetRequestV1]].
  *
  * @param administrativePermissions the retrieved sequence of [[AdministrativePermissionV1]]
  */
case class AdministrativePermissionsForProjectGetResponseV1(administrativePermissions: Seq[AdministrativePermissionV1]) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionsForProjectGetResponseV1Format.write(this)
}

/**
  * Represents an anser to [[AdministrativePermissionForIriGetRequestV1]].
  *
  * @param administrativePermission the retrieved [[AdministrativePermissionV1]].
  */
case class AdministrativePermissionForIriGetResponseV1(administrativePermission: AdministrativePermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionForIriGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[AdministrativePermissionForProjectGroupGetRequestV1]]
  *
  * @param administrativePermission the retrieved [[AdministrativePermissionV1]]
  */
case class AdministrativePermissionForProjectGroupGetResponseV1(administrativePermission: AdministrativePermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionForProjectGroupGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[AdministrativePermissionCreateRequestV1]].
  *
  * @param administrativePermission the newly created [[AdministrativePermissionV1]].
  */
case class AdministrativePermissionCreateResponseV1(administrativePermission: AdministrativePermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionCreateResponseV1Format.write(this)
}

// Default Object Access Permissions

/**
  * Represents an answer to [[DefaultObjectAccessPermissionsForProjectGetRequestV1]]
  *
  * @param defaultObjectAccessPermissions the retrieved sequence of [[DefaultObjectAccessPermissionV1]]
  */
case class DefaultObjectAccessPermissionsForProjectGetResponseV1(defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionV1]) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = defaultObjectAccessPermissionsForProjectGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[DefaultObjectAccessPermissionGetRequestV1]].
  *
  * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionV1]].
  */
case class DefaultObjectAccessPermissionGetResponseV1(defaultObjectAccessPermission: DefaultObjectAccessPermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = defaultObjectAccessPermissionForProjectGroupGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[DefaultObjectAccessPermissionForIriGetRequestV1]].
  *
  * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionV1]].
  */
case class DefaultObjectAccessPermissionForIriGetResponseV1(defaultObjectAccessPermission: DefaultObjectAccessPermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = defaultObjectAccessPermissionForIriGetResponseV1Format.write(this)
}

/**
  * Represents default permissions for an object, formatted as the literal object of `knora-base:hasPermissions`.
  *
  * @param permissionLiteral a permission literal string.
  */
case class DefaultObjectAccessPermissionsStringResponseV1(permissionLiteral: String)

/*
/**
  * Represents an answer to a [[TemplatePermissionsCreateRequestV1]].
  * @param success
  * @param msg
  * @param administrativePermissions
  * @param defaultObjectAccessPermissions
  */

case class TemplatePermissionsCreateResponseV1(success: Boolean,
                                               msg: String,
                                               administrativePermissions: Seq[AdministrativePermissionV1],
                                               defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionV1]
                                              ) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = templatePermissionsCreateResponseV1Format.write(this)
}


/**
  * Represents an answer to an administrative permission creating/modifying/deletion operation.
  * @param success
  * @param operationType
  * @param administrativePermissionV1
  * @param msg
  */

case class AdministrativePermissionOperationResponseV1(success: Boolean,
                                                       operationType: PermissionOperation,
                                                       administrativePermissionV1:
                                                       Option[AdministrativePermissionV1],
                                                       msg: String) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionOperationResponseV1Format.write(this)
}

/**
  * Represents an answer to a default object access permission creating/modifying/deletion operation.
  * @param success
  * @param operationType
  * @param defaultObjectAccessPermissionV1
  * @param msg
  */
case class DefaultObjectAccessPermissionOperationResponseV1(success: Boolean,
                                                            operationType: PermissionOperation,
                                                            defaultObjectAccessPermissionV1: Option[DefaultObjectAccessPermissionV1],
                                                            msg: String) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = defaultObjectAccessPermissionOperationResponseV1Format.write(this)
}
*/

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


/**
  * Represents a user's permission data. The permission data object is user centric and holds permission information
  * used during permission checking administrative operations and resource creation.
  *
  * @param groupsPerProject                         the groups the user belongs to for each project.
  * @param administrativePermissionsPerProject      the user's administrative permissions for each project.
  */
case class PermissionDataV1(groupsPerProject: Map[IRI, List[IRI]] = Map.empty[IRI, List[IRI]],
                            administrativePermissionsPerProject: Map[IRI, Set[PermissionV1]] = Map.empty[IRI, Set[PermissionV1]],
                            anonymousUser: Boolean
                           ) {

    /**
      * Returns [[PermissionDataV1]] of the requested type.
      *
      * @return a [[PermissionDataV1]]
      */
    def ofType(permissionProfileType: PermissionProfileType): PermissionDataV1 = {
        permissionProfileType match {

            case PermissionDataType.RESTRICTED => {

                PermissionDataV1(
                    groupsPerProject = groupsPerProject,
                    administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionV1]], // remove administrative permission information
                    anonymousUser = anonymousUser
                )
            }
            case PermissionDataType.FULL => {

                PermissionDataV1(
                    groupsPerProject = groupsPerProject,
                    administrativePermissionsPerProject = administrativePermissionsPerProject,
                    anonymousUser = anonymousUser
                )
            }
            case _ => throw BadRequestException(s"The requested userProfileType: $permissionProfileType is invalid.")
        }
    }

    /* Is the user a member of the SystemAdmin group */
    def isSystemAdmin: Boolean = {
        groupsPerProject.getOrElse(OntologyConstants.KnoraBase.SystemProject, List.empty[IRI]).contains(OntologyConstants.KnoraBase.SystemAdmin)
    }

    /* Does the user have the 'ProjectAdminAllPermission' permission for the project */
    def hasProjectAdminAllPermissionFor(projectIri: IRI): Boolean = {
        administrativePermissionsPerProject.get(projectIri) match {
            case Some(permissions) => {
                permissions(PermissionV1.ProjectAdminAllPermission)
            }
            case None => false
        }
    }


    /**
      * Given an operation, checks if the user is allowed to perform it.
      *
      * @param operation     the name of the operation.
      * @param insideProject the IRI of the project inside which the operation will be performed.
      * @return a boolean value.
      */
    def hasPermissionFor(operation: OperationV1, insideProject: IRI, objectAccessPermissions: Option[Set[PermissionV1]]): Boolean = {

        //println(s"hasPermissionFor - administrativePermissionsPerProject: ${administrativePermissionsPerProject}, operation: $operation, insideProject: $insideProject")

        if (this.isSystemAdmin) {
            /* A member of the SystemAdmin group is allowed to perform any operation */
            // println("TRUE: A member of the SystemAdmin group is allowed to perform any operation")
            true
        } else {
            operation match {
                case ResourceCreateOperation(resourceClassIri) => {
                    this.administrativePermissionsPerProject.get(insideProject) match {
                        case Some(set) => {
                            set(PermissionV1.ProjectResourceCreateAllPermission) || set(PermissionV1.projectResourceCreateRestrictedPermission(resourceClassIri))
                        }
                        case None => {
                            // println("FALSE: No administrative permissions defined for this project.")
                            false
                        }
                    }
                }
            }
        }

    }

    /* custom equality implementation with additional debugging output */
    def canEqual(a: Any) = a.isInstanceOf[PermissionDataV1]

    override def equals(that: Any): Boolean =
        that match {
            case that: PermissionDataV1 => that.canEqual(this) && {

                val gppEqual = if (this.groupsPerProject.hashCode != that.groupsPerProject.hashCode) {
                    println("groupsPerProject not equal")
                    println(s"this (expected): ${this.groupsPerProject}")
                    println(s"that (found): ${that.groupsPerProject}")
                    false
                } else {
                    true
                }

                val apppEqual = if (this.administrativePermissionsPerProject.hashCode != that.administrativePermissionsPerProject.hashCode) {
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

    def toSourceString: String = {
        "PermissionDataV1( \n" +
            s"\t groupsPerProject = ${MessageUtil.toSource(groupsPerProject)} \n" +
            s"\t administrativePermissionsPerProject = ${MessageUtil.toSource(administrativePermissionsPerProject)} \n" +
            ")"
    }
}


/**
  * Represents 'knora-base:AdministrativePermission'
  *
  * @param iri            the IRI of the administrative permission.
  * @param forProject     the project this permission applies to.
  * @param forGroup       the group this permission applies to.
  * @param hasPermissions the administrative permissions.
  */
case class AdministrativePermissionV1(iri: IRI, forProject: IRI, forGroup: IRI, hasPermissions: Set[PermissionV1]) extends Jsonable with PermissionV1JsonProtocol {

    def toJsValue = administrativePermissionV1Format.write(this)
}

/**
  * Represents information needed during administrative permission creation / change.
  *
  * @param iri
  * @param forProject
  * @param forGroup
  * @param hasOldPermissions
  * @param hasNewPermissions
  */
case class NewAdministrativePermissionV1(iri: IRI,
                                         forProject: IRI,
                                         forGroup: IRI,
                                         hasOldPermissions: Set[PermissionV1],
                                         hasNewPermissions: Set[PermissionV1])

/**
  * Represents object access permissions attached to a resource OR value via the
  * 'knora-base:hasPermission' property.
  *
  * @param forResource    the IRI of the resource.
  * @param forValue       the IRI of the value.
  * @param hasPermissions the permissions.
  */
case class ObjectAccessPermissionV1(forResource: Option[IRI], forValue: Option[IRI], hasPermissions: Set[PermissionV1]) extends Jsonable with PermissionV1JsonProtocol {

    def toJsValue = objectAccessPermissionV1Format.write(this)
}

/**
  * Represents information needed during object access permission creation / change requests.
  *
  * @param forResource
  * @param forValue
  * @param oldHasPermissions
  * @param newHasPermissions
  */
case class NewObjectAccessPermissionV1(forResource: Option[IRI],
                                       forValue: Option[IRI],
                                       oldHasPermissions: Set[PermissionV1],
                                       newHasPermissions: Set[PermissionV1])

/**
  * Represents 'knora-base:DefaultObjectAccessPermission'
  *
  * @param iri
  * @param forProject
  * @param forGroup
  * @param forResourceClass
  * @param forProperty
  * @param hasPermissions
  */
case class DefaultObjectAccessPermissionV1(iri: IRI, forProject: IRI, forGroup: Option[IRI], forResourceClass: Option[IRI], forProperty: Option[IRI], hasPermissions: Set[PermissionV1])

/**
  * Represents information needed during default object access permission creation.
  *
  * @param iri
  * @param forProject
  * @param forGroup
  * @param forResourceClass
  * @param forProperty
  * @param hasPermissions
  */
case class NewDefaultObjectAccessPermissionV1(iri: IRI,
                                              forProject: IRI,
                                              forGroup: IRI,
                                              forResourceClass: IRI,
                                              forProperty: IRI,
                                              hasPermissions: Set[PermissionV1])

/**
  * Case class representing a permission.
  *
  * @param name                  the name of the permission.
  * @param additionalInformation an optional IRI (e.g., group IRI, resource class IRI).
  */
case class PermissionV1(name: String,
                        additionalInformation: Option[IRI],
                        v1Code: Option[Int]
                       ) extends Jsonable with PermissionV1JsonProtocol {

    def toJsValue = permissionV1Format.write(this)
}

/**
  * The permission companion object, used to create specific permissions.
  */
object PermissionV1 {

    ///////////////////////////////////////////////////////////////////////////
    // Administrative Permissions
    ///////////////////////////////////////////////////////////////////////////

    val ProjectResourceCreateAllPermission: PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission,
            additionalInformation = None,
            v1Code = None
        )
    }

    def projectResourceCreateRestrictedPermission(restriction: IRI): PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission,
            additionalInformation = Some(restriction),
            v1Code = None
        )
    }

    val ProjectAdminAllPermission: PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminAllPermission,
            additionalInformation = None,
            v1Code = None
        )
    }

    val ProjectAdminGroupAllPermission: PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminGroupAllPermission,
            additionalInformation = None,
            v1Code = None
        )
    }

    def projectAdminGroupRestrictedPermission(restriction: IRI): PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission,
            additionalInformation = Some(restriction),
            v1Code = None
        )
    }

    val ProjectAdminRightsAllPermission: PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminRightsAllPermission,
            additionalInformation = None,
            v1Code = None
        )
    }

    val ProjectAdminOntologyAllPermission: PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminOntologyAllPermission,
            additionalInformation = None,
            v1Code = None
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // Object Access Permissions
    ///////////////////////////////////////////////////////////////////////////

    def changeRightsPermission(restriction: IRI): PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ChangeRightsPermission,
            additionalInformation = Some(restriction),
            v1Code = Some(8)
        )
    }

    def deletePermission(restriction: IRI): PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(restriction),
            v1Code = Some(7)
        )
    }

    def modifyPermission(restriction: IRI): PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ModifyPermission,
            additionalInformation = Some(restriction),
            v1Code = Some(6)
        )
    }

    def viewPermission(restriction: IRI): PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ViewPermission,
            additionalInformation = Some(restriction),
            v1Code = Some(2)
        )
    }

    def restrictedViewPermission(restriction: IRI): PermissionV1 = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.RestrictedViewPermission,
            additionalInformation = Some(restriction),
            v1Code = Some(1)
        )
    }
}

/**
  * An abstract trait representing operations for which the user needs Administrative Permissions.
  */
sealed trait OperationV1

/* Creating a resource of a certain class */
case class ResourceCreateOperation(resourceClass: IRI) extends OperationV1


/**
  * Permission data types:
  * restricted: only group memberships, without administrative and default permissions.
  * full: everything
  *
  * Used in the 'ofType' method.
  */
object PermissionDataType extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type PermissionProfileType = Value

    val RESTRICTED = Value(0, "restricted")
    // only group memberships
    val FULL = Value(1, "full") // everything

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Permission profile type not supported: $name")
        }
    }
}

/**
  * The permission type.
  */
object PermissionType extends Enumeration {

    type PermissionType = Value

    val OAP = Value(0, "ObjectAccessPermission")
    val AP = Value(1, "AdministrativePermission")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting


trait PermissionV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions with ProjectV1JsonProtocol {

    implicit object PermissionProfileTypeFormat extends JsonFormat[PermissionProfileType] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[PermissionDataType]] into [[JsValue]] for formatting as JSON.
          *
          * @param permissionProfileType the [[PermissionDataType]] to be converted.
          * @return a [[JsValue]].
          */
        def write(permissionProfileType: PermissionDataType.Value): JsValue = {
            JsObject(Map("permission_profile_type" -> permissionProfileType.toString.toJson))
        }
    }

    implicit val permissionV1Format: JsonFormat[PermissionV1] = jsonFormat(PermissionV1.apply, "name", "additionalInformation", "v1Code")
    // apply needed because we have an companion object of a case class
    implicit val administrativePermissionV1Format: JsonFormat[AdministrativePermissionV1] = jsonFormat(AdministrativePermissionV1, "iri", "forProject", "forGroup", "hasPermissions")
    implicit val objectAccessPermissionV1Format: JsonFormat[ObjectAccessPermissionV1] = jsonFormat(ObjectAccessPermissionV1, "forResource", "forValue", "hasPermissions")
    implicit val defaultObjectAccessPermissionV1Format: JsonFormat[DefaultObjectAccessPermissionV1] = jsonFormat6(DefaultObjectAccessPermissionV1)
    implicit val permissionDataV1Format: JsonFormat[PermissionDataV1] = jsonFormat3(PermissionDataV1)
    //implicit val templatePermissionsCreateResponseV1Format: RootJsonFormat[TemplatePermissionsCreateResponseV1] = jsonFormat4(TemplatePermissionsCreateResponseV1)
    //implicit val administrativePermissionOperationResponseV1Format: RootJsonFormat[AdministrativePermissionOperationResponseV1] = jsonFormat4(AdministrativePermissionOperationResponseV1)
    //implicit val defaultObjectAccessPermissionOperationResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionOperationResponseV1] = jsonFormat4(DefaultObjectAccessPermissionOperationResponseV1)
    implicit val administrativePermissionsForProjectGetResponseV1Format: RootJsonFormat[AdministrativePermissionsForProjectGetResponseV1] = jsonFormat(AdministrativePermissionsForProjectGetResponseV1, "administrative_permissions")
    implicit val administrativePermissionForIriGetResponseV1Format: RootJsonFormat[AdministrativePermissionForIriGetResponseV1] = jsonFormat(AdministrativePermissionForIriGetResponseV1, "administrative_permission")
    implicit val administrativePermissionForProjectGroupGetResponseV1Format: RootJsonFormat[AdministrativePermissionForProjectGroupGetResponseV1] = jsonFormat(AdministrativePermissionForProjectGroupGetResponseV1, "administrative_permission")
    implicit val administrativePermissionCreateResponseV1Format: RootJsonFormat[AdministrativePermissionCreateResponseV1] = jsonFormat(AdministrativePermissionCreateResponseV1, "administrative_permission")
    implicit val defaultObjectAccessPermissionsForProjectGetResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionsForProjectGetResponseV1] = jsonFormat(DefaultObjectAccessPermissionsForProjectGetResponseV1, "default_object_access_permissions")
    implicit val defaultObjectAccessPermissionForIriGetResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionForIriGetResponseV1] = jsonFormat(DefaultObjectAccessPermissionForIriGetResponseV1, "default_object_access_permission")
    implicit val defaultObjectAccessPermissionForProjectGroupGetResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionGetResponseV1] = jsonFormat(DefaultObjectAccessPermissionGetResponseV1, "default_object_access_permission")

}