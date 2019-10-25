/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.admin.responder.permissionsmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDataType.PermissionProfileType
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.responders.admin.PermissionsResponderADM
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

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
case class PermissionDataGetADM(projectIris: Seq[IRI],
                                groupIris: Seq[IRI],
                                isInProjectAdminGroups: Seq[IRI],
                                isInSystemAdminGroup: Boolean,
                                requestingUser: UserADM
                               ) extends PermissionsResponderRequestADM


/**
  * A message that requests all administrative and default object access
  * permissions defined inside a project. A successful response will be
  * a [[PermissionsForProjectGetResponseADM]].
  *
  * @param projectIri     the project for which the permissions are queried.
  * @param requestingUser the user initiation the request.
  * @param apiRequestID   the API request ID.
  */
case class PermissionsForProjectGetRequestADM(projectIri: IRI,
                                              requestingUser: UserADM,
                                              apiRequestID: UUID
                                             ) extends PermissionsResponderRequestADM

// Administrative Permissions

/**
  * A message that requests all administrative permissions defined inside a project.
  * A successful response will contain a list of [[AdministrativePermissionADM]].
  *
  * @param projectIri     the project for which the administrative permissions are queried.
  * @param requestingUser the user initiation the request.
  * @param apiRequestID   the API request ID.
  */
case class AdministrativePermissionsForProjectGetRequestADM(projectIri: IRI,
                                                            requestingUser: UserADM,
                                                            apiRequestID: UUID
                                                           ) extends PermissionsResponderRequestADM

/**
  * A message that requests an administrative permission object identified through his IRI.
  * A successful response will contain an [[AdministrativePermissionADM]] object.
  *
  * @param administrativePermissionIri the iri of the administrative permission object.
  * @param requestingUser              the user initiating the request.
  * @param apiRequestID                the API request ID.
  */
case class AdministrativePermissionForIriGetRequestADM(administrativePermissionIri: IRI,
                                                       requestingUser: UserADM,
                                                       apiRequestID: UUID
                                                      ) extends PermissionsResponderRequestADM

/**
  * A message that requests an administrative permission object identified by project and group.
  * A response will contain an optional [[AdministrativePermissionADM]] object.
  *
  * @param projectIri     the project.
  * @param groupIri       the group.
  * @param requestingUser the user initiating the request.
  */
case class AdministrativePermissionForProjectGroupGetADM(projectIri: IRI,
                                                         groupIri: IRI,
                                                         requestingUser: UserADM) extends PermissionsResponderRequestADM

/**
  * A message that requests an administrative permission object identified by project and group.
  * A successful response will be an [[AdministrativePermissionForProjectGroupGetResponseADM]] object.
  *
  * @param projectIri
  * @param groupIri
  * @param requestingUser
  */
case class AdministrativePermissionForProjectGroupGetRequestADM(projectIri: IRI,
                                                                groupIri: IRI,
                                                                requestingUser: UserADM
                                                               ) extends PermissionsResponderRequestADM


/**
  * Create a single [[AdministrativePermissionADM]] (internal use).
  *
  * @param createRequest  the API create request payload.
  * @param requestingUser the requesting user.
  * @param apiRequestID   the API request ID.
  */
case class AdministrativePermissionCreateADM(createRequest: CreateAdministrativePermissionAPIRequestADM,
                                             requestingUser: UserADM,
                                             apiRequestID: UUID
                                            ) extends PermissionsResponderRequestADM

/**
  * Create a single [[AdministrativePermissionADM]].
  *
  * @param createRequest  the API create request payload.
  * @param requestingUser the requesting user.
  * @param apiRequestID   the API request ID.
  */
case class AdministrativePermissionCreateRequestADM(createRequest: CreateAdministrativePermissionAPIRequestADM,
                                                    requestingUser: UserADM,
                                                    apiRequestID: UUID
                                                   ) extends PermissionsResponderRequestADM

/**
  * Update a single [[AdministrativePermissionADM]]
  *
  * @param changeRequest  the API change request payload.
  * @param requestingUser the requesting user.
  * @param apiRequestID   the API request ID.
  */
case class AdministrativePermissionChangeRequestADM(changeRequest: ChangeAdministrativePermissionAPIRequestADM,
                                                    requestingUser: UserADM,
                                                    apiRequestID: UUID
                                                   ) extends PermissionsResponderRequestADM

/**
  * Delete a single [[AdministrativePermissionADM]]
  *
  * @param administrativePermissionIri the IRI of the administrative permission.
  * @param requestingUser              the requesting user.
  * @param apiRequestID                the API request ID.
  */
case class AdministrativePermissionDeleteRequestADM(administrativePermissionIri: IRI,
                                                    requestingUser: UserADM,
                                                    apiRequestID: UUID
                                                   ) extends PermissionsResponderRequestADM


// Object Access Permissions

/**
  * A message that requests the object access permissions attached to a resource via the 'knora-base:hasPermissions' property.
  *
  * @param resourceIri the IRI of the resource.
  */
case class ObjectAccessPermissionsForResourceGetADM(resourceIri: IRI, requestingUser: UserADM) extends PermissionsResponderRequestADM

/**
  * A message that requests the object access permissions attached to a value via the 'knora-base:hasPermissions' property.
  *
  * @param valueIri the IRI of the value.
  */
case class ObjectAccessPermissionsForValueGetADM(valueIri: IRI, requestingUser: UserADM) extends PermissionsResponderRequestADM


// Default Object Access Permissions

/**
  * A message that requests all default object access permissions defined inside a project.
  * A successful response will be a list of [[DefaultObjectAccessPermissionADM]].
  *
  * @param projectIri     the project for which the default object access permissions are queried.
  * @param requestingUser the user initiating this request.
  * @param apiRequestID                the API request ID.
  */
case class DefaultObjectAccessPermissionsForProjectGetRequestADM(projectIri: IRI,
                                                                 requestingUser: UserADM,
                                                                 apiRequestID: UUID
                                                                ) extends PermissionsResponderRequestADM

/**
  * A message that requests an object access permission identified by project and either group / resource class / property.
  * A successful response will be a [[DefaultObjectAccessPermissionADM]].
  *
  * @param projectIRI       the project.
  * @param groupIRI         the group.
  * @param resourceClassIRI the resource class.
  * @param propertyIRI      the property.
  */
case class DefaultObjectAccessPermissionGetADM(projectIRI: IRI,
                                               groupIRI: Option[IRI],
                                               resourceClassIRI: Option[IRI],
                                               propertyIRI: Option[IRI],
                                               requestingUser: UserADM) extends PermissionsResponderRequestADM

/**
  * A message that requests an object access permission identified by project and either group / resource class / property.
  * A successful response will be a [[DefaultObjectAccessPermissionGetResponseADM]].
  *
  * @param projectIRI       the project.
  * @param groupIRI         the group.
  * @param resourceClassIRI the resource class.
  * @param propertyIRI      the property.
  * @param requestingUser   the user initiating this request.
  */
case class DefaultObjectAccessPermissionGetRequestADM(projectIRI: IRI,
                                                      groupIRI: Option[IRI],
                                                      resourceClassIRI: Option[IRI],
                                                      propertyIRI: Option[IRI],
                                                      requestingUser: UserADM
                                                     ) extends PermissionsResponderRequestADM

/**
  * A message that requests a default object access permission object identified through his IRI.
  * A successful response will be an [[DefaultObjectAccessPermissionADM]] object.
  *
  * @param defaultObjectAccessPermissionIri the iri of the default object access permission object.
  * @param requestingUser                   the user initiation the request.
  * @param apiRequestID                     the API request ID.
  */
case class DefaultObjectAccessPermissionForIriGetRequestADM(defaultObjectAccessPermissionIri: IRI,
                                                            requestingUser: UserADM,
                                                            apiRequestID: UUID
                                                           ) extends PermissionsResponderRequestADM


/**
  * A message that requests the default object access permissions string for a
  * resource class inside a specific project. A successful response will be a
  * [[DefaultObjectAccessPermissionsStringResponseADM]].
  *
  * @param projectIri       the project for which the default object permissions
  *                         need to be retrieved.
  * @param resourceClassIri the resource class which can also cary default object
  *                         access permissions.
  * @param targetUser       the user for whom we calculate the permission
  * @param requestingUser   the requesting user.
  */
case class DefaultObjectAccessPermissionsStringForResourceClassGetADM(projectIri: IRI,
                                                                      resourceClassIri: IRI,
                                                                      targetUser: UserADM,
                                                                      requestingUser: UserADM
                                                                     ) extends PermissionsResponderRequestADM

/**
  * A message that requests default object access permissions for a resource
  * class / property combination inside a specific project. A successful response
  * will be a
  * [[DefaultObjectAccessPermissionsStringResponseADM]].
  *
  * @param projectIri       the project for which the default object permissions
  *                         need to be retrieved.
  * @param resourceClassIri the resource class which can also cary default object
  *                         access permissions.
  * @param propertyIri      the property type which can also cary default object
  *                         access permissions.
  * @param targetUser       the user for whom we calculate the permission
  * @param requestingUser   the requesting user.
  */
case class DefaultObjectAccessPermissionsStringForPropertyGetADM(projectIri: IRI,
                                                                 resourceClassIri: IRI,
                                                                 propertyIri: IRI,
                                                                 targetUser: UserADM,
                                                                 requestingUser: UserADM
                                                                ) extends PermissionsResponderRequestADM

/**
  * Create a single [[DefaultObjectAccessPermissionADM]].
  *
  * @param createRequest  the create request.
  * @param requestingUser the requesting user.
  * @param apiRequestID   the API request ID.
  */
case class DefaultObjectAccessPermissionCreateRequestADM(createRequest: CreateDefaultObjectAccessPermissionAPIRequestADM,
                                                         requestingUser: UserADM,
                                                         apiRequestID: UUID
                                                        ) extends PermissionsResponderRequestADM

/**
  * Change a single [[DefaultObjectAccessPermissionADM]].
  *
  * @param changeRequest  the change request.
  * @param requestingUser the requesting user.
  * @param apiRequestID   the API request ID.
  */
case class DefaultObjectAccessPermissionChangeRequestADM(changeRequest: ChangeDefaultObjectAccessPermissionAPIRequestADM,
                                                         requestingUser: UserADM,
                                                         apiRequestID: UUID
                                                        ) extends PermissionsResponderRequestADM

/**
  * Delete a single [[DefaultObjectAccessPermissionADM]]
  *
  * @param permissionIri  the permission IRI
  * @param requestingUser the requesting user.
  * @param apiRequestID   the API request ID.
  */
case class DefaultObjectAccessPermissionDeleteRequestADM(permissionIri: IRI,
                                                         requestingUser: UserADM,
                                                         apiRequestID: UUID
                                                        ) extends PermissionsResponderRequestADM


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
  * Represents an answer to [[PermissionsForProjectGetRequestADM]].
  *
  * @param administrativePermissions the retrieved sequence of [[AdministrativePermissionADM]]
  * @param defaultObjectAccessPermissions the retrieved sequence of [[AdministrativePermissionADM]]
  */
case class PermissionsForProjectGetResponseADM(administrativePermissions: Seq[AdministrativePermissionADM],
                                              defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionADM]
                                              ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = permissionsForProjectGetResponseADMFormat.write(this)
}

// Administrative Permissions

/**
  * Represents an answer to [[AdministrativePermissionsForProjectGetRequestADM]].
  *
  * @param administrativePermissions the retrieved sequence of [[AdministrativePermissionADM]]
  */
case class AdministrativePermissionsForProjectGetResponseADM(administrativePermissions: Seq[AdministrativePermissionADM]
                                                            ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = administrativePermissionsForProjectGetResponseADMFormat.write(this)
}

/**
  * Represents an anser to [[AdministrativePermissionForIriGetRequestADM]].
  *
  * @param administrativePermission the retrieved [[AdministrativePermissionADM]].
  */
case class AdministrativePermissionForIriGetResponseADM(administrativePermission: AdministrativePermissionADM
                                                       ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = administrativePermissionForIriGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to [[AdministrativePermissionForProjectGroupGetRequestADM]]
  *
  * @param administrativePermission the retrieved [[AdministrativePermissionADM]]
  */
case class AdministrativePermissionForProjectGroupGetResponseADM(administrativePermission: AdministrativePermissionADM
                                                                ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = administrativePermissionForProjectGroupGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to [[AdministrativePermissionCreateRequestADM]].
  *
  * @param administrativePermission the newly created [[AdministrativePermissionADM]].
  */
case class AdministrativePermissionCreateResponseADM(administrativePermission: AdministrativePermissionADM
                                                    ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = administrativePermissionCreateResponseADMFormat.write(this)
}

/**
  * Represents an answer to [[AdministrativePermissionDeleteRequestADM]].
  */
case class AdministrativePermissionDeleteResponseADM(result: String = "permission deleted"
                                                    ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = administrativePermissionDeleteResponseADMFormat.write(this)
}

//
// Default Object Access Permissions
//

/**
  * Represents an answer to [[DefaultObjectAccessPermissionsForProjectGetRequestADM]]
  *
  * @param defaultObjectAccessPermissions the retrieved sequence of [[DefaultObjectAccessPermissionADM]]
  */
case class DefaultObjectAccessPermissionsForProjectGetResponseADM(defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionADM]
                                                                 ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = defaultObjectAccessPermissionsForProjectGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to [[DefaultObjectAccessPermissionGetRequestADM]].
  *
  * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionADM]].
  */
case class DefaultObjectAccessPermissionGetResponseADM(defaultObjectAccessPermission: DefaultObjectAccessPermissionADM
                                                      ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = defaultObjectAccessPermissionForProjectGroupGetResponseADMFormat.write(this)
}

/**
  * Represents an answer to [[DefaultObjectAccessPermissionForIriGetRequestADM]].
  *
  * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionADM]].
  */
case class DefaultObjectAccessPermissionForIriGetResponseADM(defaultObjectAccessPermission: DefaultObjectAccessPermissionADM
                                                            ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = defaultObjectAccessPermissionForIriGetResponseADMFormat.write(this)
}

/**
  * Represents default permissions for an object, formatted as the literal object of `knora-base:hasPermissions`.
  *
  * @param permissionLiteral a permission literal string.
  */
case class DefaultObjectAccessPermissionsStringResponseADM(permissionLiteral: String)


/**
  * Represents an answer to [[DefaultObjectAccessPermissionCreateRequestADM]].
  *
  * @param defaultObjectAccessPermission the newly created [[DefaultObjectAccessPermissionADM]].
  */
case class DefaultObjectAccessPermissionCreateResponseADM(defaultObjectAccessPermission: DefaultObjectAccessPermissionADM
                                                    ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = administrativePermissionCreateResponseADMFormat.write(this)
}

/**
  * Represents an answer to [[DefaultObjectAccessPermissionDeleteRequestADM]].
  */
case class DefaultObjectAccessPermissionDeleteResponseADM(result: String = "permission deleted"
                                                    ) extends KnoraResponseADM with PermissionsADMJsonProtocol {
    def toJsValue = administrativePermissionDeleteResponseADMFormat.write(this)
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
case class PermissionsDataADM(groupsPerProject: Map[IRI, Seq[IRI]] = Map.empty[IRI, Seq[IRI]],
                              administrativePermissionsPerProject: Map[IRI, Set[PermissionADM]] = Map.empty[IRI, Set[PermissionADM]]
                             ) {

    /**
      * Returns [[PermissionsDataADM]] of the requested type.
      *
      * @return a [[PermissionsDataADM]]
      */
    def ofType(permissionProfileType: PermissionProfileType): PermissionsDataADM = {
        permissionProfileType match {

            case PermissionDataType.RESTRICTED =>
                PermissionsDataADM(
                    groupsPerProject = groupsPerProject,
                    administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]] // remove administrative permission information
                )

            case PermissionDataType.FULL =>
                PermissionsDataADM(
                    groupsPerProject = groupsPerProject,
                    administrativePermissionsPerProject = administrativePermissionsPerProject
                )

            case _ => throw BadRequestException(s"The requested userProfileType: $permissionProfileType is invalid.")
        }
    }

    /* Is the user a member of the SystemAdmin group */
    def isSystemAdmin: Boolean = {
        groupsPerProject.getOrElse(OntologyConstants.KnoraAdmin.SystemProject, List.empty[IRI]).contains(OntologyConstants.KnoraAdmin.SystemAdmin)
    }

    /* Is the user a member of the ProjectAdmin group in any project */
    def isProjectAdminInAnyProject(): Boolean = {
        groupsPerProject.flatMap(_._2).toSeq.contains(OntologyConstants.KnoraAdmin.ProjectAdmin)
    }

    /* Is the user a member of the ProjectAdmin group */
    def isProjectAdmin(projectIri: IRI): Boolean = {
        groupsPerProject.getOrElse(projectIri, List.empty[IRI]).contains(OntologyConstants.KnoraAdmin.ProjectAdmin)
    }

    /* Does the user have the 'ProjectAdminAllPermission' permission for the project */
    def hasProjectAdminAllPermissionFor(projectIri: IRI): Boolean = {
        administrativePermissionsPerProject.get(projectIri) match {
            case Some(permissions) => permissions(PermissionADM.ProjectAdminAllPermission)
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
    def hasPermissionFor(operation: OperationADM, insideProject: IRI, objectAccessPermissions: Option[Set[PermissionADM]]): Boolean = {

        // println(s"hasPermissionFor - administrativePermissionsPerProject: ${administrativePermissionsPerProject}, operation: $operation, insideProject: $insideProject")

        if (this.isSystemAdmin) {
            /* A member of the SystemAdmin group is allowed to perform any operation */
            // println("TRUE: A member of the SystemAdmin group is allowed to perform any operation")
            true
        } else {
            operation match {
                case ResourceCreateOperation(resourceClassIri) => {
                    this.administrativePermissionsPerProject.get(insideProject) match {
                        case Some(set) => {
                            set(PermissionADM.ProjectResourceCreateAllPermission) || set(PermissionADM.projectResourceCreateRestrictedPermission(resourceClassIri))
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
    def canEqual(a: Any): Boolean = a.isInstanceOf[PermissionsDataADM]

    override def equals(that: Any): Boolean =
        that match {
            case that: PermissionsDataADM => that.canEqual(this) && {

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
          s"\t groupsPerProject = ${groupsPerProject.toString} \n" +
          s"\t administrativePermissionsPerProject = ${administrativePermissionsPerProject.toString} \n" +
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
case class AdministrativePermissionADM(iri: IRI,
                                       forProject: IRI,
                                       forGroup: IRI,
                                       hasPermissions: Set[PermissionADM]
                                      ) extends Jsonable with PermissionsADMJsonProtocol {

    def toJsValue = administrativePermissionADMFormat.write(this)
}

/**
  * Represents a payload that asks the Knora API server to create a new
  * administrative permission
  *
  * @param forProject     the project for which this permission is created.
  * @param forGroup       the group for which this permission is created.
  * @param hasPermissions the set of permissions.
  */
case class CreateAdministrativePermissionAPIRequestADM(forProject: IRI,
                                                       forGroup: IRI,
                                                       hasPermissions: Set[PermissionADM])

/**
  * Represents a payload that asks the Knora API server to change an existing
  * administrative permission
  *
  * @param iri               the IRI of the administrative permission.
  * @param forProject        the project for which this permission is created.
  * @param forGroup          the group for which this permission is created.
  * @param hasOldPermissions the set of existing permissions.
  * @param hasNewPermissions the set of new permissions.
  */
case class ChangeAdministrativePermissionAPIRequestADM(iri: IRI,
                                                       forProject: IRI,
                                                       forGroup: IRI,
                                                       hasOldPermissions: Set[PermissionADM],
                                                       hasNewPermissions: Set[PermissionADM])

/**
  * Represents object access permissions attached to a resource OR value via the
  * 'knora-base:hasPermission' property.
  *
  * @param forResource    the IRI of the resource.
  * @param forValue       the IRI of the value.
  * @param hasPermissions the permissions.
  */
case class ObjectAccessPermissionADM(forResource: Option[IRI],
                                     forValue: Option[IRI],
                                     hasPermissions: Set[PermissionADM]
                                    ) extends Jsonable with PermissionsADMJsonProtocol {

    def toJsValue = objectAccessPermissionADMFormat.write(this)
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
case class DefaultObjectAccessPermissionADM(iri: IRI,
                                            forProject: IRI,
                                            forGroup: Option[IRI],
                                            forResourceClass: Option[IRI],
                                            forProperty: Option[IRI],
                                            hasPermissions: Set[PermissionADM]
                                           ) {

    /**
      * @return a simple string representing the permission which can be used as the cache key.
      */
    def cacheKey: String = PermissionsResponderADM.getDefaultObjectAccessPermissionADMKey(forProject, forGroup, forResourceClass, forProperty)
}

/**
  * Represents a payload that asks the Knora API server to create a new
  * default object access permission
  *
  * @param iri              the IRI of the permission
  * @param forProject       the project
  * @param forGroup         the group
  * @param forResourceClass the resource class
  * @param forProperty      the property
  * @param hasPermissions   the permissions
  */
case class CreateDefaultObjectAccessPermissionAPIRequestADM(iri: IRI,
                                                            forProject: IRI,
                                                            forGroup: IRI,
                                                            forResourceClass: IRI,
                                                            forProperty: IRI,
                                                            hasPermissions: Set[PermissionADM])

/**
  * Represents a payload that asks the Knora API server to change an existing
  * default object access permission
  *
  * @param iri              the IRI of the permission
  * @param forProject       the project
  * @param forGroup         the group
  * @param forResourceClass the resource class
  * @param forProperty      the property
  * @param hasPermissions   the permissions
  */
case class ChangeDefaultObjectAccessPermissionAPIRequestADM(iri: IRI,
                                                            forProject: IRI,
                                                            forGroup: IRI,
                                                            forResourceClass: IRI,
                                                            forProperty: IRI,
                                                            hasPermissions: Set[PermissionADM])

/**
  * Case class representing a permission.
  *
  * @param name                  the name of the permission.
  * @param additionalInformation an optional IRI (e.g., group IRI, resource class IRI).
  */
case class PermissionADM(name: String,
                         additionalInformation: Option[IRI],
                         permissionCode: Option[Int]
                        ) extends Jsonable with PermissionsADMJsonProtocol {

    def toJsValue = permissionADMFormat.write(this)
}


/**
  * The permission companion object, used to create specific permissions.
  */
object PermissionADM {

    ///////////////////////////////////////////////////////////////////////////
    // Administrative Permissions
    ///////////////////////////////////////////////////////////////////////////

    val ProjectResourceCreateAllPermission: PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectResourceCreateAllPermission,
            additionalInformation = None,
            permissionCode = None
        )
    }

    def projectResourceCreateRestrictedPermission(restriction: IRI): PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectResourceCreateRestrictedPermission,
            additionalInformation = Some(restriction),
            permissionCode = None
        )
    }

    val ProjectAdminAllPermission: PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectAdminAllPermission,
            additionalInformation = None,
            permissionCode = None
        )
    }

    val ProjectAdminGroupAllPermission: PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectAdminGroupAllPermission,
            additionalInformation = None,
            permissionCode = None
        )
    }

    def projectAdminGroupRestrictedPermission(restriction: IRI): PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectAdminGroupRestrictedPermission,
            additionalInformation = Some(restriction),
            permissionCode = None
        )
    }

    val ProjectAdminRightsAllPermission: PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectAdminRightsAllPermission,
            additionalInformation = None,
            permissionCode = None
        )
    }


    ///////////////////////////////////////////////////////////////////////////
    // Object Access Permissions
    ///////////////////////////////////////////////////////////////////////////

    def changeRightsPermission(restriction: IRI): PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraBase.ChangeRightsPermission,
            additionalInformation = Some(restriction),
            permissionCode = Some(8)
        )
    }

    def deletePermission(restriction: IRI): PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(restriction),
            permissionCode = Some(7)
        )
    }

    def modifyPermission(restriction: IRI): PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraBase.ModifyPermission,
            additionalInformation = Some(restriction),
            permissionCode = Some(6)
        )
    }

    def viewPermission(restriction: IRI): PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraBase.ViewPermission,
            additionalInformation = Some(restriction),
            permissionCode = Some(2)
        )
    }

    def restrictedViewPermission(restriction: IRI): PermissionADM = {
        PermissionADM(
            name = OntologyConstants.KnoraBase.RestrictedViewPermission,
            additionalInformation = Some(restriction),
            permissionCode = Some(1)
        )
    }
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
object PermissionDataType extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type PermissionProfileType = Value

    val RESTRICTED: PermissionProfileType = Value(0, "restricted")
    // only group memberships
    val FULL: PermissionProfileType = Value(1, "full") // everything

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

    val OAP: PermissionType = Value(0, "ObjectAccessPermission")
    val AP: PermissionType = Value(1, "AdministrativePermission")
    val DOAP: PermissionType = Value(2, "DefaultObjectAccessPermission")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting


trait PermissionsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol {

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

    implicit val permissionADMFormat: JsonFormat[PermissionADM] = jsonFormat(PermissionADM.apply, "name", "additionalInformation", "permissionCode")
    // apply needed because we have an companion object of a case class
    implicit val administrativePermissionADMFormat: JsonFormat[AdministrativePermissionADM] = jsonFormat(AdministrativePermissionADM, "iri", "forProject", "forGroup", "hasPermissions")
    implicit val objectAccessPermissionADMFormat: JsonFormat[ObjectAccessPermissionADM] = jsonFormat(ObjectAccessPermissionADM, "forResource", "forValue", "hasPermissions")
    implicit val defaultObjectAccessPermissionADMFormat: JsonFormat[DefaultObjectAccessPermissionADM] = jsonFormat6(DefaultObjectAccessPermissionADM)
    implicit val permissionsDataADMFormat: JsonFormat[PermissionsDataADM] = jsonFormat2(PermissionsDataADM)
    implicit val permissionsForProjectGetResponseADMFormat: RootJsonFormat[PermissionsForProjectGetResponseADM] = jsonFormat(PermissionsForProjectGetResponseADM, "administrative_permissions", "default_object_access_permissions")
    implicit val administrativePermissionsForProjectGetResponseADMFormat: RootJsonFormat[AdministrativePermissionsForProjectGetResponseADM] = jsonFormat(AdministrativePermissionsForProjectGetResponseADM, "administrative_permissions")
    implicit val administrativePermissionForIriGetResponseADMFormat: RootJsonFormat[AdministrativePermissionForIriGetResponseADM] = jsonFormat(AdministrativePermissionForIriGetResponseADM, "administrative_permission")
    implicit val administrativePermissionForProjectGroupGetResponseADMFormat: RootJsonFormat[AdministrativePermissionForProjectGroupGetResponseADM] = jsonFormat(AdministrativePermissionForProjectGroupGetResponseADM, "administrative_permission")
    implicit val administrativePermissionCreateResponseADMFormat: RootJsonFormat[AdministrativePermissionCreateResponseADM] = jsonFormat(AdministrativePermissionCreateResponseADM, "administrative_permission")
    implicit val administrativePermissionDeleteResponseADMFormat: RootJsonFormat[AdministrativePermissionDeleteResponseADM] = jsonFormat(AdministrativePermissionDeleteResponseADM, "result")
    implicit val defaultObjectAccessPermissionsForProjectGetResponseADMFormat: RootJsonFormat[DefaultObjectAccessPermissionsForProjectGetResponseADM] = jsonFormat(DefaultObjectAccessPermissionsForProjectGetResponseADM, "default_object_access_permissions")
    implicit val defaultObjectAccessPermissionForIriGetResponseADMFormat: RootJsonFormat[DefaultObjectAccessPermissionForIriGetResponseADM] = jsonFormat(DefaultObjectAccessPermissionForIriGetResponseADM, "default_object_access_permission")
    implicit val defaultObjectAccessPermissionForProjectGroupGetResponseADMFormat: RootJsonFormat[DefaultObjectAccessPermissionGetResponseADM] = jsonFormat(DefaultObjectAccessPermissionGetResponseADM, "default_object_access_permission")
    implicit val defaultObjectAccessPermissionCreateResponseADMFormat: RootJsonFormat[DefaultObjectAccessPermissionCreateResponseADM] = jsonFormat(DefaultObjectAccessPermissionCreateResponseADM, "default_object_access_permission")
    implicit val defaultObjectAccessPermissionDeleteResponseADMFormat: RootJsonFormat[DefaultObjectAccessPermissionDeleteResponseADM] = jsonFormat(DefaultObjectAccessPermissionDeleteResponseADM, "result")

}