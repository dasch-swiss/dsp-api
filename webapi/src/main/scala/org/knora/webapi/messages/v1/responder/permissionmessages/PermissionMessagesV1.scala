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
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionOperation.PermissionOperation
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionProfileType.PermissionProfileType
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionType.PermissionType
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsTemplate.PermissionsTemplate
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.{BadRequestException, IRI, InconsistentTriplestoreDataException, OntologyConstants}
import spray.json.{JsArray, JsString, _}


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
case class PermissionProfileGetRequestV1(projectIris: Seq[IRI],
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


/**
  * A message that requests the IRIs of all administrative permissions defined inside a project.
  * A successful response will contain a list of IRIs.
  *
  * @param projectIri the project for which the administrative permissions are queried.
  * @param userProfileV1 the user initiation the request.
  */
case class AdministrativePermissionIrisForProjectGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests the administrative permissions defined inside projects.
  * A successful response will contain map of project IRIs pointing to a list of [[AdministrativePermissionV1]].
  *
  * @param projectIris the projects for which the administrative permissions are queried.
  * @param userProfileV1 the user initiation the request.
  */
case class AdministrativePermissionsForProjectsGetRequestV1(projectIris: List[IRI], userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

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
  * A successful response will contain an [[AdministrativePermissionV1]] object.
  *
  * @param projectIri       the project.
  * @param groupIri         the group.
  * @param userProfileV1    the user initiation the request.
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

/**
  * A message that requests all IRIs of default object access permissions defined inside a project.
  * A successful response will contain a list with IRIs of default object access permissions.
  *
  * @param projectIri the project for which the default object access permissions are queried.
  * @param userProfileV1 the user initiating this request.
  */
case class DefaultObjectAccessPermissionIrisForProjectGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests all default object access permissions defined inside the projects.
  * A successful response will contain a map of project IRIs pointing to a list of [[DefaultObjectAccessPermissionV1]].
  *
  * @param projectIris the project for which the default object access permissions are queried.
  * @param userProfileV1 the user initiating this request.
  */
case class DefaultObjectAccessPermissionsForProjectsGetRequestV1(projectIris: List[IRI], userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests a default object access permission object identified through his IRI.
  * A successful response will contain an [[DefaultObjectAccessPermissionV1]] object.
  *
  * @param defaultObjectAccessPermissionIri the iri of the default object access permission object.
  * @param userProfileV1 the user initiation the request.
  */
case class DefaultObjectAccessPermissionGetRequestV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

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

// Responses

/**
  * Represents an answer to a [[TemplatePermissionsCreateRequestV1]].
  * @param success
  * @param msg
  * @param administrativePermissions
  * @param defaultObjectAccessPermissions
  */
/*
case class TemplatePermissionsCreateResponseV1(success: Boolean,
                                               msg: String,
                                               administrativePermissions: Seq[AdministrativePermissionV1],
                                               defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionV1]
                                              ) extends KnoraResponseV1 with PermissionV1JsonProtocol {
    def toJsValue = templatePermissionsCreateResponseV1Format.write(this)
}
*/

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


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


/**
  * Represents a user's permission profile.
  *
  * @param projectInfos the project info of the projects that the user belongs to.
  * @param groupsPerProjects the groups the user belongs to for each project.
  * @param isInSystemAdminGroup the user's knora-base:SystemAdmin group membership status.
  * @param isInProjectAdminGroups shows for which projects the user is member in the knora-base:ProjectAdmin group.
  * @param administrativePermissionsPerProject the user's administrative permissions for each project.
  * @param defaultObjectAccessPermissionsPerProject the user's default object access permissions for each project.
  */
case class PermissionProfileV1(projectInfos: Seq[ProjectInfoV1] = Vector.empty[ProjectInfoV1],
                               groupsPerProjects: Map[IRI, List[IRI]] = Map.empty[IRI, List[IRI]],
                               isInSystemAdminGroup: Boolean = false,
                               isInProjectAdminGroups: Seq[IRI] = Vector.empty[IRI],
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
                    groupsPerProjects = groupsPerProjects,
                    isInSystemAdminGroup = false, // remove system admin status
                    isInProjectAdminGroups = Vector.empty[IRI], // remove privileged group membership
                    administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionV1]], // remove administrative permission information
                    defaultObjectAccessPermissionsPerProject = Map.empty[IRI, Set[PermissionV1]] // remove default object access permission information
                )
            }
            case PermissionProfileType.SAFE => {

                PermissionProfileV1(
                    projectInfos = projectInfos,
                    groupsPerProjects = groupsPerProjects,
                    isInSystemAdminGroup = false, // remove system admin status
                    isInProjectAdminGroups = Vector.empty[IRI], // remove privileged group membership
                    administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionV1]], // remove administrative permission information
                    defaultObjectAccessPermissionsPerProject = Map.empty[IRI, Set[PermissionV1]] // remove default object access permission information
                )
            }
            case PermissionProfileType.FULL => {

                PermissionProfileV1(
                    projectInfos = projectInfos,
                    groupsPerProjects = groupsPerProjects,
                    isInSystemAdminGroup = isInSystemAdminGroup,
                    isInProjectAdminGroups = isInProjectAdminGroups,
                    administrativePermissionsPerProject = administrativePermissionsPerProject,
                    defaultObjectAccessPermissionsPerProject = defaultObjectAccessPermissionsPerProject
                )
            }
            case _ => throw BadRequestException(s"The requested userProfileType: $permissionProfileType is invalid.")
        }
    }

    def isSystemAdmin: Boolean = {
        isInSystemAdminGroup
    }
}



/**
  * Represents 'knora-base:AdministrativePermission'
  *
  * @param forProject the project this permission applies to.
  * @param forGroup the group this permission applies to.
  * @param hasPermissions the administrative permissions.
  */
case class AdministrativePermissionV1(forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                      forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                      hasPermissions: Seq[PermissionV1] = Seq.empty[PermissionV1]
                                     )

/**
  * Represents information needed during administrative permission creation.
  *
  * @param iri
  * @param forProject
  * @param forGroup
  * @param hasPermissions
  */
case class NewAdministrativePermissionV1(iri: IRI,
                                         forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                         forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                         hasPermissions: Seq[PermissionV1] = Seq.empty[PermissionV1]
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
case class DefaultObjectAccessPermissionV1(forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                           forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                           forResourceClass: IRI = OntologyConstants.KnoraBase.AllResourceClasses,
                                           forProperty: IRI = OntologyConstants.KnoraBase.AllProperties,
                                           hasPermissions: Seq[PermissionV1] = Seq.empty[PermissionV1]
                                          )

/**
  * Represents information needed during default object access permission creation.
  *
  * @param iri
  * @param forProject
  * @param forGroup
  * @param forResourceClass
  * @param forProperty
  * @param hasDefaultChangeRightsPermission
  * @param hasDefaultDeletePermission
  * @param hasDefaultModifyPermission
  * @param hasDefaultViewPermission
  * @param hasDefaultRestrictedViewPermission
  */
case class NewDefaultObjectAccessPermissionV1(iri: IRI,
                                              forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                              forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                              forResourceClass: IRI = OntologyConstants.KnoraBase.AllResourceClasses,
                                              forProperty: IRI = OntologyConstants.KnoraBase.AllProperties,
                                              hasDefaultChangeRightsPermission: Seq[IRI] = Vector.empty[IRI],
                                              hasDefaultDeletePermission: Seq[IRI] = Vector.empty[IRI],
                                              hasDefaultModifyPermission: Seq[IRI] = Vector.empty[IRI],
                                              hasDefaultViewPermission: Seq[IRI] = Vector.empty[IRI],
                                              hasDefaultRestrictedViewPermission: Seq[IRI] = Vector.empty[IRI]
                                             )


/**
  * UserProfile types:
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

object PermissionOperation extends Enumeration {

    type PermissionOperation = Value

    val CREATE = Value("create")
    val UPDATE = Value("update")
    val DELETE = Value("delete")
}

object PermissionType extends Enumeration {

    type PermissionType = Value

    val OAP     = Value(0, "ObjectAccessPermission")
    val AP      = Value(1, "AdministrativePermission")
    val DOAP    = Value(2, "DefaultObjectAccessPermission")

}

/**
  * Case class representing a permission.
  * @param name the name of the permission.
  * @param restrictions the optional set of IRIs providing additional information.
  * @param permissionType the type of the permission.
  */
case class PermissionV1(name: String, restrictions: Set[IRI], permissionType: PermissionType)

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
            restrictions = Set.empty[IRI],
            permissionType = PermissionType.AP
        )
    }

    def ProjectResourceCreateRestrictedPermission(restrictions: Set[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission,
            restrictions = restrictions,
            permissionType = PermissionType.AP
        )
    }

    def ProjectAdminAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminAllPermission,
            restrictions = Set.empty[IRI],
            permissionType = PermissionType.AP
        )
    }

    def ProjectAdminGroupAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminGroupAllPermission,
            restrictions = Set.empty[IRI],
            permissionType = PermissionType.AP
        )
    }

    def ProjectAdminGroupRestrictedPermission(restrictions: Set[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission,
            restrictions = restrictions,
            permissionType = PermissionType.AP
        )
    }

    def ProjectAdminRightsAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminRightsAllPermission,
            restrictions = Set.empty[IRI],
            permissionType = PermissionType.AP
        )
    }

    def ProjectAdminOntologyAllPermission = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.ProjectAdminOntologyAllPermission,
            restrictions = Set.empty[IRI],
            permissionType = PermissionType.AP
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // Default Object Access Permissions
    ///////////////////////////////////////////////////////////////////////////

    def DefaultChangeRightsPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultChangeRightsPermission,
            restrictions = restriction,
            permissionType = PermissionType.DOAP
        )
    }

    def DefaultDeletePermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultDeletePermission,
            restrictions = restriction,
            permissionType = PermissionType.DOAP
        )
    }

    def DefaultModifyPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultModifyPermission,
            restrictions = restriction,
            permissionType = PermissionType.DOAP
        )
    }

    def DefaultViewPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultViewPermission,
            restrictions = restriction,
            permissionType = PermissionType.DOAP
        )
    }

    def DefaultRestrictedViewPermission(restriction: Set[IRI] = Set.empty[IRI]) = {
        PermissionV1(
            name = OntologyConstants.KnoraBase.DefaultRestrictedViewPermission,
            restrictions = restriction,
            permissionType = PermissionType.DOAP
        )
    }

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
        def write(permissionProfileType: PermissionProfileType): JsValue = {
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
        def write(permissionTemplate: PermissionsTemplate): JsValue = {
            JsObject(Map("permission_operation" -> permissionTemplate.toString.toJson))
        }
    }



    implicit object PermissionOperationFormat extends JsonFormat[PermissionOperation] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[PermissionOperation]] into [[JsValue]] for formatting as JSON.
          *
          * @param permissionOperation the [[PermissionOperation]] to be converted.
          * @return a [[JsValue]].
          */
        def write(permissionOperation: PermissionOperation): JsValue = {
            JsObject(Map("permission_operation" -> permissionOperation.toString.toJson))
        }
    }

    /**
      * Converts between [[PermissionV1]] objects and [[JsValue]] objects.
      */
    implicit object PermissionV1Format extends JsonFormat[PermissionV1] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts an [[PermissionV1]] to a [[JsValue]].
          *
          * @param permissionV1 a [[PermissionV1]]
          * @return a [[JsValue]].
          */
        def write(permissionV1: PermissionV1): JsValue = JsObject(
            Map(
                "name" -> JsString(permissionV1.name),
                "restrictions" -> JsArray(permissionV1.restrictions.toVector.map(_.toString.toJson)),
                "permission_type" -> permissionV1.permissionType.toString.toJson
            )
        )
    }

    implicit object PermissionTypeV1Format extends JsonFormat[PermissionType] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts an [[PermissionType]] to a [[JsValue]].
          *
          * @param permissionType a [[PermissionType]]
          * @return a [[JsValue]].
          */
        def write(permissionType: PermissionType): JsValue = {
            JsObject(Map("permission_type" -> permissionType.toString.toJson))
        }
    }

    implicit val administrativePermissionV1Format: JsonFormat[AdministrativePermissionV1] = jsonFormat3(AdministrativePermissionV1)
    implicit val defaultObjectAccessPermissionV1Format: JsonFormat[DefaultObjectAccessPermissionV1] = jsonFormat5(DefaultObjectAccessPermissionV1)
    implicit val permissionProfileV1Format: JsonFormat[PermissionProfileV1] = jsonFormat6(PermissionProfileV1)
    //implicit val templatePermissionsCreateResponseV1Format: RootJsonFormat[TemplatePermissionsCreateResponseV1] = jsonFormat4(TemplatePermissionsCreateResponseV1)
    implicit val administrativePermissionOperationResponseV1Format: RootJsonFormat[AdministrativePermissionOperationResponseV1] = jsonFormat4(AdministrativePermissionOperationResponseV1)
    implicit val defaultObjectAccessPermissionOperationResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionOperationResponseV1] = jsonFormat4(DefaultObjectAccessPermissionOperationResponseV1)
}