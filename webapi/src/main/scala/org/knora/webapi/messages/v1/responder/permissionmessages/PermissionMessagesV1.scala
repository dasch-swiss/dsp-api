package org.knora.webapi.messages.v1.responder.permissionmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.KnoraRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
  */
sealed trait PermissionsResponderRequestV1 extends KnoraRequestV1


/**
  * A message that requests all permissions attached to groups a user is a member of in the context of a single project.
  * A successful response will contain a list of [[PermissionV1]].
  *
  * @param userProfileV1
  */
case class GetGroupPermissionsV1(projectIri: IRI, userProfileV1: UserProfileV1) extends PermissionsResponderRequestV1



// Permissions UserGroups
// Default Permissions on Resources and Values
// Default Permissions on UserGroups


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

case class PermissionV1