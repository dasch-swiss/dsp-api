package org.knora.webapi.responders.v2.resources

import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.responders.v2.ResponderWithStandoffV2

import scala.concurrent.Future

abstract class ResourcesResponderV2(responderData: ResponderData) extends ResponderWithStandoffV2(responderData) {
  def receive(msg: ResourcesResponderRequestV2): Future[Any]
}
