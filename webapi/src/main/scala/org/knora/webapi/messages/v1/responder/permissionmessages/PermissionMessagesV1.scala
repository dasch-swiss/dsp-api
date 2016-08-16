package org.knora.webapi.messages.v1.responder.permissionmessages

import org.knora.webapi
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionOperation.PermissionOperation
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsTemplate.PermissionsTemplate
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import spray.httpx.SprayJsonSupport
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
  */
sealed trait PermissionsResponderRequestV1 extends KnoraRequestV1

/**
  * A message that requests the permissions for the supplied user.
  *
  * @param projectGroups a map of projects pointing to a list of groups the user is a member of.
  */
case class GetUserAdministrativePermissionsRequestV1(projectGroups: Map[IRI, List[IRI]]) extends PermissionsResponderRequestV1

/**
  * A message that requests the permissions for the supplied user.
  *
  * @param projectGroups a map of projects pointing to a list of groups the user is a member of.
  */
case class GetUserDefaultObjectAccessPermissionsRequestV1(projectGroups: Map[IRI, List[IRI]]) extends PermissionsResponderRequestV1


/**
  * A message that requests the creation of permissions (administrative and default) for a certain project
  * based on a predefined template. These permissions can be applied to a newly created or an exesting project.
  * In the case of an existing project, this operation behaves destructive, in the sense that all existing permissions
  * attached to a project are deleted, before any new permissions are created.
  *
  * @param projectIri the IRI of the project.
  * @param permissionsTemplate the permissions template.
  */
case class TemplatePermissionsCreateRequestV1(projectIri: IRI, permissionsTemplate: PermissionsTemplate, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1


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
  */
case class AdministrativePermissionGetRequestV1(administrativePermissionIri: IRI) extends PermissionsResponderRequestV1

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
  */
case class DefaultObjectAccessPermissionGetRequestV1(defaultObjectAccessPermissionIri: IRI) extends PermissionsResponderRequestV1

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
case class TemplatePermissionsCreateResponseV1(success: Boolean,
                                             msg: String,
                                             administrativePermissions: Seq[AdministrativePermissionV1],
                                             defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionV1]
                                            ) extends KnoraResponseV1 {
    def toJsValue = PermissionV1JsonProtocol.templatePermissionsCreateResponseV1Format.write(this)
}

/**
  * Represents an answer to an administrative permission creating/modifying/deletion operation.
  * @param success
  * @param operationType
  * @param administrativePermissionV1
  * @param msg
  */
case class AdministrativePermissionOperationResponseV1(success: Boolean, operationType: PermissionOperation, administrativePermissionV1: Option[AdministrativePermissionV1], msg: String) extends KnoraResponseV1 {
    def toJsValue = PermissionV1JsonProtocol.administrativePermissionOperationResponseV1Format.write(this)
}

/**
  * Represents an answer to a default object access permission creating/modifying/deletion operation.
  * @param success
  * @param operationType
  * @param defaultObjectAccessPermissionV1
  * @param msg
  */
case class DefaultObjectAccessPermissionOperationResponseV1(success: Boolean, operationType: PermissionOperation, defaultObjectAccessPermissionV1: Option[DefaultObjectAccessPermissionV1], msg: String) extends KnoraResponseV1 {
    def toJsValue = PermissionV1JsonProtocol.defaultObjectAccessPermissionOperationResponseV1Format.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents 'knora-base:AdministrativePermission'
  *
  * @param forProject the project this permission applies to.
  * @param forGroup the group this permission applies to.
  * @param resourceCreationPermissionValues a list of resource creation permission values given to members of the project/group combination.
  * @param hasRestrictedProjectResourceCreatePermission a list of resource classes to which the members of the project/group combination is restricted to create.
  * @param projectAdministrationPermissionValues a list of project administration permission value given to members of the project/group combination.
  * @param hasRestrictedProjectGroupAdminPermission a list of user groups to which the members of the project/group combination is restricted to perform administrative tasks.
  * @param ontologyAdministrationPermissionValues a list of ontology administrative permission values given to members of the project/group combination.
  */
case class AdministrativePermissionV1(forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                      forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                      hasPermissions: Array[String] = Array.empty[String]
                                     )

/**
  * Represents information needed during administrative permission creation.
  *
  * @param iri
  * @param forProject
  * @param forGroup
  * @param resourceCreationPermissionValues
  * @param hasRestrictedProjectResourceCreatePermission
  * @param projectAdministrationPermissionValues
  * @param hasRestrictedProjectGroupAdminPermission
  * @param ontologyAdministrationPermissionValues
  */
case class NewAdministrativePermissionV1(iri: IRI,
                                         forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                         forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                         resourceCreationPermissionValues: Seq[IRI] = Vector.empty[IRI],
                                         hasRestrictedProjectResourceCreatePermission: Seq[IRI] = Vector.empty[IRI],
                                         projectAdministrationPermissionValues: Seq[IRI] = Vector.empty[IRI],
                                         hasRestrictedProjectGroupAdminPermission: Seq[IRI] = Vector.empty[IRI],
                                         ontologyAdministrationPermissionValues: Seq[IRI] = Vector.empty[IRI]
                                        )

/**
  * Represents 'knora-base:DefaultObjectAccessPermission'
  *
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
case class DefaultObjectAccessPermissionV1(forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
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

object Permissions {

    object Creation {
        val All = "ProjectResourceCreateAllPermission"
        val Restricted = "ProjectResourceCreateRestrictedPermission"
    }

    object ProjectAdmin {
        val All = "ProjectAdminAllPermission"
        val GroupAll = "ProjectAdminGroupAllPermission"
        val GroupRestricted = "ProjectAdminGroupRestrictedPermission"
        val RightsAll = "ProjectAdminRightsAllPermission"
        val OntologyAll = "ProjectAdminOntologyAllPermission"
    }

    object Default {
        val RestrictedView = "RV"
        val View = "V"
        val Modify = "M"
        val Delete = "D"
        val ChangeRights = "CR"
    }

}


object KnoraPermissions {

    sealed abstract class Permission(val order: Int,
                                     val name: String,
                                     val category: String) extends Ordered[Permission] {

        def compare(that: Permission) = this.order - that.order

        override def toString = name
    }

    case object SA                      extends Permission(99, "SystemAdminPermission",                     "SYS")
    case object RES_CREATE_ALL          extends Permission(10, "ProjectResourceCreateAllPermission",        "RES_CREATE")
    case object RES_CREATE_RESTRICTED   extends Permission( 9, "ProjectResourceCreateRestrictedPermission", "RES_CREATE")
    case object PROJ_ADM_ALL            extends Permission(10, "ProjectAdminAllPermission",                 "PROJ_ADM")
    case object PROJ_ADM_GRP_ALL        extends Permission( 9, "ProjectAdminGroupAllPermission",            "PROJ_ADM")
    case object PROJ_ADM_GRP_RESTRICTED extends Permission( 8, "ProjectAdminGroupRestrictedPermission",     "PROJ_ADM")
    case object PROJ_ADM_RIGHTS_ALL     extends Permission( 9, "ProjectAdminRightsAllPermission",           "PROJ_ADM")
    case object PROJ_ADM_ONTO_ALL       extends Permission( 9, "ProjectAdminOntologyAllPermission",         "PROJ_ADM")

    case object RESTRICTED_VIEW extends Permission(0, "RV", "DOAP")
    case object VIEW            extends Permission(1, "V",  "DOAP")
    case object MODIFY          extends Permission(2, "M",  "DOAP")
    case object DELETE          extends Permission(3, "D",  "DOAP")
    case object CHANGE_RIGHTS   extends Permission(4, "CR", "DOAP")

    val administrativePermissions: Set[Permission] = Set(
        SA,
        RES_CREATE_ALL,
        RES_CREATE_RESTRICTED,
        PROJ_ADM_ALL,
        PROJ_ADM_GRP_ALL,
        PROJ_ADM_GRP_RESTRICTED,
        PROJ_ADM_RIGHTS_ALL,
        PROJ_ADM_ONTO_ALL
    )

    val defaultObjectAccessPermissions: Set[Permission] = Set(
        RESTRICTED_VIEW,
        VIEW,
        MODIFY,
        DELETE,
        CHANGE_RIGHTS
    )

}



//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting


object PermissionV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit object PermissionOperation extends JsonFormat[PermissionOperation] {
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
    implicit val administrativePermissionV1Format: JsonFormat[AdministrativePermissionV1] = jsonFormat3(AdministrativePermissionV1)
    implicit val defaultObjectAccessPermissionV1Format: JsonFormat[DefaultObjectAccessPermissionV1] = jsonFormat9(DefaultObjectAccessPermissionV1)
    implicit val templatePermissionsCreateResponseV1Format: RootJsonFormat[TemplatePermissionsCreateResponseV1] = jsonFormat4(TemplatePermissionsCreateResponseV1)
    implicit val administrativePermissionOperationResponseV1Format: RootJsonFormat[AdministrativePermissionOperationResponseV1] = jsonFormat4(AdministrativePermissionOperationResponseV1)
    implicit val defaultObjectAccessPermissionOperationResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionOperationResponseV1] = jsonFormat4(DefaultObjectAccessPermissionOperationResponseV1)
}