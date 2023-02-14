package org.knora.webapi.core
import zio._

import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersResponderRequestADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.listsmessages.ListsResponderRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.ActorToZioBridge

case class AppRouterRelayingMessageHandler(zioBridge: ActorToZioBridge) extends MessageHandler {

  override def handle(message: ResponderRequest): Task[Any] = zioBridge.askAppActor(message)

  /**
   * Must contain exactly the same cases apart from the general [[ResponderRequest]]
   * as handled in [[org.knora.webapi.core.actors.RoutingActor#receive()]].
   *
   * This way all messages sent to the [[MessageRelay]] will be forwarded to the [[org.knora.webapi.core.actors.RoutingActor]].
   */
  override def isResponsibleFor(message: ResponderRequest): Boolean = message match {

    // V1 request messages
    case _: CkanResponderRequestV1      => true
    case _: ResourcesResponderRequestV1 => true
    case _: ValuesResponderRequestV1    => true
    case _: ListsResponderRequestV1     => true
    case _: SearchResponderRequestV1    => true
    case _: OntologyResponderRequestV1  => true
    case _: StandoffResponderRequestV1  => true
    case _: UsersResponderRequestV1     => true
    case _: ProjectsResponderRequestV1  => true

    // V2 request messages
    case _: OntologiesResponderRequestV2 => true
    case _: SearchResponderRequestV2     => true
    case _: ResourcesResponderRequestV2  => true
    case _: ValuesResponderRequestV2     => true
    case _: StandoffResponderRequestV2   => true
    case _: ListsResponderRequestV2      => true

    // Admin request messages
    case _: ProjectsResponderRequestADM => true
    case _: StoreResponderRequestADM    => true
    case _: UsersResponderRequestADM    => true
    case _: CacheServiceRequest         => true
    case _: IIIFRequest                 => true
    case _: TriplestoreRequest          => true

    case _ => false
  }
}

object AppRouterRelayingMessageHandler {
  val layer: URLayer[MessageRelay with ActorToZioBridge, AppRouterRelayingMessageHandler] = ZLayer.fromZIO {
    for {
      zioBridge    <- ZIO.service[ActorToZioBridge]
      messageRelay <- ZIO.service[MessageRelay]
      handler       = AppRouterRelayingMessageHandler(zioBridge)
      _            <- messageRelay.subscribe(handler)
    } yield handler
  }
}
