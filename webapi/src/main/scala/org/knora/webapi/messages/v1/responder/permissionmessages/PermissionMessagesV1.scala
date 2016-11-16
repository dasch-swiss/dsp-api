/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionProfileType.PermissionProfileType
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsTemplate.PermissionsTemplate
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
  */
sealed trait PermissionsResponderRequestV1 extends KnoraRequestV1

/**
  * A message that requests the user's [[PermissionProfileV1]].
  * @param projectIris the projects the user is part of.
  * @param groupIris the groups the user is member of.
  * @param isInProjectAdminGroups the projects for which the user is member of the ProjectAdmin group.
  * @param isInSystemAdminGroup the flag denoting users membership in the SystemAdmin group.
  */
case class PermissionProfileGetV1(projectIris: Seq[IRI],
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
  * @param projectIri the IRI of the project.
  * @param permissionsTemplate the permissions template.
  */
//case class TemplatePermissionsCreateRequestV1(projectIri: IRI, permissionsTemplate: PermissionsTemplate, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1


// Administrative Permissions

/**
  * A message that requests all administrative permissions defined inside a project.
  * A successful response will contain a list of [[AdministrativePermissionV1]].
  *
  * @param projectIri the project for which the administrative permissions are queried.
  * @param userProfileV1 the user initiation the request.
  */
case class AdministrativePermissionsForProjectGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an administrative permission object identified through his IRI.
  * A successful response will contain an [[AdministrativePermissionV1]] object.
  *
  * @param administrativePermissionIri the iri of the administrative permission object.
  * @param userProfileV1 the user initiation the request.
  */
case class AdministrativePermissionForIriGetRequestV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an administrative permission object identified by project and group.
  * A response will contain an optional [[AdministrativePermissionV1]] object.
  *
  * @param projectIri       the project.
  * @param groupIri         the group.
  * @param userProfileV1    the user initiation the request.
  */
case class AdministrativePermissionForProjectGroupGetV1(projectIri: IRI, groupIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an administrative permission object identified by project and group.
  * A successful response will be an [[AdministrativePermissionForProjectGroupGetResponseV1]] object.
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

// Default Object Access Permissions

/**
  * A message that requests all default object access permissions defined inside a project.
  * A successful response will contain a list of [[DefaultObjectAccessPermissionV1]].
  *
  * @param projectIri the project for which the default object access permissions are queried.
  * @param userProfileV1 the user initiating this request.
  */
case class DefaultObjectAccessPermissionsForProjectGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an object access permission identified by project and group.
  * A successful response will contain a [[DefaultObjectAccessPermissionV1]].
  *
  * @param projectIri the project.
  * @param groupIri the group.
  * @param userProfileV1 the user initiating this request.
  */
case class DefaultObjectAccessPermissionForProjectGroupGetRequestV1(projectIri: IRI, groupIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests a default object access permission object identified through his IRI.
  * A successful response will contain an [[DefaultObjectAccessPermissionV1]] object.
  *
  * @param defaultObjectAccessPermissionIri the iri of the default object access permission object.
  * @param userProfileV1 the user initiation the request.
  */
case class DefaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * Create a singel [[DefaultObjectAccessPermissionV1]].
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
  * @param administrativePermissions the retrieved sequence of [[AdministrativePermissionV1]]
  */
case class AdministrativePermissionsForProjectGetResponseV1(administrativePermissions: Seq[AdministrativePermissionV1]) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionsForProjectGetResponseV1Format.write(this)
}

/**
  * Represents an anser to [[AdministrativePermissionForIriGetRequestV1]].
  * @param administrativePermission the retrieved [[AdministrativePermissionV1]].
  */
case class AdministrativePermissionForIriGetResponseV1(administrativePermission: AdministrativePermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionForIriGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[AdministrativePermissionForProjectGroupGetRequestV1]]
  * @param administrativePermission the retrieved [[AdministrativePermissionV1]]
  */
case class AdministrativePermissionForProjectGroupGetResponseV1(administrativePermission: AdministrativePermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionForProjectGroupGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[AdministrativePermissionCreateRequestV1]].
  * @param administrativePermission the newly created [[AdministrativePermissionV1]].
  */
case class AdministrativePermissionCreateResponseV1(administrativePermission: AdministrativePermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionCreateResponseV1Format.write(this)
}

// Default Object Access Permissions

/**
  * Represents an answer to [[DefaultObjectAccessPermissionsForProjectGetRequestV1]]
  * @param defaultObjectAccessPermissions the retrieved sequence of [[DefaultObjectAccessPermissionV1]]
  */
case class DefaultObjectAccessPermissionsForProjectGetResponseV1(defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionV1]) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = defaultObjectAccessPermissionsForProjectGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[DefaultObjectAccessPermissionForProjectGroupGetRequestV1]].
  * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionV1]].
  */
case class DefaultObjectAccessPermissionForProjectGroupGetResponseV1(defaultObjectAccessPermission: DefaultObjectAccessPermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = defaultObjectAccessPermissionForProjectGroupGetResponseV1Format.write(this)
}

/**
  * Represents an answer to [[DefaultObjectAccessPermissionForIriGetRequestV1]].
  * @param defaultObjectAccessPermission the retrieved [[DefaultObjectAccessPermissionV1]].
  */
case class DefaultObjectAccessPermissionForIriGetResponseV1(defaultObjectAccessPermission: DefaultObjectAccessPermissionV1) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = defaultObjectAccessPermissionForIriGetResponseV1Format.write(this)
}

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
  * Represents a user's permission profile.
  *
  * @param projectInfos the project info of the projects that the user belongs to.
  * @param groupsPerProject the groups the user belongs to for each project.
  * @param administrativePermissionsPerProject the user's administrative permissions for each project.
  * @param defaultObjectAccessPermissionsPerProject the user's default object access permissions for each project.
  */
case class PermissionProfileV1(projectInfos: Seq[ProjectInfoV1] = Vector.empty[ProjectInfoV1],
                               groupsPerProject: Map[IRI, List[IRI]] = Map.empty[IRI, List[IRI]],
                               administrativePermissionsPerProject: Map[IRI, Set[PermissionV1]] = Map.empty[IRI, Set[PermissionV1]],
                               defaultObjectAccessPermissionsPerProject: Map[IRI, Set[PermissionV1]] = Map.empty[IRI, Set[PermissionV1]]
                              ) {

    /**
      * Creating a [[UserProfileV1]] with sensitive information stripped.
      *
      * @return a [[UserProfileV1]]
      */
    def ofType(permissionProfileType: PermissionProfileType): PermissionProfileV1 = {
        permissionProfileType match {
            case PermissionProfileType.SHORT => {

                PermissionProfileV1(
                    projectInfos = Vector.empty[ProjectInfoV1], // remove
                    groupsPerProject = groupsPerProject,
                    administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionV1]], // remove administrative permission information
                    defaultObjectAccessPermissionsPerProject = Map.empty[IRI, Set[PermissionV1]] // remove default object access permission information
                )
            }
            case PermissionProfileType.SAFE => {

                PermissionProfileV1(
                    projectInfos = projectInfos,
                    groupsPerProject = groupsPerProject,
                    administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionV1]], // remove administrative permission information
                    defaultObjectAccessPermissionsPerProject = Map.empty[IRI, Set[PermissionV1]] // remove default object access permission information
                )
            }
            case PermissionProfileType.FULL => {

                PermissionProfileV1(
                    projectInfos = projectInfos,
                    groupsPerProject = groupsPerProject,
                    administrativePermissionsPerProject = administrativePermissionsPerProject,
                    defaultObjectAccessPermissionsPerProject = defaultObjectAccessPermissionsPerProject
                )
            }
            case _ => throw BadRequestException(s"The requested userProfileType: $permissionProfileType is invalid.")
        }
    }

    def isSystemAdmin: Boolean = {
        groupsPerProject.contains("http://www.knora.org/ontology/knora-base#SystemProject")
    }
}



/**
  * Represents 'knora-base:AdministrativePermission'
  *
  * @param forProject the project this permission applies to.
  * @param forGroup the group this permission applies to.
  * @param hasPermissions the administrative permissions.
  */
case class AdministrativePermissionV1(forProject: IRI,
                                      forGroup: IRI,
                                      hasPermissions: Seq[PermissionV1]
                                     ) extends Jsonable with PermissionV1JsonProtocol {
    def toJsValue = administrativePermissionV1Format.write(this)
}

/**
  * Represents information needed during administrative permission creation.
  *
  * @param iri
  * @param forProject
  * @param forGroup
  * @param hasPermissions
  */
case class NewAdministrativePermissionV1(iri: IRI,
                                         forProject: IRI,
                                         forGroup: IRI,
                                         hasPermissions: Seq[PermissionV1]
                                        )

/**
  * Represents 'knora-base:DefaultObjectAccessPermission'
  *
  * @param forProject
  * @param forGroup
  * @param forResourceClass
  * @param forProperty
  * @param hasPermissions
  */
case class DefaultObjectAccessPermissionV1(forProject: IRI,
                                           forGroup: IRI,
                                           forResourceClass: IRI,
                                           forProperty: IRI,
                                           hasPermissions: Seq[PermissionV1]
                                          )

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
                                              hasPermissions: Seq[PermissionV1]
                                             )

/**
  * Case class representing a permission.
  * @param name the name of the permission.
  * @param restrictions the optional set of IRIs providing additional information.
  */
case class PermissionV1(name: String,
                        restrictions: Set[IRI]
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

    def ProjectResourceCreateAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission,
            restrictions = Set.empty[IRI]
        )
    }

    def ProjectResourceCreateRestrictedPermission(restrictions: Set[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission,
            restrictions = restrictions
        )
    }

    def ProjectAdminAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminAllPermission,
            restrictions = Set.empty[IRI]
        )
    }

    def ProjectAdminGroupAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminGroupAllPermission,
            restrictions = Set.empty[IRI]
        )
    }

    def ProjectAdminGroupRestrictedPermission(restrictions: Set[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission,
            restrictions = restrictions
        )
    }

    def ProjectAdminRightsAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminRightsAllPermission,
            restrictions = Set.empty[IRI]
        )
    }

    def ProjectAdminOntologyAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminOntologyAllPermission,
            restrictions = Set.empty[IRI]
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // Default Object Access Permissions
    ///////////////////////////////////////////////////////////////////////////

    def DefaultChangeRightsPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultChangeRightsPermission,
            restrictions = restriction
        )
    }

    def DefaultDeletePermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultDeletePermission,
            restrictions = restriction
        )
    }

    def DefaultModifyPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultModifyPermission,
            restrictions = restriction
        )
    }

    def DefaultViewPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultViewPermission,
            restrictions = restriction
        )
    }

    def DefaultRestrictedViewPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultRestrictedViewPermission,
            restrictions = restriction
        )
    }

}

/**
  * Permission profile types:
  * short: short without sensitive information
  * safe: everything without sensitive information
  * full: everything
  */
object PermissionProfileType extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type PermissionProfileType = Value

    val SHORT = Value(0, "short") // short without sensitive information
    val SAFE = Value(1, "safe") // everything without sensitive information
    val FULL = Value(2, "full") // everything

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
  * Permissions template values
  */
object PermissionsTemplate extends Enumeration {

    type PermissionsTemplate = Value

    val NONE = Value(0, "none")
    val OPEN = Value(1, "open")
    val CLOSED = Value(2, "closed")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the calue.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Initial permissions template not supported: $name")
        }
    }
}

object PermissionType extends Enumeration {

    type PermissionType = Value

    val OAP     = Value(0, "ObjectAccessPermission")
    val AP      = Value(1, "AdministrativePermission")
    val DOAP    = Value(2, "DefaultObjectAccessPermission")

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
          * Converts a [[PermissionProfileType]] into [[JsValue]] for formatting as JSON.
          *
          * @param permissionProfileType the [[PermissionProfileType]] to be converted.
          * @return a [[JsValue]].
          */
        def write(permissionProfileType: PermissionProfileType.Value): JsValue = {
            JsObject(Map("permission_profile_type" -> permissionProfileType.toString.toJson))
        }
    }

    implicit object PermissionsTemplateFormat extends JsonFormat[PermissionsTemplate] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[PermissionsTemplate]] into [[JsValue]] for formatting as JSON.
          *
          * @param permissionTemplate the [[PermissionsTemplate]] to be converted.
          * @return a [[JsValue]].
          */
        def write(permissionTemplate: PermissionsTemplate.Value): JsValue = {
            JsObject(Map("permission_operation" -> permissionTemplate.toString.toJson))
        }
    }

    implicit val permissionV1Format: JsonFormat[PermissionV1] = jsonFormat(PermissionV1.apply, "name", "restrictions") // apply needed because we have an companion object of a case class
    implicit val administrativePermissionV1Format: JsonFormat[AdministrativePermissionV1] = jsonFormat(AdministrativePermissionV1, "for_project", "for_group", "has_permissions")
    implicit val defaultObjectAccessPermissionV1Format: JsonFormat[DefaultObjectAccessPermissionV1] = jsonFormat5(DefaultObjectAccessPermissionV1)
    implicit val permissionProfileV1Format: JsonFormat[PermissionProfileV1] = jsonFormat4(PermissionProfileV1)
    //implicit val templatePermissionsCreateResponseV1Format: RootJsonFormat[TemplatePermissionsCreateResponseV1] = jsonFormat4(TemplatePermissionsCreateResponseV1)
    //implicit val administrativePermissionOperationResponseV1Format: RootJsonFormat[AdministrativePermissionOperationResponseV1] = jsonFormat4(AdministrativePermissionOperationResponseV1)
    //implicit val defaultObjectAccessPermissionOperationResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionOperationResponseV1] = jsonFormat4(DefaultObjectAccessPermissionOperationResponseV1)
    implicit val administrativePermissionsForProjectGetResponseV1Format: RootJsonFormat[AdministrativePermissionsForProjectGetResponseV1] = jsonFormat(AdministrativePermissionsForProjectGetResponseV1, "administrative_permissions")
    implicit val administrativePermissionForIriGetResponseV1Format: RootJsonFormat[AdministrativePermissionForIriGetResponseV1] = jsonFormat(AdministrativePermissionForIriGetResponseV1, "administrative_permission")
    implicit val administrativePermissionForProjectGroupGetResponseV1Format: RootJsonFormat[AdministrativePermissionForProjectGroupGetResponseV1] = jsonFormat(AdministrativePermissionForProjectGroupGetResponseV1, "administrative_permission")
    implicit val administrativePermissionCreateResponseV1Format: RootJsonFormat[AdministrativePermissionCreateResponseV1] = jsonFormat(AdministrativePermissionCreateResponseV1, "administrative_permission")
    implicit val defaultObjectAccessPermissionsForProjectGetResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionsForProjectGetResponseV1] = jsonFormat(DefaultObjectAccessPermissionsForProjectGetResponseV1, "default_object_access_permissions")
    implicit val defaultObjectAccessPermissionForIriGetResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionForIriGetResponseV1] = jsonFormat(DefaultObjectAccessPermissionForIriGetResponseV1, "default_object_access_permission")
    implicit val defaultObjectAccessPermissionForProjectGroupGetResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionForProjectGroupGetResponseV1] = jsonFormat(DefaultObjectAccessPermissionForProjectGroupGetResponseV1, "default_object_access_permission")

}