package org.knora.webapi.messages.v1.responder.permissionmessages

import org.knora.webapi.messages.v1.responder.KnoraRequestV1


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `PermissionsResponderV1`.
  */
sealed trait PermissionsResponderRequestV1 extends KnoraRequestV1