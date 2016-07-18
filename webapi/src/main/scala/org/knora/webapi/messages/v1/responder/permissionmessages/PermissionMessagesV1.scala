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
  * A message that requests all permissions attached to a group inside a project.
  * A successful response will contain a [[PermissionV1]] object.
  *
  * @param projectIri the project to which the group belongs to.
  * @param groupIri the group for which we want to retrieve the permission object.
  * @param userProfileV1 the user initiating this request.
  */
case class GetGroupPermissionV1(projectIri: IRI, groupIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1



// Permissions UserGroups
// Default Permissions on Resources and Values
// Default Permissions on UserGroups


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents 'knora-base:Permission'
  *
  * @param forProject
  * @param forGroup
  * @param forResourceClass
  * @param forProperty
  * @param resourceCreationPermissionValues
  * @param resourceCreationPermissionProperties
  * @param projectAdministrationPermissionValues
  * @param projectAdministrationPermissionProperties
  * @param ontologyAdministrationPermissionValues
  * @param ontologyAdministrationPermissionProperties
  * @param defaultObjectAccessPermissionProperties
  */
case class PermissionV1(forProject: IRI = OntologyConstants.KnoraBase.ProjectNotApplicable,
                        forGroup: IRI = OntologyConstants.KnoraBase.GroupNotApplicable,
                        forResourceClass: IRI = OntologyConstants.KnoraBase.ResourceClassNotApplicable,
                        forProperty: IRI = OntologyConstants.KnoraBase.PropertyNotApplicable,
                        resourceCreationPermissionValues: Option[List[IRI]] = None,
                        resourceCreationPermissionProperties: Option[Map[IRI, List[IRI]]] = None,
                        projectAdministrationPermissionValues: Option[List[IRI]] = None,
                        projectAdministrationPermissionProperties: Option[Map[IRI, List[IRI]]] = None,
                        ontologyAdministrationPermissionValues: Option[List[IRI]] = None,
                        ontologyAdministrationPermissionProperties: Option[Map[IRI, List[IRI]]] = None,
                        defaultObjectAccessPermissionProperties: Option[Map[IRI, List[IRI]]] = None
                       )