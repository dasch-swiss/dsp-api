package org.knora.webapi.messages.v1.responder.permissionmessages

import org.knora.webapi.{IRI, OntologyConstants}
import org.knora.webapi.messages.v1.responder.KnoraRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
  */
sealed trait PermissionsResponderRequestV1 extends KnoraRequestV1

/**
  * A message that requests the IRIs of all administrative permissions defined inside a project.
  * A successful response will contain a list of IRIs.
  *
  * @param projectIri the project for which the administrative permissions are queried.
  * @param userProfileV1 the user initiation the request.
  */
case class GetProjectAdministrativePermissionsV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests all administrative permissions attached to a group inside a project.
  * A successful response will contain an [[AdministrativePermissionV1]] object.
  *
  * @param projectIri the project to which the group belongs to.
  * @param groupIri the group for which we want to retrieve the permission object.
  * @param userProfileV1 the user initiating this request.
  */
case class GetGroupAdministrativePermissionV1(projectIri: IRI, groupIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests an administrative permission object identified through his IRI.
  * A successful response will contain an [[AdministrativePermissionV1]] object.
  *
  * @param administrativePermissionIri the iri of the administrative permission object.
  */
case class GetAdministrativePermissionV1(administrativePermissionIri: IRI) extends PermissionsResponderRequestV1

/**
  * A message that requests all IRIs of default object access permissions defined inside a project.
  * A successful response will contain a list with IRIs of default object access permissions.
  *
  * @param projectIri the project for which the default object access permissions are queried.
  * @param userProfileV1 the user initiating this request.
  */
case class GetProjectDefaultObjectAccessPermissionsV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1

/**
  * A message that requests a default object access permission object identified through his IRI.
  * A successful response will contain an [[DefaultObjectAccessPermissionV1]] object.
  *
  * @param defaultObjectAccessPermissionIri the iri of the default object access permission object.
  */
case class GetDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri: IRI) extends PermissionsResponderRequestV1

// Permissions UserGroups
// Default Permissions on Resources and Values
// Default Permissions on UserGroups


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents 'knora-base:AdministrativePermission'
  *
  * @param forProject
  * @param forGroup
  * @param resourceCreationPermissionValues
  * @param resourceCreationPermissionProperties
  * @param projectAdministrationPermissionValues
  * @param projectAdministrationPermissionProperties
  * @param ontologyAdministrationPermissionValues
  * @param ontologyAdministrationPermissionProperties
  */
case class AdministrativePermissionV1(forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                      forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                      resourceCreationPermissionValues: Option[List[IRI]] = None,
                                      resourceCreationPermissionProperties: Option[Map[IRI, List[IRI]]] = None,
                                      projectAdministrationPermissionValues: Option[List[IRI]] = None,
                                      projectAdministrationPermissionProperties: Option[Map[IRI, List[IRI]]] = None,
                                      ontologyAdministrationPermissionValues: Option[List[IRI]] = None,
                                      ontologyAdministrationPermissionProperties: Option[Map[IRI, List[IRI]]] = None
                                     )

/**
  * Represents 'knora-base:DefaultObjectAccessPermission'
  *
  * @param forProject
  * @param forGroup
  * @param forResourceClass
  * @param forProperty
  * @param defaultObjectAccessPermissionProperties
  */
case class DefaultObjectAccessPermissionV1(forProject: IRI = OntologyConstants.KnoraBase.AllProjects,
                                           forGroup: IRI = OntologyConstants.KnoraBase.AllGroups,
                                           forResourceClass: IRI = OntologyConstants.KnoraBase.AllResourceClasses,
                                           forProperty: IRI = OntologyConstants.KnoraBase.AllProperties,
                                           defaultObjectAccessPermissionProperties: Option[Map[IRI, List[IRI]]] = None
                                          )