package org.knora.webapi.messages.v1.responder.permissionmessages

import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
  */
sealed trait PermissionsResponderRequestV1 extends KnoraRequestV1

/**
  * A message that requests the creation of permissions (administrative and default) for a certain project
  * based on a predefined template. These permissions can be applied to a newly created or an exesting project.
  * In the case of an existing project, this operation behaves destructive, in the sense that all existing permissions
  * attached to a project are deleted, before any new permissions are created.
  *
  * @param projectIri the IRI of the project.
  * @param permissionsTemplate the permissions template.
  */
case class TemplatePermissionsCreateRequest(projectIri: IRI, permissionsTemplate: PermissionsTemplate.Value) extends PermissionsResponderRequestV1
case class TemplatePermissionsCreateResponse(success: Boolean,
                                             msg: String,
                                             administrativePermissions: Seq[AdministrativePermissionV1],
                                             defaultObjectAccessPermissions: Seq[DefaultObjectAccessPermissionV1]
                                            )

/**
  * A message that requests the IRIs of all administrative permissions defined inside a project.
  * A successful response will contain a list of IRIs.
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
  * Response to the delete request
  * @param success
  * @param msg
  */
case class AdministrativePermissionDeleteResponseV1(success: Boolean, msg: String)

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
case class DefaultObjectAccessPermissionsForProjectGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

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
  * Response to the delete request.
  *
  * @param success
  * @param msg
  */
case class DefaultObjectAccessPermissionDeleteResponseV1(success: Boolean, msg: String)

/**
  * Update a single [[DefaultObjectAccessPermissionV1]].
  *
  * @param userProfileV1
  */
case class DefaultObjectAccessPermissionUpdateRequestV1(userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

// Responses

/**
  * Represents an answer to an administrative permission creating/modifying operation.
  * @param administrativePermissionV1
  */
case class AdministrativePermissionOperationResponseV1(administrativePermissionV1: AdministrativePermissionV1) extends KnoraResponseV1 {
    def toJsValue = PermissionV1JsonProtocol.administrativePermissionOperationResponseV1Format.write(this)
}

/**
  * Represents an answer to a default object access permission creating/modifying operation.
  * @param defaultObjectAccessPermissionV1
  */
case class DefaultObjectAccessPermissionOperationResponseV1(defaultObjectAccessPermissionV1: DefaultObjectAccessPermissionV1) extends KnoraResponseV1 {
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
                                      resourceCreationPermissionValues: Seq[IRI] = Vector.empty[IRI],
                                      hasRestrictedProjectResourceCreatePermission: Seq[IRI] = Vector.empty[IRI],
                                      projectAdministrationPermissionValues: Seq[IRI] = Vector.empty[IRI],
                                      hasRestrictedProjectGroupAdminPermission: Seq[IRI] = Vector.empty[IRI],
                                      ontologyAdministrationPermissionValues: Seq[IRI] = Vector.empty[IRI]
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




//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting


object PermissionV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val administrativePermissionOperationResponseV1Format: RootJsonFormat[AdministrativePermissionOperationResponseV1] = jsonFormat1(AdministrativePermissionOperationResponseV1)
    implicit val defaultObjectAccessPermissionOperationResponseV1Format: RootJsonFormat[DefaultObjectAccessPermissionOperationResponseV1] = jsonFormat1(DefaultObjectAccessPermissionOperationResponseV1)
}